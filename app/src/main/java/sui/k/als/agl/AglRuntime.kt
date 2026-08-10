package sui.k.als.agl

import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sui.k.als.log.ALSLog
import java.util.concurrent.Executors

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
    private var launched = false
    var state by mutableStateOf(AglRunState.Idle)
        private set
    var failureMessage by mutableStateOf<String?>(null)
        private set

    internal val currentLaunch: AglLaunch?
        get() = synchronized(lock) { launch }

    internal fun prepare(value: AglLaunch) {
        ALSLog.info(
            "AGL",
            "prepare ${value.backend.libraryName} ${value.workDir} ${value.args.joinToString(" ")}"
        )
        var prepared = false
        synchronized(lock) {
            if (!launched || state == AglRunState.Stopped || state == AglRunState.Failed) {
                launched = false
                launch = value
                failureMessage = null
                state = AglRunState.Idle
                prepared = true
            }
        }
        if (prepared) {
            runCatching { AglNative.load(value.backend) }.onFailure { error ->
                ALSLog.error("AGL", "native library load failed", error)
                synchronized(lock) {
                    if (launch === value) {
                        state = AglRunState.Failed
                        failureMessage = error.message ?: error.javaClass.simpleName
                    }
                }
            }
        }
    }

    fun attach(surface: Surface, refreshRate: Float) {
        val startLaunch: AglLaunch?
        val updateSurface: Boolean
        synchronized(lock) {
            startLaunch = if (!launched) launch else null
            updateSurface = startLaunch == null && launched &&
                state != AglRunState.Stopped && state != AglRunState.Failed
            if (startLaunch != null) {
                launched = true
                state = AglRunState.Starting
            }
        }
        if (startLaunch == null) {
            if (updateSurface) {
                AglNative.setSurface(surface, refreshRate)
            }
            return
        }
        ALSLog.info("AGL", "starting ${startLaunch.backend.libraryName}")
        executor.execute {
            runCatching {
                ALSLog.info("AGL", "preflight started")
                startLaunch.preflight()
                ALSLog.info("AGL", "preflight passed")
                main.post { state = AglRunState.Running }
                AglNative.run(
                    startLaunch.workDir,
                    startLaunch.args,
                    surface,
                    refreshRate
                )
            }.onSuccess { result ->
                ALSLog.info("AGL", "QEMU returned $result")
                main.post {
                    state = if (result == 0) AglRunState.Stopped else AglRunState.Failed
                    failureMessage = if (result == 0) {
                        null
                    } else {
                        "QEMU exited with status $result"
                    }
                }
            }.onFailure { error ->
                ALSLog.error("AGL", "QEMU launch failed", error)
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
            ALSLog.info("AGL", "stop requested")
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
}
