package sui.k.als.qemu.gunyah

import android.content.*
import android.net.*
import android.os.*
import android.provider.*
import androidx.activity.compose.*
import androidx.activity.result.contract.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import sui.k.als.*
import sui.k.als.R
import sui.k.als.app.*
import sui.k.als.app.qemu.gunyah.QemuGunyahConfig
import sui.k.als.app.qemu.gunyah.toQemuGunyahDisplayDevice
import sui.k.als.ui.*

@Composable
fun QemuGunyahScreen(
    started: Boolean,
    onCreate: (QemuGunyahConfig) -> Unit,
    onDisplay: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(QemuGunyahConfigStore.load(context)) }
    val cdrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { config = config.copy(isoPath = context.qemuGunyahPath(it)) }
    }
    val disk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { config = config.copy(diskPath = context.qemuGunyahPath(it)) }
    }
    fun save() = QemuGunyahConfigStore.save(context, config)
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
                stringResource(R.string.qemu_gunyah),
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = localFont.current,
                fontWeight = FontWeight.Medium
            )
            Column {
                QemuGunyahText(stringResource(R.string.cfg_name), config.name, true) {
                    config = config.copy(name = it)
                }
                QemuGunyahNumber(stringResource(R.string.cpu_cores), config.cpuCores) {
                    config = config.copy(cpuCores = it.coerceAtLeast(1))
                }
                QemuGunyahNumber(
                    stringResource(R.string.memory),
                    config.memoryMb,
                    valueText = config.qemuMemoryArgument(),
                    parse = { it.trim().removeSuffix("M").removeSuffix("m").toIntOrNull() },
                    last = true
                ) { config = config.copy(memoryMb = it.coerceAtLeast(256)) }
            }
            Column {
                QemuGunyahToggle(stringResource(R.string.cdrom), config.cdrom, true) {
                    config = config.copy(cdrom = it)
                }
                QemuGunyahPath(stringResource(R.string.cdrom_path), config.isoPath) {
                    cdrom.launch(arrayOf("*/*"))
                }
                QemuGunyahPath(stringResource(R.string.disk_path), config.diskPath) {
                    disk.launch(arrayOf("*/*"))
                }
                QemuGunyahToggle(
                    stringResource(R.string.io_thread_optimization), config.iothread, last = true
                ) { config = config.copy(iothread = it) }
            }
            Column {
                QemuGunyahDisplayDevice(config, first = true) {
                    config = config.copy(displayDevice = it)
                }
                QemuGunyahNumber(stringResource(R.string.xres), config.width) {
                    config = config.copy(width = it.coerceAtLeast(320))
                }
                QemuGunyahNumber(
                    stringResource(R.string.yres), config.height, last = true
                ) { config = config.copy(height = it.coerceAtLeast(200)) }
            }
            Column {
                QemuGunyahToggle(
                    stringResource(R.string.virtio_tablet), config.tablet, true
                ) { config = config.copy(tablet = it) }
                QemuGunyahToggle(
                    stringResource(R.string.virtio_keyboard), config.keyboard, last = true
                ) { config = config.copy(keyboard = it) }
            }
            Column {
                QemuGunyahToggle(stringResource(R.string.network), config.network, true) {
                    config = config.copy(network = it)
                }
            }
            Column {
                QemuGunyahToggle(stringResource(R.string.audio), config.audio, true) {
                    config = config.copy(audio = it)
                }
                QemuGunyahToggle(
                    stringResource(R.string.serial_console), config.serial, last = true
                ) { config = config.copy(serial = it) }
            }
            QemuGunyahText(
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
                    ALSButton(R.drawable.preview, size = 30.dp) { onDisplay() }
                    ALSButton(R.drawable.power, size = 30.dp) { onStop() }
                }
                if (!started) {
                    ALSButton(R.drawable.arrow_forward, size = 30.dp) {
                        save()
                        onCreate(config)
                    }
                }
            }
        }
    }
}

@Composable
private fun QemuGunyahText(
    label: String,
    value: String,
    first: Boolean = false,
    last: Boolean = false,
    onChange: (String) -> Unit
) {
    ALSList(label, value = value, first = first, last = last, onValueChange = onChange)
}

@Composable
private fun QemuGunyahPath(
    label: String,
    value: String,
    first: Boolean = false,
    last: Boolean = false,
    onClick: () -> Unit
) {
    ALSList(label, value = value, first = first, last = last, onClick = { onClick() })
}

@Composable
private fun QemuGunyahNumber(
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
        onValueChange = { parse(it)?.let(onChange) })
}

@Composable
private fun QemuGunyahToggle(
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
        onClick = { onChange(!value) })
}

@Composable
private fun QemuGunyahDisplayDevice(
    config: QemuGunyahConfig,
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
        options.first { it.first == config.displayDevice.toQemuGunyahDisplayDevice() }.second,
        options.map { it.second },
        first,
        last
    ) { selected ->
        onChange(options.first { it.second == selected }.first)
    }
}

private fun Context.qemuGunyahPath(uri: Uri): String {
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
