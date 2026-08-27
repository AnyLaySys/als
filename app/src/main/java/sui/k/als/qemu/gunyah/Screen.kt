package sui.k.als.qemu.gunyah

import androidx.compose.runtime.Composable
import sui.k.als.qemu.vm.QemuConfigScreen
import sui.k.als.qemu.vm.QemuEditorChange

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
    QemuConfigScreen(
        title = "QEMU Gunyah",
        started = started,
        store = QemuGunyahConfigStore,
        toArgs = { it.toQemuGunyahArgs() },
        applyChange = { config, change -> config.apply(change) },
        onCreate = onCreate,
        displayDeviceChoices = listOf("virtio-gpu-gl-pci", "off"),
        onDisplay = onDisplay,
        onConsole = onConsole,
        onStop = onStop,
        onBack = onBack,
        onKeyboardSettingsChange = onKeyboardSettingsChange
    )
}

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
    is QemuEditorChange.MemMiB -> copy(memMiB = change.value)
    is QemuEditorChange.Width -> copy(width = change.value)
    is QemuEditorChange.Height -> copy(height = change.value)
    is QemuEditorChange.Cdrom -> copy(
        cdrom = change.value,
        isoPaths = if (change.value && isoPaths.isEmpty()) listOf("") else isoPaths
    )
    is QemuEditorChange.Disk -> copy(
        disk = change.value,
        iothread = if (change.value) iothread else false,
        diskPaths = if (change.value && diskPaths.all(String::isBlank)) listOf("") else diskPaths
    )
    is QemuEditorChange.Iothread -> copy(iothread = change.value)
    is QemuEditorChange.Network -> copy(network = change.value)
    is QemuEditorChange.Tablet -> copy(tablet = change.value)
    is QemuEditorChange.Keyboard -> copy(keyboard = change.value)
    is QemuEditorChange.HideKeyboard -> copy(hideKeyboard = change.value)
    is QemuEditorChange.SoftKeyboard -> copy(softKeyboard = change.value)
    is QemuEditorChange.DisplayDevice -> copy(displayDevice = change.value)
    is QemuEditorChange.AudioOutput -> copy(audioOutput = change.value)
    is QemuEditorChange.AudioInput -> copy(audioInput = change.value)
    is QemuEditorChange.Serial -> copy(serial = change.value)
}
