package sui.k.als.qemu.gzvm

import androidx.compose.runtime.Composable
import sui.k.als.app.qemu.gzvm.QemuGzvmConfig

@Composable
fun QemuGzvm(
    started: Boolean,
    onCreate: (QemuGzvmConfig) -> Unit,
    onDisplay: () -> Unit,
    onStop: () -> Unit
) {
    QemuGzvmScreen(started, onCreate, onDisplay, onStop)
}
