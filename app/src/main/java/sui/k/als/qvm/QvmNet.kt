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
fun QvmNetwork(state: MutableMap<String, Any>) {
    val showDevDialog = remember { mutableStateOf(false) }
    val devList = remember { mutableStateOf(emptyList<String>()) }
    val scope = rememberCoroutineScope()
    val backendList = listOf("user", "tap")
    val protocolList = listOf("tcp", "udp")
    val dev = state["device"]?.toString() ?: "virtio-net-pci"
    val backend = state["backend"]?.toString() ?: "user"
    val protocol = state["protocol"]?.toString() ?: "tcp"
    val ports = state["ports"]?.toString() ?: "2222-:22"
    LaunchedEffect(dev, backend, protocol, ports) {
        val netStr =
            if (backend == "user") "-netdev user,id=net0,hostfwd=$protocol::$ports -device $dev,netdev=net0"
            else "-netdev tap,id=net0 -device $dev,netdev=net0"
        state["network"] = netStr
    }
    Column {
        ALSList(
            data = stringResource(R.string.network_device), value = dev, first = true, onClick = {
                scope.launch(Dispatchers.IO) {
                    val qvmDir = "$alsDir/app/qvm"
                    val cmd =
                        "LD_LIBRARY_PATH=$qvmDir/libs $qvmDir/qemu-system-aarch64 -M virt -device help 2>&1 | sed -n '/Network devices:/,/^$/p' | sed '1d' | awk -F'[\\\" ,]' '{print $3}'"
                    runCatching {
                        val process = Runtime.getRuntime().exec(arrayOf(su, "-c", cmd))
                        val result = Scanner(process.inputStream).useDelimiter("\n").asSequence()
                            .filter { it.isNotBlank() }.toList()
                        if (result.isNotEmpty()) {
                            devList.value = result
                            showDevDialog.value = true
                        }
                    }
                }
            })
        Field(
            stringResource(R.string.network_backend), backend, backendList, last = backend != "user"
        ) { state["backend"] = it }
        if (backend == "user") {
            Field(
                stringResource(R.string.network_protocol), protocol, protocolList
            ) { state["protocol"] = it }
            ALSList(
                data = stringResource(R.string.port_forwarding),
                value = ports,
                onValueChange = { state["ports"] = it },
                last = true,
                background = Color.Red.takeIf { ports.isEmpty() })
        }
    }
    if (showDevDialog.value) {
        ALSList(
            data = devList.value,
            show = true,
            onDismiss = { showDevDialog.value = false },
            onClick = { state["device"] = it; showDevDialog.value = false })
    }
}
