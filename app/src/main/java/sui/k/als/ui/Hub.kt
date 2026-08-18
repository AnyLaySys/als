package sui.k.als.ui

import android.app.Activity
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sui.k.als.R
import sui.k.als.agl.AglNativeBackend
import sui.k.als.agl.AglRunState
import sui.k.als.agl.AglRuntime
import sui.k.als.agl.AglScreen
import sui.k.als.app.App
import sui.k.als.qemu.gunyah.QemuGunyah
import sui.k.als.qemu.gunyah.QemuGunyahConfigStore
import sui.k.als.app.qemu.gunyah.toAglLaunch as toGunyahAglLaunch
import sui.k.als.qemu.gzvm.QemuGzvm
import sui.k.als.qemu.gzvm.toAglLaunch as toGzvmAglLaunch
import sui.k.als.qemu.kvm.QemuKvm
import sui.k.als.qemu.kvm.toAglLaunch as toKvmAglLaunch
import sui.k.als.tty.TTYHub
import sui.k.als.tty.TTYIME
import sui.k.als.tty.TTYInstance
import sui.k.als.tty.TTYScreen
import sui.k.als.tty.TTYSessionStub
import sui.k.als.tty.TTYViewStub
import sui.k.als.tty.cmd
import sui.k.als.tty.createQemuTTYInstance
import sui.k.als.tty.createTTYInstance

const val alsDir = "/data/local/tmp/als"

private enum class Destination {
    Home, Backends, Gunyah, Gzvm, Kvm, Sessions, Terminal, Display, Console
}

@Composable
fun Hub(modifier: Modifier = Modifier, onFin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aglState = AglRuntime.state
    var sessions by remember { mutableStateOf(emptyList<TTYInstance>()) }
    var active by remember { mutableStateOf<TTYInstance?>(null) }
    var qemuConsole by remember { mutableStateOf<TTYInstance?>(null) }
    var destination by remember { mutableStateOf(Destination.Home) }
    var aglLaunch by remember {
        mutableStateOf(AglRuntime.currentLaunch ?: QemuGunyahConfigStore.load(context).toGunyahAglLaunch())
    }

    val close = {
        AglRuntime.stop()
        sessions.forEach { it.session.finishIfRunning() }
        qemuConsole?.session?.finishIfRunning()
        sessions = emptyList()
        active = null
        qemuConsole = null
    }
    val create: () -> Unit = {
        val instance = createTTYInstance(context, object : TTYSessionStub() {
            override fun onSessionFinished(session: TerminalSession) {
                super.onSessionFinished(session)
                sessions = sessions.filter { it.session != session }
                if (active?.session == session) active = sessions.lastOrNull()
                destination = if (active == null) Destination.Home else Destination.Sessions
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
    BackHandler(destination != Destination.Home) {
        destination = when (destination) {
            Destination.Backends, Destination.Sessions -> Destination.Home
            Destination.Gunyah, Destination.Gzvm, Destination.Kvm -> Destination.Backends
            Destination.Terminal -> if (sessions.isEmpty()) Destination.Home else Destination.Sessions
            Destination.Display -> when (aglLaunch.backend) {
                AglNativeBackend.Gunyah -> Destination.Gunyah
                AglNativeBackend.Gzvm -> Destination.Gzvm
                AglNativeBackend.Kvm -> Destination.Kvm
            }
            Destination.Console -> when (aglLaunch.backend) {
                AglNativeBackend.Gunyah -> Destination.Gunyah
                AglNativeBackend.Gzvm -> Destination.Gzvm
                AglNativeBackend.Kvm -> Destination.Kvm
            }
            Destination.Home -> Destination.Home
        }
    }

    when (destination) {
        Destination.Home -> HomeScreen(
            modifier = modifier,
            onBackends = { destination = Destination.Backends },
            onTerminal = {
                if (sessions.isEmpty()) create() else destination = Destination.Sessions
            },
            onExit = {
                close()
                onFin()
                (context as? Activity)?.finishAffinity()
            }
        )
        Destination.Backends -> App(
            onBack = { destination = Destination.Home },
            onQemuGunyah = { destination = Destination.Gunyah },
            onQemuGzvm = { destination = Destination.Gzvm },
            onQemuKvm = { destination = Destination.Kvm }
        )
        Destination.Gunyah -> QemuGunyah(
            started = aglState.active,
            onCreate = {
                val console = createQemuConsole()
                aglLaunch = it.toGunyahAglLaunch().copy(consolePid = console.session.pid)
                AglRuntime.prepare(aglLaunch)
                destination = Destination.Display
            },
            onDisplay = { destination = Destination.Display },
            onConsole = { if (qemuConsole == null) createQemuConsole(); destination = Destination.Console },
            onStop = AglRuntime::stop,
            onBack = { destination = Destination.Backends },
            onKeyboardSettingsChange = { hide, soft ->
                aglLaunch = aglLaunch.copy(hideKeyboard = hide, softKeyboard = soft)
            }
        )
        Destination.Gzvm -> QemuGzvm(
            started = aglState.active,
            onCreate = {
                val console = createQemuConsole()
                aglLaunch = it.toGzvmAglLaunch().copy(consolePid = console.session.pid)
                AglRuntime.prepare(aglLaunch)
                destination = Destination.Display
            },
            onDisplay = { destination = Destination.Display },
            onConsole = { if (qemuConsole == null) createQemuConsole(); destination = Destination.Console },
            onStop = AglRuntime::stop,
            onBack = { destination = Destination.Backends },
            onKeyboardSettingsChange = { hide, soft ->
                aglLaunch = aglLaunch.copy(hideKeyboard = hide, softKeyboard = soft)
            }
        )
        Destination.Kvm -> QemuKvm(
            started = aglState.active,
            onCreate = {
                val console = createQemuConsole()
                aglLaunch = it.toKvmAglLaunch().copy(consolePid = console.session.pid)
                AglRuntime.prepare(aglLaunch)
                destination = Destination.Display
            },
            onDisplay = { destination = Destination.Display },
            onConsole = { if (qemuConsole == null) createQemuConsole(); destination = Destination.Console },
            onStop = AglRuntime::stop,
            onBack = { destination = Destination.Backends },
            onKeyboardSettingsChange = { hide, soft ->
                aglLaunch = aglLaunch.copy(hideKeyboard = hide, softKeyboard = soft)
            }
        )
        Destination.Sessions -> TTYHub(
            sessions = sessions,
            onBack = { destination = Destination.Home },
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

@Composable
private fun HomeScreen(
    modifier: Modifier,
    onBackends: () -> Unit,
    onTerminal: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            HomeIcon(R.drawable.arrow_forward, stringResource(R.string.home_virtual_machines), onBackends)
            HomeIcon(R.drawable.terminal, stringResource(R.string.home_terminal), onTerminal)
            HomeIcon(R.drawable.power, stringResource(R.string.home_exit), onExit)
        }
    }
}

@Composable
private fun HomeIcon(icon: Int, description: String, onClick: () -> Unit) {
    Icon(
        painterResource(icon),
        description,
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        tint = Color.White
    )
}
