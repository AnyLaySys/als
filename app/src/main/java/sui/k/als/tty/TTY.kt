package sui.k.als.tty

import android.content.ClipData
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import sui.k.als.CODE_FONT_ASSET
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import android.content.ClipboardManager as AndroidClipboardManager

data class TTYInstance(val session: TerminalSession, val view: TerminalView)

val LocalSession = staticCompositionLocalOf<TerminalSession?> { null }

@Composable
fun TTYScreen(instance: TTYInstance, content: @Composable () -> Unit = {}) {
    LaunchedEffect(instance) {
        instance.view.requestFocus()
        instance.view.onScreenUpdated()
    }
    CompositionLocalProvider(LocalSession provides instance.session) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { instance.view },
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                update = { view -> view.onScreenUpdated() })
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
            ) {
                content()
            }
        }
    }
}

fun createTTYInstance(
    context: Context, sessionClient: TTYSessionStub, viewClient: TTYViewStub
): TTYInstance = createTTYInstance(
    context,
    TerminalSession(TTYEnv, 9216, sessionClient),
    sessionClient,
    viewClient,
    false
)

fun createQemuTTYInstance(
    context: Context, sessionClient: TTYSessionStub, viewClient: TTYViewStub
): TTYInstance {
    val instance = createTTYInstance(
        context,
        TerminalSession(TTYEnv, 9216, sessionClient),
        sessionClient,
        viewClient,
        true
    )
    cmd(instance.session, "exec /system/bin/sleep 2147483647")
    return instance
}

private fun createTTYInstance(
    context: Context,
    session: TerminalSession,
    sessionClient: TTYSessionStub,
    viewClient: TTYViewStub,
    initialize: Boolean
): TTYInstance {
    installTTYMessageCoalescing(session)
    val textSize = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        9f,
        context.resources.displayMetrics
    ).roundToInt()
    val minimumTextSize = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        3f,
        context.resources.displayMetrics
    ).roundToInt()
    val view = TerminalView(context, null).apply {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isFocusable = true
        isFocusableInTouchMode = true
        setTextSize(textSize)
        setTypeface(
            try {
                Typeface.createFromAsset(context.assets, CODE_FONT_ASSET)
            } catch (_: Exception) {
                Typeface.MONOSPACE
            }
        )
        setBackgroundColor(android.graphics.Color.BLACK)
        setTerminalViewClient(viewClient)
        attachSession(session)
    }
    sessionClient.bindView(view)
    viewClient.bindView(view, textSize, minimumTextSize)
    if (initialize) {
        session.updateSize(120, 36, 0, 0)
    }
    return TTYInstance(session, view)
}

open class TTYSessionStub : TerminalSessionClient {
    private var view: TerminalView? = null
    fun bindView(targetView: TerminalView) {
        view = targetView
    }

    override fun onTextChanged(session: TerminalSession) {
        view?.onScreenUpdated()
    }

    override fun onTitleChanged(session: TerminalSession) {}
    override fun onSessionFinished(session: TerminalSession) {}
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        (view?.context?.getSystemService(Context.CLIPBOARD_SERVICE) as? AndroidClipboardManager)?.setPrimaryClip(
            ClipData.newPlainText("T", text)
        )
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val context = view?.context ?: return
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
        manager.primaryClip?.getItemAt(0)?.let { item ->
            session?.write(item.coerceToText(context).toString())
        }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(visible: Boolean) {
        view?.onScreenUpdated()
    }

    override fun getTerminalCursorStyle() = 2
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun logError(tag: String?, msg: String?) {}
    override fun logWarn(tag: String?, msg: String?) {}
    override fun logInfo(tag: String?, msg: String?) {}
    override fun logDebug(tag: String?, msg: String?) {}
    override fun logVerbose(tag: String?, msg: String?) {}
    override fun logStackTraceWithMessage(tag: String?, msg: String?, error: Exception?) {}
    override fun logStackTrace(tag: String?, error: Exception?) {}
}

fun cmd(session: TerminalSession, command: String) {
    session.write("$command\n")
}

private fun installTTYMessageCoalescing(session: TerminalSession) {
    val field = TerminalSession::class.java.getDeclaredField("mMainThreadHandler")
    field.isAccessible = true
    val original = field.get(session) as Handler
    field.set(session, TTYCoalescingHandler(original))
}

private class TTYCoalescingHandler(private val original: Handler) :
    Handler(Looper.getMainLooper()) {
    private val pending = AtomicBoolean(false)
    private val dropped = AtomicBoolean(false)
    override fun sendMessageAtTime(msg: Message, uptimeMillis: Long): Boolean {
        if (msg.what != 1) return super.sendMessageAtTime(msg, uptimeMillis)
        if (!pending.compareAndSet(false, true)) {
            dropped.set(true)
            return true
        }
        return super.sendMessageAtTime(msg, uptimeMillis)
    }

    override fun handleMessage(msg: Message) {
        original.handleMessage(msg)
        if (msg.what == 1) {
            pending.set(false)
            if (dropped.getAndSet(false)) sendMessage(obtainMessage(1))
        }
    }
}

open class TTYViewStub : TerminalViewClient {
    private var view: TerminalView? = null
    private var size = 9f
    private var minimumSize = 3f
    fun bindView(targetView: TerminalView, textSize: Int, minimumTextSize: Int) {
        view = targetView
        size = textSize.toFloat()
        minimumSize = minimumTextSize.toFloat()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent, session: TerminalSession) = false
    override fun onKeyUp(keyCode: Int, event: KeyEvent) = false
    override fun onSingleTapUp(event: MotionEvent) {}
    override fun onLongPress(event: MotionEvent) = false
    override fun onScale(factor: Float): Float {
        val next = (size * factor).coerceIn(minimumSize, 270f)
        if (next != size) {
            size = next
            view?.post { view?.setTextSize(size.toInt()) }
        }
        return 1f
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false
    override fun readControlKey() = IMEState.consumeCtrl()
    override fun readAltKey() = IMEState.consumeAlt()
    override fun readShiftKey() = IMEState.consumeShift()
    override fun readFnKey() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(enabled: Boolean) {}
    override fun onEmulatorSet() {}
    override fun logError(tag: String?, msg: String?) {}
    override fun logWarn(tag: String?, msg: String?) {}
    override fun logInfo(tag: String?, msg: String?) {}
    override fun logDebug(tag: String?, msg: String?) {}
    override fun logVerbose(tag: String?, msg: String?) {}
    override fun logStackTraceWithMessage(tag: String?, msg: String?, error: Exception?) {}
    override fun logStackTrace(tag: String?, error: Exception?) {}
}
