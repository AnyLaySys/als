package sui.k.als.qvm
import androidx.compose.runtime.*
import org.json.*
import sui.k.als.*
import sui.k.als.R

@Composable
fun QvmCreate(config: QvmConfig? = null, onBack: () -> Unit) {
    val qvmMap = remember {
        mutableStateMapOf<String, Any>().apply {
            config?.raw?.let { raw -> raw.keys().forEach { key -> put(key, raw.get(key)) } }
            if (config != null) put("name", config.name)
            if (get("smp") == null) put("smp", "4")
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
                val resX = Runtime.getRuntime().exec(arrayOf(su, "-c", "wm size | cut -d' ' -f3 | cut -d'x' -f2")).inputStream.bufferedReader().readText().trim()
                val resY = Runtime.getRuntime().exec(arrayOf(su, "-c", "wm size | cut -d' ' -f3 | cut -d'x' -f1")).inputStream.bufferedReader().readText().trim()
                if (resX.isNotEmpty()) qvmMap["xres"] = resX
                if (resY.isNotEmpty()) qvmMap["yres"] = resY
            } catch (_: Exception) {}
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
            list.add(mutableStateMapOf("device" to "virtio-net-pci", "backend" to "user", "protocol" to "tcp", "ports" to "2222-:22"))
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
                val x = qvmMap["xres"]?.toString()?.toInt() ?: 1280
                val y = qvmMap["yres"]?.toString()?.toInt() ?: 720
                x to y
            } catch (_: Exception) {
                1280 to 720
            },
            cdrom = cdroms.map {
                StorageDevice(
                    it["path"]?.toString() ?: "",
                    it["index"]?.toString()?.toIntOrNull()
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
            }
        )
    }
    val icons = remember(cdroms.size, disks.size, networks.size, qvmMap["audio"]) {
        val list = mutableListOf(R.drawable.archive, R.drawable.chips, R.drawable.memory)
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
            val startCdrom = 3
            val startDisk = startCdrom + cdroms.size
            val startNetwork = startDisk + disks.size
            val startDisplay = startNetwork + networks.size
            when (index) {
                in startCdrom until startDisk -> { cdroms.removeAt(index - startCdrom); activeIndex.intValue = 0 }
                in startDisk until startNetwork -> { disks.removeAt(index - startDisk); activeIndex.intValue = 0 }
                in startNetwork until startDisplay -> { networks.removeAt(index - startNetwork); activeIndex.intValue = 0 }
                startDisplay + 1 -> if (qvmMap["audio"] == 1) { qvmMap["audio"] = 0; activeIndex.intValue = 0 }
            }
        },
        onAction = {
            if (qvmMap["name"]?.toString()?.isBlank() != false) { activeIndex.intValue = 0; return@ExpressiveCanvas }
            try {
                val content = buildString {
                    val base = listOf("name", "mem", "swiotlb", "smp", "xres", "yres", "vnc_port")
                    base.forEach { k -> qvmMap[k]?.let { v -> append("$k:$v\n") } }
                    append("audio:${if (qvmMap["audio"] == 1) 1 else 0}\n")
                    append("prealloc:${if (qvmMap["prealloc"] == true) 1 else 0}\n")
                    append("lock_memory:${if (qvmMap["lock_memory"] == true) 1 else 0}\n")
                    cdroms.forEachIndexed { i, m -> m.forEach { (k, v) -> append("cdrom${i + 1}.$k:$v\n") } }
                    disks.forEachIndexed { i, m -> m.forEach { (k, v) -> append("disk${i + 1}.$k:$v\n") } }
                    networks.forEachIndexed { i, m -> m.forEach { (k, v) -> append("net${i + 1}.$k:$v\n") } }
                }.trim()
                val dir = "/data/local/tmp/als/app/qvm/${qvmMap["name"]}"
                val cmd = "mkdir -p \"$dir\"\necho '${content.replace("'", "'\\''")}' > \"$dir/${qvmMap["name"]}.cfg\"\nexit\n"
                val proc = Runtime.getRuntime().exec(su)
                proc.outputStream.use { it.write(cmd.toByteArray(Charsets.UTF_8)); it.flush() }
                proc.waitFor()
                onBack()
            } catch (_: Exception) {}
        }) { index ->
        val startCdrom = 3
        val startDisk = startCdrom + cdroms.size
        val startNetwork = startDisk + disks.size
        val startDisplay = startNetwork + networks.size
        val startAudio = startDisplay + 1
        val startAdd = startAudio + (if (qvmMap["audio"] == 1) 1 else 0)
        when (index) {
            in 0 until startCdrom -> when (index) {
                0 -> QvmArchive(qvmMap)
                ; 1 -> QvmProcessor(qvmMap)
                ; else -> QvmMemory(qvmMap)
            }
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