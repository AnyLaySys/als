package sui.k.als.app.qemu.kvm

import org.json.JSONObject

data class QemuKvmConfig(
    val name: String = "Ubuntu 26.10 S2 GNOME",
    val diskPath: String = "",
    val cpuCores: Int = 8,
    val memoryMb: Int = 8192,
    val width: Int = 1400,
    val height: Int = 636,
    val network: Boolean = true,
    val tablet: Boolean = true,
    val keyboard: Boolean = true,
    val displayDevice: String = "virtio-gpu",
    val audio: Boolean = false,
    val serial: Boolean = true,
    val extraQemuArgs: String = ""
)

fun QemuKvmConfig.toQemuKvmJson(): String =
    JSONObject().put("schemaVersion", 1).put("name", name).put("diskPath", diskPath)
        .put("cpuCores", cpuCores).put("memoryMb", memoryMb).put("width", width)
        .put("height", height).put("network", network).put("tablet", tablet)
        .put("keyboard", keyboard).put("displayDevice", displayDevice).put("audio", audio)
        .put("serial", serial).put("extraQemuArgs", extraQemuArgs).toString(2)

fun parseQemuKvmConfigJson(text: String): QemuKvmConfig {
    val base = QemuKvmConfig()
    val json = JSONObject(text)
    if (json.optInt("schemaVersion") < 1) {
        return base
    }
    return QemuKvmConfig(
        name = json.optString("name", base.name),
        diskPath = json.optString("diskPath", base.diskPath),
        cpuCores = json.optInt("cpuCores", base.cpuCores).coerceAtLeast(1),
        memoryMb = json.optInt("memoryMb", base.memoryMb).coerceAtLeast(256),
        width = json.optInt("width", base.width).coerceAtLeast(320),
        height = json.optInt("height", base.height).coerceAtLeast(200),
        network = json.optBoolean("network", base.network),
        tablet = json.optBoolean("tablet", base.tablet),
        keyboard = json.optBoolean("keyboard", base.keyboard),
        displayDevice = json.optString("displayDevice", base.displayDevice)
            .toQemuKvmDisplayDevice(),
        audio = json.optBoolean("audio", base.audio),
        serial = json.optBoolean("serial", base.serial),
        extraQemuArgs = json.optString("extraQemuArgs", base.extraQemuArgs)
    )
}

fun String.toQemuKvmDisplayDevice(): String = when (this) {
    "virtio-gpu", "ramfb", "off" -> this
    else -> "virtio-gpu"
}
