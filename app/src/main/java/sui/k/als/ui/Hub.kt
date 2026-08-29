package sui.k.als.ui

import android.app.*
import android.view.*
import android.view.inputmethod.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import com.termux.terminal.*
import kotlinx.coroutines.*
import sui.k.als.qemu.vm.*
import sui.k.als.*
import sui.k.als.qemu.gunyah.QemuGunyah
import sui.k.als.qemu.gunyah.QemuGunyahConfigStore
import sui.k.als.qemu.gzvm.QemuGzvm
import sui.k.als.tty.*
import sui.k.als.qemu.gunyah.toVMLaunch as toGunyahVMLaunch
import sui.k.als.qemu.gzvm.toVMLaunch as toGzvmVMLaunch

const val alsDir = "/data/local/tmp/als"

enum class Destination {
    Backends, Gunyah, Gzvm, Sessions, Terminal, Display, Console
}

internal object HubState {
    var sessions by mutableStateOf(emptyList<TTYInstance>())
    var active by mutableStateOf<TTYInstance?>(null)
    var qemuConsole by mutableStateOf<TTYInstance?>(null)
    var vmLaunch by mutableStateOf<VMLaunch?>(null)

    fun close() {
        VMRuntime.stop()
        sessions.forEach { it.session.finishIfRunning() }
        qemuConsole?.session?.finishIfRunning()
        sessions = emptyList()
        active = null
        qemuConsole = null
        vmLaunch = null
    }
}

@Composable
fun Hub(destination: Destination, onNavigate: (Destination) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vmState = VMRuntime.state
    val activity = context as? Activity
    val defaultLaunch = remember {
        VMRuntime.currentLaunch ?: QemuGunyahConfigStore.load(context).toGunyahVMLaunch()
    }
    val create: () -> Unit = {
        val instance = createTTYInstance(context, object : TTYSessionStub() {
            override fun onSessionFinished(session: TerminalSession) {
                super.onSessionFinished(session)
                HubState.sessions = HubState.sessions.filter { it.session != session }
                if (HubState.active?.session == session) HubState.active = HubState.sessions.lastOrNull()
            }
        }, object : TTYViewStub() {
            override fun onSingleTapUp(event: MotionEvent) {
                HubState.active?.view?.run {
                    requestFocus()
                    context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
                }
            }
        })
        scope.launch {
            delay(90)
            cmd(instance.session, su)
            delay(90)
            cmd(instance.session, "printf \"\\033[2J\\033[3J\\033[H\"")
        }
        HubState.sessions = HubState.sessions + instance
        HubState.active = instance
        onNavigate(Destination.Terminal)
    }
    val createQemuConsole: () -> TTYInstance = {
        HubState.qemuConsole?.session?.finishIfRunning()
        val instance = createQemuTTYInstance(context, TTYSessionStub(), object : TTYViewStub() {
            override fun onSingleTapUp(event: MotionEvent) {
                HubState.qemuConsole?.view?.run {
                    requestFocus()
                    context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
                }
            }
        })
        HubState.qemuConsole = instance
        instance
    }

    LaunchedEffect(vmState, destination) {
        if (vmState == VMRunState.Failed && destination == Destination.Display && HubState.qemuConsole != null) {
            onNavigate(Destination.Console)
            activity?.finish()
        }
    }
    when (destination) {
        Destination.Backends -> App(
            onQemuGunyah = { onNavigate(Destination.Gunyah) },
            onQemuGzvm = { onNavigate(Destination.Gzvm) })

        Destination.Gunyah -> QemuGunyah(
            started = vmState.active,
            onCreate = {
                val console = createQemuConsole()
                HubState.vmLaunch = it.toGunyahVMLaunch().copy(consolePid = console.session.pid)
                VMRuntime.prepare(HubState.vmLaunch!!)
                onNavigate(Destination.Display)
            },
            onDisplay = { onNavigate(Destination.Display) },
            onConsole = {
                if (HubState.qemuConsole == null) createQemuConsole()
                onNavigate(Destination.Console)
            },
            onStop = {
                scope.launch(Dispatchers.IO) {
                    VMRuntime.stop()
                }
            },
            onBack = { activity?.finish() },
            onKeyboardSettingsChange = { hide, soft ->
                HubState.vmLaunch = (HubState.vmLaunch ?: defaultLaunch).copy(
                    hideKeyboard = hide, softKeyboard = soft
                )
            })

        Destination.Gzvm -> QemuGzvm(
            started = vmState.active,
            onCreate = {
                val console = createQemuConsole()
                HubState.vmLaunch = it.toGzvmVMLaunch().copy(consolePid = console.session.pid)
                VMRuntime.prepare(HubState.vmLaunch!!)
                onNavigate(Destination.Display)
            },
            onDisplay = { onNavigate(Destination.Display) },
            onConsole = {
                if (HubState.qemuConsole == null) createQemuConsole()
                onNavigate(Destination.Console)
            },
            onStop = {
                scope.launch(Dispatchers.IO) {
                    VMRuntime.stop()
                }
            },
            onBack = { activity?.finish() },
            onKeyboardSettingsChange = { hide, soft ->
                HubState.vmLaunch = (HubState.vmLaunch ?: defaultLaunch).copy(
                    hideKeyboard = hide, softKeyboard = soft
                )
            })

        Destination.Sessions -> TTYHub(
            sessions = HubState.sessions,
            onBack = { activity?.finish() },
            onSelect = {
                HubState.active = it
                onNavigate(Destination.Terminal)
            },
            onDelete = { it.session.finishIfRunning() },
            onCreate = create
        )

        Destination.Terminal -> HubState.active?.let { TTYScreen(it) { TTYIME() } }
        Destination.Display -> (HubState.vmLaunch ?: VMRuntime.currentLaunch)?.let { VMScreen(it) }
        Destination.Console -> HubState.qemuConsole?.let { TTYScreen(it) { TTYIME() } }
    }
}

private val VMRunState.active: Boolean
    get() = this == VMRunState.Starting || this == VMRunState.Running || this == VMRunState.Stopping
