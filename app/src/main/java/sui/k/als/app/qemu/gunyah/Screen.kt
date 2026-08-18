package sui.k.als.qemu.gunyah

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sui.k.als.app.qemu.gunyah.QemuGunyahConfig
import sui.k.als.app.qemu.gunyah.toQemuGunyahArgs
import sui.k.als.app.qemu.QemuDeviceCommands
import sui.k.als.app.qemu.QemuEditor
import sui.k.als.app.qemu.QemuEditorChange
import sui.k.als.app.qemu.QemuEditorState

@Composable
fun QemuGunyahScreen(
    started: Boolean,
    onCreate: (QemuGunyahConfig) -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onKeyboardSettingsChange: (Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(QemuGunyahConfigStore.load(context)) }
    QemuEditor(
        title = "QEMU Gunyah",
        state = config.editorState,
        started = started,
        onChange = {
            val updated = config.apply(it)
            config = updated
            if (it is QemuEditorChange.HideKeyboard || it is QemuEditorChange.SoftKeyboard) {
                onKeyboardSettingsChange(updated.hideKeyboard, updated.softKeyboard)
                scope.launch(Dispatchers.IO) { QemuGunyahConfigStore.save(context, updated) }
            }
        },
        deviceCommands = QemuDeviceCommands(
            iothread = "-object iothread,id=io0",
            tablet = "-device virtio-tablet-pci",
            keyboard = "-device virtio-keyboard-pci",
            network = "-netdev tap,id=usernet,ifname=tap0,script=no,downscript=no -device virtio-net-pci,netdev=usernet",
            audio = "-audiodev aaudio,id=aa -device virtio-snd-pci,audiodev=aa"
        ),
        onSave = { scope.launch(Dispatchers.IO) { QemuGunyahConfigStore.save(context, config) } },
        onRun = {
            onCreate(config)
            scope.launch(Dispatchers.IO) { QemuGunyahConfigStore.save(context, config) }
        },
        onDisplay = onDisplay,
        onConsole = onConsole,
        onStop = onStop,
        onBack = onBack
    )
}

private val QemuGunyahConfig.editorState: QemuEditorState
    get() = QemuEditorState(
        name, uefiPath, efiVirtioRomPath, isoPaths, diskPaths, cpuCores, memoryMb, width, height, cdrom, iothread,
        network, tablet, keyboard, hideKeyboard, softKeyboard, displayDevice, audio, serial,
        toQemuGunyahArgs().joinToString(" ")
    )

private fun QemuGunyahConfig.apply(change: QemuEditorChange) = when (change) {
    is QemuEditorChange.Name -> copy(name = change.value)
    is QemuEditorChange.UefiPath -> copy(uefiPath = change.value)
    is QemuEditorChange.EfiVirtioRomPath -> copy(efiVirtioRomPath = change.value)
    is QemuEditorChange.IsoPath -> copy(isoPaths = isoPaths.mapIndexed { index, path -> if (index == change.index) change.value else path })
    is QemuEditorChange.DiskPath -> copy(diskPaths = diskPaths.mapIndexed { index, path -> if (index == change.index) change.value else path })
    QemuEditorChange.AddIsoPath -> copy(isoPaths = isoPaths + "")
    is QemuEditorChange.RemoveIsoPath -> copy(isoPaths = isoPaths.toMutableList().also { it.removeAt(change.index) })
    QemuEditorChange.AddDiskPath -> copy(diskPaths = diskPaths + "")
    is QemuEditorChange.RemoveDiskPath -> copy(diskPaths = diskPaths.toMutableList().also { it.removeAt(change.index) })
    is QemuEditorChange.CpuCores -> copy(cpuCores = change.value)
    is QemuEditorChange.MemoryMb -> copy(memoryMb = change.value)
    is QemuEditorChange.Width -> copy(width = change.value)
    is QemuEditorChange.Height -> copy(height = change.value)
    is QemuEditorChange.Cdrom -> copy(
        cdrom = change.value,
        isoPaths = if (change.value && isoPaths.isEmpty()) listOf("") else isoPaths
    )
    is QemuEditorChange.Iothread -> copy(iothread = change.value)
    is QemuEditorChange.Network -> copy(network = change.value)
    is QemuEditorChange.Tablet -> copy(tablet = change.value)
    is QemuEditorChange.Keyboard -> copy(keyboard = change.value)
    is QemuEditorChange.HideKeyboard -> copy(hideKeyboard = change.value)
    is QemuEditorChange.SoftKeyboard -> copy(softKeyboard = change.value)
    is QemuEditorChange.DisplayDevice -> copy(displayDevice = change.value)
    is QemuEditorChange.Audio -> copy(audio = change.value)
    is QemuEditorChange.Serial -> copy(serial = change.value)
}
