package sui.k.als.app.qemu.vm

import android.view.Surface

internal object AGL {
    external fun setSurface(surface: Surface?, refreshRate: Float)
}
