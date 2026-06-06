package sui.k.als.qvm

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import kotlinx.coroutines.*
import org.json.*
import sui.k.als.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun QvmCreate(config: QvmConfig? = null, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hostProcessorCount = remember { qvmHostProcessorCount() }
    val qvmMap = remember {
        mutableStateMapOf<String, Any>().apply {
            config?.raw?.let { raw -> raw.keys().forEach { key -> put(key, raw.get(key)) } }
            if (config != null) put("name", config.name)
            if (get("smp") == null) put("smp", hostProcessorCount)
            if (get("mem") == null) put("mem", "6G")
            if (get("swiotlb") == null) put("swiotlb", "64M")
            if (get("vnc_port") == null) put("vnc_port", ":0")
            if (get("prealloc") == null) put("prealloc", false)
            if (get("lock_memory") == null) put("lock_memory", false)
        }
    }
    LaunchedEffect(Unit) {
        if (qvmMap["xres"] == null || qvmMap["yres"] == null) {
            try {
                val resX = Runtime.getRuntime().exec(
                    arrayOf(
                        su, "-c", "wm size | cut -d' ' -f3 | cut -d'x' -f2"
                    )
                ).inputStream.bufferedReader().readText().trim()
                val resY = Runtime.getRuntime().exec(
                    arrayOf(
                        su, "-c", "wm size | cut -d' ' -f3 | cut -d'x' -f1"
                    )
                ).inputStream.bufferedReader().readText().trim()
                if (resX.isNotEmpty()) qvmMap["xres"] = resX
                if (resY.isNotEmpty()) qvmMap["yres"] = resY
            } catch (_: Exception) {
            }
        }
    }
    val cdroms = remember {
        val list = mutableStateListOf<MutableMap<String, Any>>()
        val array = qvmMap["cdrom"] as? JSONArray
        if (array != null) {
            for (idx in 0 until array.length()) {
                val obj = array.getJSONObject(idx)
                val map = mutableStateMapOf<String, Any>()
                obj.keys().forEach { key -> map[key] = obj.get(key) }
                list.add(map)
            }
        } else if (config == null) {
            list.add(mutableStateMapOf("path" to "", "index" to 1))
        }
        list
    }
    val disks = remember {
        val list = mutableStateListOf<MutableMap<String, Any>>()
        val array = qvmMap["disk"] as? JSONArray
        if (array != null) {
            for (idx in 0 until array.length()) {
                val obj = array.getJSONObject(idx)
                val map = mutableStateMapOf<String, Any>()
                obj.keys().forEach { key -> map[key] = obj.get(key) }
                list.add(map)
            }
        } else if (config == null) {
            list.add(mutableStateMapOf("path" to "", "cache" to "unsafe", "index" to 2))
        }
        list
    }
    val networks = remember {
        val list = mutableStateListOf<MutableMap<String, Any>>()
        val array = qvmMap["net"] as? JSONArray
        if (array != null) {
            for (idx in 0 until array.length()) {
                val obj = array.getJSONObject(idx)
                val map = mutableStateMapOf<String, Any>()
                obj.keys().forEach { key -> map[key] = obj.get(key) }
                list.add(map)
            }
        } else if (config == null) {
            list.add(
                mutableStateMapOf(
                    "device" to "virtio-net-pci",
                    "backend" to "user",
                    "protocol" to "tcp",
                    "ports" to "2222-:22"
                )
            )
        }
        list
    }
    val activeIndex = remember { mutableIntStateOf(0) }
    val currentCfg = remember(qvmMap.toMap(), cdroms.toList(), disks.toList(), networks.toList()) {
        QvmCfg(
            smp = qvmMap["smp"]?.toString() ?: "4",
            mem = qvmMap["mem"]?.toString() ?: "6G",
            swiotlb = qvmMap["swiotlb"]?.toString() ?: "64M",
            prealloc = qvmMap["prealloc"] as? Boolean ?: false,
            preallocSize = qvmMap["mem"]?.toString() ?: "6G",
            lockMemory = qvmMap["lock_memory"] as? Boolean ?: false,
            vncPort = qvmMap["vnc_port"]?.toString() ?: ":0",
            audioEnabled = qvmMap["audio"] == 1,
            usbEnabled = qvmMap["usb"] == 1,
            resolution = try {
                val width = qvmMap["xres"]?.toString()?.toInt() ?: 1280
                val height = qvmMap["yres"]?.toString()?.toInt() ?: 720
                width to height
            } catch (_: Exception) {
                1280 to 720
            },
            cdrom = cdroms.map {
                StorageDevice(
                    it["path"]?.toString() ?: "", it["index"]?.toString()?.toIntOrNull()
                )
            },
            disk = disks.map {
                StorageDevice(
                    it["path"]?.toString() ?: "",
                    it["index"]?.toString()?.toIntOrNull(),
                    it["cache"]?.toString() ?: "unsafe"
                )
            },
            network = networks.map {
                NetworkConfig(
                    it["backend"]?.toString() ?: "user",
                    it["protocol"]?.toString() ?: "tcp",
                    it["ports"]?.toString() ?: "2222-:22",
                    it["device"]?.toString() ?: "virtio-net-pci"
                )
            })
    }
    val icons = remember(cdroms.size, disks.size, networks.size, qvmMap["audio"]) {
        val list = mutableListOf(R.drawable.chips)
        repeat(cdroms.size) { list.add(R.drawable.album) }
        repeat(disks.size) { list.add(R.drawable.hard_drive) }
        repeat(networks.size) { list.add(R.drawable.wifi) }
        list.add(R.drawable.square)
        if (qvmMap["audio"] == 1) list.add(R.drawable.volume_up)
        list.add(R.drawable.add)
        list.add(R.drawable.preview)
        list
    }
    ExpressiveCanvas(
        icons = icons,
        activeIndex = activeIndex.intValue,
        onIndexChange = { activeIndex.intValue = it },
        onLongClick = { index ->
            val startCdrom = 1
            val startDisk = startCdrom + cdroms.size
            val startNetwork = startDisk + disks.size
            val startDisplay = startNetwork + networks.size
            when (index) {
                in startCdrom until startDisk -> {
                    cdroms.removeAt(index - startCdrom); activeIndex.intValue = 0
                }

                in startDisk until startNetwork -> {
                    disks.removeAt(index - startDisk); activeIndex.intValue = 0
                }

                in startNetwork until startDisplay -> {
                    networks.removeAt(index - startNetwork); activeIndex.intValue = 0
                }

                startDisplay + 1 -> if (qvmMap["audio"] == 1) {
                    qvmMap["audio"] = 0; activeIndex.intValue = 0
                }
            }
        },
        onAction = {
            val name = qvmMap["name"]?.toString() ?: ""
            if (!qvmNameRegex.matches(name)) {
                activeIndex.intValue = 0; return@ExpressiveCanvas
            }
            val content = buildString {
                val base = listOf("name", "mem", "swiotlb", "smp", "xres", "yres", "vnc_port")
                base.forEach { k -> qvmMap[k]?.let { v -> append("$k:$v\n") } }
                append("audio:${if (qvmMap["audio"] == 1) 1 else 0}\n")
                append("prealloc:${if (qvmMap["prealloc"] == true) 1 else 0}\n")
                append("lock_memory:${if (qvmMap["lock_memory"] == true) 1 else 0}\n")
                cdroms.forEachIndexed { deviceIndex, device ->
                    device.forEach { (key, value) ->
                        append(
                            "cdrom${deviceIndex + 1}.$key:$value\n"
                        )
                    }
                }
                disks.forEachIndexed { deviceIndex, device ->
                    device.forEach { (key, value) ->
                        append(
                            "disk${deviceIndex + 1}.$key:$value\n"
                        )
                    }
                }
                networks.forEachIndexed { deviceIndex, device ->
                    device.forEach { (key, value) ->
                        append(
                            "net${deviceIndex + 1}.$key:$value\n"
                        )
                    }
                }
            }.trim()
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = "$alsDir/app/qvm/$name"
                        val target = "$dir/$name.cfg"
                        var delimiter = "ALS_CFG_${System.nanoTime()}"
                        while (content.lineSequence().any { it == delimiter }) delimiter += "_"
                        val cmd =
                            "mkdir -p ${shellQuote(dir)}\ncat > ${shellQuote(target)} <<'$delimiter'\n$content\n$delimiter\nexit\n"
                        val proc = Runtime.getRuntime().exec(su)
                        proc.outputStream.use { it.write(cmd.toByteArray(Charsets.UTF_8)); it.flush() }
                        proc.waitFor() == 0
                    }.getOrDefault(false)
                }
                if (saved) onBack()
            }
        }) { index ->
        val startCdrom = 1
        val startDisk = startCdrom + cdroms.size
        val startNetwork = startDisk + disks.size
        val startDisplay = startNetwork + networks.size
        val startAudio = startDisplay + 1
        val startAdd = startAudio + (if (qvmMap["audio"] == 1) 1 else 0)
        when (index) {
            0 -> QvmSystem(qvmMap)
            in startCdrom until startDisk -> QvmCdrom(cdroms[index - startCdrom])
            in startDisk until startNetwork -> QvmDisk(disks[index - startDisk])
            in startNetwork until startDisplay -> QvmNetwork(networks[index - startNetwork])
            startDisplay -> QvmDisplay(qvmMap)
            in startAudio until startAdd -> QvmAudio(qvmMap)
            startAdd -> QvmAddDev(cdroms, disks, networks, qvmMap) { activeIndex.intValue = it }
            else -> QvmPreview(currentCfg)
        }
    }
}

