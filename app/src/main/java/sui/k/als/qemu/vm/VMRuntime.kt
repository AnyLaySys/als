package sui.k.als.qemu.vm

import android.content.*
import android.os.*
import android.view.*
import androidx.compose.runtime.*
import sui.k.als.*

enum class VMRunState {
    Idle, Starting, Running, Stopping, Stopped, Failed
}

object VMRuntime {
    private val main = Handler(Looper.getMainLooper())
    private var launch: VMLaunch? = null
    private var surface: Surface? = null
    private var refreshRate = 0f
    private var connection: ServiceConnection? = null
    private var qemu: Qemu? = null
    private var bound = false
    private var restarting = false
    private var token = 0L
    var state by mutableStateOf(VMRunState.Idle)
        private set
    var failureMessage by mutableStateOf<String?>(null)
        private set

    internal val currentLaunch: VMLaunch?
        get() = launch

    internal fun prepare(value: VMLaunch) = onMain {
        if (state != VMRunState.Idle && state != VMRunState.Stopped && state != VMRunState.Failed) {
            return@onMain
        }
        begin(value, false)
    }

    private fun begin(value: VMLaunch, preserveSurface: Boolean) {
        token++
        launch = value
        if (!preserveSurface) {
            surface = null
            refreshRate = 0f
        }
        failureMessage = null
        restarting = false
        state = VMRunState.Starting
        val current = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (connection !== this) {
                    return
                }
                qemu = Qemu.Stub.asInterface(binder)
                start(this)
            }

            override fun onServiceDisconnected(name: ComponentName) = lost(this)

            override fun onBindingDied(name: ComponentName) = lost(this)

            override fun onNullBinding(name: ComponentName) = lost(this)
        }
        connection = current
        bound = ALSApplication.instance.bindService(
            Intent(ALSApplication.instance, QemuService::class.java),
            current,
            Context.BIND_AUTO_CREATE,
        )
        if (!bound) {
            connection = null
            state = VMRunState.Failed
            failureMessage = "QEMU process could not start"
        }
    }

    fun attach(value: Surface, refreshRate: Float) = onMain {
        surface = value
        this.refreshRate = refreshRate
        call { it.setSurface(token, value, refreshRate) }
    }

    fun detach(refreshRate: Float = 0f) = onMain {
        surface = null
        this.refreshRate = refreshRate
        call { it.setSurface(token, null, refreshRate) }
    }

    fun stop() = onMain {
        if (state != VMRunState.Starting && state != VMRunState.Running && state != VMRunState.Stopping) {
            return@onMain
        }
        restarting = false
        state = VMRunState.Stopping
        surface = null
        refreshRate = 0f
        if (qemu == null) {
            release(connection)
            state = VMRunState.Stopped
        } else {
            call { it.stop(token) }
        }
    }

    fun pointer(x: Float, y: Float, buttons: Int) = onMain {
        if (acceptsInput()) {
            call { it.pointer(token, x, y, buttons) }
        }
    }

    fun scroll(x: Float, y: Float) = onMain {
        if (acceptsInput()) {
            call { it.scroll(token, x, y) }
        }
    }

    fun key(scanCode: Int, down: Boolean) = onMain {
        if (acceptsInput()) {
            call { it.key(token, scanCode, down) }
        }
    }

    private fun start(current: ServiceConnection) {
        if (connection !== current) {
            return
        }
        val value = launch ?: return
        call(current) {
            it.start(
                token,
                value.backend.name,
                value.workDir,
                value.configuration,
                value.consolePid,
                surface,
                refreshRate,
                replies,
            )
        }
    }

    private fun call(current: ServiceConnection? = connection, action: (Qemu) -> Unit) {
        val target = qemu ?: return
        runCatching { action(target) }.onFailure {
            current?.let(::lost)
        }
    }

    private fun acceptsInput(): Boolean =
        state == VMRunState.Starting || state == VMRunState.Running

    private fun receiveRunning(value: Long) = onMain {
        if (value == token && state == VMRunState.Starting) {
            state = VMRunState.Running
        }
    }

    private fun receiveExited(value: Long, status: Int) = onMain {
        if (value != token) {
            return@onMain
        }
        if (status == qemuGunyahRestartStatus && launch?.backend == VMBackend.Gunyah &&
            state != VMRunState.Stopping) {
            restarting = true
            state = VMRunState.Starting
            call { it.restart(token) }
            return@onMain
        }
        val stopped = state == VMRunState.Stopping || status == 0
        release(connection)
        state = if (stopped) VMRunState.Stopped else VMRunState.Failed
        failureMessage = if (stopped) null else "QEMU exited with status $status"
    }

    private fun receiveFailed(value: Long, error: String) = onMain {
        if (value != token) {
            return@onMain
        }
        val stopped = state == VMRunState.Stopping
        release(connection)
        state = if (stopped) VMRunState.Stopped else VMRunState.Failed
        failureMessage = if (stopped) null else error
    }

    private fun lost(current: ServiceConnection) = onMain {
        if (connection !== current) {
            return@onMain
        }
        val restart = restarting && state != VMRunState.Stopping
        val value = launch
        restarting = false
        val stopped = state == VMRunState.Stopping
        release(current)
        if (restart && value != null) {
            begin(value, true)
            return@onMain
        }
        state = if (stopped) VMRunState.Stopped else VMRunState.Failed
        failureMessage = if (stopped) null else "QEMU process exited unexpectedly"
        if (!stopped) {
            Log.error("VM", failureMessage.orEmpty())
        }
    }

    private fun release(current: ServiceConnection?) {
        if (current == null || connection !== current) {
            return
        }
        connection = null
        qemu = null
        if (bound) {
            bound = false
            runCatching { ALSApplication.instance.unbindService(current) }
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            main.post(action)
        }
    }

    private val replies = object : QemuCallback.Stub() {
        override fun running(token: Long) {
            receiveRunning(token)
        }

        override fun exited(token: Long, status: Int) {
            receiveExited(token, status)
        }

        override fun failed(token: Long, error: String) {
            receiveFailed(token, error)
        }
    }
}
