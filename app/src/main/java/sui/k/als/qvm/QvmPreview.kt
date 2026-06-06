package sui.k.als.qvm

import androidx.compose.foundation.text.selection.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import sui.k.als.*

data class QvmCfg(
    val smp: String = qvmHostProcessorCount(),
    val mem: String = "6G",
    val swiotlb: String = "64M",
    val prealloc: Boolean = false,
    val preallocSize: String = "6G",
    val forcePrealloc: Boolean = false,
    val lockMemory: Boolean = false,
    val disk: List<StorageDevice> = emptyList(),
    val cdrom: List<StorageDevice> = emptyList(),
    val network: List<NetworkConfig> = emptyList(),
    val resolution: Pair<Int, Int>? = 1280 to 720,
    val vncPort: String = ":0",
    val audioEnabled: Boolean = false,
    val usbEnabled: Boolean = false
)

data class StorageDevice(val path: String, val index: Int? = null, val cache: String = "unsafe")
data class NetworkConfig(
    val backend: String = "user",
    val protocol: String = "tcp",
    val ports: String = "2222-:22",
    val device: String = "virtio-net-pci"
)

val qvmNameRegex = Regex("[A-Za-z0-9._-]+")

object QvmCmd {
    fun build(qvmCfg: QvmCfg): String {
        val cmdArgs = mutableListOf<String>()
        fun MutableList<String>.args(vararg elements: String) {
            elements.forEach { add(it) }
        }
        with(cmdArgs) {
            add("LD_LIBRARY_PATH=${shellQuote("${qvmDir}/libs")}")
            add("${qvmDir}/qemu-system-aarch64")
            args("-M", "virt,confidential-guest-support=prot0", "-accel", "gunyah", "-cpu", "host")
            args("-smp", qvmCfg.smp, "-m", qvmCfg.mem)
            args("-object", "arm-confidential-guest,id=prot0,swiotlb-size=${qvmCfg.swiotlb}")
            if (qvmCfg.prealloc) {
                val mb = if (qvmCfg.preallocSize.endsWith(
                        "G", true
                    )
                ) "${
                    qvmCfg.preallocSize.removeSuffix("G").toInt() * 1024
                }M" else qvmCfg.preallocSize
                args("-object", "memory-backend-ram,id=mem,size=$mb,prealloc=on")
                if (qvmCfg.forcePrealloc) add("-mem-prealloc")
                if (qvmCfg.lockMemory) args("-overcommit", "mem-lock=on")
            }
            args("-bios", "${qvmDir}/QEMU_EFI.fd", "-L", "${qvmDir}/pc-bios")
            qvmCfg.cdrom.forEachIndexed { i, cd ->
                if (cd.path.isNotEmpty()) {
                    args("-drive", "file=${cd.path},if=none,id=dr_cd$i,format=raw,media=cdrom")
                    args("-device", "virtio-blk-pci,drive=dr_cd$i,bootindex=${cd.index ?: (i + 1)}")
                }
            }
            qvmCfg.disk.forEachIndexed { i, d ->
                if (d.path.isNotEmpty()) {
                    args("-drive", "file=${d.path},if=none,id=dr_d$i,cache=${d.cache}")
                    args("-device", "virtio-blk-pci,drive=dr_d$i,bootindex=${d.index ?: (i + 2)}")
                }
            }
            qvmCfg.network.forEachIndexed { i, n ->
                val netdev =
                    if (n.backend == "user") "${n.backend},id=net$i,hostfwd=${n.protocol}::${n.ports}" else "${n.backend},id=net$i"
                args("-netdev", netdev)
                args("-device", "${n.device},netdev=net$i")
            }
            val gpu = qvmCfg.resolution?.let { "virtio-gpu-pci,xres=${it.first},yres=${it.second}" }
                ?: "virtio-gpu-pci"
            args("-device", gpu, "-vnc", qvmCfg.vncPort)
            if (qvmCfg.audioEnabled) args(
                "-audiodev", "aaudio,id=snd0", "-device", "virtio-sound-pci,audiodev=snd0"
            )
            if (qvmCfg.usbEnabled) {
                args("-device", "qemu-xhci,id=usb,bus=pcie.0,addr=0x4")
                args("-device", "usb-tablet,bus=usb.0,port=1")
                args("-device", "usb-kbd,bus=usb.0,port=2")
            }
            args("-serial", "mon:stdio")
        }
        return cmdArgs.joinToString(" ") { arg ->
            if (arg.startsWith("LD_LIBRARY_PATH=")) arg else shellQuote(arg)
        }
    }
}

@Composable
fun QvmPreview(qvmCfg: QvmCfg) {
    val cmd = remember(qvmCfg) { QvmCmd.build(qvmCfg) }
    SelectionContainer {
        Text(
            text = cmd,
            fontSize = 9.sp,
            color = Color.Gray,
            fontFamily = localFont.current,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
