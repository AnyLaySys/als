package sui.k.als.qvm.img

import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import sui.k.als.qvm.*
import sui.k.als.ui.*
import java.io.*

@Composable
fun QvmImg(onExit: () -> Unit) {
    var diskList by remember { mutableStateOf<List<File>>(emptyList()) }
    val formats = listOf("blkdebug", "blklogwrites", "blkverify", "bochs", "cloop", "compress", "copy-before-write", "copy-on-read", "dmg", "file", "host_cdrom", "host_device", "luks", "nbd", "null-aio", "null-co", "nvme", "parallels", "preallocate", "qcow", "qcow2", "qed", "quorum", "raw", "replication", "snapshot-access", "throttle", "vdi", "vhdx", "vmdk", "vpc", "vvfat")

    LaunchedEffect(Unit) {
        val directory = File(qvmDir)
        if (directory.exists() && directory.isDirectory) {
            diskList = directory.listFiles { file ->
                file.isFile && file.extension.isNotEmpty() && formats.contains(file.extension.lowercase())
            }?.toList() ?: emptyList()
        }
    }

    BackHandler {
        onExit()
    }

    Row(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Column(
            Modifier
                .fillMaxHeight()
                .weight(0.4f)
                .padding(3.dp)
                .verticalScroll(rememberScrollState())
        ) {
            diskList.forEachIndexed { index, file ->
                ALSList(
                    data = file.name,
                    value = file.extension,
                    first = index == 0,
                    last = index == diskList.size - 1,
                    onClick = {}
                )
            }
        }
        Box(
            Modifier
                .fillMaxHeight()
                .weight(0.6f)
        )
    }
}