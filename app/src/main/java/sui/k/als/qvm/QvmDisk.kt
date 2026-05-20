package sui.k.als.qvm

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun QvmDisk(state: MutableMap<String, Any>) {
    val path = state["path"]?.toString() ?: ""
    ALSList(
        stringResource(R.string.disk_path),
        value = path,
        first = true,
        background = if (path.isEmpty()) Color.Red else null,
        onValueChange = { state["path"] = it })
    ALSList(
        stringResource(R.string.cache_mode),
        value = state["cache"]?.toString() ?: "unsafe",
        onValueChange = { state["cache"] = it })
    ALSList(
        stringResource(R.string.aio_mode),
        value = state["aio"]?.toString() ?: "threads",
        onValueChange = { state["aio"] = it })
    ALSList(
        stringResource(R.string.discard_mode),
        value = state["discard"]?.toString() ?: "unmap",
        onValueChange = { state["discard"] = it })
    ALSList(
        stringResource(R.string.queue_count),
        value = state["queues"]?.toString() ?: "$(nproc)",
        onValueChange = { state["queues"] = it })
    ALSList(
        stringResource(R.string.boot_index),
        value = state["index"]?.toString() ?: "2",
        last = true,
        onValueChange = { state["index"] = it })
}