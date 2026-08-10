package sui.k.als.qemu.gzvm

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sui.k.als.R
import sui.k.als.localFont
import sui.k.als.app.qemu.gzvm.QemuGzvmConfig
import sui.k.als.app.qemu.gzvm.toQemuGzvmDisplayDevice
import sui.k.als.ui.ALSButton
import sui.k.als.ui.ALSList
import sui.k.als.ui.Field

@Composable
fun QemuGzvmScreen(
    started: Boolean,
    onCreate: (QemuGzvmConfig) -> Unit,
    onDisplay: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(QemuGzvmConfigStore.load(context)) }
    val cdrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { config = config.copy(isoPath = context.qemuGzvmPath(it)) }
    }
    val disk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { config = config.copy(diskPath = context.qemuGzvmPath(it)) }
    }
    fun save() = QemuGzvmConfigStore.save(context, config)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black), Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                stringResource(R.string.qemu_gzvm),
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = localFont.current,
                fontWeight = FontWeight.Medium
            )
            Column {
                QemuGzvmText(stringResource(R.string.cfg_name), config.name, true) {
                    config = config.copy(name = it)
                }
                QemuGzvmNumber(stringResource(R.string.cpu_cores), config.cpuCores) {
                    config = config.copy(cpuCores = it.coerceAtLeast(1))
                }
                QemuGzvmNumber(
                    stringResource(R.string.memory),
                    config.memoryMb,
                    valueText = config.qemuMemoryArgument(),
                    parse = { it.trim().removeSuffix("M").removeSuffix("m").toIntOrNull() },
                    last = true
                ) { config = config.copy(memoryMb = it.coerceAtLeast(256)) }
            }
            Column {
                QemuGzvmToggle(stringResource(R.string.cdrom), config.cdrom, true) {
                    config = config.copy(cdrom = it)
                }
                QemuGzvmPath(stringResource(R.string.cdrom_path), config.isoPath) {
                    cdrom.launch(arrayOf("*/*"))
                }
                QemuGzvmPath(stringResource(R.string.disk_path), config.diskPath) {
                    disk.launch(arrayOf("*/*"))
                }
                QemuGzvmToggle(
                    stringResource(R.string.io_thread_optimization), config.iothread, last = true
                ) { config = config.copy(iothread = it) }
            }
            Column {
                QemuGzvmDisplayDevice(config, first = true) {
                    config = config.copy(displayDevice = it)
                }
                QemuGzvmNumber(stringResource(R.string.xres), config.width) {
                    config = config.copy(width = it.coerceAtLeast(320))
                }
                QemuGzvmNumber(
                    stringResource(R.string.yres), config.height, last = true
                ) { config = config.copy(height = it.coerceAtLeast(200)) }
            }
            Column {
                QemuGzvmToggle(
                    stringResource(R.string.virtio_tablet), config.tablet, true
                ) { config = config.copy(tablet = it) }
                QemuGzvmToggle(
                    stringResource(R.string.virtio_keyboard), config.keyboard, last = true
                ) { config = config.copy(keyboard = it) }
            }
            Column {
                QemuGzvmToggle(stringResource(R.string.network), config.network, true) {
                    config = config.copy(network = it)
                }
            }
            Column {
                QemuGzvmToggle(stringResource(R.string.audio), config.audio, true) {
                    config = config.copy(audio = it)
                }
                QemuGzvmToggle(
                    stringResource(R.string.serial_console), config.serial, last = true
                ) { config = config.copy(serial = it) }
            }
            QemuGzvmText(
                stringResource(R.string.extra_qemu_args),
                config.extraQemuArgs,
                first = true,
                last = true
            ) { config = config.copy(extraQemuArgs = it) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (started) {
                    ALSButton(R.drawable.preview, size = 36.dp, iconSize = 36.dp) { onDisplay() }
                    ALSButton(R.drawable.power, size = 36.dp, iconSize = 36.dp) { onStop() }
                }
                if (!started) {
                    ALSButton(R.drawable.arrow_forward, size = 36.dp, iconSize = 36.dp) {
                        save()
                        onCreate(config)
                    }
                }
            }
        }
    }
}

@Composable
private fun QemuGzvmText(
    label: String,
    value: String,
    first: Boolean = false,
    last: Boolean = false,
    onChange: (String) -> Unit
) {
    ALSList(label, value = value, first = first, last = last, onValueChange = onChange)
}

@Composable
private fun QemuGzvmPath(
    label: String,
    value: String,
    first: Boolean = false,
    last: Boolean = false,
    onClick: () -> Unit
) {
    ALSList(label, value = value, first = first, last = last, onClick = { onClick() })
}

@Composable
private fun QemuGzvmNumber(
    label: String,
    value: Int,
    valueText: String = value.toString(),
    parse: (String) -> Int? = { it.toIntOrNull() },
    first: Boolean = false,
    last: Boolean = false,
    onChange: (Int) -> Unit
) {
    ALSList(
        label,
        value = valueText,
        first = first,
        last = last,
        onValueChange = { parse(it)?.let(onChange) }
    )
}

@Composable
private fun QemuGzvmToggle(
    label: String,
    value: Boolean,
    first: Boolean = false,
    last: Boolean = false,
    onChange: (Boolean) -> Unit
) {
    ALSList(
        label,
        value = stringResource(if (value) R.string.on else R.string.off),
        checked = value,
        first = first,
        last = last,
        onClick = { onChange(!value) }
    )
}

@Composable
private fun QemuGzvmDisplayDevice(
    config: QemuGzvmConfig,
    first: Boolean = false,
    last: Boolean = false,
    onChange: (String) -> Unit
) {
    val options = listOf(
        "virtio-gpu" to checkNotNull(config.qemuDisplayDeviceArgument("virtio-gpu")),
        "ramfb" to checkNotNull(config.qemuDisplayDeviceArgument("ramfb")),
        "off" to "none"
    )
    Field(
        stringResource(R.string.display_device),
        options.first { it.first == config.displayDevice.toQemuGzvmDisplayDevice() }.second,
        options.map { it.second },
        first,
        last
    ) { selected ->
        onChange(options.first { it.second == selected }.first)
    }
}

private fun Context.qemuGzvmPath(uri: Uri): String {
    val doc = if (DocumentsContract.isDocumentUri(this, uri)) DocumentsContract.getDocumentId(uri) else null
    if (uri.authority == "com.android.externalstorage.documents" && doc != null) {
        val parts = doc.split(":", limit = 2)
        if (parts[0].equals("primary", true)) return Environment.getExternalStorageDirectory().path + "/" + parts.getOrElse(1) { "" }
        return "/storage/${parts[0]}/${parts.getOrElse(1) { "" }}"
    }
    if (uri.authority == "com.android.providers.downloads.documents" && doc != null && doc.startsWith("raw:")) return doc.removePrefix("raw:")
    return contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.path.orEmpty()
}
