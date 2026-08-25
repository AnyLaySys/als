package sui.k.als.vm

import android.app.*
import android.content.*
import android.content.ClipboardManager
import android.content.res.*
import android.graphics.*
import android.view.*
import android.view.inputmethod.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.*
import androidx.core.view.*
import sui.k.als.R

class VMView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private var bufferWidth = 1
    private var bufferHeight = 1
    private var refreshRate = 0f
    private var touchDown = false
    private var pointerX = Float.NaN
    private var pointerY = Float.NaN
    private var pointerButtons = 0
    private val pressedKeys = HashSet<Int>()

    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.OPAQUE)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    fun configure(width: Int, height: Int) {
        if (bufferWidth == width && bufferHeight == height) {
            return
        }
        bufferWidth = width
        bufferHeight = height
        holder.setFixedSize(bufferWidth, bufferHeight)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        refreshRate = preferredRefreshRate()
        applyFrameRate(holder.surface)
        VMRuntime.attach(holder.surface, refreshRate)
        requestFocus()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        refreshRate = preferredRefreshRate()
        applyFrameRate(holder.surface)
        VMRuntime.attach(holder.surface, refreshRate)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseKeys()
        VMRuntime.detach(refreshRate)
    }

    private fun preferredRefreshRate(): Float {
        val target = display
        val current = target.mode
        return target.supportedModes.asSequence().filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }.maxOfOrNull { it.refreshRate } ?: current.refreshRate
    }

    private fun applyFrameRate(surface: Surface) {
        if (refreshRate > 0f && surface.isValid) {
            surface.setFrameRate(
                refreshRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS
            )
        }
    }

    private fun pointer(event: MotionEvent, buttons: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        setPointer(
            event.x * bufferWidth / width, event.y * bufferHeight / height, buttons
        )
    }

    private fun setPointer(x: Float, y: Float, buttons: Int) {
        pointerX = x.coerceIn(0f, bufferWidth.toFloat())
        pointerY = y.coerceIn(0f, bufferHeight.toFloat())
        pointerButtons = buttons
        VMRuntime.pointer(
            pointerX, pointerY, buttons
        )
    }

    internal fun movePointer(dx: Float, dy: Float) {
        val x = pointerX.takeUnless { it.isNaN() } ?: (bufferWidth / 2f)
        val y = pointerY.takeUnless { it.isNaN() } ?: (bufferHeight / 2f)
        val scaleX = bufferWidth.toFloat() / width.coerceAtLeast(1)
        val scaleY = bufferHeight.toFloat() / height.coerceAtLeast(1)
        setPointer(x + dx * scaleX, y + dy * scaleY, pointerButtons)
    }

    internal fun setPointerButton(button: Int, down: Boolean) {
        val buttons = if (down) {
            pointerButtons or button
        } else {
            pointerButtons and button.inv()
        }
        val x = pointerX.takeUnless { it.isNaN() } ?: (bufferWidth / 2f)
        val y = pointerY.takeUnless { it.isNaN() } ?: (bufferHeight / 2f)
        setPointer(x, y, buttons)
    }

    internal fun clickPointer(button: Int) {
        setPointerButton(button, true)
        setPointerButton(button, false)
    }

    internal fun scrollPointer(x: Float, y: Float) {
        VMRuntime.scroll(x, y)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDown = true
                requestFocus()
                pointer(event, 1)
            }

            MotionEvent.ACTION_MOVE -> pointer(event, if (touchDown) 1 else 0)
            MotionEvent.ACTION_UP -> {
                touchDown = false
                pointer(event, 0)
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                touchDown = false
                pointer(event, 0)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                pointer(event, event.buttonState)
                return true
            }

            MotionEvent.ACTION_SCROLL -> {
                VMRuntime.scroll(
                    event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                    -event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                )
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon =
        PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE
        return BaseInputConnection(this, false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val scanCode = event.scanCode.takeIf { it > 0 } ?: linuxScanCode(keyCode)
        if (!setScanCode(scanCode, true)) {
            return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val scanCode = event.scanCode.takeIf { it > 0 } ?: linuxScanCode(keyCode)
        if (!setScanCode(scanCode, false)) {
            return super.onKeyUp(keyCode, event)
        }
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            releaseKeys()
        }
    }

    internal fun setKeyCode(keyCode: Int, down: Boolean): Boolean =
        setScanCode(linuxScanCode(keyCode), down)

    internal fun tapKeyCode(keyCode: Int) {
        if (setKeyCode(keyCode, true)) {
            setKeyCode(keyCode, false)
        }
    }

    internal fun isKeyCodeDown(keyCode: Int): Boolean = linuxScanCode(keyCode) in pressedKeys

    private fun setScanCode(scanCode: Int, down: Boolean): Boolean {
        if (scanCode <= 0) {
            return false
        }
        if (down) {
            if (pressedKeys.add(scanCode)) {
                VMRuntime.key(scanCode, true)
            }
        } else if (pressedKeys.remove(scanCode)) {
            VMRuntime.key(scanCode, false)
        }
        return true
    }

    internal fun releaseKeys() {
        pressedKeys.forEach { VMRuntime.key(it, false) }
        pressedKeys.clear()
    }

    private fun linuxScanCode(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_ESCAPE -> 1
        KeyEvent.KEYCODE_1 -> 2
        KeyEvent.KEYCODE_2 -> 3
        KeyEvent.KEYCODE_3 -> 4
        KeyEvent.KEYCODE_4 -> 5
        KeyEvent.KEYCODE_5 -> 6
        KeyEvent.KEYCODE_6 -> 7
        KeyEvent.KEYCODE_7 -> 8
        KeyEvent.KEYCODE_8 -> 9
        KeyEvent.KEYCODE_9 -> 10
        KeyEvent.KEYCODE_0 -> 11
        KeyEvent.KEYCODE_MINUS -> 12
        KeyEvent.KEYCODE_EQUALS -> 13
        KeyEvent.KEYCODE_DEL -> 14
        KeyEvent.KEYCODE_TAB -> 15
        KeyEvent.KEYCODE_Q -> 16
        KeyEvent.KEYCODE_W -> 17
        KeyEvent.KEYCODE_E -> 18
        KeyEvent.KEYCODE_R -> 19
        KeyEvent.KEYCODE_T -> 20
        KeyEvent.KEYCODE_Y -> 21
        KeyEvent.KEYCODE_U -> 22
        KeyEvent.KEYCODE_I -> 23
        KeyEvent.KEYCODE_O -> 24
        KeyEvent.KEYCODE_P -> 25
        KeyEvent.KEYCODE_LEFT_BRACKET -> 26
        KeyEvent.KEYCODE_RIGHT_BRACKET -> 27
        KeyEvent.KEYCODE_ENTER -> 28
        KeyEvent.KEYCODE_CTRL_LEFT -> 29
        KeyEvent.KEYCODE_A -> 30
        KeyEvent.KEYCODE_S -> 31
        KeyEvent.KEYCODE_D -> 32
        KeyEvent.KEYCODE_F -> 33
        KeyEvent.KEYCODE_G -> 34
        KeyEvent.KEYCODE_H -> 35
        KeyEvent.KEYCODE_J -> 36
        KeyEvent.KEYCODE_K -> 37
        KeyEvent.KEYCODE_L -> 38
        KeyEvent.KEYCODE_SEMICOLON -> 39
        KeyEvent.KEYCODE_APOSTROPHE -> 40
        KeyEvent.KEYCODE_GRAVE -> 41
        KeyEvent.KEYCODE_SHIFT_LEFT -> 42
        KeyEvent.KEYCODE_BACKSLASH -> 43
        KeyEvent.KEYCODE_Z -> 44
        KeyEvent.KEYCODE_X -> 45
        KeyEvent.KEYCODE_C -> 46
        KeyEvent.KEYCODE_V -> 47
        KeyEvent.KEYCODE_B -> 48
        KeyEvent.KEYCODE_N -> 49
        KeyEvent.KEYCODE_M -> 50
        KeyEvent.KEYCODE_COMMA -> 51
        KeyEvent.KEYCODE_PERIOD -> 52
        KeyEvent.KEYCODE_SLASH -> 53
        KeyEvent.KEYCODE_SHIFT_RIGHT -> 54
        KeyEvent.KEYCODE_ALT_LEFT -> 56
        KeyEvent.KEYCODE_SPACE -> 57
        KeyEvent.KEYCODE_CAPS_LOCK -> 58
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F10 -> 59 + keyCode - KeyEvent.KEYCODE_F1
        KeyEvent.KEYCODE_F11 -> 87
        KeyEvent.KEYCODE_F12 -> 88
        KeyEvent.KEYCODE_CTRL_RIGHT -> 97
        KeyEvent.KEYCODE_ALT_RIGHT -> 100
        KeyEvent.KEYCODE_MOVE_HOME -> 102
        KeyEvent.KEYCODE_DPAD_UP -> 103
        KeyEvent.KEYCODE_PAGE_UP -> 104
        KeyEvent.KEYCODE_DPAD_LEFT -> 105
        KeyEvent.KEYCODE_DPAD_RIGHT -> 106
        KeyEvent.KEYCODE_MOVE_END -> 107
        KeyEvent.KEYCODE_DPAD_DOWN -> 108
        KeyEvent.KEYCODE_PAGE_DOWN -> 109
        KeyEvent.KEYCODE_INSERT -> 110
        KeyEvent.KEYCODE_FORWARD_DEL -> 111
        KeyEvent.KEYCODE_META_LEFT -> 125
        KeyEvent.KEYCODE_META_RIGHT -> 126
        else -> 0
    }
}

