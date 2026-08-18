package sui.k.als.agl

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.system.Os
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sui.k.als.log.ALSLog
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class AglRunState {
    Idle, Starting, Running, Stopping, Stopped, Failed
}

object AglRuntime {
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor {
        Thread(it, "qemu-agl")
    }
    private var launch: AglLaunch? = null
    private var preparedLaunch: AglPreparedLaunch? = null
    private var launched = false
    var state by mutableStateOf(AglRunState.Idle)
        private set
    var failureMessage by mutableStateOf<String?>(null)
        private set

    internal val currentLaunch: AglLaunch?
        get() = synchronized(lock) { launch }

    internal fun prepare(value: AglLaunch) {
        val accepted = synchronized(lock) {
            !launched || state == AglRunState.Stopped || state == AglRunState.Failed
        }
        if (!accepted) {
            return
        }
        synchronized(lock) {
            launch = value
            failureMessage = null
            state = AglRunState.Starting
        }
        executor.execute {
            val result = runCatching {
                AglNative.load(value.backend)
                value.backend.prepare(value.configuration)
            }
            result.onSuccess { prepared ->
                ALSLog.info(
                    "AGL",
                    "Prepare ${value.backend.libraryName} ${value.workDir} ${prepared.args.joinToString(" ")}"
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
                        state = AglRunState.Idle
                    }
                }
            }.onFailure { error ->
                ALSLog.error("AGL", "Launch preparation failed", error)
                writeConsole(value, "QEMU launch preparation failed: ${error.message ?: error.javaClass.simpleName}")
                synchronized(lock) {
                    preparedLaunch = null
                    launched = false
                    launch = value
                }
                main.post {
                    state = AglRunState.Failed
                    failureMessage = error.message ?: error.javaClass.simpleName
                }
            }
        }
    }

    fun attach(surface: Surface, refreshRate: Float) {
        val startLaunch: AglLaunch?
        val startPrepared: AglPreparedLaunch?
        val updateSurface: Boolean
        synchronized(lock) {
            startLaunch = if (!launched && state != AglRunState.Failed) launch else null
            startPrepared = if (startLaunch != null) preparedLaunch else null
            updateSurface = startLaunch == null && launched &&
                state != AglRunState.Stopped && state != AglRunState.Failed
            if (startLaunch != null && startPrepared != null) {
                launched = true
                state = AglRunState.Starting
            }
        }
        if (startLaunch == null || startPrepared == null) {
            if (updateSurface) {
                AglNative.setSurface(surface, refreshRate)
            }
            return
        }
        ALSLog.info("AGL", "Starting ${startLaunch.backend.libraryName}")
        executor.execute {
            val result = runCatching {
                ALSLog.info("AGL", "Preflight started")
                startPrepared.preflight()
                ALSLog.info("AGL", "Preflight passed")
                awaitConsole(startLaunch.consolePid)
                val redirectError = AglNative.redirectStdio(startLaunch.consolePid)
                check(redirectError == 0) {
                    "QEMU console connection failed: ${Os.strerror(redirectError)}"
                }
                val binding = AtomicBoolean(true)
                val binder = Thread({
                    val deadline = SystemClock.uptimeMillis() + 270
                    while (binding.get() && SystemClock.uptimeMillis() < deadline) {
                        AglNative.rebindOutput(startLaunch.consolePid)
                        try {
                            Thread.sleep(3)
                        } catch (_: InterruptedException) {
                            return@Thread
                        }
                    }
                }, "qemu-console")
                try {
                    binder.start()
                    main.post { state = AglRunState.Running }
                    AglNative.run(
                        startLaunch.workDir,
                        startPrepared.args,
                        surface,
                        refreshRate
                    )
                } finally {
                    binding.set(false)
                    binder.interrupt()
                    binder.join()
                    AglNative.restoreStdio()
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
                    state = if (status == 0) AglRunState.Stopped else AglRunState.Failed
                    failureMessage = if (status == 0) {
                        null
                    } else {
                        "QEMU exited with status $status"
                    }
                }
            }.onFailure { error ->
                ALSLog.error("AGL", "QEMU launch failed", error)
                writeConsole(startLaunch, "QEMU launch failed: ${error.message ?: error.javaClass.simpleName}")
                main.post {
                    state = AglRunState.Failed
                    failureMessage = error.message ?: error.javaClass.simpleName
                }
            }
        }
    }

    fun detach(refreshRate: Float = 0f) {
        val detach = synchronized(lock) {
            if (launched && state != AglRunState.Stopped && state != AglRunState.Failed) {
                true
            } else {
                false
            }
        }
        if (detach) {
            AglNative.setSurface(null, refreshRate)
        }
    }

    fun stop() {
        val stop = synchronized(lock) {
            if (!launched || state == AglRunState.Stopped || state == AglRunState.Failed) {
                return
            }
            state = AglRunState.Stopping
            true
        }
        if (stop) {
            ALSLog.info("AGL", "Stop requested")
            AglNative.stop()
        }
    }

    fun pointer(x: Float, y: Float, buttons: Int) {
        if (active()) {
            AglNative.pointer(x, y, buttons)
        }
    }

    fun scroll(x: Float, y: Float) {
        if (active()) {
            AglNative.scroll(x, y)
        }
    }

    fun key(scanCode: Int, down: Boolean) {
        if (active()) {
            AglNative.key(scanCode, down)
        }
    }

    private fun active(): Boolean = synchronized(lock) {
        if (launched && state != AglRunState.Stopped && state != AglRunState.Failed) {
            true
        } else {
            false
        }
    }

    private fun writeConsole(value: AglLaunch, message: String) {
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
