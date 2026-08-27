package sui.k.als.qemu.vm

import android.app.*
import android.content.*
import android.os.*
import android.system.*
import android.view.*
import sui.k.als.*
import java.io.*

class QemuService : Service() {
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var request: Request? = null
    private var surface: Surface? = null
    private var refreshRate = 0f
    private var callback: QemuCallback? = null
    private var token = 0L
    private var started = false
    private var loaded = false
    private var nativeRunning = false
    private var stopping = false
    private var finished = false

    private val binder = object : Qemu.Stub() {
        override fun start(
            token: Long,
            backend: String,
            workDir: String,
            configuration: String,
            consolePid: Int,
            surface: Surface?,
            refreshRate: Float,
            callback: QemuCallback?,
        ) {
            val value = runCatching {
                Request(VMBackend.valueOf(backend), workDir, configuration, consolePid)
            }.getOrElse {
                replyFailure(callback, token, it)
                return
            }
            val accepted = synchronized(lock) {
                if (request != null || finished) {
                    false
                } else {
                    request = value
                    this@QemuService.surface = surface?.takeIf(Surface::isValid)
                    this@QemuService.refreshRate = refreshRate
                    this@QemuService.callback = callback
                    this@QemuService.token = token
                    true
                }
            }
            if (!accepted) {
                replyFailure(
                    callback, token, IllegalStateException("QEMU process is already in use")
                )
                return
            }
            startWhenReady()
        }

        override fun setSurface(token: Long, surface: Surface?, refreshRate: Float) {
            val update = synchronized(lock) {
                if (token != this@QemuService.token || finished) {
                    null
                } else {
                    this@QemuService.surface = surface?.takeIf(Surface::isValid)
                    this@QemuService.refreshRate = refreshRate
                    if (loaded && !stopping) {
                        SurfaceState(this@QemuService.surface, refreshRate)
                    } else {
                        null
                    }
                }
            }
            update?.let { AGL.setSurface(it.surface, it.refreshRate) }
            startWhenReady()
        }

        override fun pointer(token: Long, x: Float, y: Float, buttons: Int) {
            if (active(token)) {
                VMNative.pointer(x, y, buttons)
            }
        }

        override fun scroll(token: Long, x: Float, y: Float) {
            if (active(token)) {
                VMNative.scroll(x, y)
            }
        }

        override fun key(token: Long, scanCode: Int, down: Boolean) {
            if (active(token)) {
                VMNative.key(scanCode, down)
            }
        }

        override fun stop(token: Long) {
            if (token == this@QemuService.token) {
                stop()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent): Boolean {
        stop()
        return false
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun startWhenReady() {
        val value = synchronized(lock) {
            request?.takeIf {
                !started && !stopping && !finished && surface?.isValid == true
            }?.also { started = true }
        } ?: return
        runCatching { Thread({ run(value) }, "qemu").start() }.onFailure(::finishFailure)
    }

    private fun run(value: Request) {
        var audioInput: VMAudioInput? = null
        var binder: Thread? = null
        var status: Int? = null
        var error: Throwable? = null
        try {
            VMNative.load(value.backend)
            val prepared = value.backend.prepare(value.configuration)
            val initialSurface = synchronized(lock) {
                loaded = true
                if (stopping || finished) null else SurfaceState(surface, refreshRate)
            }
            initialSurface?.let { AGL.setSurface(it.surface, it.refreshRate) }
            if (!stopped()) {
                audioInput = if (prepared.args.any {
                        it.startsWith("virtio-snd-pci,") && it.contains("input=true")
                    }) {
                    VMAudioInput.open()
                } else {
                    null
                }
                Log.info("VM", "Preflight started")
                prepared.preflight()
                Log.info("VM", "Preflight passed")
            }
            if (!stopped()) {
                awaitConsole(value.consolePid)
                val redirectError = VMNative.redirectStdio(value.consolePid)
                check(redirectError == 0) {
                    "QEMU console connection failed: ${Os.strerror(redirectError)}"
                }
                binder = Thread({
                    val deadline = SystemClock.uptimeMillis() + 270
                    while (!Thread.currentThread().isInterrupted && SystemClock.uptimeMillis() < deadline) {
                        VMNative.rebindOutput(value.consolePid)
                        try {
                            Thread.sleep(3)
                        } catch (_: InterruptedException) {
                            return@Thread
                        }
                    }
                }, "console")
                binder.start()
            }
            if (!stopped()) {
                synchronized(lock) {
                    nativeRunning = true
                }
                replyRunning()
                status = VMNative.run(value.workDir, prepared.args, audioInput?.readFd ?: -1)
            }
        } catch (value: Throwable) {
            error = value
        } finally {
            synchronized(lock) {
                nativeRunning = false
            }
            binder?.interrupt()
            binder?.join()
            runCatching { VMNative.restoreStdio() }
            audioInput?.close()
        }
        if (stopped()) {
            finishExited(0)
        } else if (error != null) {
            finishFailure(error)
        } else {
            finishExited(checkNotNull(status))
        }
    }

    private fun active(token: Long): Boolean = synchronized(lock) {
        token == this.token && nativeRunning && !stopping && !finished
    }

    private fun stop() {
        val action = synchronized(lock) {
            if (finished || stopping) {
                0
            } else {
                stopping = true
                when {
                    nativeRunning -> 1
                    started -> 2
                    else -> 3
                }
            }
        }
        if (action == 1) {
            VMNative.stop()
        }
        if (action == 3) {
            finishExited(0)
        } else if (action != 0) {
            main.postDelayed({
                if (stopped()) {
                    finishExited(0)
                }
            }, 3000)
        }
    }

    private fun stopped(): Boolean = synchronized(lock) { stopping }

    private fun replyRunning() {
        val value = synchronized(lock) {
            if (finished || stopping) null else Finish(callback, request, token)
        } ?: return
        runCatching { value.callback?.running(value.token) }
    }

    private fun finishExited(status: Int) {
        val value = takeFinish() ?: return
        if (status == 0) {
            Log.info("VM", "QEMU returned 0")
        } else {
            Log.info("VM", "QEMU returned $status")
            value.request?.let { writeConsole(it, "QEMU exited with status $status") }
        }
        runCatching { value.callback?.exited(value.token, status) }
        end()
    }

    private fun finishFailure(error: Throwable) {
        val value = takeFinish() ?: return
        val message = error.message ?: error.javaClass.simpleName
        Log.error("VM", "QEMU launch failed", error)
        value.request?.let { writeConsole(it, "QEMU launch failed: $message") }
        runCatching { value.callback?.failed(value.token, message) }
        end()
    }

    private fun replyFailure(callback: QemuCallback?, token: Long, error: Throwable) {
        runCatching { callback?.failed(token, error.message ?: error.javaClass.simpleName) }
    }

    private fun takeFinish(): Finish? = synchronized(lock) {
        if (finished) {
            null
        } else {
            finished = true
            Finish(callback, request, token)
        }
    }

    private fun end() {
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 100)
    }

    private fun writeConsole(value: Request, message: String) {
        if (value.consolePid <= 0) {
            return
        }
        runCatching {
            FileOutputStream("/proc/${value.consolePid}/fd/0").use {
                it.write("\r\n$message\r\n".toByteArray())
            }
        }
    }

    private fun awaitConsole(pid: Int) {
        check(pid > 0) { "QEMU console is unavailable" }
        val processName = File("/proc/$pid/comm")
        val deadline = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < deadline) {
            if (runCatching { processName.readText().trim() }.getOrNull() == "tail") {
                return
            }
            Thread.sleep(9)
        }
        error("QEMU console did not become ready")
    }

    private data class Request(
        val backend: VMBackend,
        val workDir: String,
        val configuration: String,
        val consolePid: Int,
    )

    private data class SurfaceState(val surface: Surface?, val refreshRate: Float)

    private data class Finish(
        val callback: QemuCallback?,
        val request: Request?,
        val token: Long,
    )
}