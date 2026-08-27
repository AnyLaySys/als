package sui.k.als.qemu.vm

import android.content.*
import android.view.*
import android.view.inputmethod.*
import android.widget.*
import sui.k.als.*

class VMDisplay(context: Context) : FrameLayout(context) {
    private val display = VMView(context)
    private val keyboard = VMKeyboard(context, display)
    private val softKeyboardButton =
        LayoutInflater.from(context).inflate(R.layout.vm_keyboard, this, false) as ImageView

    init {
        addView(
            display, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(keyboard)
        addView(softKeyboardButton)
        softKeyboardButton.setOnClickListener {
            display.requestFocus()
            display.post {
                context.getSystemService(InputMethodManager::class.java)?.showSoftInput(display, 0)
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
