package sui.k.als.app.qemu.kvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sui.k.als.qemu.QemuEditor
import sui.k.als.qemu.QemuEditorChange
import sui.k.als.qemu.QemuEditorState
import sui.k.als.qemu.QemuDeviceCommands

@Composable
fun QemuKvmScreen(
    started: Boolean,
    consoleAvailable: Boolean,
    onCreate: (QemuKvmConfig) -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(QemuKvmConfigStore.load(context)) }
    QemuEditor(
        title = "QEMU KVM",
        state = config.editorState,
        started = started,
        consoleAvailable = consoleAvailable,
        onChange = { config = config.apply(it) },
        deviceCommands = QemuDeviceCommands(
            cdrom = "",
            iothread = "",
            tablet = "-device qemu-xhci,id=xhci -device usb-tablet,bus=xhci.0",
            keyboard = "-device qemu-xhci,id=xhci -device usb-kbd,bus=xhci.0",
            network = "-netdev tap,id=net,ifname=tap0,script=no,downscript=no -device virtio-net-pci,netdev=net",
            audio = "-audiodev aaudio,id=aa -device virtio-snd-pci,audiodev=aa"
        ),
        onRun = {
            onCreate(config)
            scope.launch(Dispatchers.IO) { QemuKvmConfigStore.save(context, config) }
        },
        onDisplay = onDisplay,
        onConsole = onConsole,
        onStop = onStop,
        onBack = onBack
    )
}

private val QemuKvmConfig.editorState: QemuEditorState
    get() = QemuEditorState(
        name, null, diskPath, cpuCores, memoryMb, width, height, null, null,
        network, tablet, keyboard, displayDevice, audio, serial, extraQemuArgs
    )

private fun QemuKvmConfig.apply(change: QemuEditorChange) = when (change) {
    is QemuEditorChange.Name -> copy(name = change.value)
    is QemuEditorChange.DiskPath -> copy(diskPath = change.value)
    is QemuEditorChange.CpuCores -> copy(cpuCores = change.value)
    is QemuEditorChange.MemoryMb -> copy(memoryMb = change.value)
    is QemuEditorChange.Width -> copy(width = change.value)
    is QemuEditorChange.Height -> copy(height = change.value)
    is QemuEditorChange.Network -> copy(network = change.value)
    is QemuEditorChange.Tablet -> copy(tablet = change.value)
    is QemuEditorChange.Keyboard -> copy(keyboard = change.value)
    is QemuEditorChange.DisplayDevice -> copy(displayDevice = change.value)
    is QemuEditorChange.Audio -> copy(audio = change.value)
    is QemuEditorChange.Serial -> copy(serial = change.value)
    is QemuEditorChange.ExtraArgs -> copy(extraQemuArgs = change.value)
    is QemuEditorChange.IsoPath, is QemuEditorChange.Cdrom, is QemuEditorChange.Iothread -> this
}
