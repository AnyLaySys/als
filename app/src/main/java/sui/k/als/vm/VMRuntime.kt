package sui.k.als.vm

import android.os.*
import android.system.*
import android.view.*
import androidx.compose.runtime.*
import sui.k.als.log.*
import java.io.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

enum class VMRunState {
    Idle, Starting, Running, Stopping, Stopped, Failed
}

object VMRuntime {
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor {
        Thread(it, "qemu-agl")
    }
    private var launch: VMLaunch? = null
    private var preparedLaunch: VMPreparedLaunch? = null
    private var launched = false
    var state by mutableStateOf(VMRunState.Idle)
        private set
    var failureMessage by mutableStateOf<String?>(null)
        private set

    internal val currentLaunch: VMLaunch?
        get() = synchronized(lock) { launch }

    internal fun prepare(value: VMLaunch) {
        val accepted = synchronized(lock) {
            !launched || state == VMRunState.Stopped || state == VMRunState.Failed
        }
        if (!accepted) {
            return
        }
        synchronized(lock) {
            launch = value
            failureMessage = null
            state = VMRunState.Starting
        }
        executor.execute {
            val result = runCatching {
                VMNative.load(value.backend)
                value.backend.prepare(value.configuration)
            }
            result.onSuccess { prepared ->
                ALSLog.info(
                    "AGL", "Prepare ${value.backend.libraryName} ${value.workDir} ${
                        prepared.args.joinToString(" ")
                    }"
                )
                val retained = synchronized(lock) {
                    if (!launched && launch === value) {
                        preparedLaunch = prepared
                        true
                    } else {
                        false
                    }
                }
                if (retained) {
                    main.post {
                        failureMessage = null
                        state = VMRunState.Idle
                    }
                }
            }.onFailure { error ->
                ALSLog.error("AGL", "Launch preparation failed", error)
                writeConsole(
                    value,
                    "QEMU launch preparation failed: ${error.message ?: error.javaClass.simpleName}"
                )
                synchronized(lock) {
                    preparedLaunch = null
                    launched = false
                    launch = value
                }
                main.post {
                    state = VMRunState.Failed
                    failureMessage = error.message ?: error.javaClass.simpleName
                }
            }
        }
    }

    fun attach(surface: Surface, refreshRate: Float) {
        val startLaunch: VMLaunch?
        val startPrepared: VMPreparedLaunch?
        val updateSurface: Boolean
        synchronized(lock) {
            startLaunch = if (!launched && state != VMRunState.Failed) launch else null
            startPrepared = if (startLaunch != null) preparedLaunch else null
            updateSurface =
                startLaunch == null && launched && state != VMRunState.Stopping &&
                    state != VMRunState.Stopped && state != VMRunState.Failed
            if (startLaunch != null && startPrepared != null) {
                launched = true
                state = VMRunState.Starting
            }
        }
        if (startLaunch == null || startPrepared == null) {
            if (updateSurface) {
                VMNative.setSurface(surface, refreshRate)
            }
            return
        }
        ALSLog.info("AGL", "Starting ${startLaunch.backend.libraryName}")
        executor.execute {
            val result = runCatching {
                val audioInput = if (
                    startPrepared.args.any {
                        it.startsWith("virtio-snd-pci,") && it.contains("input=true")
                    }
                ) {
                    VMAudioInput.open()
                } else {
                    null
                }
                try {
                    ALSLog.info("AGL", "Preflight started")
                    startPrepared.preflight()
                    ALSLog.info("AGL", "Preflight passed")
                    awaitConsole(startLaunch.consolePid)
                    val redirectError = VMNative.redirectStdio(startLaunch.consolePid)
                    check(redirectError == 0) {
                        "QEMU console connection failed: ${Os.strerror(redirectError)}"
                    }
                    val binding = AtomicBoolean(true)
                    val binder = Thread({
                        val deadline = SystemClock.uptimeMillis() + 270
                        while (binding.get() && SystemClock.uptimeMillis() < deadline) {
                            VMNative.rebindOutput(startLaunch.consolePid)
                            try {
                                Thread.sleep(3)
                            } catch (_: InterruptedException) {
                                return@Thread
                            }
                        }
                    }, "qemu-console")
                    try {
                        binder.start()
                        main.post { state = VMRunState.Running }
                        VMNative.run(
                            startLaunch.workDir,
                            startPrepared.args,
                            surface,
                            refreshRate,
                            audioInput?.readFd ?: -1,
                        )
                    } finally {
                        binding.set(false)
                        binder.interrupt()
                        binder.join()
                        VMNative.restoreStdio()
                    }
                } finally {
                    audioInput?.close()
                }
            }
            synchronized(lock) {
                launched = false
                if (preparedLaunch === startPrepared) {
                    preparedLaunch = null
                }
            }
            result.onSuccess { status ->
                ALSLog.info("AGL", "QEMU returned $status")
                if (status != 0) {
                    writeConsole(startLaunch, "QEMU exited with status $status")
                }
                main.post {
                    state = if (status == 0) VMRunState.Stopped else VMRunState.Failed
                    failureMessage = if (status == 0) {
                        null
                    } else {
                        "QEMU exited with status $status"
                    }
                }
            }.onFailure { error ->
                ALSLog.error("AGL", "QEMU launch failed", error)
                writeConsole(
                    startLaunch,
                    "QEMU launch failed: ${error.message ?: error.javaClass.simpleName}"
                )
                main.post {
                    state = VMRunState.Failed
                    failureMessage = error.message ?: error.javaClass.simpleName
                }
            }
        }
    }

    fun detach(refreshRate: Float = 0f) {
        val detach = synchronized(lock) {
            launched && state != VMRunState.Stopping &&
                state != VMRunState.Stopped && state != VMRunState.Failed
        }
        if (detach) {
            VMNative.setSurface(null, refreshRate)
        }
    }

    fun stop() {
        val stop = synchronized(lock) {
            if (!launched || state == VMRunState.Stopped || state == VMRunState.Failed) {
                return
            }
            state = VMRunState.Stopping
            true
        }
        if (stop) {
            ALSLog.info("AGL", "Stop requested")
            VMNative.setSurface(null, 0f)
            VMNative.stop()
        }
    }

    fun pointer(x: Float, y: Float, buttons: Int) {
        if (active()) {
            VMNative.pointer(x, y, buttons)
        }
    }

    fun scroll(x: Float, y: Float) {
        if (active()) {
            VMNative.scroll(x, y)
        }
    }

    fun key(scanCode: Int, down: Boolean) {
        if (active()) {
            VMNative.key(scanCode, down)
        }
    }

    private fun active(): Boolean = synchronized(lock) {
        launched && state != VMRunState.Stopped && state != VMRunState.Failed
    }

    private fun writeConsole(value: VMLaunch, message: String) {
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
}
