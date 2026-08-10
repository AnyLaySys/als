package sui.k.als.ui

import android.app.Activity
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import sui.k.als.qemu.gunyah.toAglLaunch as toGunyahAglLaunch
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
import sui.k.als.tty.createTTYInstance
import sui.k.als.tty.shellQuote
import kotlin.time.Duration.Companion.milliseconds

const val alsDir = "/data/local/tmp/als"

@Composable
fun Hub(modifier: Modifier = Modifier, onFin: () -> Unit) = Box(
    modifier
        .fillMaxSize()
        .background(Color.Black),
    Alignment.Center
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aglState = AglRuntime.state
    var sessions by remember { mutableStateOf(emptyList<TTYInstance>()) }
    var active by remember { mutableStateOf<TTYInstance?>(null) }
    var showTTY by remember { mutableStateOf(false) }
    var showTTYHub by remember { mutableStateOf(false) }
    var showApp by remember { mutableStateOf(false) }
    var showQemuGunyah by remember { mutableStateOf(false) }
    var showQemuGzvm by remember { mutableStateOf(false) }
    var showQemuKvm by remember { mutableStateOf(false) }
    var showAgl by remember { mutableStateOf(false) }
    var aglLaunch by remember {
        mutableStateOf(
            AglRuntime.currentLaunch
                ?: QemuGunyahConfigStore.load(context).toGunyahAglLaunch()
        )
    }

    val close = {
        AglRuntime.stop()
        sessions.forEach { it.session.finishIfRunning() }
        sessions = emptyList()
        active = null
    }
    val create: (String, Boolean) -> Unit = { command, enterSu ->
        val instance = createTTYInstance(context, object : TTYSessionStub() {
            override fun onSessionFinished(session: TerminalSession) {
                super.onSessionFinished(session)
                sessions = sessions.filter { it.session != session }
                if (active?.session == session) {
                    active = sessions.lastOrNull()
                }
                if (active == null) {
                    showTTY = false
                    showTTYHub = sessions.isNotEmpty()
                }
            }
        }, object : TTYViewStub() {
            override fun onSingleTapUp(event: MotionEvent) {
                active?.view?.run {
                    requestFocus()
                    context.getSystemService(InputMethodManager::class.java)
                        ?.showSoftInput(this, 0)
                }
            }
        }).also { instance ->
            scope.launch {
                if (enterSu) {
                    delay(90.milliseconds)
                    cmd(instance.session, su)
                    delay(90.milliseconds)
                }
                cmd(instance.session, command)
            }
        }
        sessions = sessions + instance
        active = instance
        showTTY = true
        showTTYHub = false
    }

    DisposableEffect(Unit) {
        onDispose(close)
    }
    BackHandler {
        when {
            showAgl -> {
                showAgl = false
                when (aglLaunch.backend) {
                    AglNativeBackend.Gunyah -> showQemuGunyah = true
                    AglNativeBackend.Gzvm -> showQemuGzvm = true
                    AglNativeBackend.Kvm -> showQemuKvm = true
                }
            }
            showTTY -> {
                showTTY = false
                showTTYHub = sessions.isNotEmpty()
            }
            showQemuGunyah || showQemuGzvm || showQemuKvm || showApp || showTTYHub -> {
                showTTYHub = false
                showApp = false
                showQemuGunyah = false
                showQemuGzvm = false
                showQemuKvm = false
            }
        }
    }

    when {
        showAgl -> AglScreen(aglLaunch)
        showQemuGunyah -> QemuGunyah(
            started = aglState == AglRunState.Starting ||
                aglState == AglRunState.Running ||
                aglState == AglRunState.Stopping,
            onCreate = {
                val launch = it.toGunyahAglLaunch()
                aglLaunch = launch
                AglRuntime.prepare(launch)
                showQemuGunyah = false
                showAgl = true
            },
            onDisplay = {
                showQemuGunyah = false
                showAgl = true
            },
            onStop = AglRuntime::stop
        )
        showQemuGzvm -> QemuGzvm(
            started = aglState == AglRunState.Starting ||
                aglState == AglRunState.Running ||
                aglState == AglRunState.Stopping,
            onCreate = {
                val launch = it.toGzvmAglLaunch()
                aglLaunch = launch
                AglRuntime.prepare(launch)
                showQemuGzvm = false
                showAgl = true
            },
            onDisplay = {
                showQemuGzvm = false
                showAgl = true
            },
            onStop = AglRuntime::stop
        )
        showQemuKvm -> QemuKvm(
            started = aglState == AglRunState.Starting ||
                aglState == AglRunState.Running ||
                aglState == AglRunState.Stopping,
            onCreate = {
                val launch = it.toKvmAglLaunch()
                aglLaunch = launch
                AglRuntime.prepare(launch)
                showQemuKvm = false
                showAgl = true
            },
            onDisplay = {
                showQemuKvm = false
                showAgl = true
            },
            onStop = AglRuntime::stop
        )
        showApp -> App(
            onQemuGunyah = {
                showApp = false
                showQemuGunyah = true
            },
            onQemuGzvm = {
                showApp = false
                showQemuGzvm = true
            },
            onQemuKvm = {
                showApp = false
                showQemuKvm = true
            }
        )
        showTTY -> active?.let { TTYScreen(it) { TTYIME() } }
        showTTYHub -> TTYHub(
            sessions,
            onSelect = {
                active = it
                showTTY = true
                showTTYHub = false
            },
            onDelete = { it.session.finishIfRunning() },
            onCreate = { create(shellQuote("$alsDir/app/ate"), true) }
        )
        else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ALSButton(R.drawable.arrow_forward) { showApp = true }
                ALSButton(R.drawable.terminal) {
                    if (sessions.isEmpty()) {
                        create(shellQuote("$alsDir/app/ate"), true)
                    } else {
                        showTTYHub = true
                    }
                }
                ALSButton(R.drawable.power) {
                    close()
                    onFin()
                    (context as? Activity)?.finishAffinity()
                }
            }
        }
    }
}
