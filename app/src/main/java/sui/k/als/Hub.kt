package sui.k.als

import android.app.*
import android.view.*
import android.view.inputmethod.*
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import com.termux.terminal.*
import kotlinx.coroutines.*
import sui.k.als.tty.*
import sui.k.als.ui.*
import kotlin.time.Duration.Companion.milliseconds

const val alsDir = "/data/local/tmp/als"
const val x11Dir = "$alsDir/x11"

@Composable
fun Hub(modifier: Modifier = Modifier, onFin: () -> Unit) = Box(
    modifier
        .fillMaxSize()
        .background(Color.Black), Alignment.Center
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf(emptyList<TTYInstance>()) }
    var active by remember { mutableStateOf<TTYInstance?>(null) }
    var showTTY by remember { mutableStateOf(false) }
    var showTTYHub by remember { mutableStateOf(false) }
    var showGunyah by remember { mutableStateOf(false) }
    var gunyahSession by remember { mutableStateOf<TerminalSession?>(null) }
    val close =
        { sessions.forEach { it.session.finishIfRunning() }; sessions = emptyList(); active = null; gunyahSession = null }
    val create: (String, Boolean, Boolean) -> Unit = { command, enterSu, gunyah ->
        val instance = createTTYInstance(ctx, object : TTYSessionStub() {
            override fun onSessionFinished(session: TerminalSession) {
                val wasGunyah = gunyahSession == session
                if (wasGunyah) gunyahSession = null
                sessions = sessions.filter { it.session != session }
                if (active?.session == session) active = sessions.lastOrNull()
                if (active == null) {
                    showTTY = false; showTTYHub = sessions.isNotEmpty(); showGunyah = wasGunyah
                }
            }
        }, object : TTYViewStub() {
            override fun onSingleTapUp(event: MotionEvent) {
                active?.view?.run {
                    requestFocus(); ctx.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(this, 0)
                }
            }
        }).also { instance ->
            scope.launch {
                if (enterSu) {
                    delay(90.milliseconds)
                    cmd(instance.session, shellQuote(su))
                    delay(90.milliseconds)
                }
                cmd(instance.session, command)
            }
        }
        if (gunyah) gunyahSession = instance.session
        sessions = sessions + instance; active = instance; showTTY = true; showTTYHub = false
    }
    DisposableEffect(Unit) { onDispose(close) }
    BackHandler {
        if (showTTY) {
            val toGunyah = active?.session == gunyahSession
            showTTY = false; showTTYHub = !toGunyah; showGunyah = toGunyah
        } else {
            showTTYHub = false; showGunyah = false
        }
    }
    val gunyahTTY = sessions.firstOrNull { it.session == gunyahSession }
    if (showGunyah) QvmGunyah(started = gunyahTTY != null, onCreate = {
        showGunyah = false; create(gunyahCommand(it), true, true)
    }, onEnter = {
        gunyahTTY?.let { active = it; showTTY = true; showTTYHub = false; showGunyah = false }
    }, onX11 = {
        gunyahTTY?.let { active = it; showTTY = true; showTTYHub = false; showGunyah = false }
        ctx.openX11()
    }) else if (showTTY) active?.let { TTYScreen(it) { TTYIME() } } else if (showTTYHub) TTYHub(
        sessions,
        onSelect = { active = it; showTTY = true; showTTYHub = false },
        onDelete = { it.session.finishIfRunning() },
        onCreate = { create(shellQuote("$alsDir/app/ate"), true, false) }) else Box(
        Modifier.fillMaxSize(), Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ALSButton(R.drawable.arrow_forward) { showGunyah = true }
            ALSButton(R.drawable.terminal) {
                if (sessions.isEmpty()) create(shellQuote("$alsDir/app/ate"), true, false) else showTTYHub = true
            }
            ALSButton(R.drawable.power) { close(); onFin(); (ctx as? Activity)?.finishAffinity() }
        }
    }
}
