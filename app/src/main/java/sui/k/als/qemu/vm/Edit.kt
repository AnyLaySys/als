package sui.k.als.qemu.vm

import android.*
import android.content.*
import androidx.activity.compose.*
import androidx.activity.result.contract.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import sui.k.als.R
import sui.k.als.ui.*

internal interface QemuEditorState {
    val name: String
    val uefiPath: String
    val efiVirtioRomPath: String
    val isoPaths: List<String>
    val diskPaths: List<String>
    val cpuCores: Int
    val memMiB: Int
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
    val audioOutput: Boolean
    val audioInput: Boolean
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
    data class MemMiB(val value: Int) : QemuEditorChange
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
    data class AudioOutput(val value: Boolean) : QemuEditorChange
    data class AudioInput(val value: Boolean) : QemuEditorChange
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
    onBack: () -> Unit,
    displayDeviceChoices: List<String>
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
                ALSSection(stringResource(R.string.basic_config)) {
                    ALSTextField(stringResource(R.string.config_name), state.name) {
                        onChange(
                            QemuEditorChange.Name(it)
                        )
                    }
                    ALSPathField(stringResource(R.string.uefi_path), state.uefiPath) {
                        onChange(QemuEditorChange.UefiPath(it))
                    }
                    ALSPathField(
                        stringResource(R.string.efi_virtio_rom_path), state.efiVirtioRomPath
                    ) {
                        onChange(QemuEditorChange.EfiVirtioRomPath(it))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ALSTextField(
                            stringResource(R.string.cpu_cores),
                            state.cpuCores.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value ->
                            value.toIntOrNull()?.let { onChange(QemuEditorChange.CpuCores(it)) }
                        }
                        ALSTextField(
                            stringResource(R.string.memory_mib),
                            state.memMiB.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value ->
                            value.toIntOrNull()?.let { onChange(QemuEditorChange.MemMiB(it)) }
                        }
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.storage)) {
                    ALSSwitchRow(stringResource(R.string.cdrom), checked = state.cdrom) {
                        onChange(QemuEditorChange.Cdrom(it))
                    }
                    if (state.cdrom) {
                        state.isoPaths.forEachIndexed { index, path ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ALSPathField(
                                    "${stringResource(R.string.iso_image)} ${index + 1}",
                                    path,
                                    Modifier.weight(1f)
                                ) { onChange(QemuEditorChange.IsoPath(index, it)) }
                                if (state.isoPaths.size > 1) {
                                    ALSIconAction(
                                        R.drawable.delete, stringResource(R.string.remove_cdrom)
                                    ) {
                                        onChange(QemuEditorChange.RemoveIsoPath(index))
                                    }
                                }
                            }
                        }
                        ALSIconAction(R.drawable.add, stringResource(R.string.add_cdrom)) {
                            onChange(QemuEditorChange.AddIsoPath)
                        }
                    }
                    ALSSwitchRow(stringResource(R.string.disk), checked = state.disk) {
                        onChange(QemuEditorChange.Disk(it))
                    }
                    if (state.disk) {
                        state.diskPaths.forEachIndexed { index, path ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ALSPathField(
                                    "${stringResource(R.string.disk)} ${index + 1}",
                                    path,
                                    Modifier.weight(1f)
                                ) { onChange(QemuEditorChange.DiskPath(index, it)) }
                                if (state.diskPaths.size > 1) {
                                    ALSIconAction(
                                        R.drawable.delete, stringResource(R.string.remove_disk)
                                    ) {
                                        onChange(QemuEditorChange.RemoveDiskPath(index))
                                    }
                                }
                            }
                        }
                        ALSIconAction(R.drawable.add, stringResource(R.string.add_disk)) {
                            onChange(QemuEditorChange.AddDiskPath)
                        }
                    }
                    ALSSwitchRow(
                        stringResource(R.string.iothread), deviceCommands.iothread, state.iothread
                    ) {
                        onChange(QemuEditorChange.Iothread(it))
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.display_section)) {
                    ALSChoiceField(
                        stringResource(R.string.display_device),
                        state.displayDevice,
                        displayDeviceChoices
                    ) { onChange(QemuEditorChange.DisplayDevice(it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ALSTextField(
                            stringResource(R.string.width),
                            state.width.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value ->
                            value.toIntOrNull()?.let { onChange(QemuEditorChange.Width(it)) }
                        }
                        ALSTextField(
                            stringResource(R.string.height),
                            state.height.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value ->
                            value.toIntOrNull()?.let { onChange(QemuEditorChange.Height(it)) }
                        }
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.keyboard_settings)) {
                    ALSSwitchRow(
                        stringResource(R.string.mouse), deviceCommands.tablet, state.tablet
                    ) {
                        onChange(QemuEditorChange.Tablet(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.keyboard), deviceCommands.keyboard, state.keyboard
                    ) {
                        onChange(QemuEditorChange.Keyboard(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.soft_keyboard),
                        stringResource(R.string.soft_keyboard_summary),
                        state.softKeyboard
                    ) {
                        onChange(QemuEditorChange.SoftKeyboard(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.hide_keyboard),
                        stringResource(R.string.hide_builtin_keyboard),
                        state.hideKeyboard
                    ) {
                        onChange(QemuEditorChange.HideKeyboard(it))
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.audio)) {
                    ALSSwitchRow(
                        stringResource(R.string.audio_input), "virtio-snd-pci", state.audioInput
                    ) {
                        onChange(QemuEditorChange.AudioInput(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.audio_output),
                        deviceCommands.audio,
                        state.audioOutput
                    ) {
                        onChange(QemuEditorChange.AudioOutput(it))
                    }
                }
            }
            item {
                ALSSection(stringResource(R.string.devices)) {
                    ALSSwitchRow(
                        stringResource(R.string.network), deviceCommands.network, state.network
                    ) {
                        onChange(QemuEditorChange.Network(it))
                    }
                    ALSSwitchRow(
                        stringResource(R.string.serial), deviceCommands.serial, state.serial
                    ) {
                        onChange(QemuEditorChange.Serial(it))
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
                            onClick = onDisplay,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(painterResource(R.drawable.preview), null, Modifier.size(24.dp))
                            Spacer(Modifier.size(9.dp))
                            Text(stringResource(R.string.display), maxLines = 1)
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
                            Text(stringResource(R.string.terminal), maxLines = 1)
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
                            Text(stringResource(R.string.power), maxLines = 1)
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
                            Text(stringResource(R.string.save), maxLines = 1)
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
                            Text(stringResource(R.string.start), maxLines = 1)
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
                            Text(stringResource(R.string.terminal), maxLines = 1)
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
) {
    companion object {
        fun default() = QemuDeviceCommands(
            iothread = "-object iothread,id=io0",
            tablet = "-device virtio-tablet-pci",
            keyboard = "-device virtio-keyboard-pci",
            network = "-netdev tap,id=net,ifname=tap0,script=no,downscript=no -device virtio-net-pci,netdev=net",
            audio = "-audiodev aaudio,id=aa -device virtio-snd-pci,audiodev=aa"
        )
    }
}

internal interface QemuConfigStore<T> {
    fun load(context: Context): T
    fun save(context: Context, config: T)
}

@Composable
internal fun <T : QemuEditorState> QemuConfigScreen(
    title: String,
    started: Boolean,
    store: QemuConfigStore<T>,
    toArgs: (T) -> Array<String>,
    applyChange: (T, QemuEditorChange) -> T,
    onCreate: (T) -> Unit,
    displayDeviceChoices: List<String>,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onKeyboardSettingsChange: (Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(store.load(context)) }
    val deviceCommands = remember { QemuDeviceCommands.default() }
    val qemuArgs = remember(config) { toArgs(config).joinToString(" ") }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && config.audioInput) {
            config = applyChange(config, QemuEditorChange.AudioInput(false))
            scope.launch(Dispatchers.IO) { runCatching { store.save(context, config) } }
        }
    }
    QemuEditor(
        title = title,
        state = config,
        started = started,
        onChange = {
            val updated = applyChange(config, it)
            config = updated
            if (it is QemuEditorChange.HideKeyboard || it is QemuEditorChange.SoftKeyboard) {
                onKeyboardSettingsChange(updated.hideKeyboard, updated.softKeyboard)
                scope.launch(Dispatchers.IO) {
                    runCatching { store.save(context, updated) }
                }
            }
            if (it is QemuEditorChange.AudioInput) {
                if (it.value) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        deviceCommands = deviceCommands,
        qemuArguments = qemuArgs,
        onSave = { scope.launch(Dispatchers.IO) { store.save(context, config) } },
        onRun = {
            onCreate(config)
            scope.launch(Dispatchers.IO) { store.save(context, config) }
        },
        onDisplay = onDisplay,
        onConsole = onConsole,
        onStop = onStop,
        onBack = onBack,
        displayDeviceChoices = displayDeviceChoices
    )
}