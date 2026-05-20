package sui.k.als.qvm
import android.content.*
import android.view.*
import android.view.inputmethod.*
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.core.net.*
import com.termux.terminal.*
import kotlinx.coroutines.*
import org.json.*
import sui.k.als.*
import sui.k.als.R
import sui.k.als.tty.*
import sui.k.als.ui.*
import java.io.*

const val qvmDir = "$alsDir/app/qvm"
data class QvmConfig(
    val name: String,
    var isRunning: Boolean = false,
    val raw: JSONObject? = null
) {
    fun getCommand(): String {
        val json = raw ?: JSONObject()
        val cfg = QvmCfg(
            smp = json.optString("smp", "4"),
            mem = json.optString("mem", "6G"),
            swiotlb = json.optString("swiotlb", "64M"),
            prealloc = json.optInt("prealloc", 0) == 1,
            lockMemory = json.optInt("lock_memory", 0) == 1,
            vncPort = json.optString("vnc_port", ":0"),
            audioEnabled = json.optInt("audio", 0) == 1,
            resolution = try {
                val x = json.optString("xres").toIntOrNull() ?: 1280
                val y = json.optString("yres").toIntOrNull() ?: 720
                x to y
            } catch (_: Exception) {
                1280 to 720
            },
            cdrom = mutableListOf<StorageDevice>().apply {
                val arr = json.optJSONArray("cdrom") ?: return@apply
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(StorageDevice(o.optString("path"), o.optString("index").toIntOrNull()))
                }
            },
            disk = mutableListOf<StorageDevice>().apply {
                val arr = json.optJSONArray("disk") ?: return@apply
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        StorageDevice(
                            o.optString("path"),
                            o.optString("index").toIntOrNull(),
                            o.optString("cache", "unsafe")
                        )
                    )
                }
            },
            network = mutableListOf<NetworkConfig>().apply {
                val arr = json.optJSONArray("net") ?: return@apply
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        NetworkConfig(
                            o.optString("backend", "user"),
                            o.optString("protocol", "tcp"),
                            o.optString("ports", "2222-:22"),
                            o.optString("device", "virtio-net-pci")
                        )
                    )
                }
            }
        )
        return QvmCmd.build(cfg)
    }
}
@Composable
fun Qvm(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var configs by remember { mutableStateOf(emptyList<QvmConfig>()) }
    var editing by remember { mutableStateOf<QvmConfig?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var terminalInstance by remember { mutableStateOf<TTYInstance?>(null) }
    var showTerminal by remember { mutableStateOf(false) }
    var showQvmSplash by remember { mutableStateOf(false) }
    fun refresh() = mutableListOf<QvmConfig>().apply {
        File("$alsDir/app/qvm").takeIf { it.exists() }?.listFiles { it.isDirectory }
            ?.forEach { dir ->
                File(dir, "${dir.name}.cfg").takeIf { it.exists() }?.let { file ->
                    runCatching {
                        val qvmMap = parseFlatConfigFile(file)
                        val vnc = qvmMap.optString("vnc_port").ifEmpty { ":0" }
                        val isRunning = Runtime.getRuntime().exec(
                            arrayOf(su, "-c", "ps -ef | grep qemu-system-aarch64 | grep \"vnc $vnc\" | grep -v grep")
                        ).inputStream.bufferedReader().use { it.readText().trim().isNotEmpty() }
                        add(QvmConfig(qvmMap.optString("name").ifEmpty { dir.name }, isRunning, qvmMap))
                    }
                }
            }
    }.also { configs = it }
    LaunchedEffect(Unit) {
        while (true) {
            refresh(); delay(3000)
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
    Surface(color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            if (showTerminal && terminalInstance != null) {
                if (showQvmSplash) Splash(instance = terminalInstance!!, onTimeout = { showQvmSplash = false })
                else TTYScreen(terminalInstance!!) { TTYIME() }
            } else when {
                editing != null -> QvmCreate(editing) { editing = null; refresh() }
                isCreating -> QvmCreate(null) { isCreating = false; refresh() }
                else -> Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).padding(9.dp).verticalScroll(rememberScrollState())) {
                        configs.forEachIndexed { index, qvm ->
                            ALSList(
                                data = qvm.name,
                                first = index == 0,
                                last = index == configs.size - 1,
                                onClick = { editing = qvm },
                                iconContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        ALSButton(R.drawable.square) {
                                            runCatching {
                                                val portStr = qvm.raw?.optString("vnc_port") ?: ":0"
                                                val port = if (portStr.startsWith(":")) portStr.substring(1).toInt() + 5900 else portStr.toInt()
                                                context.startActivity(Intent(Intent.ACTION_VIEW, "vnc://localhost:$port".toUri()).apply {
                                                    setPackage("com.gaurav.avnc"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                })
                                            }
                                        }
                                        ALSButton(R.drawable.power) {
                                            if (terminalInstance == null) {
                                                terminalInstance = createTTYInstance(context, object : TTYSessionStub() {
                                                    override fun onSessionFinished(session: TerminalSession) {
                                                        terminalInstance = null; showTerminal = false
                                                    }
                                                }, object : TTYViewStub() {
                                                    override fun onSingleTapUp(event: MotionEvent) {
                                                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(terminalInstance?.view, 0)
                                                    }
                                                }).also {
                                                    ttySession = it.session
                                                    scope.launch {
                                                        delay(90)
                                                        cmd(su); cmd("VM_DIR=\"$alsDir/app/qvm/${qvm.name}\"")
                                                        cmd(qvm.getCommand())
                                                    }
                                                }
                                                showQvmSplash = true
                                            } else showQvmSplash = false
                                            showTerminal = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 9.dp), Alignment.Center) { ALSButton(R.drawable.add) { isCreating = true } }
                }
            }
        }
    }
}
private fun parseFlatConfigFile(file: File): JSONObject = JSONObject().apply {
    val maps = mapOf("cdrom" to mutableMapOf(), "disk" to mutableMapOf(), "net" to mutableMapOf<Int, JSONObject>())
    file.readLines().forEach { line ->
        val parts = line.split(":", limit = 2).takeIf { it.size == 2 } ?: return@forEach
        val key = parts[0].trim()
        val value = parts[1].trim()
        val prefix = maps.keys.find { key.startsWith(it) }
        if (prefix != null) {
            val dotIdx = key.indexOf('.')
            if (dotIdx != -1) {
                val idx = key.substring(prefix.length, dotIdx).toIntOrNull() ?: 0
                (maps[prefix] as MutableMap<Int, JSONObject>).getOrPut(idx) { JSONObject() }.put(key.substring(dotIdx + 1), value)
            }
        } else put(key, value)
    }
    maps.forEach { (k, v) -> put(k, JSONArray().apply { v.keys.sorted().forEach { put(v[it]) } }) }
}