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
import java.io.*
import kotlin.time.Duration.Companion.milliseconds

const val alsDir = "/data/local/tmp/als"
private val qvmRoots = setOf("qemu-gunyah", "qemu-gzvm")

private data class QvmEntry(val file: File, val name: String, val runnable: Boolean)

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
    val create: (String?) -> Unit = { script ->
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
        }).also { instance ->
            scope.launch {
                delay(90.milliseconds)
                cmd(instance.session, shellQuote(su))
                delay(90.milliseconds)
                cmd(
                    instance.session,
                    script?.let { "sh ${shellQuote(it)}" } ?: shellQuote("$alsDir/app/ate"))
            }
        }
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
    if (showQvm) Qvm(onClose = { showQvm = false }, onScript = { script ->
        showQvm = false; create(script)
    }) else if (showTTY) active?.let { TTYScreen(it) { TTYIME() } } else if (showTTYHub) TTYHub(
        sessions,
        onSelect = { active = it; showTTY = true; showTTYHub = false },
        onDelete = { it.session.finishIfRunning() },
        onCreate = { create(null) }) else Box(
        Modifier.fillMaxSize(), Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ALSButton(R.drawable.arrow_forward) { showQvm = true }
            ALSButton(R.drawable.terminal) {
                if (sessions.isEmpty()) create(null) else showTTYHub = true
            }
            ALSButton(R.drawable.power) { close(); onFin(); (ctx as? Activity)?.finishAffinity() }
        }
    }
}

@Composable
private fun Qvm(onClose: () -> Unit, onScript: (String) -> Unit) {
    val root = remember { File(alsDir) }
    var current by remember { mutableStateOf(root) }
    val entries by produceState(emptyList(), current) {
        value = withContext(Dispatchers.IO) { scanQvmEntries(root, current) }
    }
    BackHandler {
        current = if (current.absolutePath == root.absolutePath) {
            onClose()
            root
        } else {
            current.parentFile ?: root
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black), Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.72f)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (entries.isEmpty()) {
                ALSList(
                    current.name.ifEmpty { current.absolutePath },
                    checked = false,
                    first = true,
                    last = true
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    ALSList(
                        data = entry.name,
                        checked = entry.file.isDirectory || entry.runnable,
                        first = index == 0,
                        last = index == entries.lastIndex,
                        onClick = {
                            when {
                                entry.file.isDirectory -> current = entry.file
                                entry.runnable -> onScript(entry.file.absolutePath)
                            }
                        })
                }
            }
        }
    }
}

private fun scanQvmEntries(root: File, directory: File): List<QvmEntry> =
    directory.listFiles()?.filter { file ->
        !file.isHidden && (!file.isDirectory || directory.absolutePath != root.absolutePath || file.name in qvmRoots)
    }?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })?.map { file ->
        QvmEntry(
            file = file,
            name = if (file.isDirectory) "${file.name}/" else file.name,
            runnable = file.isFile && file.extension.equals("sh", ignoreCase = true)
        )
    }.orEmpty()