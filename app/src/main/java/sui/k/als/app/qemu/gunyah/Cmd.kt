package sui.k.als.qemu.gunyah

import sui.k.als.app.qemu.gunyah.QemuGunyahConfig
import sui.k.als.app.qemu.gunyah.toQemuGunyahDisplayDevice
import sui.k.als.agl.AglLaunch
import sui.k.als.agl.AglNativeBackend
import sui.k.als.agl.AglPreparedLaunch

const val qemuGunyahDir = "/data/local/tmp/als/qemu-gunyah"

fun QemuGunyahConfig.qemuMemoryArgument(): String = "${memoryMb}M"

fun QemuGunyahConfig.qemuDisplayDeviceArgument(
    device: String = displayDevice
): String? = when (device.toQemuGunyahDisplayDevice()) {
    "virtio-gpu" -> "virtio-gpu-gl-pci,xres=$width,yres=$height"
    "ramfb" -> "ramfb"
    else -> null
}

fun QemuGunyahConfig.toQemuGunyahArgs(): Array<String> {
    val queueCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val args = mutableListOf(
        "./qemu-system-aarch64",
        "-L", "./fw",
        "-bios", "edk2-aarch64-gunyah.fd",
        "-M", "virt,confidential-guest-support=prot0",
        "-accel", "gunyah",
        "-cpu", "host",
        "-smp", cpuCores.toString(),
        "-m", qemuMemoryArgument(),
        "-object", "arm-confidential-guest,id=prot0,swiotlb-size=256M"
    )
    if (iothread) {
        args += listOf("-object", "iothread,id=io0")
    }
    if (diskPath.isNotBlank()) {
        args += listOf(
            "-drive",
            "file=$diskPath,format=raw,if=none,id=dr0,media=disk,cache=unsafe,aio=io_uring,discard=unmap"
        )
        args += listOf(
            "-device",
            "virtio-blk-pci,drive=dr0,num-queues=$queueCount${if (iothread) ",iothread=io0" else ""},disable-legacy=on,disable-modern=off,bootindex=1"
        )
    }
    if (cdrom && isoPath.isNotBlank()) {
        args += listOf(
            "-drive",
            "file=$isoPath,format=raw,if=none,id=cd0,media=cdrom,readonly=on,cache=unsafe,aio=threads"
        )
        args += listOf(
            "-device",
            "virtio-blk-pci,drive=cd0,num-queues=1${if (iothread) ",iothread=io0" else ""},disable-legacy=on,disable-modern=off"
        )
    }
    if (network) {
        args += listOf("-netdev", "tap,id=usernet,ifname=tap0,script=no,downscript=no")
        args += listOf("-device", "virtio-net-pci,netdev=usernet")
    }
    if (tablet) {
        args += listOf("-device", "virtio-tablet-pci")
    }
    if (keyboard) {
        args += listOf("-device", "virtio-keyboard-pci")
    }
    val displayDeviceArgument = qemuDisplayDeviceArgument()
    displayDeviceArgument?.let { args += listOf("-device", it) }
    if (audio) {
        args += listOf("-audiodev", "aaudio,id=aa")
        args += listOf("-device", "virtio-snd-pci,audiodev=aa")
    }
    args += listOf(
        "-display", if (displayDeviceArgument == null) "none" else "agl"
    )
    if (serial) {
        args += listOf("-serial", "mon:stdio")
    }
    args += splitQemuArgs(extraQemuArgs)
    return args.toTypedArray()
}

internal fun QemuGunyahConfig.toAglLaunch() = AglLaunch(
    width = width,
    height = height,
    workDir = qemuGunyahDir,
    backend = AglNativeBackend.Gunyah,
    prepare = {
        AglPreparedLaunch(
            args = toQemuGunyahArgs(),
            preflight = { QemuGunyahPreflight.run(this) }
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
