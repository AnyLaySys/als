package sui.k.als.qvm
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import kotlinx.coroutines.*
import sui.k.als.*
import sui.k.als.R
import sui.k.als.ui.*
import java.util.*
@Composable
fun QvmDisplay(state: MutableMap<String, Any>) {
    val showDiscovery = remember { mutableStateOf(false) }
    val deviceItems = remember { mutableStateOf(emptyList<String>()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state["vnc_port"], state["display_device"], state["xres"], state["yres"]) {
        val device = state["display_device"] ?: "virtio-gpu-pci"
        val vnc = state.getOrPut("vnc_port") { ":0" }
        val x = state["xres"] ?: ""
        val y = state["yres"] ?: ""
        state["display"] = "-device $device,xres=$x,yres=$y -vnc $vnc "
    }
    Column {
        ALSList(
            data = stringResource(R.string.display_device),
            value = state["display_device"]?.toString() ?: "virtio-gpu-pci",
            first = true,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    val qvmDir = "$alsDir/app/qvm"
                    val cmd = "LD_LIBRARY_PATH=$qvmDir/libs $qvmDir/qemu-system-aarch64 -M virt -device help 2>&1 | sed -n '/Display devices:/,/^$/p' | sed '1d' | awk -F'[\" ,]' '{print $3}'"
                    runCatching {
                        val process = Runtime.getRuntime().exec(arrayOf(su, "-c", cmd))
                        val result = Scanner(process.inputStream).useDelimiter("\n").asSequence().filter { it.isNotBlank() }.toList()
                        if (result.isNotEmpty()) {
                            deviceItems.value = result
                            showDiscovery.value = true
                        }
                    }
                }
            }
        )
        ALSList(data = stringResource(R.string.xres), value = state["xres"]?.toString() ?: "", onValueChange = { state["xres"] = it }, background = Color.Red.takeIf { state["xres"]?.toString().isNullOrEmpty() })
        ALSList(data = stringResource(R.string.yres), value = state["yres"]?.toString() ?: "", onValueChange = { state["yres"] = it }, background = Color.Red.takeIf { state["yres"]?.toString().isNullOrEmpty() })
        ALSList(data = stringResource(R.string.vnc_port), value = state.getOrPut("vnc_port") { ":0" }.toString(), onValueChange = { state["vnc_port"] = it }, last = true, background = Color.Red.takeIf { state["vnc_port"]?.toString().isNullOrEmpty() })
    }
    if (showDiscovery.value) {
        ALSList(
            data = deviceItems.value,
            show = true,
            onDismiss = { showDiscovery.value = false },
            onClick = {
                state["display_device"] = it
                showDiscovery.value = false
            }
        )
    }
}