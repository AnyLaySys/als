package sui.k.als.qemu.vm

import android.media.*
import android.os.*
import android.system.*
import sui.k.als.*
import java.util.concurrent.atomic.*

internal class VMAudioInput private constructor(
    private val recorder: AudioRecord,
    private val readEnd: ParcelFileDescriptor,
    private val writeEnd: ParcelFileDescriptor,
    private val chunk: ByteArray,
) : AutoCloseable {
    private val active = AtomicBoolean(true)
    private val captureThread = Thread(::capture, "audio-in")

    val readFd: Int
        get() = readEnd.fd

    init {
        recorder.startRecording()
        check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            "AudioRecord did not enter recording state"
        }
        captureThread.start()
        Log.info("VM", "Audio input bridge started: 48000 Hz PCM16 mono")
    }

    private fun capture() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        while (active.get()) {
            val count = recorder.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
            if (count <= 0) {
                if (active.get()) {
                    Log.error("VM", "AudioRecord read failed: $count")
                }
                continue
            }
            try {
                Os.write(writeEnd.fileDescriptor, chunk, 0, count)
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.EAGAIN && active.get()) {
                    Log.error("VM", "Audio input bridge write failed", error)
                    return
                }
            }
        }
    }

    override fun close() {
        if (!active.compareAndSet(true, false)) {
            return
        }
        runCatching { recorder.stop() }
        captureThread.join()
        recorder.release()
        writeEnd.close()
        readEnd.close()
    }

    companion object {
        private const val sampleRate = 48000
        private const val channelMask = AudioFormat.CHANNEL_IN_MONO
        private const val encoding = AudioFormat.ENCODING_PCM_16BIT

        fun open(): VMAudioInput {
            val minimum = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
            check(minimum > 0) { "AudioRecord does not support 48 kHz PCM16 mono input" }
            val format = AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(channelMask)
                .setEncoding(encoding).build()
            val recorder =
                AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minimum * 2, sampleRate / 50 * 2)).build()
            try {
                check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                    "AudioRecord initialization failed"
                }
                val pipe = ParcelFileDescriptor.createPipe()
                try {
                    val flags = Os.fcntlInt(pipe[1].fileDescriptor, OsConstants.F_GETFL, 0)
                    Os.fcntlInt(
                        pipe[1].fileDescriptor, OsConstants.F_SETFL, flags or OsConstants.O_NONBLOCK
                    )
                    return VMAudioInput(
                        recorder, pipe[0], pipe[1], ByteArray(sampleRate * 9 / 1000 * 2)
                    )
                } catch (error: Throwable) {
                    pipe[0].close()
                    pipe[1].close()
                    throw error
                }
            } catch (error: Throwable) {
                recorder.release()
                throw error
            }
        }
    }
}