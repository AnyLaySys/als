package sui.k.als.agl

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import sui.k.als.R

class AglView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
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
        AglRuntime.attach(holder.surface, refreshRate)
        requestFocus()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        refreshRate = preferredRefreshRate()
        applyFrameRate(holder.surface)
        AglRuntime.attach(holder.surface, refreshRate)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseKeys()
        AglRuntime.detach(refreshRate)
    }

    private fun preferredRefreshRate(): Float {
        val target = display
        val current = target.mode
        return target.supportedModes.asSequence()
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            .maxOfOrNull { it.refreshRate }
            ?: current.refreshRate
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
            event.x * bufferWidth / width,
            event.y * bufferHeight / height,
            buttons
        )
    }

    private fun setPointer(x: Float, y: Float, buttons: Int) {
        pointerX = x.coerceIn(0f, bufferWidth.toFloat())
        pointerY = y.coerceIn(0f, bufferHeight.toFloat())
        pointerButtons = buttons
        AglRuntime.pointer(
            pointerX,
            pointerY,
            buttons
        )
    }

    internal fun movePointer(dx: Float, dy: Float) {
        val x = pointerX.takeUnless { it.isNaN() } ?: bufferWidth / 2f
        val y = pointerY.takeUnless { it.isNaN() } ?: bufferHeight / 2f
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
        val x = pointerX.takeUnless { it.isNaN() } ?: bufferWidth / 2f
        val y = pointerY.takeUnless { it.isNaN() } ?: bufferHeight / 2f
        setPointer(x, y, buttons)
    }

    internal fun clickPointer(button: Int) {
        setPointerButton(button, true)
        setPointerButton(button, false)
    }

    internal fun scrollPointer(x: Float, y: Float) {
        AglRuntime.scroll(x, y)
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

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                pointer(event, event.buttonState)
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                AglRuntime.scroll(
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

    internal fun isKeyCodeDown(keyCode: Int): Boolean =
        linuxScanCode(keyCode) in pressedKeys

    private fun setScanCode(scanCode: Int, down: Boolean): Boolean {
        if (scanCode <= 0) {
            return false
        }
        if (down) {
            if (pressedKeys.add(scanCode)) {
                AglRuntime.key(scanCode, true)
            }
        } else if (pressedKeys.remove(scanCode)) {
            AglRuntime.key(scanCode, false)
        }
        return true
    }

    internal fun releaseKeys() {
        pressedKeys.forEach { AglRuntime.key(it, false) }
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
internal fun AglScreen(launch: AglLaunch, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? Activity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            AglRuntime.detach()
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }
    if (!landscape) {
        Box(modifier.fillMaxSize().background(Color.Black))
    } else if (AglRuntime.state == AglRunState.Failed) {
        Box(
            modifier.fillMaxSize().background(Color.Black).padding(24.dp),
            Alignment.Center
        ) {
            Text(
                "${stringResource(R.string.qemu_start_failed)}\n${AglRuntime.failureMessage.orEmpty()}",
                color = Color.White
            )
        }
    } else {
        AndroidView(
            factory = { AglDisplayView(it).apply { configure(launch.width, launch.height) } },
            modifier = modifier.fillMaxSize(),
            update = { it.configure(launch.width, launch.height) }
        )
    }
}
