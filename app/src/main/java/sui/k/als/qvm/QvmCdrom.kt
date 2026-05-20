package sui.k.als.qvm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import sui.k.als.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun QvmCdrom(state: MutableMap<String, Any>) {
    val path = state["path"]?.toString() ?: ""
    val index = state["index"]?.toString() ?: ""
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CD-ROM", color = Color.Gray, fontSize = 10.sp, fontFamily = localFont.current)
        }
        ALSList(
            stringResource(R.string.cdrom_path),
            value = path,
            first = true,
            background = if (path.isEmpty()) Color.Red else null,
            onValueChange = { state["path"] = it })
        ALSList(
            stringResource(R.string.boot_index),
            value = index,
            last = true,
            onValueChange = { state["index"] = it })
    }
}