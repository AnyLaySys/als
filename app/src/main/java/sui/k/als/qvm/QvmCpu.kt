package sui.k.als.qvm

import androidx.compose.runtime.*
import androidx.compose.ui.res.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun QvmProcessor(state: MutableMap<String, Any>) {
    val cores = state["cores"]?.toString() ?: "$(nproc)"
    val threads = state["threads"]?.toString() ?: "1"
    val sockets = state["sockets"]?.toString() ?: "1"
    val smp = state["smp"]?.toString() ?: "$(nproc)"

    LaunchedEffect(cores, threads, sockets, smp) {
        state["processor"] = "-smp cores=$cores,threads=$threads,sockets=$sockets,$smp "
    }

    ALSList(
        stringResource(R.string.cpu_cores),
        value = cores,
        first = true,
        onValueChange = { state["cores"] = it })
    ALSList(
        stringResource(R.string.cpu_threads),
        value = threads,
        onValueChange = { state["threads"] = it })
    ALSList(
        stringResource(R.string.cpu_sockets),
        value = sockets,
        onValueChange = { state["sockets"] = it })
    ALSList(
        stringResource(R.string.smp),
        value = smp,
        last = true,
        onValueChange = { state["smp"] = it })
}