package sui.k.als.qemu.gunyah

import androidx.compose.runtime.*
import sui.k.als.app.qemu.gunyah.QemuGunyahConfig

@Composable
fun QemuGunyah(
    started: Boolean,
    onCreate: (QemuGunyahConfig) -> Unit,
    onDisplay: () -> Unit,
    onStop: () -> Unit
) {
    QemuGunyahScreen(started, onCreate, onDisplay, onStop)
}
