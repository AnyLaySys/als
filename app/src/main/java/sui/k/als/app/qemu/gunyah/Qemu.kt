package sui.k.als.qemu.gunyah

import androidx.compose.runtime.Composable
import sui.k.als.app.qemu.gunyah.QemuGunyahConfig

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
