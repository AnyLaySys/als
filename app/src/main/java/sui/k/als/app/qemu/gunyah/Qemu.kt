package sui.k.als.qemu.gunyah

import androidx.compose.runtime.*
import sui.k.als.app.qemu.gunyah.QemuGunyahConfig

@Composable
fun QemuGunyah(
    started: Boolean,
    consoleAvailable: Boolean,
    onCreate: (QemuGunyahConfig) -> Unit,
    onDisplay: () -> Unit,
    onConsole: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    QemuGunyahScreen(started, consoleAvailable, onCreate, onDisplay, onConsole, onStop, onBack)
}
