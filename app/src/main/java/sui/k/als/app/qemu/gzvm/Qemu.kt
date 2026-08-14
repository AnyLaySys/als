package sui.k.als.qemu.gzvm

import androidx.compose.runtime.Composable
import sui.k.als.app.qemu.gzvm.QemuGzvmConfig

@Composable
fun QemuGzvm(
    started: Boolean,
    consoleAvailable: Boolean,
    onCreate: (QemuGzvmConfig) -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    QemuGzvmScreen(started, consoleAvailable, onCreate, onDisplay, onConsole, onStop, onBack)
}
