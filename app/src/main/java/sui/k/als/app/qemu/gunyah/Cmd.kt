package sui.k.als.app.qemu.gunyah

import sui.k.als.agl.AglLaunch
import sui.k.als.agl.AglNativeBackend
import java.io.File

const val qemuGunyahDir = "/data/local/tmp/als/qemu-gunyah"

fun QemuGunyahConfig.qemuMemoryArgument(): String = memoryMb.toString() + "M"

fun QemuGunyahConfig.qemuDisplayDeviceArgument(
    device: String = displayDevice
): String? = when (device.toQemuGunyahDisplayDevice()) {
    "virtio-gpu" -> "virtio-gpu-gl-pci,xres=$width,yres=$height,venus=on,blob=on,hostmem=256M"
    else -> null
}

fun QemuGunyahConfig.toQemuGunyahArgs(): Array<String> {
    val queueCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val args = mutableListOf(
        "./qemu-system-aarch64",
        "-M", "virt",
        "-accel", "gunyah",
        "-cpu", "host",
        "-smp", cpuCores.toString(),
        "-m", qemuMemoryArgument()
    )
    uefiPath.takeIf(String::isNotBlank)?.let { args += listOf("-bios", it) }
    efiVirtioRomPath.takeIf(String::isNotBlank)?.let { rom ->
        File(rom).parent?.let { args += listOf("-L", it) }
    }
    if (disk) {
        if (iothread) {
            args += listOf("-object", "iothread,id=io0")
        }
        diskPaths.filter(String::isNotBlank).forEachIndexed { index, path ->
            val id = "dr$index"
            args += listOf(
                "-drive",
                "file=$path,format=raw,if=none,id=$id,media=disk,cache=unsafe,aio=io_uring,discard=unmap"
            )
            args += listOf(
                "-device",
                "virtio-blk-pci,drive=$id,num-queues=$queueCount${if (iothread) ",iothread=io0" else ""},disable-legacy=on,disable-modern=off${if (index == 0) ",bootindex=1" else ""}"
            )
        }
    }
    if (cdrom) {
        if (iothread) {
            args += listOf("-object", "iothread,id=io1")
        }
        isoPaths.filter(String::isNotBlank).forEachIndexed { index, path ->
            val id = "cd$index"
            args += listOf(
                "-drive",
                "file=$path,format=raw,if=none,id=$id,media=cdrom,readonly=on,cache=unsafe,aio=threads"
            )
            args += listOf(
                "-device",
                "virtio-blk-pci,drive=$id,num-queues=1${if (iothread) ",iothread=io1" else ""},disable-legacy=on,disable-modern=off"
            )
        }
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
    return args.toTypedArray()
}

internal fun QemuGunyahConfig.toAglLaunch() = AglLaunch(
    width = width,
    height = height,
    workDir = qemuGunyahDir,
    backend = AglNativeBackend.Gunyah,
    configuration = toQemuGunyahJson(),
    hideKeyboard = hideKeyboard,
    softKeyboard = softKeyboard
)
