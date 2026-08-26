package sui.k.als.app.qemu.gunyah

import androidx.compose.runtime.Composable

@Composable
fun QemuGunyah(
    started: Boolean,
    onCreate: (QemuGunyahConfig) -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onKeyboardSettingsChange: (Boolean, Boolean) -> Unit
) {
    QemuGunyahScreen(
        started,
        onCreate,
        onDisplay,
        onConsole,
        onStop,
        onBack,
        onKeyboardSettingsChange
    )
}
