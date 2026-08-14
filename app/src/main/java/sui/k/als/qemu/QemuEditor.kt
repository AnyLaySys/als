package sui.k.als.qemu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sui.k.als.R
import sui.k.als.ui.ALSActionButton
import sui.k.als.ui.ALSChoiceField
import sui.k.als.ui.ALSPathField
import sui.k.als.ui.ALSScaffold
import sui.k.als.ui.ALSSection
import sui.k.als.ui.ALSSwitchRow
import sui.k.als.ui.ALSTextField

internal data class QemuEditorState(
    val name: String,
    val isoPath: String?,
    val diskPath: String,
    val cpuCores: Int,
    val memoryMb: Int,
    val width: Int,
    val height: Int,
    val cdrom: Boolean?,
    val iothread: Boolean?,
    val network: Boolean,
    val tablet: Boolean,
    val keyboard: Boolean,
    val displayDevice: String,
    val audio: Boolean,
    val serial: Boolean,
    val extraQemuArgs: String
)

internal sealed interface QemuEditorChange {
    data class Name(val value: String) : QemuEditorChange
    data class IsoPath(val value: String) : QemuEditorChange
    data class DiskPath(val value: String) : QemuEditorChange
    data class CpuCores(val value: Int) : QemuEditorChange
    data class MemoryMb(val value: Int) : QemuEditorChange
    data class Width(val value: Int) : QemuEditorChange
    data class Height(val value: Int) : QemuEditorChange
    data class Cdrom(val value: Boolean) : QemuEditorChange
    data class Iothread(val value: Boolean) : QemuEditorChange
    data class Network(val value: Boolean) : QemuEditorChange
    data class Tablet(val value: Boolean) : QemuEditorChange
    data class Keyboard(val value: Boolean) : QemuEditorChange
    data class DisplayDevice(val value: String) : QemuEditorChange
    data class Audio(val value: Boolean) : QemuEditorChange
    data class Serial(val value: Boolean) : QemuEditorChange
    data class ExtraArgs(val value: String) : QemuEditorChange
}

@Composable
internal fun QemuEditor(
    title: String,
    state: QemuEditorState,
    started: Boolean,
    consoleAvailable: Boolean,
    onChange: (QemuEditorChange) -> Unit,
    deviceCommands: QemuDeviceCommands,
    onRun: () -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
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
                ALSSection("基本配置") {
                    ALSTextField("配置名称", state.name) { onChange(QemuEditorChange.Name(it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ALSTextField(
                            "CPU 核心",
                            state.cpuCores.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value -> value.toIntOrNull()?.let { onChange(QemuEditorChange.CpuCores(it.coerceAtLeast(1))) } }
                        ALSTextField(
                            "内存 MiB",
                            state.memoryMb.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value -> value.toIntOrNull()?.let { onChange(QemuEditorChange.MemoryMb(it.coerceAtLeast(256))) } }
                    }
                }
            }
            item {
                ALSSection("存储") {
                    state.cdrom?.let { enabled ->
                        ALSSwitchRow("启用光盘", deviceCommands.cdrom, enabled) {
                            onChange(QemuEditorChange.Cdrom(it))
                        }
                    }
                    state.isoPath?.let { path ->
                        ALSPathField("光盘镜像", path) { onChange(QemuEditorChange.IsoPath(it)) }
                    }
                    ALSPathField("虚拟磁盘", state.diskPath) { onChange(QemuEditorChange.DiskPath(it)) }
                    state.iothread?.let { enabled ->
                        ALSSwitchRow("I/O 线程", deviceCommands.iothread, enabled) {
                            onChange(QemuEditorChange.Iothread(it))
                        }
                    }
                }
            }
            item {
                ALSSection("显示") {
                    ALSChoiceField(
                        "显示设备",
                        state.displayDevice,
                        listOf("virtio-gpu", "ramfb", "off")
                    ) { onChange(QemuEditorChange.DisplayDevice(it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ALSTextField(
                            "宽度",
                            state.width.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value -> value.toIntOrNull()?.let { onChange(QemuEditorChange.Width(it.coerceAtLeast(320))) } }
                        ALSTextField(
                            "高度",
                            state.height.toString(),
                            Modifier.weight(1f),
                            numeric = true
                        ) { value -> value.toIntOrNull()?.let { onChange(QemuEditorChange.Height(it.coerceAtLeast(200))) } }
                    }
                }
            }
            item {
                ALSSection("设备") {
                    ALSSwitchRow("指针设备", deviceCommands.tablet, state.tablet) {
                        onChange(QemuEditorChange.Tablet(it))
                    }
                    ALSSwitchRow("键盘", deviceCommands.keyboard, state.keyboard) {
                        onChange(QemuEditorChange.Keyboard(it))
                    }
                    ALSSwitchRow("网络", deviceCommands.network, state.network) {
                        onChange(QemuEditorChange.Network(it))
                    }
                    ALSSwitchRow("音频", deviceCommands.audio, state.audio) {
                        onChange(QemuEditorChange.Audio(it))
                    }
                    ALSSwitchRow("串口控制台", deviceCommands.serial, state.serial) {
                        onChange(QemuEditorChange.Serial(it))
                    }
                }
            }
            item {
                ALSSection("高级") {
                    ALSTextField(
                        label = "附加 QEMU 参数",
                        value = state.extraQemuArgs,
                        singleLine = false,
                        supporting = "参数会追加到自动生成的启动命令末尾",
                        onValueChange = { onChange(QemuEditorChange.ExtraArgs(it)) }
                    )
                }
            }
            item {
                if (started) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onDisplay,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp)
                        ) {
                            Icon(painterResource(R.drawable.preview), null, Modifier.size(24.dp))
                            Text("显示", Modifier.padding(start = 6.dp))
                        }
                        FilledTonalButton(
                            onClick = onConsole,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp)
                        ) {
                            Icon(painterResource(R.drawable.terminal), null, Modifier.size(24.dp))
                            Text(stringResource(R.string.qemu_console), Modifier.padding(start = 6.dp))
                        }
                        ALSActionButton(
                            "停止",
                            painterResource(R.drawable.power),
                            Modifier.weight(1f),
                            destructive = true,
                            onClick = onStop
                        )
                    }
                } else if (consoleAvailable) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ALSActionButton(
                            "保存并启动",
                            painterResource(R.drawable.arrow_forward),
                            Modifier.weight(1f),
                            onClick = onRun
                        )
                        FilledTonalButton(
                            onClick = onConsole,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp)
                        ) {
                            Icon(painterResource(R.drawable.terminal), null, Modifier.size(24.dp))
                            Text(stringResource(R.string.qemu_console), Modifier.padding(start = 6.dp))
                        }
                    }
                } else {
                    ALSActionButton(
                        "保存并启动",
                        painterResource(R.drawable.arrow_forward),
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 9.dp),
                        onClick = onRun
                    )
                }
            }
        }
    }
}

internal data class QemuDeviceCommands(
    val cdrom: String,
    val iothread: String,
    val tablet: String,
    val keyboard: String,
    val network: String,
    val audio: String,
    val serial: String = "-serial mon:stdio"
)
