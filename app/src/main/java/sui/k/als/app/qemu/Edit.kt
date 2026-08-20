package sui.k.als.app.qemu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import sui.k.als.R
import sui.k.als.ui.*

internal interface QemuEditorState {
    val name: String
    val uefiPath: String
    val efiVirtioRomPath: String
    val isoPaths: List<String>
    val diskPaths: List<String>
    val cpuCores: Int
    val memoryMb: Int
    val width: Int
    val height: Int
    val cdrom: Boolean
    val disk: Boolean
    val iothread: Boolean
    val network: Boolean
    val tablet: Boolean
    val keyboard: Boolean
    val hideKeyboard: Boolean
    val softKeyboard: Boolean
    val displayDevice: String
    val audio: Boolean
    val serial: Boolean
}

internal sealed interface QemuEditorChange {
    data class Name(val value: String) : QemuEditorChange
    data class UefiPath(val value: String) : QemuEditorChange
    data class EfiVirtioRomPath(val value: String) : QemuEditorChange
    data class IsoPath(val index: Int, val value: String) : QemuEditorChange
    data class DiskPath(val index: Int, val value: String) : QemuEditorChange
    data object AddIsoPath : QemuEditorChange
    data class RemoveIsoPath(val index: Int) : QemuEditorChange
    data object AddDiskPath : QemuEditorChange
    data class RemoveDiskPath(val index: Int) : QemuEditorChange
    data class CpuCores(val value: Int) : QemuEditorChange
    data class MemoryMb(val value: Int) : QemuEditorChange
    data class Width(val value: Int) : QemuEditorChange
    data class Height(val value: Int) : QemuEditorChange
    data class Cdrom(val value: Boolean) : QemuEditorChange
    data class Disk(val value: Boolean) : QemuEditorChange
    data class Iothread(val value: Boolean) : QemuEditorChange
    data class Network(val value: Boolean) : QemuEditorChange
    data class Tablet(val value: Boolean) : QemuEditorChange
    data class Keyboard(val value: Boolean) : QemuEditorChange
    data class HideKeyboard(val value: Boolean) : QemuEditorChange
    data class SoftKeyboard(val value: Boolean) : QemuEditorChange
    data class DisplayDevice(val value: String) : QemuEditorChange
    data class Audio(val value: Boolean) : QemuEditorChange
    data class Serial(val value: Boolean) : QemuEditorChange
}

