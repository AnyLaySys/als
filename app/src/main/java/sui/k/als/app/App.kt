package sui.k.als.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import sui.k.als.ui.ALSList

@Composable
fun App(onQemuGunyah: () -> Unit, onX11: () -> Unit) = Box(
    Modifier
        .fillMaxSize()
        .background(Color.Black),
    Alignment.Center
) {
    Column(Modifier.fillMaxWidth(0.86f)) {
        ALSList("qemu-gunyah", first = true) { onQemuGunyah() }
        ALSList("x11", last = true) { onX11() }
    }
}
