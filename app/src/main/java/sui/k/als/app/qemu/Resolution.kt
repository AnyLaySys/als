package sui.k.als.app.qemu

import android.hardware.display.DisplayManager
import android.content.res.Configuration
import android.view.Display
import sui.k.als.ALSApplication

internal object QemuResolution {
    private val size by lazy {
        val context = ALSApplication.instance
        val mode = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)?.mode
        val metrics = context.resources.displayMetrics
        val width = mode?.physicalWidth ?: metrics.widthPixels
        val height = mode?.physicalHeight ?: metrics.heightPixels
        val portrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val shortSide = minOf(width, height) / 2
        val longSide = maxOf(width, height) / 2
        if (portrait) shortSide to longSide else longSide to shortSide
    }

    val width: Int
        get() = size.first

    val height: Int
        get() = size.second
}
