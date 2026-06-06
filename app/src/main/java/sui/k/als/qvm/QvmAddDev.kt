package sui.k.als.qvm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import sui.k.als.*
import sui.k.als.R

@Composable
fun QvmAddDev(
    cdroms: SnapshotStateList<MutableMap<String, Any>>,
    disks: SnapshotStateList<MutableMap<String, Any>>,
    networks: SnapshotStateList<MutableMap<String, Any>>,
    qvmMap: MutableMap<String, Any>,
    onAdded: (Int) -> Unit
) {
    val showAudio = qvmMap["audio"] != 1
    Column {
        QvmAddDeviceButton(R.drawable.hard_drive, stringResource(R.string.disk), first = true) {
            disks.add(
                mutableStateMapOf(
                    "path" to "",
                    "cache" to "unsafe",
                    "index" to (disks.size + cdroms.size + 2).toString()
                )
            )
            onAdded(1 + cdroms.size + disks.size - 1)
        }
        QvmAddDeviceButton(R.drawable.album, stringResource(R.string.cdrom)) {
            cdroms.add(mutableStateMapOf("path" to "", "index" to (cdroms.size + 1).toString()))
            onAdded(1 + cdroms.size - 1)
        }
        QvmAddDeviceButton(R.drawable.wifi, stringResource(R.string.network), last = !showAudio) {
            networks.add(
                mutableStateMapOf(
                    "device" to "virtio-net-pci",
                    "backend" to "user",
                    "protocol" to "tcp",
                    "ports" to "2222-:22"
                )
            )
            onAdded(1 + cdroms.size + disks.size + networks.size - 1)
        }
        if (showAudio) {
            QvmAddDeviceButton(R.drawable.volume_up, stringResource(R.string.audio), last = true) {
                qvmMap["audio"] = 1
                onAdded(1 + cdroms.size + disks.size + networks.size + 1)
            }
        }
    }
}

@Composable
private fun QvmAddDeviceButton(
    icon: Int,
    label: String,
    first: Boolean = false,
    last: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(
        topStart = if (first) 10.dp else 3.dp,
        topEnd = if (first) 10.dp else 3.dp,
        bottomStart = if (last) 10.dp else 3.dp,
        bottomEnd = if (last) 10.dp else 3.dp
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clickable { onClick() },
        color = Color(0xFF1B1B1F),
        contentColor = Color(0xFFE9EEF5),
        shape = shape
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(painterResource(icon), null, Modifier.size(17.dp), tint = Color(0xFFA8C7FA))
            Text(label, fontSize = 10.sp, fontFamily = localFont.current, fontWeight = FontWeight.Bold)
        }
    }
}
