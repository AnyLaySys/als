package sui.k.als.qvm

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun QvmDisk(state: MutableMap<String, Any>) {
    val cacheModes = listOf("unsafe", "writeback", "writethrough", "none", "directsync")
    val aioModes = listOf("threads", "native", "io_uring")
    val discardModes = listOf("unmap", "ignore")
    val path = state["path"]?.toString() ?: ""
    val cache = state["cache"]?.toString() ?: "unsafe"
    val aio = state["aio"]?.toString() ?: "threads"
    val discard = state["discard"]?.toString() ?: "unmap"
    ALSList(
        stringResource(R.string.disk_path),
        value = path,
        first = true,
        background = if (path.isEmpty()) Color.Red else null,
        onValueChange = { state["path"] = it })
    Field(stringResource(R.string.cache_mode), cache, cacheModes) { state["cache"] = it }
    Field(stringResource(R.string.aio_mode), aio, aioModes) { state["aio"] = it }
    Field(
        stringResource(R.string.discard_mode), discard, discardModes
    ) { state["discard"] = it }
    ALSList(
        stringResource(R.string.queue_count),
        value = state["queues"]?.toString() ?: qvmHostProcessorCount(),
        onValueChange = { state["queues"] = it })
    ALSList(
        stringResource(R.string.boot_index),
        value = state["index"]?.toString() ?: "2",
        last = true,
        onValueChange = { state["index"] = it })
}
