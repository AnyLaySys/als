package sui.k.als.qvm.gunyah

import org.json.JSONObject

data class QvmGunyahConfig(
    val name: String = "Ubuntu 26.04",
    val isoPath: String = "",
    val cpuCores: Int = 4,
    val memoryMb: Int = 4096,
    val width: Int = 2376,
    val height: Int = 1080,
    val sshPort: Int = 2222,
    val cdrom: Boolean = false,
    val iothread: Boolean = true,
    val network: Boolean = true,
    val tablet: Boolean = true,
    val keyboard: Boolean = true,
    val displayOutput: Boolean = true,
    val displayDevice: String = "virtio-gpu",
    val audio: Boolean = true,
    val serial: Boolean = true,
    val extraQemuArgs: String = ""
)

fun QvmGunyahConfig.toQvmGunyahJson(): String = JSONObject()
    .put("name", name)
    .put("isoPath", isoPath)
    .put("cpuCores", cpuCores)
    .put("memoryMb", memoryMb)
    .put("width", width)
    .put("height", height)
    .put("sshPort", sshPort)
    .put("cdrom", cdrom)
    .put("iothread", iothread)
    .put("network", network)
    .put("tablet", tablet)
    .put("keyboard", keyboard)
    .put("displayOutput", displayOutput)
    .put("displayDevice", displayDevice)
    .put("audio", audio)
    .put("serial", serial)
    .put("extraQemuArgs", extraQemuArgs)
    .toString(2)

fun parseQvmGunyahConfigJson(text: String): QvmGunyahConfig {
    val base = QvmGunyahConfig()
    val json = JSONObject(text)
    return QvmGunyahConfig(
        name = json.optString("name", base.name),
        isoPath = json.optString("isoPath", base.isoPath),
        cpuCores = json.optInt("cpuCores", base.cpuCores).coerceAtLeast(1),
        memoryMb = json.optInt("memoryMb", base.memoryMb).coerceAtLeast(256),
        width = json.optInt("width", base.width).coerceAtLeast(320),
        height = json.optInt("height", base.height).coerceAtLeast(200),
        sshPort = json.optInt("sshPort", base.sshPort).coerceIn(1, 65535),
        cdrom = json.optBoolean("cdrom", base.cdrom),
        iothread = json.optBoolean("iothread", base.iothread),
        network = json.optBoolean("network", base.network),
        tablet = json.optBoolean("tablet", base.tablet),
        keyboard = json.optBoolean("keyboard", base.keyboard),
        displayOutput = json.optBoolean("displayOutput", base.displayOutput),
        displayDevice = json.optString("displayDevice", base.displayDevice).toQvmGunyahDisplayDevice(),
        audio = json.optBoolean("audio", base.audio),
        serial = json.optBoolean("serial", base.serial),
        extraQemuArgs = json.optString("extraQemuArgs", base.extraQemuArgs)
    )
}

fun String.toQvmGunyahDisplayDevice(): String = when (this) {
    "virtio-gpu", "ramfb", "off" -> this
    else -> "virtio-gpu"
}
