package sui.k.als.qvm.gunyah

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
import sui.k.als.app.qvm.gunyah.QvmGunyahConfig
import sui.k.als.app.qvm.gunyah.toQvmGunyahDisplayDevice
import sui.k.als.ui.*

@Composable
fun QvmGunyahScreen(
    started: Boolean, onCreate: (QvmGunyahConfig) -> Unit, onEnter: () -> Unit, onX11: () -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(QvmGunyahConfigStore.load(context)) }
    val cdrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { config = config.copy(isoPath = context.qvmGunyahPath(it)) }
    }
    val disk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { config = config.copy(diskPath = context.qvmGunyahPath(it)) }
    }
    fun save() = QvmGunyahConfigStore.save(context, config)
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
                stringResource(R.string.qvm_gunyah),
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = localFont.current,
                fontWeight = FontWeight.Medium
            )
            Column {
                QvmGunyahText(stringResource(R.string.cfg_name), config.name, true) {
                    config = config.copy(name = it)
                }
                QvmGunyahNumber(stringResource(R.string.cpu_cores), config.cpuCores) {
                    config = config.copy(cpuCores = it.coerceAtLeast(1))
                }
                QvmGunyahNumber(
                    stringResource(R.string.memory_size), config.memoryMb, last = true
                ) { config = config.copy(memoryMb = it.coerceAtLeast(256)) }
            }
            Column {
                QvmGunyahToggle(stringResource(R.string.cdrom), config.cdrom, true) {
                    config = config.copy(cdrom = it)
                }
                QvmGunyahPath(stringResource(R.string.cdrom_path), config.isoPath) {
                    cdrom.launch(arrayOf("*/*"))
                }
                QvmGunyahPath(stringResource(R.string.disk_path), config.diskPath) {
                    disk.launch(arrayOf("*/*"))
                }
                QvmGunyahToggle(
                    stringResource(R.string.io_thread_optimization), config.iothread, last = true
                ) { config = config.copy(iothread = it) }
            }
            Column {
                QvmGunyahToggle(
                    stringResource(R.string.sdl_display), config.displayOutput, true
                ) { config = config.copy(displayOutput = it) }
                QvmGunyahDisplayDevice(config.displayDevice) {
                    config = config.copy(displayDevice = it)
                }
                QvmGunyahNumber(stringResource(R.string.xres), config.width) {
                    config = config.copy(width = it.coerceAtLeast(320))
                }
                QvmGunyahNumber(
                    stringResource(R.string.yres), config.height, last = true
                ) { config = config.copy(height = it.coerceAtLeast(200)) }
            }
            Column {
                QvmGunyahToggle(
                    stringResource(R.string.virtio_tablet), config.tablet, true
                ) { config = config.copy(tablet = it) }
                QvmGunyahToggle(
                    stringResource(R.string.virtio_keyboard), config.keyboard, last = true
                ) { config = config.copy(keyboard = it) }
            }
            Column {
                QvmGunyahToggle(stringResource(R.string.network), config.network, true) {
                    config = config.copy(network = it)
                }
                QvmGunyahNumber(
                    stringResource(R.string.ssh_port), config.sshPort, last = true
                ) { config = config.copy(sshPort = it.coerceIn(1, 65535)) }
            }
            Column {
                QvmGunyahToggle(stringResource(R.string.audio), config.audio, true) {
                    config = config.copy(audio = it)
                }
                QvmGunyahToggle(
                    stringResource(R.string.serial_console), config.serial, last = true
                ) { config = config.copy(serial = it) }
            }
            QvmGunyahText(
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
                    ALSButton(R.drawable.terminal, size = 30.dp) { onEnter() }
                    ALSButton(R.drawable.preview, size = 30.dp) { onX11() }
                }
                if (!started) {
                    ALSButton(R.drawable.arrow_forward, size = 30.dp) {
                        save()
                        X11.prepare(context)
                        onCreate(config)
                    }
                }
            }
        }
    }
}

@Composable
private fun QvmGunyahText(
    label: String,
    value: String,
    first: Boolean = false,
    last: Boolean = false,
    onChange: (String) -> Unit
) {
    ALSList(label, value = value, first = first, last = last, onValueChange = onChange)
}

@Composable
private fun QvmGunyahPath(
    label: String,
    value: String,
    first: Boolean = false,
    last: Boolean = false,
    onClick: () -> Unit
) {
    ALSList(label, value = value, first = first, last = last, onClick = { onClick() })
}

@Composable
private fun QvmGunyahNumber(
    label: String,
    value: Int,
    first: Boolean = false,
    last: Boolean = false,
    onChange: (Int) -> Unit
) {
    ALSList(
        label,
        value = value.toString(),
        first = first,
        last = last,
        onValueChange = { it.toIntOrNull()?.let(onChange) })
}

@Composable
private fun QvmGunyahToggle(
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
private fun QvmGunyahDisplayDevice(
    value: String, first: Boolean = false, last: Boolean = false, onChange: (String) -> Unit
) {
    val options = listOf(
        "virtio-gpu" to stringResource(R.string.virtio_gpu),
        "ramfb" to stringResource(R.string.ramfb),
        "off" to stringResource(R.string.off)
    )
    Field(
        stringResource(R.string.display_device),
        options.first { it.first == value.toQvmGunyahDisplayDevice() }.second,
        options.map { it.second },
        first,
        last
    ) { selected ->
        onChange(options.first { it.second == selected }.first)
    }
}

private fun Context.qvmGunyahPath(uri: Uri): String {
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
