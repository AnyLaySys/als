package sui.k.als.agl

import android.content.Context
import android.widget.FrameLayout

class AglDisplayView(context: Context) : FrameLayout(context) {
    private val display = AglView(context)
    private val keyboard = AglKeyboardView(context, display)

    init {
        addView(
            display,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(keyboard)
        post(keyboard::applyLayout)
    }

    fun configure(width: Int, height: Int) {
        display.configure(width, height)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        keyboard.applyLayout()
    }

    override fun onDetachedFromWindow() {
        keyboard.release()
        display.releaseKeys()
        super.onDetachedFromWindow()
    }
}
