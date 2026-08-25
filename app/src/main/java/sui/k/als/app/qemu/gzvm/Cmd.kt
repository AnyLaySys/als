package sui.k.als.qemu.gzvm

import sui.k.als.vm.VMLaunch
import sui.k.als.vm.VMBackend
import sui.k.als.app.qemu.gzvm.QemuGzvmConfig
import sui.k.als.app.qemu.gzvm.toQemuGzvmDisplayDevice
import sui.k.als.app.qemu.gzvm.toQemuGzvmJson
import java.io.File

const val qemuGzvmDir = "/data/local/tmp/als/qemu-gzvm"

fun QemuGzvmConfig.qemuMemArgument(): String = memMiB.toString() + "M"

fun QemuGzvmConfig.qemuDisplayDeviceArgument(
    device: String = displayDevice
): String? = when (device.toQemuGzvmDisplayDevice()) {
    "virtio-gpu-gl-pci" -> "virtio-gpu-gl-pci,xres=$width,yres=$height"
    "ramfb" -> "ramfb"
    else -> null
}

fun QemuGzvmConfig.toQemuGzvmArgs(): Array<String> {
    val queueCount = Runtime.getRuntime().availableProcessors()
    val args = mutableListOf(
        "./qemu-system-aarch64",
        "-M", "virt",
        "-accel", "gzvm",
        "-cpu", "host",
        "-smp", cpuCores.toString(),
        "-m", qemuMemArgument()
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
        args += listOf("-netdev", "tap,id=net,ifname=tap0,script=no,downscript=no")
        args += listOf("-device", "virtio-net-pci,netdev=net")
    }
    if (tablet) {
        args += listOf("-device", "virtio-tablet-pci")
    }
    if (keyboard) {
        args += listOf("-device", "virtio-keyboard-pci")
    }
    val displayDeviceArgument = qemuDisplayDeviceArgument()
    displayDeviceArgument?.let { args += listOf("-device", it) }
    if (audioOutput) {
        args += listOf("-audiodev", "aaudio,id=aa,in.fixed-settings=on,in.frequency=48000,in.channels=1,in.format=s16")
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

internal fun QemuGzvmConfig.toVMLaunch() = VMLaunch(
    width = width,
    height = height,
    workDir = qemuGzvmDir,
    backend = VMBackend.Gzvm,
    configuration = toQemuGzvmJson(),
    hideKeyboard = hideKeyboard,
    softKeyboard = softKeyboard
)