@Composable
internal fun QemuEditor(
    title: String,
    state: QemuEditorState,
    started: Boolean,
    onChange: (QemuEditorChange) -> Unit,
    deviceCommands: QemuDeviceCommands,
    onSave: () -> Unit,
    onRun: () -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    qemuArguments: String,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    ALSScaffold(title = title, onBack = onBack) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                ALSSection(stringResource(R.string.qemu_basic_config)) {
                    ALSTextField(stringResource(R.string.qemu_config_name), state.name) {
                        onChange(
                            QemuEditorChange.Name(it)
                        )
                    }
                    ALSPathField(stringResource(R.string.qemu_uefi_path), state.uefiPath) {
                        onChange(QemuEditorChange.UefiPath(it))
                    }
                    ALSPathField(
                        stringResource(R.string.qemu_efi_virtio_rom_path),
                        state.efiVirtioRomPath
                    ) {
                        onChange(QemuEditorChange.EfiVirtioRomPath(it))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ALSTextField(
                            stringResource(R.string.qemu_cpu_cores),
                            state.cpuCores.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value ->
                            value.toIntOrNull()
                                ?.let { onChange(QemuEditorChange.CpuCores(it)) }
                        }
                        ALSTextField(
                            stringResource(R.string.qemu_memory_mib),
                            state.memoryMb.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value ->
                            value.toIntOrNull()
                                ?.let { onChange(QemuEditorChange.MemoryMb(it)) }
                        }
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.qemu_storage)) {
                    ALSSwitchRow(stringResource(R.string.qemu_cdrom), checked = state.cdrom) {
                        onChange(QemuEditorChange.Cdrom(it))
                    }
                    if (state.cdrom) {
                        state.isoPaths.forEachIndexed { index, path ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ALSPathField(
                                    "${stringResource(R.string.qemu_iso_image)} ${index + 1}",
                                    path,
                                    Modifier.weight(1f)
                                ) { onChange(QemuEditorChange.IsoPath(index, it)) }
                                if (state.isoPaths.size > 1) {
                                    ALSIconAction(
                                        R.drawable.delete,
                                        stringResource(R.string.qemu_remove_cdrom)
                                    ) {
                                        onChange(QemuEditorChange.RemoveIsoPath(index))
                                    }
                                }
                            }
                        }
                        ALSIconAction(R.drawable.add, stringResource(R.string.qemu_add_cdrom)) {
                            onChange(QemuEditorChange.AddIsoPath)
                        }
                    }
                    ALSSwitchRow(stringResource(R.string.qemu_disk), checked = state.disk) {
                        onChange(QemuEditorChange.Disk(it))
                    }
                    if (state.disk) {
                        state.diskPaths.forEachIndexed { index, path ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ALSPathField(
                                    "${stringResource(R.string.qemu_disk)} ${index + 1}",
                                    path,
                                    Modifier.weight(1f)
                                ) { onChange(QemuEditorChange.DiskPath(index, it)) }
                                if (state.diskPaths.size > 1) {
                                    ALSIconAction(
                                        R.drawable.delete, stringResource(R.string.qemu_remove_disk)
                                    ) {
                                        onChange(QemuEditorChange.RemoveDiskPath(index))
                                    }
                                }
                            }
                        }
                        ALSIconAction(R.drawable.add, stringResource(R.string.qemu_add_disk)) {
                            onChange(QemuEditorChange.AddDiskPath)
                        }
                    }
                    ALSSwitchRow(
                        stringResource(R.string.qemu_iothread), deviceCommands.iothread, state.iothread
                    ) {
                        onChange(QemuEditorChange.Iothread(it))
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.qemu_display_section)) {
                    ALSChoiceField(
                        stringResource(R.string.qemu_display_device),
                        state.displayDevice,
                        listOf("virtio-gpu", "ramfb", "off")
                    ) { onChange(QemuEditorChange.DisplayDevice(it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ALSTextField(
                            stringResource(R.string.qemu_width),
                            state.width.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value ->
                            value.toIntOrNull()
                                ?.let { onChange(QemuEditorChange.Width(it)) }
                        }
                        ALSTextField(
                            stringResource(R.string.qemu_height),
                            state.height.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value ->
                            value.toIntOrNull()
                                ?.let { onChange(QemuEditorChange.Height(it)) }
                        }
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.qemu_devices)) {
                    ALSSwitchRow(
                        stringResource(R.string.qemu_mouse), deviceCommands.tablet, state.tablet
                    ) {
                        onChange(QemuEditorChange.Tablet(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.qemu_keyboard),
                        deviceCommands.keyboard,
                        state.keyboard
                    ) {
                        onChange(QemuEditorChange.Keyboard(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.qemu_network), deviceCommands.network, state.network
                    ) {
                        onChange(QemuEditorChange.Network(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.qemu_audio), deviceCommands.audio, state.audio
                    ) {
                        onChange(QemuEditorChange.Audio(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.qemu_serial), deviceCommands.serial, state.serial
                    ) {
                        onChange(QemuEditorChange.Serial(it))
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.qemu_keyboard_settings)) {
                    ALSSwitchRow(
                        stringResource(R.string.qemu_soft_keyboard),
                        stringResource(R.string.qemu_soft_keyboard_summary),
                        state.softKeyboard
                    ) {
                        onChange(QemuEditorChange.SoftKeyboard(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.qemu_hide_keyboard),
                        stringResource(R.string.qemu_hide_keyboard_summary),
                        state.hideKeyboard
                    ) {
                        onChange(QemuEditorChange.HideKeyboard(it))
                    }
                }
            }
            item {
                if (started) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onConsole,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(painterResource(R.drawable.terminal), null, Modifier.size(24.dp))
                            Spacer(Modifier.size(9.dp))
                            Text(stringResource(R.string.qemu_terminal), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onDisplay,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(painterResource(R.drawable.preview), null, Modifier.size(24.dp))
                            Spacer(Modifier.size(9.dp))
                            Text(stringResource(R.string.qemu_display), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onStop,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(painterResource(R.drawable.power), null, Modifier.size(24.dp))
                            Spacer(Modifier.size(9.dp))
                            Text(stringResource(R.string.qemu_power), maxLines = 1)
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onSave,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(painterResource(R.drawable.save), null, Modifier.size(24.dp))
                            Spacer(Modifier.size(9.dp))
                            Text(stringResource(R.string.qemu_save), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onRun,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_forward),
                                null,
                                Modifier.size(24.dp)
                            )
                            Spacer(Modifier.size(9.dp))
                            Text(stringResource(R.string.qemu_start), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onConsole,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(painterResource(R.drawable.terminal), null, Modifier.size(24.dp))
                            Spacer(Modifier.size(9.dp))
                            Text(stringResource(R.string.qemu_terminal), maxLines = 1)
                        }
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.qemu_arguments)) {
                    Text(qemuArguments, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

internal data class QemuDeviceCommands(
    val iothread: String,
    val tablet: String,
    val keyboard: String,
    val network: String,
    val audio: String,
    val serial: String = "-serial mon:stdio"
)
