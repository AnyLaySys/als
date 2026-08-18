package sui.k.als.agl

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.view.Surface
import sui.k.als.log.ALSLog
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AglService : Service() {
    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "qemu-agl") }
    private var active = false
    private var nativeRunning = false
    private var surface: Surface? = null
    private val surfaces = ArrayList<Surface>()

    private val binder = object : IAglService.Stub() {
        override fun start(
            backend: Int,
            configuration: String,
            workDir: String,
            consolePid: Int,
            surface: Surface,
            refreshRate: Float,
            callback: IAglCallback
        ) {
            val accepted = synchronized(lock) {
                if (active) {
                    false
                } else {
                    active = true
                    this@AglService.surface = surface
                    surfaces += surface
                    true
                }
            }
            if (!accepted) {
                surface.release()
                runCatching { callback.onFinished(-1, "QEMU is already running") }
                return
            }
            executor.execute {
                runQemu(backend, configuration, workDir, consolePid, refreshRate, callback)
            }
        }

        override fun setSurface(surface: Surface, refreshRate: Float) {
            replaceSurface(surface, refreshRate)
        }

        override fun clearSurface(refreshRate: Float) {
            replaceSurface(null, refreshRate)
        }

        override fun pointer(x: Float, y: Float, buttons: Int) {
            if (isNativeRunning()) {
                runCatching { AglNative.pointer(x, y, buttons) }
            }
        }

        override fun scroll(x: Float, y: Float) {
            if (isNativeRunning()) {
                runCatching { AglNative.scroll(x, y) }
            }
        }

        override fun key(scanCode: Int, down: Boolean) {
            if (isNativeRunning()) {
                runCatching { AglNative.key(scanCode, down) }
            }
        }

        override fun stop() {
            Process.killProcess(Process.myPid())
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        executor.shutdownNow()
        val owned = synchronized(lock) {
            surface = null
            surfaces.toList().also { surfaces.clear() }
        }
        owned.forEach { it.release() }
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    private fun runQemu(
        backend: Int,
        configuration: String,
        workDir: String,
        consolePid: Int,
        refreshRate: Float,
        callback: IAglCallback
    ) {
        var redirected = false
        val binding = AtomicBoolean(false)
        var outputBinder: Thread? = null
        val result = runCatching {
            val nativeBackend = AglNativeBackend.entries.getOrNull(backend)
                ?: error("Unknown QEMU backend")
            AglNative.load(nativeBackend)
            val prepared = nativeBackend.prepare(configuration)
            ALSLog.info(
                "AGL",
                "Prepare ${nativeBackend.libraryName} $workDir ${prepared.args.joinToString(" ")}"
            )
            ALSLog.info("AGL", "Preflight started")
            prepared.preflight()
            ALSLog.info("AGL", "Preflight passed")
            awaitConsole(consolePid)
            val redirectError = AglNative.redirectStdio(consolePid)
            check(redirectError == 0) {
                "QEMU console connection failed: ${Os.strerror(redirectError)}"
            }
            redirected = true
            if (nativeBackend != AglNativeBackend.Gunyah) {
                binding.set(true)
                outputBinder = Thread({
                    val deadline = SystemClock.uptimeMillis() + 270
                    while (binding.get() && SystemClock.uptimeMillis() < deadline) {
                        AglNative.rebindOutput(consolePid)
                        try {
                            Thread.sleep(3)
                        } catch (_: InterruptedException) {
                            return@Thread
                        }
                    }
                }, "qemu-console").also { it.start() }
            }
            val startSurface = synchronized(lock) {
                nativeRunning = true
                surface
            }
            callback.onRunning()
            AglNative.run(workDir, prepared.args, startSurface, refreshRate)
        }
        synchronized(lock) {
            nativeRunning = false
        }
        binding.set(false)
        outputBinder?.interrupt()
        runCatching { outputBinder?.join() }
        if (redirected) {
            runCatching { AglNative.restoreStdio() }
        }
        val owned: List<Surface>
        synchronized(lock) {
            active = false
            surface = null
            owned = surfaces.toList()
            surfaces.clear()
        }
        owned.forEach { it.release() }
        result.onSuccess { status ->
            ALSLog.info("AGL", "QEMU returned $status")
            val message = if (status == 0) "" else "QEMU exited with status $status"
            runCatching { callback.onFinished(status, message) }
        }.onFailure { error ->
            ALSLog.error("AGL", "QEMU launch failed", error)
            runCatching {
                callback.onFinished(-1, error.message ?: error.javaClass.simpleName)
            }
        }
        stopSelf()
    }

    private fun replaceSurface(value: Surface?, refreshRate: Float) {
        val accepted: Boolean
        val running: Boolean
        synchronized(lock) {
            accepted = active
            running = nativeRunning
            if (accepted) {
                surface = value
                value?.let { surfaces += it }
            }
        }
        if (!accepted) {
            value?.release()
            return
        }
        if (running) {
            runCatching { AglNative.setSurface(value, refreshRate) }
        }
    }

    private fun isNativeRunning(): Boolean = synchronized(lock) { nativeRunning }

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
