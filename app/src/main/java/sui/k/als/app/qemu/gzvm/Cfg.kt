package sui.k.als.app.qemu.gzvm

import sui.k.als.app.qemu.vm.QemuEditorState
import org.json.JSONObject
import sui.k.als.app.qemu.vm.QemuResolution
import sui.k.als.app.qemu.vm.readPaths
import sui.k.als.app.qemu.vm.toJsonArray

data class QemuGzvmConfig(
    override val name: String = "Ubuntu",
    override val uefiPath: String = "$qemuGzvmDir/edk2-aarch64-gzvm.fd",
    override val efiVirtioRomPath: String = "$qemuGzvmDir/efi-virtio.rom",
    override val isoPaths: List<String> = emptyList(),
    override val diskPaths: List<String> = listOf(""),
    override val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    override val memMiB: Int = 4096,
    override val width: Int = QemuResolution.width,
    override val height: Int = QemuResolution.height,
    override val cdrom: Boolean = false,
    override val disk: Boolean = true,
    override val iothread: Boolean = true,
    override val network: Boolean = true,
    override val tablet: Boolean = true,
    override val keyboard: Boolean = true,
    override val hideKeyboard: Boolean = false,
    override val softKeyboard: Boolean = false,
    override val displayDevice: String = "virtio-gpu-gl-pci",
    override val audioOutput: Boolean = true,
    override val audioInput: Boolean = false,
    override val serial: Boolean = true,
) : QemuEditorState

fun QemuGzvmConfig.toQemuGzvmJson(): String =
    JSONObject().put("schemaVersion", 7).put("name", name).put("uefiPath", uefiPath)
        .put("efiVirtioRomPath", efiVirtioRomPath)
        .put("isoPaths", isoPaths.toJsonArray()).put("diskPaths", diskPaths.toJsonArray()).put("cpuCores", cpuCores)
        .put("memMiB", memMiB).put("width", width).put("height", height)
        .put("cdrom", cdrom).put("disk", disk).put("iothread", iothread).put("network", network).put("tablet", tablet)
        .put("keyboard", keyboard).put("hideKeyboard", hideKeyboard).put("softKeyboard", softKeyboard)
        .put("displayDevice", displayDevice).put("audioOutput", audioOutput).put("audioInput", audioInput)
        .put("serial", serial)
        .toString(2)

fun parseQemuGzvmConfigJson(text: String): QemuGzvmConfig {
    val base = QemuGzvmConfig()
    val json = JSONObject(text)
    val version = json.optInt("schemaVersion")
    if (version < 1) {
        return base
    }
    return QemuGzvmConfig(
        name = json.optString("name", base.name),
        uefiPath = json.optString("uefiPath", base.uefiPath),
        efiVirtioRomPath = json.optString("efiVirtioRomPath", base.efiVirtioRomPath),
        isoPaths = json.readPaths("isoPaths", "isoPath", base.isoPaths),
        diskPaths = json.readPaths("diskPaths", "diskPath", base.diskPaths),
        cpuCores = json.optInt("cpuCores", base.cpuCores),
        memMiB = json.optInt("memMiB", base.memMiB),
        width = if (version < 2) base.width else json.optInt("width", base.width),
        height = if (version < 2) base.height else json.optInt("height", base.height),
        cdrom = json.optBoolean("cdrom", base.cdrom),
        disk = json.optBoolean("disk", base.disk),
        iothread = json.optBoolean("iothread", base.iothread),
        network = json.optBoolean("network", base.network),
        tablet = json.optBoolean("tablet", base.tablet),
        keyboard = json.optBoolean("keyboard", base.keyboard),
        hideKeyboard = json.optBoolean("hideKeyboard", base.hideKeyboard),
        softKeyboard = json.optBoolean("softKeyboard", base.softKeyboard),
        displayDevice = json.optString("displayDevice", base.displayDevice)
            .toQemuGzvmDisplayDevice(),
        audioOutput = json.optBoolean("audioOutput", json.optBoolean("audio", base.audioOutput)),
        audioInput = json.optBoolean("audioInput", json.optBoolean("audio", base.audioInput)),
        serial = json.optBoolean("serial", base.serial),
    )
}

fun String.toQemuGzvmDisplayDevice(): String = when (this) {
    "virtio-gpu-gl-pci", "ramfb", "off" -> this
    else -> "virtio-gpu-gl-pci"
}
