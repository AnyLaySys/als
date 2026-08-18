package sui.k.als.agl

import android.content.Context
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import sui.k.als.R

class AglDisplayView(context: Context) : FrameLayout(context) {
    private val display = AglView(context)
    private val keyboard = AglKeyboardView(context, display)
    private val softKeyboardButton = LayoutInflater.from(context)
        .inflate(R.layout.agl_soft_keyboard, this, false) as ImageView

    init {
        addView(
            display,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(keyboard)
        addView(softKeyboardButton)
        softKeyboardButton.setOnClickListener {
            display.requestFocus()
            display.post {
                context.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(display, 0)
            }
        }
        post(keyboard::applyLayout)
    }

    fun configure(width: Int, height: Int, hideKeyboard: Boolean, softKeyboard: Boolean) {
        display.configure(width, height)
        keyboard.visibility = if (hideKeyboard) GONE else VISIBLE
        softKeyboardButton.visibility = if (softKeyboard) VISIBLE else GONE
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
