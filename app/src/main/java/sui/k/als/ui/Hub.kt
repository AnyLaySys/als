package sui.k.als.ui

import android.app.*
import android.view.*
import android.view.inputmethod.*
import androidx.activity.compose.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import com.termux.terminal.*
import kotlinx.coroutines.*
import sui.k.als.agl.*
import sui.k.als.app.*
import sui.k.als.qemu.gunyah.*
import sui.k.als.qemu.gzvm.*
import sui.k.als.tty.*
import sui.k.als.app.qemu.gunyah.toAglLaunch as toGunyahAglLaunch
import sui.k.als.qemu.gzvm.toAglLaunch as toGzvmAglLaunch

const val alsDir = "/data/local/tmp/als"

private enum class Destination {
    Backends, Gunyah, Gzvm, Sessions, Terminal, Display, Console
}

@Composable
fun Hub(modifier: Modifier = Modifier, onFin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aglState = AglRuntime.state
    var sessions by remember { mutableStateOf(emptyList<TTYInstance>()) }
    var active by remember { mutableStateOf<TTYInstance?>(null) }
    var qemuConsole by remember { mutableStateOf<TTYInstance?>(null) }
    var destination by remember { mutableStateOf(Destination.Backends) }
    var aglLaunch by remember {
        mutableStateOf(
            AglRuntime.currentLaunch ?: QemuGunyahConfigStore.load(context).toGunyahAglLaunch()
        )
    }

    val close = {
        AglRuntime.stop()
        sessions.forEach { it.session.finishIfRunning() }
        qemuConsole?.session?.finishIfRunning()
        sessions = emptyList()
        active = null
        qemuConsole = null
    }
    val exit = {
        close()
        onFin()
        (context as? Activity)?.finishAffinity()
    }
    val create: () -> Unit = {
        val instance = createTTYInstance(context, object : TTYSessionStub() {
            override fun onSessionFinished(session: TerminalSession) {
                super.onSessionFinished(session)
                sessions = sessions.filter { it.session != session }
                if (active?.session == session) active = sessions.lastOrNull()
                destination = if (active == null) Destination.Backends else Destination.Sessions
            }
        }, object : TTYViewStub() {
            override fun onSingleTapUp(event: MotionEvent) {
                active?.view?.run {
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
        sessions = sessions + instance
        active = instance
        destination = Destination.Terminal
    }
    val createQemuConsole: () -> TTYInstance = {
        qemuConsole?.session?.finishIfRunning()
        val instance = createQemuTTYInstance(context, TTYSessionStub(), object : TTYViewStub() {
            override fun onSingleTapUp(event: MotionEvent) {
                qemuConsole?.view?.run {
                    requestFocus()
                    context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
                }
            }
        })
        qemuConsole = instance
        instance
    }

    val currentSessions by rememberUpdatedState(sessions)
    val currentConsole by rememberUpdatedState(qemuConsole)

    DisposableEffect(Unit) {
        onDispose {
            AglRuntime.stop()
            currentSessions.forEach { it.session.finishIfRunning() }
            currentConsole?.session?.finishIfRunning()
        }
    }
    LaunchedEffect(aglState) {
        if (aglState == AglRunState.Failed && destination == Destination.Display && qemuConsole != null) {
            destination = Destination.Console
        }
    }
    BackHandler {
        when (destination) {
            Destination.Backends -> exit()
            Destination.Sessions -> destination = Destination.Backends
            Destination.Gunyah, Destination.Gzvm -> destination = Destination.Backends
            Destination.Terminal -> {
                destination = if (sessions.isEmpty()) Destination.Backends else Destination.Sessions
            }

            Destination.Display -> destination = when (aglLaunch.backend) {
                AglNativeBackend.Gunyah -> Destination.Gunyah
                AglNativeBackend.Gzvm -> Destination.Gzvm
            }

            Destination.Console -> destination = when (aglLaunch.backend) {
                AglNativeBackend.Gunyah -> Destination.Gunyah
                AglNativeBackend.Gzvm -> Destination.Gzvm
            }
        }
    }

    when (destination) {
        Destination.Backends -> App(
            onQemuGunyah = { destination = Destination.Gunyah },
            onQemuGzvm = { destination = Destination.Gzvm })

        Destination.Gunyah -> QemuGunyah(
            started = aglState.active,
            onCreate = {
                val console = createQemuConsole()
                aglLaunch = it.toGunyahAglLaunch().copy(consolePid = console.session.pid)
                AglRuntime.prepare(aglLaunch)
                destination = Destination.Display
            },
            onDisplay = { destination = Destination.Display },
            onConsole = {
                if (qemuConsole == null) createQemuConsole(); destination = Destination.Console
            },
            onStop = AglRuntime::stop,
            onBack = { destination = Destination.Backends },
            onKeyboardSettingsChange = { hide, soft ->
                aglLaunch = aglLaunch.copy(hideKeyboard = hide, softKeyboard = soft)
            })

        Destination.Gzvm -> QemuGzvm(
            started = aglState.active,
            onCreate = {
                val console = createQemuConsole()
                aglLaunch = it.toGzvmAglLaunch().copy(consolePid = console.session.pid)
                AglRuntime.prepare(aglLaunch)
                destination = Destination.Display
            },
            onDisplay = { destination = Destination.Display },
            onConsole = {
                if (qemuConsole == null) createQemuConsole(); destination = Destination.Console
            },
            onStop = AglRuntime::stop,
            onBack = { destination = Destination.Backends },
            onKeyboardSettingsChange = { hide, soft ->
                aglLaunch = aglLaunch.copy(hideKeyboard = hide, softKeyboard = soft)
            })

        Destination.Sessions -> TTYHub(
            sessions = sessions,
            onBack = { destination = Destination.Backends },
            onSelect = {
                active = it
                destination = Destination.Terminal
            },
            onDelete = { it.session.finishIfRunning() },
            onCreate = create
        )

        Destination.Terminal -> active?.let { TTYScreen(it) { TTYIME() } }
        Destination.Display -> AglScreen(aglLaunch)
        Destination.Console -> qemuConsole?.let { TTYScreen(it) { TTYIME() } }
    }
}

private val AglRunState.active: Boolean
    get() = this == AglRunState.Starting || this == AglRunState.Running || this == AglRunState.Stopping