package sui.k.als.qemu.kvm

import androidx.compose.runtime.Composable
import sui.k.als.app.qemu.kvm.QemuKvmConfig
import sui.k.als.app.qemu.kvm.QemuKvmScreen

@Composable
fun QemuKvm(
    started: Boolean,
    onCreate: (QemuKvmConfig) -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onKeyboardSettingsChange: (Boolean, Boolean) -> Unit
) {
    QemuKvmScreen(
        started,
        onCreate,
        onDisplay,
        onConsole,
        onStop,
        onBack,
        onKeyboardSettingsChange
    )
}