@Composable
internal fun VMScreen(launch: VMLaunch, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val shortSide = minOf(launch.width, launch.height)
    val longSide = maxOf(launch.width, launch.height)
    val displayWidth = if (portrait) shortSide else longSide
    val displayHeight = if (portrait) longSide else shortSide
    DisposableEffect(activity) {
        val window = activity?.window
        val previousCutoutMode = window?.attributes?.layoutInDisplayCutoutMode
        val hideSystemBars = {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener {
            if (it) {
                hideSystemBars()
            }
        }
        if (activity != null) {
            hideSystemBars()
            window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener(focusListener)
        }
        onDispose {
            VMRuntime.detach()
            window?.decorView?.viewTreeObserver?.takeIf { it.isAlive }
                ?.removeOnWindowFocusChangeListener(focusListener)
            if (window != null) {
                WindowInsetsControllerCompat(
                    window, window.decorView
                ).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
                if (previousCutoutMode != null) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = previousCutoutMode
                    }
                }
            }
        }
    }
    if (VMRuntime.state == VMRunState.Failed) {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(27.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(27.dp)) {
                    Text(
                        stringResource(R.string.qemu_start_failed),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        VMRuntime.failureMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable {
                                val message = VMRuntime.failureMessage.orEmpty()
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("QEMU", message))
                            })
                }
            }
        }
    } else {
        AndroidView(factory = {
            VMDisplay(it).apply {
                configure(displayWidth, displayHeight, launch.hideKeyboard, launch.softKeyboard)
            }
        }, modifier = modifier.fillMaxSize(), update = {
            it.configure(displayWidth, displayHeight, launch.hideKeyboard, launch.softKeyboard)
        })
    }
}
