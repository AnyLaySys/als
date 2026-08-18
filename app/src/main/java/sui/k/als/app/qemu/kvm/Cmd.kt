package sui.k.als.qemu.kvm

import sui.k.als.agl.AglLaunch
import sui.k.als.agl.AglNativeBackend
import sui.k.als.app.qemu.kvm.QemuKvmConfig
import sui.k.als.app.qemu.kvm.toQemuKvmDisplayDevice
import sui.k.als.app.qemu.kvm.toQemuKvmJson

const val qemuKvmDir = "/data/local/tmp/als/qemu-kvm"

fun QemuKvmConfig.qemuMemoryArgument(): String = memoryMb.toString() + "M"

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
    diskPaths.filter(String::isNotBlank).forEachIndexed { index, path ->
        val id = "system$index"
        args += listOf(
            "-drive",
            "file=$path,if=none,id=$id,format=raw,cache=writeback,discard=unmap"
        )
        args += listOf("-device", "nvme,drive=$id,serial=UBUNTU-DISK$index")
    }
    if (cdrom) isoPaths.filter(String::isNotBlank).forEachIndexed { index, path ->
        val id = "cd$index"
        args += listOf(
            "-drive",
            "file=$path,if=none,id=$id,media=cdrom,readonly=on,format=raw"
        )
        args += listOf("-device", "ide-cd,drive=$id")
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
    return args.toTypedArray()
}

internal fun QemuKvmConfig.toAglLaunch() = AglLaunch(
    width = width,
    height = height,
    workDir = qemuKvmDir,
    backend = AglNativeBackend.Kvm,
    configuration = toQemuKvmJson(),
    hideKeyboard = hideKeyboard,
    softKeyboard = softKeyboard
)
