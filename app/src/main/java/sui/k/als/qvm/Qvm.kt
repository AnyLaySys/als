package sui.k.als.qvm

import android.content.*
import android.view.*
import android.view.inputmethod.*
import androidx.activity.compose.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.core.net.*
import com.termux.terminal.*
import kotlinx.coroutines.*
import sui.k.als.*
import sui.k.als.tty.*
import java.io.*

@Composable
fun Qvm(onExit: () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFA8C7FA),
            secondary = Color(0xFFD7C46C),
            background = Color(0xFF05070A),
            surface = Color(0xFF111318),
            surfaceVariant = Color(0xFF1B1B1F),
            onSurface = Color(0xFFE3E2E6)
        )
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var configs by remember { mutableStateOf(emptyList<QvmConfig>()) }
        var editing by remember { mutableStateOf<QvmConfig?>(null) }
        var isCreating by remember { mutableStateOf(false) }
        var terminalInstance by remember { mutableStateOf<TTYInstance?>(null) }
        var showTerminal by remember { mutableStateOf(false) }
        var showQvmSplash by remember { mutableStateOf(false) }
        fun loadConfigs() = mutableListOf<QvmConfig>().apply {
            File("$alsDir/app/qvm").takeIf { it.exists() }?.listFiles { it.isDirectory }
                ?.forEach { directory ->
                    File(directory, "${directory.name}.cfg").takeIf { it.exists() }?.let { file ->
                        runCatching {
                            val qvmMap = parseFlatConfigFile(file)
                            val vnc = qvmMap.optString("vnc_port").ifEmpty { ":0" }
                            val processes = Runtime.getRuntime()
                                .exec(arrayOf(su, "-c", "ps -ef")).inputStream.bufferedReader()
                                .use { it.readText() }
                            val isRunning = processes.lineSequence().any {
                                it.contains("qemu-system-aarch64") && it.contains("vnc $vnc")
                            }
                            add(
                                QvmConfig(
                                    qvmMap.optString("name").ifEmpty { directory.name },
                                    isRunning,
                                    qvmMap
                                )
                            )
                        }
                    }
                }
        }

        suspend fun refresh() {
            configs = withContext(Dispatchers.IO) { loadConfigs() }
        }

        fun openVnc(virtualMachine: QvmConfig) {
            runCatching {
                val portString = virtualMachine.raw?.optString("vnc_port") ?: ":0"
                val port = if (portString.startsWith(":")) portString.substring(1)
                    .toInt() + 5900 else portString.toInt()
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW, "vnc://localhost:$port".toUri()
                    ).apply {
                        setPackage("com.gaurav.avnc")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
            }
        }

        fun startVirtualMachine(virtualMachine: QvmConfig) {
            if (terminalInstance == null) {
                terminalInstance = createTTYInstance(context, object : TTYSessionStub() {
                    override fun onSessionFinished(session: TerminalSession) {
                        terminalInstance = null
                        showTerminal = false
                    }
                }, object : TTYViewStub() {
                    override fun onSingleTapUp(event: MotionEvent) {
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
                            terminalInstance?.view, 0
                        )
                    }
                }).also { instance ->
                    scope.launch {
                        delay(90)
                        cmd(instance.session, shellQuote(su))
                        cmd(
                            instance.session,
                            "VM_DIR=${shellQuote("$alsDir/app/qvm/${virtualMachine.name}")}"
                        )
                        cmd(instance.session, virtualMachine.getCommand())
                    }
                }
                showQvmSplash = true
            } else showQvmSplash = false
            showTerminal = true
        }
        LaunchedEffect(Unit) {
            while (true) {
                refresh()
                delay(3000)
            }
        }
        BackHandler {
            when {
                showTerminal -> showTerminal = false
                isCreating -> isCreating = false
                editing != null -> editing = null
                else -> onExit()
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                if (showTerminal && terminalInstance != null) {
                    if (showQvmSplash) Splash(
                        instance = terminalInstance!!, onTimeout = { showQvmSplash = false })
                    else TTYScreen(terminalInstance!!) { TTYIME() }
                } else when {
                    editing != null -> QvmCreate(editing) {
                        editing = null; scope.launch { refresh() }
                    }

                    isCreating -> QvmCreate(null) { isCreating = false; scope.launch { refresh() } }
                    else -> QvmDashboard(
                        configs = configs,
                        onExit = onExit,
                        onCreate = { isCreating = true },
                        onEdit = { editing = it },
                        onOpenVnc = ::openVnc,
                        onStart = ::startVirtualMachine
                    )
                }
            }
        }
    }
}
