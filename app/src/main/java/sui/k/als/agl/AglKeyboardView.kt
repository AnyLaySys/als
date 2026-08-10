package sui.k.als.agl

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class AglKeyboardView(
    context: Context,
    private val display: AglView
) : View(context) {
    private companion object {
        const val UP = "↑"
        const val DOWN = "↓"
        const val LEFT = "←"
        const val RIGHT = "→"
        const val KEYBOARD = 0
        const val TOUCHPAD = 1
        const val BUTTON_LEFT = 1
        const val BUTTON_RIGHT = 2
        const val BUTTON_MIDDLE = 4
        val compactRows = arrayOf(
            arrayOf("Esc", "F1", "F2", "F3", "·", "F4", "F5", "F6", "Del"),
            arrayOf("Shift", "F7", "F8", "F9", UP, "F10", "F11", "F12", "Back"),
            arrayOf("Tab", "Ctrl", "Alt", LEFT, DOWN, RIGHT, "Home", "End", "Enter")
        )
        val fullRows = arrayOf(
            arrayOf("Esc", "F1", "F2", "F3", "F4", "F5", "F6", "", "F7", "F8", "F9", "F10", "F11", "F12", "Del"),
            arrayOf("`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "Back"),
            arrayOf("Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "\\"),
            arrayOf("Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "Enter"),
            arrayOf("Shift", "Z", "X", "C", "V", "B", "N", "M", ",", ".", UP, "/"),
            arrayOf("Ctrl", "Alt", "Home", " ", "End", LEFT, DOWN, RIGHT)
        )
        val keyCodes = buildMap {
            put("Esc", KeyEvent.KEYCODE_ESCAPE)
            put("Tab", KeyEvent.KEYCODE_TAB)
            put("Enter", KeyEvent.KEYCODE_ENTER)
            put("Back", KeyEvent.KEYCODE_DEL)
            put("Del", KeyEvent.KEYCODE_FORWARD_DEL)
            put("Home", KeyEvent.KEYCODE_MOVE_HOME)
            put("End", KeyEvent.KEYCODE_MOVE_END)
            put(UP, KeyEvent.KEYCODE_DPAD_UP)
            put(DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
            put(LEFT, KeyEvent.KEYCODE_DPAD_LEFT)
            put(RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT)
            for (index in 0 until 12) {
                put("F${index + 1}", KeyEvent.KEYCODE_F1 + index)
            }
        }
        val printableKeyCodes = buildMap {
            for (letter in 'A'..'Z') {
                put(letter.lowercaseChar().toString(), KeyEvent.KEYCODE_A + (letter - 'A'))
            }
            for (number in '0'..'9') {
                put(number.toString(), KeyEvent.KEYCODE_0 + (number - '0'))
            }
            put("`", KeyEvent.KEYCODE_GRAVE)
            put("-", KeyEvent.KEYCODE_MINUS)
            put("=", KeyEvent.KEYCODE_EQUALS)
            put("[", KeyEvent.KEYCODE_LEFT_BRACKET)
            put("]", KeyEvent.KEYCODE_RIGHT_BRACKET)
            put("\\", KeyEvent.KEYCODE_BACKSLASH)
            put(";", KeyEvent.KEYCODE_SEMICOLON)
            put("'", KeyEvent.KEYCODE_APOSTROPHE)
            put(",", KeyEvent.KEYCODE_COMMA)
            put(".", KeyEvent.KEYCODE_PERIOD)
            put("/", KeyEvent.KEYCODE_SLASH)
            put(" ", KeyEvent.KEYCODE_SPACE)
        }
        val shiftSymbols = mapOf(
            "`" to "~",
            "1" to "!",
            "2" to "@",
            "3" to "#",
            "4" to "\$",
            "5" to "%",
            "6" to "^",
            "7" to "&",
            "8" to "*",
            "9" to "(",
            "0" to ")",
            "-" to "_",
            "=" to "+",
            "[" to "{",
            "]" to "}",
            "\\" to "|",
            ";" to ":",
            "'" to "\"",
            "," to "<",
            "." to ">",
            "/" to "?"
        )
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            9f,
            resources.displayMetrics
        )
    }
    private val rect = RectF()
    private val handler = Handler(Looper.getMainLooper())
    private val touches = SparseArray<KeyTouch>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getTapTimeout()
    private var ctrlActive = false
    private var shiftActive = false
    private var altActive = false
    private var capsActive = false
    private var fullKeyboardVisible = false
    private var floating = false
    private var keyboardOffsetX = 0
    private var keyboardOffsetY = 0
    private var touchpadPointerId = -1
    private var touchpadMaxPointers = 0
    private var touchpadDownX = 0f
    private var touchpadDownY = 0f
    private var touchpadLastX = 0f
    private var touchpadLastY = 0f
    private var touchpadDownTime = 0L
    private var touchpadMoved = false
    private var touchpadLongPress: Runnable? = null
    private var touchpadDragging = false

    init {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            compactHeight()
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        }
        isClickable = true
        setBackgroundColor(Color.TRANSPARENT)
        setOnHoverListener { _, _ -> true }
        setOnGenericMotionListener { _, _ -> true }
    }

    internal fun applyLayout() {
        val host = parent as? View ?: return
        val hostWidth = host.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val hostHeight = host.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val portrait = hostHeight >= hostWidth
        val params = (layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(0, 0)
        when {
            fullKeyboardVisible && !portrait -> {
                params.width = hostWidth * 2 / 3
                params.height = FrameLayout.LayoutParams.MATCH_PARENT
            }
            fullKeyboardVisible -> {
                params.width = FrameLayout.LayoutParams.MATCH_PARENT
                params.height = hostHeight * 2 / 3
            }
            else -> {
                params.width = if (floating) min(floatingWidth(), hostWidth) else FrameLayout.LayoutParams.MATCH_PARENT
                params.height = compactHeight()
            }
        }
        params.gravity = Gravity.BOTTOM or if (
            fullKeyboardVisible && !portrait && !floating
        ) {
            Gravity.END
        } else {
            Gravity.START
        }
        layoutParams = params
        translationX = if (floating) keyboardOffsetX.toFloat() else 0f
        translationY = if (floating) keyboardOffsetY.toFloat() else 0f
        invalidate()
    }

    internal fun release() {
        finishAll()
        finishTouchpad()
        releaseModifiers()
        handler.removeCallbacksAndMessages(null)
    }

    private fun compactHeight() =
        (18f * resources.displayMetrics.density * compactRows.size).roundToInt()

    private fun floatingWidth() =
        (360f * resources.displayMetrics.density).roundToInt()

    private fun rows() = if (fullKeyboardVisible) fullRows else compactRows

    private fun isControlKey(label: String) = label.isEmpty() || label == "·"

    private fun isModifier(label: String) =
        label == "Ctrl" || label == "Shift" || label == "Alt" || label == "Caps"

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rows = rows()
        val left = areaLeft()
        val top = areaTop()
        val width = areaWidth()
        val rowHeight = areaHeight() / rows.size
        rows.forEachIndexed { index, row ->
            drawRow(canvas, row, left, top + index * rowHeight, width, rowHeight)
        }
    }

    private fun drawRow(
        canvas: Canvas,
        row: Array<String>,
        left: Float,
        top: Float,
        width: Float,
        rowHeight: Float
    ) {
        val total = row.sumOf { keyWeight(it).toDouble() }.toFloat()
        var x = left
        row.forEach { label ->
            val keyWidth = width * keyWeight(label) / total
            rect.set(x, top, x + keyWidth, top + rowHeight)
            val text = displayLabel(label)
            if (text.isNotEmpty()) {
                val metrics = textPaint.fontMetrics
                canvas.drawText(
                    text,
                    rect.centerX(),
                    rect.centerY() - (metrics.ascent + metrics.descent) / 2f,
                    textPaint
                )
            }
            x += keyWidth
        }
    }

    private fun keyWeight(label: String): Float {
        if (!fullKeyboardVisible) {
            return 1f
        }
        return when (label) {
            " " -> 4.2f
            "Ctrl", "Alt", "Home", "End" -> 1.2f
            else -> 1f
        }
    }

    private fun displayLabel(label: String): String {
        if (isControlKey(label)) {
            return ""
        }
        if (isModifier(label) || label.length > 1) {
            return label
        }
        val base = label.lowercase(Locale.US)
        if (shiftActive) {
            return shiftSymbols[base] ?: label.uppercase(Locale.US)
        }
        return if (capsActive && label[0].isLetter()) {
            label.uppercase(Locale.US)
        } else {
            label
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                if (touchpadPointerId != -1) {
                    return touchpad(event)
                }
                if (
                    touches.size() == 0 &&
                    fullKeyboardVisible &&
                    areaAt(event.getX(index), event.getY(index)) == TOUCHPAD
                ) {
                    return touchpad(event)
                }
                startTouch(event, index)
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                return if (touchpadPointerId != -1) touchpad(event) else moveTouches(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                return if (touchpadPointerId != -1) {
                    touchpad(event)
                } else {
                    finishTouch(event.getPointerId(event.actionIndex), event)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                finishTouchpad()
                finishAll()
                return true
            }
        }
        return true
    }

    private fun startTouch(event: MotionEvent, index: Int) {
        val label = findKey(event.getX(index), event.getY(index)) ?: return
        val pointerId = event.getPointerId(index)
        val rawX = rawX(event, index)
        val rawY = rawY(event, index)
        val touch = KeyTouch(
            pointerId = pointerId,
            label = label,
            downRawX = rawX,
            downRawY = rawY,
            lastRawX = rawX,
            lastRawY = rawY,
            startOffsetX = if (floating) keyboardOffsetX else 0,
            startOffsetY = if (floating) keyboardOffsetY else 0
        )
        touches.put(pointerId, touch)
        parent?.requestDisallowInterceptTouchEvent(true)
        display.requestFocus()
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (isModifier(touch.label)) {
            setModifier(touch.label, true)
        } else if (!isControlKey(touch.label)) {
            sendKey(touch.label)
            startRepeat(touch)
        }
        invalidate()
    }

    private fun moveTouches(event: MotionEvent): Boolean {
        for (index in 0 until touches.size()) {
            val touch = touches.valueAt(index)
            if (!isControlKey(touch.label)) {
                continue
            }
            val pointerIndex = event.findPointerIndex(touch.pointerId)
            if (pointerIndex < 0) {
                continue
            }
            val rawX = rawX(event, pointerIndex)
            val rawY = rawY(event, pointerIndex)
            val dx = rawX - touch.downRawX
            val dy = rawY - touch.downRawY
            if (!touch.dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                touch.dragging = true
                floating = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                keyboardOffsetX = touch.startOffsetX + dx.roundToInt()
                keyboardOffsetY = touch.startOffsetY + dy.roundToInt()
                touch.lastRawX = rawX
                touch.lastRawY = rawY
                applyLayout()
            }
            if (touch.dragging) {
                keyboardOffsetX += (rawX - touch.lastRawX).roundToInt()
                keyboardOffsetY += (rawY - touch.lastRawY).roundToInt()
                touch.lastRawX = rawX
                touch.lastRawY = rawY
                translationX = keyboardOffsetX.toFloat()
                translationY = keyboardOffsetY.toFloat()
            }
        }
        invalidate()
        return true
    }

    private fun finishTouch(pointerId: Int, event: MotionEvent): Boolean {
        val touch = touches[pointerId] ?: return true
        touch.repeat?.let(handler::removeCallbacks)
        if (isModifier(touch.label)) {
            setModifier(touch.label, false)
        }
        if (isControlKey(touch.label)) {
            val index = event.findPointerIndex(pointerId)
            val dx = if (index >= 0) rawX(event, index) - touch.downRawX else 0f
            val dy = if (index >= 0) rawY(event, index) - touch.downRawY else 0f
            if (!touch.dragging && dx * dx + dy * dy <= touchSlop * touchSlop) {
                tapControlKey()
            }
        }
        touches.remove(pointerId)
        if (touches.size() == 0) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        invalidate()
        return true
    }

    private fun finishAll() {
        for (index in touches.size() - 1 downTo 0) {
            val touch = touches.valueAt(index)
            touch.repeat?.let(handler::removeCallbacks)
            if (isModifier(touch.label)) {
                setModifier(touch.label, false)
            }
        }
        touches.clear()
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun startRepeat(touch: KeyTouch) {
        val repeat = object : Runnable {
            override fun run() {
                if (touches[touch.pointerId] !== touch) {
                    return
                }
                sendKey(touch.label)
                handler.postDelayed(this, 30)
            }
        }
        touch.repeat = repeat
        handler.postDelayed(repeat, 270)
    }

    private fun tapControlKey() {
        if (floating) {
            floating = false
        } else {
            fullKeyboardVisible = !fullKeyboardVisible
        }
        applyLayout()
    }

    private fun rawX(event: MotionEvent, index: Int) =
        event.rawX + event.getX(index) - event.x

    private fun rawY(event: MotionEvent, index: Int) =
        event.rawY + event.getY(index) - event.y

    private fun findKey(x: Float, y: Float): String? {
        if (areaAt(x, y) != KEYBOARD) {
            return null
        }
        val rows = rows()
        val left = areaLeft()
        val top = areaTop()
        val width = areaWidth()
        val height = areaHeight()
        if (width <= 0f || height <= 0f || x !in left..left + width || y !in top..top + height) {
            return null
        }
        val localX = x - left
        val localY = y - top
        val rowIndex = min(rows.lastIndex, max(0, (localY / (height / rows.size)).toInt()))
        val row = rows[rowIndex]
        val total = row.sumOf { keyWeight(it).toDouble() }.toFloat()
        var keyLeft = 0f
        row.forEach { label ->
            val keyWidth = width * keyWeight(label) / total
            if (localX in keyLeft..keyLeft + keyWidth) {
                return label
            }
            keyLeft += keyWidth
        }
        return null
    }

    private fun portrait() = height >= width

    private fun areaAt(x: Float, y: Float): Int {
        if (!fullKeyboardVisible) {
            return KEYBOARD
        }
        return if (portrait()) {
            if (y < height / 2f) TOUCHPAD else KEYBOARD
        } else {
            if (x >= width / 2f) KEYBOARD else TOUCHPAD
        }
    }

    private fun areaLeft() =
        if (!fullKeyboardVisible || portrait()) 0f else width / 2f

    private fun areaTop() =
        if (!fullKeyboardVisible || !portrait()) 0f else height / 2f

    private fun areaWidth() =
        if (!fullKeyboardVisible || portrait()) width.toFloat() else width / 2f

    private fun areaHeight() =
        if (!fullKeyboardVisible || !portrait()) height.toFloat() else height / 2f

    private fun touchpad(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = event.actionIndex
                touchpadPointerId = event.getPointerId(index)
                touchpadMaxPointers = 1
                touchpadDownX = event.getX(index)
                touchpadDownY = event.getY(index)
                touchpadLastX = touchpadDownX
                touchpadLastY = touchpadDownY
                touchpadDownTime = event.eventTime
                touchpadMoved = false
                touchpadDragging = false
                touchpadLongPress?.let(handler::removeCallbacks)
                touchpadLongPress = Runnable {
                    if (
                        touchpadPointerId != -1 &&
                        !touchpadMoved &&
                        touchpadMaxPointers == 1
                    ) {
                        touchpadDragging = true
                        display.setPointerButton(BUTTON_LEFT, true)
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }.also {
                    handler.postDelayed(it, ViewConfiguration.getLongPressTimeout().toLong())
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                touchpadLongPress?.let(handler::removeCallbacks)
                touchpadLongPress = null
                touchpadMaxPointers = max(touchpadMaxPointers, event.pointerCount)
                touchpadDownX = focusX(event)
                touchpadDownY = focusY(event)
                touchpadLastX = touchpadDownX
                touchpadLastY = touchpadDownY
                touchpadMoved = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val next = (0 until event.pointerCount).firstOrNull { it != event.actionIndex }
                if (next != null) {
                    touchpadPointerId = event.getPointerId(next)
                    touchpadLastX = event.getX(next)
                    touchpadLastY = event.getY(next)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(touchpadPointerId)
                if (index < 0) {
                    return true
                }
                val x = if (event.pointerCount > 1) focusX(event) else event.getX(index)
                val y = if (event.pointerCount > 1) focusY(event) else event.getY(index)
                val dx = x - touchpadLastX
                val dy = y - touchpadLastY
                if (dx != 0f || dy != 0f) {
                    val totalX = x - touchpadDownX
                    val totalY = y - touchpadDownY
                    if (!touchpadMoved && totalX * totalX + totalY * totalY > touchSlop * touchSlop) {
                        touchpadMoved = true
                        if (!touchpadDragging) {
                            touchpadLongPress?.let(handler::removeCallbacks)
                            touchpadLongPress = null
                        }
                    }
                    if (event.pointerCount > 1) {
                        display.scrollPointer(-dx, -dy)
                    } else {
                        display.movePointer(dx, dy)
                    }
                    touchpadLastX = x
                    touchpadLastY = y
                }
            }
            MotionEvent.ACTION_UP -> {
                touchpadLongPress?.let(handler::removeCallbacks)
                touchpadLongPress = null
                if (
                    !touchpadDragging &&
                    !touchpadMoved &&
                    event.eventTime - touchpadDownTime <= tapTimeout
                ) {
                    clickTouchpad(touchpadMaxPointers)
                }
                finishTouchpad()
            }
            MotionEvent.ACTION_CANCEL -> finishTouchpad()
        }
        return true
    }

    private fun focusX(event: MotionEvent): Float {
        var value = 0f
        for (index in 0 until event.pointerCount) {
            value += event.getX(index)
        }
        return value / event.pointerCount
    }

    private fun focusY(event: MotionEvent): Float {
        var value = 0f
        for (index in 0 until event.pointerCount) {
            value += event.getY(index)
        }
        return value / event.pointerCount
    }

    private fun clickTouchpad(pointers: Int) {
        val button = when (pointers) {
            1 -> BUTTON_LEFT
            2 -> BUTTON_RIGHT
            3 -> BUTTON_MIDDLE
            else -> 0
        }
        if (button != 0) {
            display.clickPointer(button)
        }
    }

    private fun finishTouchpad() {
        touchpadPointerId = -1
        touchpadMaxPointers = 0
        touchpadLongPress?.let(handler::removeCallbacks)
        touchpadLongPress = null
        if (touchpadDragging) {
            display.setPointerButton(BUTTON_LEFT, false)
            touchpadDragging = false
        }
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun setModifier(label: String, active: Boolean) {
        when (label) {
            "Ctrl" -> {
                if (ctrlActive != active) {
                    display.setKeyCode(KeyEvent.KEYCODE_CTRL_LEFT, active)
                }
                ctrlActive = active
            }
            "Shift" -> {
                if (shiftActive != active) {
                    display.setKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT, active)
                }
                shiftActive = active
            }
            "Alt" -> {
                if (altActive != active) {
                    display.setKeyCode(KeyEvent.KEYCODE_ALT_LEFT, active)
                }
                altActive = active
            }
            "Caps" -> capsActive = active
        }
        invalidate()
    }

    private fun releaseModifiers() {
        if (ctrlActive) {
            display.setKeyCode(KeyEvent.KEYCODE_CTRL_LEFT, false)
        }
        if (shiftActive) {
            display.setKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT, false)
        }
        if (altActive) {
            display.setKeyCode(KeyEvent.KEYCODE_ALT_LEFT, false)
        }
        ctrlActive = false
        shiftActive = false
        altActive = false
        capsActive = false
        invalidate()
    }

    private fun sendKey(label: String) {
        keyCodes[label]?.let {
            display.tapKeyCode(it)
            return
        }
        val keyCode = printableKeyCodes[label.lowercase(Locale.US)] ?: return
        val temporaryShift =
            capsActive && label.length == 1 && label[0].isLetter() &&
                !display.isKeyCodeDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        if (temporaryShift) {
            display.setKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT, true)
        }
        display.tapKeyCode(keyCode)
        if (temporaryShift) {
            display.setKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT, false)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fullKeyboardVisible = false
        floating = false
        keyboardOffsetX = 0
        keyboardOffsetY = 0
        translationX = 0f
        translationY = 0f
        release()
        applyLayout()
    }

    private data class KeyTouch(
        val pointerId: Int,
        val label: String,
        val downRawX: Float,
        val downRawY: Float,
        var lastRawX: Float,
        var lastRawY: Float,
        val startOffsetX: Int,
        val startOffsetY: Int,
        var dragging: Boolean = false,
        var repeat: Runnable? = null
    )
}
