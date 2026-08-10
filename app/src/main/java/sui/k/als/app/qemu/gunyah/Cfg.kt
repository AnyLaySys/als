package sui.k.als.app.qemu.gunyah

import org.json.*

data class QemuGunyahConfig(
    val name: String = "Ubuntu 26.10 S2 GNOME",
    val isoPath: String = "",
    val diskPath: String = "/data/local/tmp/als/qemu-gunyah/ubuntu-26.10-s2-gnome-arm64.img",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    val memoryMb: Int = 4352,
    val width: Int = 2376,
    val height: Int = 1080,
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

fun QemuGunyahConfig.toQemuGunyahJson(): String =
    JSONObject().put("schemaVersion", 1).put("name", name).put("isoPath", isoPath).put("diskPath", diskPath).put("cpuCores", cpuCores)
        .put("memoryMb", memoryMb).put("width", width).put("height", height)
        .put("cdrom", cdrom).put("iothread", iothread).put("network", network).put("tablet", tablet)
        .put("keyboard", keyboard).put("displayDevice", displayDevice).put("audio", audio)
        .put("serial", serial)
        .put("extraQemuArgs", extraQemuArgs).toString(2)

fun parseQemuGunyahConfigJson(text: String): QemuGunyahConfig {
    val base = QemuGunyahConfig()
    val json = JSONObject(text)
    if (json.optInt("schemaVersion") < 1) {
        return base
    }
    return QemuGunyahConfig(
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
            .toQemuGunyahDisplayDevice(),
        audio = json.optBoolean("audio", base.audio),
        serial = json.optBoolean("serial", base.serial),
        extraQemuArgs = json.optString("extraQemuArgs", base.extraQemuArgs)
    )
}

fun String.toQemuGunyahDisplayDevice(): String = when (this) {
    "virtio-gpu", "ramfb", "off" -> this
    else -> "virtio-gpu"
}
