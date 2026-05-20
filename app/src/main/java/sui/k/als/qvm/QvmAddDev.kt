package sui.k.als.qvm

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.unit.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun QvmAddDev(
    cdroms: SnapshotStateList<MutableMap<String, Any>>,
    disks: SnapshotStateList<MutableMap<String, Any>>,
    networks: SnapshotStateList<MutableMap<String, Any>>,
    qvmMap: MutableMap<String, Any>,
    onAdded: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ALSButton(R.drawable.hard_drive) {
            disks.add(
                mutableStateMapOf(
                    "path" to "",
                    "cache" to "unsafe",
                    "index" to (disks.size + cdroms.size + 2).toString()
                )
            )
            onAdded(3 + cdroms.size + disks.size - 1)
        }
        ALSButton(R.drawable.album) {
            cdroms.add(mutableStateMapOf("path" to "", "index" to (cdroms.size + 1).toString()))
            onAdded(3 + cdroms.size - 1)
        }
        ALSButton(R.drawable.wifi) {
            networks.add(
                mutableStateMapOf(
                    "device" to "virtio-net-pci",
                    "backend" to "user",
                    "protocol" to "tcp",
                    "ports" to "2222-:22"
                )
            )
            onAdded(3 + cdroms.size + disks.size + networks.size - 1)
        }
        if (qvmMap["audio"] != 1) {
            ALSButton(R.drawable.volume_up) {
                qvmMap["audio"] = 1
                onAdded(3 + cdroms.size + disks.size + networks.size + 1)
            }
        }
    }
}