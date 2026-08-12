package sui.k.als.qemu.kvm

import sui.k.als.agl.AglLaunch
import sui.k.als.agl.AglNativeBackend
import sui.k.als.agl.AglPreparedLaunch
import sui.k.als.app.qemu.kvm.QemuKvmConfig
import sui.k.als.app.qemu.kvm.toQemuKvmDisplayDevice

const val qemuKvmDir = "/data/local/tmp/als/qemu-kvm"

fun QemuKvmConfig.qemuMemoryArgument(): String = "${memoryMb}M"

fun QemuKvmConfig.qemuDisplayDeviceArgument(
    device: String = displayDevice
): String? = when (device.toQemuKvmDisplayDevice()) {
    "virtio-gpu" -> "virtio-gpu-gl-pci"
    "ramfb" -> "ramfb"
    else -> null
}

fun QemuKvmConfig.toQemuKvmArgs(): Array<String> {
    val args = mutableListOf(
        "./qemu-system-aarch64",
        "-L", "./fw",
        "-name", "ubuntu-disk8g8-kvm",
        "-machine", "virt",
        "-accel", "kvm",
        "-cpu", "host",
        "-smp", cpuCores.toString(),
        "-m", qemuMemoryArgument(),
        "-bios", "/data/local/tmp/QEMU_EFI.fd"
    )
    val displayDeviceArgument = qemuDisplayDeviceArgument()
    displayDeviceArgument?.let { args += listOf("-device", it) }
    if (keyboard || tablet) {
        args += listOf("-device", "qemu-xhci,id=xhci")
    }
    if (keyboard) {
        args += listOf("-device", "usb-kbd,bus=xhci.0")
    }
    if (tablet) {
        args += listOf("-device", "usb-tablet,bus=xhci.0")
    }
    if (diskPath.isNotBlank()) {
        args += listOf(
            "-drive",
            "file=$diskPath,if=none,id=system,format=raw,cache=writeback,discard=unmap"
        )
        args += listOf("-device", "nvme,drive=system,serial=UBUNTU-DISK")
    }
    if (network) {
        args += listOf("-netdev", "tap,id=net,ifname=tap0,script=no,downscript=no")
        args += listOf("-device", "virtio-net-pci,netdev=net")
    }
    if (audio) {
        args += listOf("-audiodev", "aaudio,id=aa")
        args += listOf("-device", "virtio-snd-pci,audiodev=aa")
    } else {
        args += listOf("-audiodev", "driver=none,id=noaudio")
    }
    args += listOf("-display", if (displayDeviceArgument == null) "none" else "agl")
    if (serial) {
        args += listOf("-serial", "mon:stdio")
    }
    args += splitQemuArgs(extraQemuArgs)
    return args.toTypedArray()
}

internal fun QemuKvmConfig.toAglLaunch() = AglLaunch(
    width = width,
    height = height,
    workDir = qemuKvmDir,
    backend = AglNativeBackend.Kvm,
    prepare = {
        AglPreparedLaunch(
            args = toQemuKvmArgs(),
            preflight = { QemuKvmPreflight.run(this) }
        )
    }
)

private fun splitQemuArgs(value: String): List<String> {
    val result = ArrayList<String>()
    val current = StringBuilder()
    var quote = '\u0000'
    var escaped = false
    value.forEach { char ->
        when {
            escaped -> {
                current.append(char)
                escaped = false
            }
            char == '\\' && quote != '\'' -> escaped = true
            quote != '\u0000' && char == quote -> quote = '\u0000'
            quote == '\u0000' && (char == '\'' || char == '"') -> quote = char
            quote == '\u0000' && char.isWhitespace() -> {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current.clear()
                }
            }
            else -> current.append(char)
        }
    }
    if (escaped) {
        current.append('\\')
    }
    if (current.isNotEmpty()) {
        result += current.toString()
    }
    return result
}