@Composable
private fun QvmSystem(state: MutableMap<String, Any>) {
    val hostProcessorCount = remember { qvmHostProcessorCount() }
    val name = state["name"]?.toString() ?: ""
    val cores = state["cores"]?.toString() ?: hostProcessorCount
    val threads = state["threads"]?.toString() ?: "1"
    val sockets = state["sockets"]?.toString() ?: "1"
    val smp = state["smp"]?.toString() ?: hostProcessorCount
    val memory = state["mem"]?.toString() ?: "6G"
    val swiotlb = state["swiotlb"]?.toString() ?: "64M"
    val prealloc = state["prealloc"] == true
    val preallocSize = state["prealloc_size"]?.toString() ?: ""
    val validName = name.isNotEmpty() && qvmNameRegex.matches(name)
    if (state["mem"] == null) state["mem"] = "6G"
    if (state["swiotlb"] == null) state["swiotlb"] = "64M"
    LaunchedEffect(cores, threads, sockets, smp) {
        state["processor"] = "-smp cores=$cores,threads=$threads,sockets=$sockets,$smp "
    }
    LaunchedEffect(state["mem"], state["swiotlb"], state["prealloc_size"], state["prealloc"], state["force"], state["lock"]) {
        val memoryText = state["mem"].toString()
        val swiotlbText = state["swiotlb"].toString()
        var memoryCommand = "-m $memoryText "
        var objectCommand = "-object arm-confidential-guest,id=prot0,swiotlb-size=$swiotlbText "
        if (state["prealloc"] == true) {
            val size = state["prealloc_size"]?.toString() ?: "6G"
            val megabytes = if (size.endsWith("G")) "${size.removeSuffix("G").toInt() * 1024}M" else size
            objectCommand += "-object memory-backend-ram,id=mem,size=$megabytes,prealloc=on "
            if (state["force"] == true) memoryCommand += "-mem-prealloc "
            if (state["lock"] == true) memoryCommand += "-overcommit mem-lock=on "
        }
        state["memory"] = memoryCommand
        state["objects"] = objectCommand
    }
    ALSList(
        stringResource(R.string.cfg_name),
        value = name,
        first = true,
        background = if (!validName) Color.Red else null,
        onValueChange = { state["name"] = it })
    ALSList(stringResource(R.string.cpu_cores), value = cores, onValueChange = { state["cores"] = it })
    ALSList(stringResource(R.string.cpu_threads), value = threads, onValueChange = { state["threads"] = it })
    ALSList(stringResource(R.string.cpu_sockets), value = sockets, onValueChange = { state["sockets"] = it })
    ALSList(stringResource(R.string.smp), value = smp, onValueChange = { state["smp"] = it })
    ALSList(
        stringResource(R.string.memory_size),
        value = memory,
        background = if (memory.isEmpty()) Color.Red else null,
        onValueChange = { state["mem"] = it })
    ALSList(
        stringResource(R.string.swiotlb_buffer_size),
        value = swiotlb,
        background = if (swiotlb.isEmpty()) Color.Red else null,
        onValueChange = { state["swiotlb"] = it })
    ALSList(stringResource(R.string.prealloc), checked = prealloc, last = !prealloc) {
        state["prealloc"] = state["prealloc"] != true
    }
    if (prealloc) {
        ALSList(
            stringResource(R.string.alloc_size),
            value = preallocSize,
            background = if (preallocSize.isEmpty()) Color.Red else null,
            onValueChange = { state["prealloc_size"] = it })
        ALSList(stringResource(R.string.force_alloc), checked = state["force"] == true) {
            state["force"] = state["force"] != true
        }
        ALSList(stringResource(R.string.mem_lock), checked = state["lock"] == true, last = true) {
            state["lock"] = state["lock"] != true
        }
    }
}
