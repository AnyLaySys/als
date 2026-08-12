package sui.k.als.app.qemu.gzvm

import org.json.*

data class QemuGzvmConfig(
    val name: String = "Ubuntu 26.10 S2 GNOME",
    val isoPath: String = "",
    val diskPath: String = "",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    val memoryMb: Int = 4352,
    val width: Int = 1400,
    val height: Int = 636,
    val cdrom: Boolean = false,
    val iothread: Boolean = true,
    val network: Boolean = true,
    val tablet: Boolean = true,
    val keyboard: Boolean = true,
    val displayDevice: String = "virtio-gpu",
    val audio: Boolean = true,
    val serial: Boolean = true,
    val extraQemuArgs: String = ""
)

fun QemuGzvmConfig.toQemuGzvmJson(): String =
    JSONObject().put("schemaVersion", 1).put("name", name).put("isoPath", isoPath).put("diskPath", diskPath).put("cpuCores", cpuCores)
        .put("memoryMb", memoryMb).put("width", width).put("height", height)
        .put("cdrom", cdrom).put("iothread", iothread).put("network", network).put("tablet", tablet)
        .put("keyboard", keyboard).put("displayDevice", displayDevice).put("audio", audio)
        .put("serial", serial)
        .put("extraQemuArgs", extraQemuArgs).toString(2)

fun parseQemuGzvmConfigJson(text: String): QemuGzvmConfig {
    val base = QemuGzvmConfig()
    val json = JSONObject(text)
    if (json.optInt("schemaVersion") < 1) {
        return base
    }
    return QemuGzvmConfig(
        name = json.optString("name", base.name),
        isoPath = json.optString("isoPath", base.isoPath),
        diskPath = json.optString("diskPath", base.diskPath),
        cpuCores = json.optInt("cpuCores", base.cpuCores).coerceAtLeast(1),
        memoryMb = json.optInt("memoryMb", base.memoryMb).coerceAtLeast(256),
        width = json.optInt("width", base.width).coerceAtLeast(320),
        height = json.optInt("height", base.height).coerceAtLeast(200),
        cdrom = json.optBoolean("cdrom", base.cdrom),
        iothread = json.optBoolean("iothread", base.iothread),
        network = json.optBoolean("network", base.network),
        tablet = json.optBoolean("tablet", base.tablet),
        keyboard = json.optBoolean("keyboard", base.keyboard),
        displayDevice = json.optString("displayDevice", base.displayDevice)
            .toQemuGzvmDisplayDevice(),
        audio = json.optBoolean("audio", base.audio),
        serial = json.optBoolean("serial", base.serial),
        extraQemuArgs = json.optString("extraQemuArgs", base.extraQemuArgs)
    )
}

fun String.toQemuGzvmDisplayDevice(): String = when (this) {
    "virtio-gpu", "ramfb", "off" -> this
    else -> "virtio-gpu"
}
