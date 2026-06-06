package sui.k.als.qvm

import org.json.*
import sui.k.als.*
import java.io.*

const val qvmDir = "$alsDir/app/qvm"

fun qvmHostProcessorCount() = Runtime.getRuntime().availableProcessors().coerceAtLeast(1).toString()

data class QvmConfig(
    val name: String, var isRunning: Boolean = false, val raw: JSONObject? = null
) {
    fun getCommand(): String {
        val json = raw ?: JSONObject()
        val cfg = QvmCfg(
            smp = json.optString("smp", qvmHostProcessorCount()),
            mem = json.optString("mem", "6G"),
            swiotlb = json.optString("swiotlb", "64M"),
            prealloc = json.optInt("prealloc", 0) == 1,
            lockMemory = json.optInt("lock_memory", 0) == 1,
            vncPort = json.optString("vnc_port", ":0"),
            audioEnabled = json.optInt("audio", 0) == 1,
            resolution = try {
                val width = json.optString("xres").toIntOrNull() ?: 1280
                val height = json.optString("yres").toIntOrNull() ?: 720
                width to height
            } catch (_: Exception) {
                1280 to 720
            },
            cdrom = mutableListOf<StorageDevice>().apply {
                val array = json.optJSONArray("cdrom") ?: return@apply
                for (deviceIndex in 0 until array.length()) {
                    val item = array.getJSONObject(deviceIndex)
                    add(
                        StorageDevice(
                            item.optString("path"), item.optString("index").toIntOrNull()
                        )
                    )
                }
            },
            disk = mutableListOf<StorageDevice>().apply {
                val array = json.optJSONArray("disk") ?: return@apply
                for (deviceIndex in 0 until array.length()) {
                    val item = array.getJSONObject(deviceIndex)
                    add(
                        StorageDevice(
                            item.optString("path"),
                            item.optString("index").toIntOrNull(),
                            item.optString("cache", "unsafe")
                        )
                    )
                }
            },
            network = mutableListOf<NetworkConfig>().apply {
                val array = json.optJSONArray("net") ?: return@apply
                for (deviceIndex in 0 until array.length()) {
                    val item = array.getJSONObject(deviceIndex)
                    add(
                        NetworkConfig(
                            item.optString("backend", "user"),
                            item.optString("protocol", "tcp"),
                            item.optString("ports", "2222-:22"),
                            item.optString("device", "virtio-net-pci")
                        )
                    )
                }
            })
        return QvmCmd.build(cfg)
    }
}

fun parseFlatConfigFile(file: File): JSONObject = JSONObject().apply {
    val maps = mapOf(
        "cdrom" to mutableMapOf<Int, JSONObject>(),
        "disk" to mutableMapOf<Int, JSONObject>(),
        "net" to mutableMapOf<Int, JSONObject>()
    )
    file.readLines().forEach { line ->
        val parts = line.split(":", limit = 2).takeIf { it.size == 2 } ?: return@forEach
        val key = parts[0].trim()
        val value = parts[1].trim()
        val prefix = maps.keys.find { key.startsWith(it) }
        if (prefix != null) {
            val dotIndex = key.indexOf('.')
            if (dotIndex != -1) {
                val itemIndex = key.substring(prefix.length, dotIndex).toIntOrNull() ?: 0
                maps.getValue(prefix).getOrPut(itemIndex) { JSONObject() }
                    .put(key.substring(dotIndex + 1), value)
            }
        } else put(key, value)
    }
    maps.forEach { (key, value) ->
        put(
            key, JSONArray().apply { value.keys.sorted().forEach { put(value[it]) } })
    }
}