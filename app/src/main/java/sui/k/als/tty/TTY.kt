package sui.k.als.tty

import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.*
import com.termux.terminal.*
import com.termux.view.*
import java.util.concurrent.atomic.*
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
): TTYInstance {
    val session = TerminalSession(TTYEnv, 9216, sessionClient)
    installTTYMessageCoalescing(session)
    val view = TerminalView(context, null).apply {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isFocusable = true
        isFocusableInTouchMode = true
        setTextSize(12)
        setTypeface(
            try {
                Typeface.createFromAsset(context.assets, "GoogleSansCode.ttf")
            } catch (_: Exception) {
                Typeface.MONOSPACE
            }
        )
        setBackgroundColor(android.graphics.Color.BLACK)
        setTerminalViewClient(viewClient)
        attachSession(session)
    }
    sessionClient.bindView(view)
    viewClient.bindView(view)
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

fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"

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
    private var size = 12f
    fun bindView(targetView: TerminalView) {
        view = targetView
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent, session: TerminalSession) = false
    override fun onKeyUp(keyCode: Int, event: KeyEvent) = false
    override fun onSingleTapUp(event: MotionEvent) {}
    override fun onLongPress(event: MotionEvent) = false
    override fun onScale(factor: Float): Float {
        val next = (size * factor).coerceIn(12f, 270f)
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