package sui.k.als.dss
import android.content.*
import android.view.*
import android.view.inputmethod.*
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import com.termux.terminal.*
import kotlinx.coroutines.*
import org.json.*
import sui.k.als.*
import sui.k.als.R
import sui.k.als.tty.*
import sui.k.als.ui.*
import java.io.*

const val dssDir = "$alsDir/app/dss"
data class DSSConfig(val name: String, var isRunning: Boolean = false, val raw: JSONObject? = null) {
    fun buildCommand(command: String, arguments: String = ""): String = buildString {
        append("$dssDir/droidspaces ")
        raw?.let { json ->
            json.optString("name").takeIf { it.isNotEmpty() }?.let { append("--name=\"$it\" ") }
            json.optString("rootfs").takeIf { it.isNotEmpty() }?.let { append("-r \"$it\" ") }
            json.optString("rootfs-img").takeIf { it.isNotEmpty() }?.let { append("-i \"$it\" ") }
            json.optString("net").takeIf { it.isNotEmpty() }?.let { append("--net=$it ") }
            json.optString("nat-ip").takeIf { it.isNotEmpty() }?.let { append("--nat-ip=$it ") }
            json.optString("upstream").takeIf { it.isNotEmpty() }?.let { append("--upstream $it ") }
            json.optString("port").takeIf { it.isNotEmpty() }?.let { append("--port $it ") }
            json.optString("dns").takeIf { it.isNotEmpty() }?.let { append("-d $it ") }
            if (json.optBoolean("disable-ipv6")) append("-I ")
            if (json.optBoolean("enable-android-storage")) append("-S ")
            if (json.optBoolean("hw-access")) append("-H ")
            if (json.optBoolean("gpu")) append("--gpu ")
            if (json.optBoolean("termux-x11")) append("-X ")
            if (json.optBoolean("selinux-permissive")) append("-P ")
            if (json.optBoolean("volatile")) append("-V ")
            if (json.optBoolean("force-cgroupv1")) append("--force-cgroupv1 ")
            if (json.optBoolean("block-nested-namespaces")) append("--block-nested-namespaces ")
            json.optString("privileged").takeIf { it.isNotEmpty() }?.let { append("--privileged=$it ") }
            if (json.optBoolean("foreground")) append("-f ")
            json.optString("env").takeIf { it.isNotEmpty() }?.let { append("-E \"$it\" ") }
            json.optJSONArray("binds")?.let { array ->
                for (index in 0 until array.length()) append("-B \"${array.getString(index)}\" ")
            }
        }
        append("$command $arguments")
    }.trim()
}
@Composable
fun DSS(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var configs by remember { mutableStateOf(emptyList<DSSConfig>()) }
    var editing by remember { mutableStateOf<DSSConfig?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var terminalInstance by remember { mutableStateOf<TTYInstance?>(null) }
    var showTerminal by remember { mutableStateOf(false) }
    fun refresh() {
        val list = mutableListOf<DSSConfig>()
        val directory = File(dssDir)
        if (directory.exists()) {
            directory.listFiles { file -> file.extension == "json" }?.forEach { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    val name = json.optString("name").ifEmpty { file.nameWithoutExtension }
                    val isRunning = Runtime.getRuntime().exec(arrayOf(su, "-c", "$dssDir/droidspaces --name=$name status | grep -q 'running'")).waitFor() == 0
                    list.add(DSSConfig(name, isRunning, json))
                }
            }
        }
        configs = list
    }
    LaunchedEffect(Unit) { while (true) { refresh(); delay(5000) } }
    fun openTerminal(command: String) {
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
            scope.launch { delay(90); cmd(su); cmd(command) }
        }
        showTerminal = true
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
                TTYScreen(terminalInstance!!) { TTYIME() }
            } else when {
                editing != null -> DSSCreate(editing) { editing = null; refresh() }
                isCreating -> DSSCreate(null) { isCreating = false; refresh() }
                else -> Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).padding(9.dp).verticalScroll(rememberScrollState())) {
                        configs.forEachIndexed { index, config ->
                            ALSList(
                                data = config.name,
                                first = index == 0,
                                last = index == configs.size - 1,
                                onClick = { editing = config },
                                iconContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        ALSButton(if (config.isRunning) R.drawable.power else R.drawable.arrow_forward, click = {
                                            openTerminal(if (config.isRunning) config.buildCommand("stop") else config.buildCommand("start"))
                                        })
                                        ALSButton(R.drawable.delete, click = {
                                            scope.launch(Dispatchers.IO) {
                                                Runtime.getRuntime().exec(arrayOf(su, "-c", "rm $dssDir/${config.name}.json")).waitFor()
                                                refresh()
                                            }
                                        })
                                    }
                                }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally)) {
                        ALSButton(R.drawable.add, click = { isCreating = true })
                        ALSButton(R.drawable.info, click = { openTerminal("$dssDir/droidspaces check; $dssDir/droidspaces scan; $dssDir/droidspaces show") })
                    }
                }
            }
        }
    }
}
@Composable
fun DSSCreate(config: DSSConfig? = null, onBack: () -> Unit) {
    val defaultName = stringResource(R.string.default_container_name)
    val state = remember {
        mutableStateMapOf<String, Any>().apply {
            config?.raw?.let { raw -> raw.keys().forEach { key -> put(key, raw.get(key)) } }
            if (get("name") == null) put("name", defaultName)
        }
    }
    val binds = remember {
        mutableStateListOf<String>().apply {
            (state["binds"] as? JSONArray)?.let { array -> for (index in 0 until array.length()) add(array.getString(index)) }
        }
    }
    val activeIndex = remember { mutableIntStateOf(0) }
    val icons = listOf(R.drawable.archive, R.drawable.wifi, R.drawable.hard_drive, R.drawable.settings, R.drawable.preview)
    ExpressiveCanvas(
        icons = icons,
        activeIndex = activeIndex.intValue,
        onIndexChange = { activeIndex.intValue = it },
        onAction = {
            val name = state["name"].toString()
            val json = JSONObject(state.toMap()).apply { put("binds", JSONArray(binds)) }
            runCatching {
                Runtime.getRuntime().exec(arrayOf(su, "-c", "mkdir -p $dssDir")).waitFor()
                val tempFile = File.createTempFile("dss_tmp", null).apply { writeText(json.toString()) }
                val targetPath = "$dssDir/$name.json"
                Runtime.getRuntime().exec(arrayOf(su, "-c", "cp ${tempFile.absolutePath} $targetPath && chmod 644 $targetPath")).waitFor()
                tempFile.delete()
            }
            onBack()
        }
    ) { index ->
        when (index) {
            0 -> ALSList(stringResource(R.string.container_name), value = state["name"]?.toString() ?: "", onValueChange = { state["name"] = it }, first = true, last = true)
            1 -> Column {
                ALSList(stringResource(R.string.network_mode), value = state["net"]?.toString() ?: "host", onValueChange = { state["net"] = it }, first = true)
                ALSList(stringResource(R.string.disable_ipv6), checked = state["disable-ipv6"] == true, onClick = { state["disable-ipv6"] = !(state["disable-ipv6"] as? Boolean ?: false) })
                ALSList(stringResource(R.string.dns_server), value = state["dns"]?.toString() ?: "", onValueChange = { state["dns"] = it })
                ALSList(stringResource(R.string.static_ip), value = state["nat-ip"]?.toString() ?: "", onValueChange = { state["nat-ip"] = it })
                ALSList(stringResource(R.string.upstream_interface), value = state["upstream"]?.toString() ?: "", onValueChange = { state["upstream"] = it })
                ALSList(stringResource(R.string.port_forwarding), value = state["port"]?.toString() ?: "", onValueChange = { state["port"] = it }, last = true)
            }
            2 -> Column {
                ALSList(stringResource(R.string.rootfs_directory), value = state["rootfs"]?.toString() ?: "", onValueChange = { state["rootfs"] = it; state["rootfs-img"] = "" }, first = true)
                ALSList(stringResource(R.string.rootfs_image), value = state["rootfs-img"]?.toString() ?: "", onValueChange = { state["rootfs-img"] = it; state["rootfs"] = "" })
                ALSList(stringResource(R.string.volatile_mode), checked = state["volatile"] == true, onClick = { state["volatile"] = !(state["volatile"] as? Boolean ?: false) }, last = true)
                Spacer(Modifier.height(9.dp))
                binds.forEachIndexed { i, b -> ALSList(stringResource(R.string.mount_point, i + 1), value = b, onValueChange = { binds[i] = it }, first = i == 0, last = i == binds.size - 1) }
                Box(Modifier.fillMaxWidth().padding(9.dp), Alignment.Center) { ALSButton(R.drawable.add, click = { binds.add("") }) }
            }
            3 -> Column {
                ALSList(stringResource(R.string.selinux), checked = state["selinux-permissive"] == true, onClick = { state["selinux-permissive"] = !(state["selinux-permissive"] as? Boolean ?: false) }, first = true)
                ALSList(stringResource(R.string.android_storage), checked = state["enable-android-storage"] == true, onClick = { state["enable-android-storage"] = !(state["enable-android-storage"] as? Boolean ?: false) })
                ALSList(stringResource(R.string.hardware_access), checked = state["hw-access"] == true, onClick = { state["hw-access"] = !(state["hw-access"] as? Boolean ?: false) })
                ALSList(stringResource(R.string.privileged_mode), value = state["privileged"]?.toString() ?: "", onValueChange = { state["privileged"] = it })
                ALSList(stringResource(R.string.gpu_acceleration), checked = state["gpu"] == true, onClick = { state["gpu"] = !(state["gpu"] as? Boolean ?: false) })
                ALSList(stringResource(R.string.termux_x11), checked = state["termux-x11"] == true, onClick = { state["termux-x11"] = !(state["termux-x11"] as? Boolean ?: false) })
                ALSList(stringResource(R.string.force_cgroupv1), checked = state["force-cgroupv1"] == true, onClick = { state["force-cgroupv1"] = !(state["force-cgroupv1"] as? Boolean ?: false) }, last = true)
            }
            4 -> SelectionContainer {
                Text(DSSConfig(state["name"].toString(), false, JSONObject(state.toMap()).apply { put("binds", JSONArray(binds)) }).buildCommand("start"), color = Color.Gray, fontSize = 9.sp, fontFamily = localFont.current)
            }
        }
    }
}