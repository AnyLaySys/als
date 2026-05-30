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
import sui.k.als.qvm.*
import sui.k.als.tty.*
import sui.k.als.ui.*

const val alsDir = "/data/local/tmp/als"

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
    var showQvm by remember { mutableStateOf(false) }
    val close =
        { sessions.forEach { it.session.finishIfRunning() }; sessions = emptyList(); active = null }
    val create = {
        val instance = createTTYInstance(ctx, object : TTYSessionStub() {
            override fun onSessionFinished(session: TerminalSession) {
                sessions = sessions.filter { it.session != session }
                if (active?.session == session) active = sessions.lastOrNull()
                if (active == null) {
                    showTTY = false; showTTYHub = sessions.isNotEmpty()
                }
            }
        }, object : TTYViewStub() {
            override fun onSingleTapUp(event: MotionEvent) {
                active?.view?.run {
                    requestFocus(); ctx.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(this, 0)
                }
            }
        }).also { instance -> scope.launch { delay(90); cmd(instance.session, shellQuote(su)); delay(90); cmd(instance.session, shellQuote("$alsDir/app/ate")) } }
        sessions = sessions + instance; active = instance; showTTY = true; showTTYHub = false
    }
    DisposableEffect(Unit) { onDispose(close) }
    BackHandler {
        if (showTTY) {
            showTTY = false; showTTYHub = true
        } else {
            showTTYHub = false; showQvm = false
        }
    }
    if (showQvm) Qvm { showQvm = false } else if (showTTY) active?.let { TTYScreen(it) { TTYIME() } } else if (showTTYHub) TTYHub(
        sessions,
        onSelect = { active = it; showTTY = true; showTTYHub = false },
        onDelete = { it.session.finishIfRunning() },
        onCreate = create
    ) else Box(
        Modifier.fillMaxSize(), Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ALSButton(R.drawable.arrow_forward) { showQvm = true }
            ALSButton(R.drawable.terminal) {
                if (sessions.isEmpty()) create() else showTTYHub = true
            }
            ALSButton(R.drawable.power) { close(); onFin(); (ctx as? Activity)?.finishAffinity() }
        }
    }
}
