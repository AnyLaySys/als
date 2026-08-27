package sui.k.als.qemu.vm

import android.view.*

internal object AGL {
    external fun setSurface(surface: Surface?, refreshRate: Float)
}