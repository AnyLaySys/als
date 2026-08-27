package sui.k.als.qemu.gzvm

import androidx.compose.runtime.Composable

@Composable
fun QemuGzvm(
    started: Boolean,
    onCreate: (QemuGzvmConfig) -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onKeyboardSettingsChange: (Boolean, Boolean) -> Unit
) {
    QemuGzvmScreen(
        started,
        onCreate,
        onDisplay,
        onConsole,
        onStop,
        onBack,
        onKeyboardSettingsChange
    )
}
