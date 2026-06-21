package sui.k.als.qvm.gunyah

import androidx.compose.runtime.Composable

@Composable
fun QvmGunyah(
    started: Boolean, onCreate: (QvmGunyahConfig) -> Unit, onEnter: () -> Unit, onX11: () -> Unit
) {
    QvmGunyahScreen(started, onCreate, onEnter, onX11)
}
