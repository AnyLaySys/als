package sui.k.als.app.qemu.gunyah

import org.json.JSONObject
import sui.k.als.app.qemu.QemuResolution
import sui.k.als.app.qemu.readPaths
import sui.k.als.app.qemu.toJsonArray

data class QemuGunyahConfig(
    val name: String = "Ubuntu 26.10 S2 GNOME",
    val uefiPath: String = "$qemuGunyahDir/fw/edk2-aarch64-gunyah.fd",
    val efiVirtioRomPath: String = "$qemuGunyahDir/fw/efi-virtio.rom",
    val isoPaths: List<String> = emptyList(),
    val diskPaths: List<String> = listOf(""),
    val cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    val memoryMb: Int = 4352,
    val width: Int = QemuResolution.width,
    val height: Int = QemuResolution.height,
    val cdrom: Boolean = false,
    val iothread: Boolean = true,
    val network: Boolean = true,
    val tablet: Boolean = true,
    val keyboard: Boolean = true,
    val hideKeyboard: Boolean = false,
    val softKeyboard: Boolean = false,
    val displayDevice: String = "virtio-gpu",
    val audio: Boolean = true,
    val serial: Boolean = true,
)

fun QemuGunyahConfig.toQemuGunyahJson(): String =
    JSONObject().put("schemaVersion", 6).put("name", name).put("uefiPath", uefiPath)
        .put("efiVirtioRomPath", efiVirtioRomPath)
        .put("isoPaths", isoPaths.toJsonArray()).put("diskPaths", diskPaths.toJsonArray()).put("cpuCores", cpuCores)
        .put("memoryMb", memoryMb).put("width", width).put("height", height)
        .put("cdrom", cdrom).put("iothread", iothread).put("network", network).put("tablet", tablet)
        .put("keyboard", keyboard).put("hideKeyboard", hideKeyboard).put("softKeyboard", softKeyboard)
        .put("displayDevice", displayDevice).put("audio", audio)
        .put("serial", serial)
        .toString(2)

fun parseQemuGunyahConfigJson(text: String): QemuGunyahConfig {
    val base = QemuGunyahConfig()
    val json = JSONObject(text)
    val version = json.optInt("schemaVersion")
    if (version < 1) {
        return base
    }
    return QemuGunyahConfig(
        name = json.optString("name", base.name),
        uefiPath = json.optString("uefiPath", base.uefiPath),
        efiVirtioRomPath = json.optString("efiVirtioRomPath", base.efiVirtioRomPath),
        isoPaths = json.readPaths("isoPaths", "isoPath", base.isoPaths),
        diskPaths = json.readPaths("diskPaths", "diskPath", base.diskPaths),
        cpuCores = json.optInt("cpuCores", base.cpuCores).coerceAtLeast(1),
        memoryMb = json.optInt("memoryMb", base.memoryMb).coerceAtLeast(256),
        width = (if (version < 2) base.width else json.optInt("width", base.width)).coerceAtLeast(320),
        height = (if (version < 2) base.height else json.optInt("height", base.height)).coerceAtLeast(200),
        cdrom = json.optBoolean("cdrom", base.cdrom),
        iothread = json.optBoolean("iothread", base.iothread),
        network = json.optBoolean("network", base.network),
        tablet = json.optBoolean("tablet", base.tablet),
        keyboard = json.optBoolean("keyboard", base.keyboard),
        hideKeyboard = json.optBoolean("hideKeyboard", base.hideKeyboard),
        softKeyboard = json.optBoolean("softKeyboard", base.softKeyboard),
        displayDevice = json.optString("displayDevice", base.displayDevice)
            .toQemuGunyahDisplayDevice(),
        audio = json.optBoolean("audio", base.audio),
        serial = json.optBoolean("serial", base.serial),
    )
}

fun String.toQemuGunyahDisplayDevice(): String = when (this) {
    "virtio-gpu", "ramfb", "off" -> this
    else -> "virtio-gpu"
}
