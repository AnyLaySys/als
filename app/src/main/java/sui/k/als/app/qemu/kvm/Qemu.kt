package sui.k.als.qemu.kvm

import androidx.compose.runtime.Composable
import sui.k.als.app.qemu.kvm.QemuKvmConfig
import sui.k.als.app.qemu.kvm.QemuKvmScreen

@Composable
fun QemuKvm(
    started: Boolean,
    consoleAvailable: Boolean,
    onCreate: (QemuKvmConfig) -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    QemuKvmScreen(started, consoleAvailable, onCreate, onDisplay, onConsole, onStop, onBack)
}
