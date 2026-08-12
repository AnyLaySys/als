package sui.k.als.tty

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

object IMEState {
    var isCtrlActive by mutableStateOf(false)
    var isShiftActive by mutableStateOf(false)
    var isAltActive by mutableStateOf(false)
    var isCapsActive by mutableStateOf(false)
    var isFullKeyboardVisible by mutableStateOf(false)
    var isFloating by mutableStateOf(false)
    var keyboardOffset by mutableStateOf(IntOffset.Zero)
    fun consumeCtrl() = isCtrlActive
    fun consumeShift() = isShiftActive
    fun consumeAlt() = isAltActive
}

private val compactRows = listOf(
    listOf("Esc", "F1", "F2", "F3", "·", "F4", "F5", "F6", "Del"),
    listOf("Shift", "F7", "F8", "F9", "↑", "F10", "F11", "F12", "Back"),
    listOf("Tab", "Ctrl", "Alt", "←", "↓", "→", "Home", "End", "Enter")
)
private val fullRows =
    "Esc·F1·F2·F3·F4·F5·F6··F7·F8·F9·F10·F11·F12·Del¦`·1·2·3·4·5·6·7·8·9·0·-·=·Back¦Tab·Q·W·E·R·T·Y·U·I·O·P·[·]·\\¦Caps·A·S·D·F·G·H·J·K·L·;·'·Enter¦Shift·Z·X·C·V·B·N·M·,·.·↑·/¦Ctrl·Alt·Home· ·End·←·↓·→".split(
        '¦'
    ).map { it.split('·') }
private val codes =
    "Tab·\t¦Esc·\u001b¦Enter·\r¦Back·\u007f¦ · ¦↑·\u001b[A¦↓·\u001b[B¦←·\u001b[D¦→·\u001b[C¦Home·\u001b[1~¦End·\u001b[4~¦Del·\u001b[3~¦F1·\u001bOP¦F2·\u001bOQ¦F3·\u001bOR¦F4·\u001bOS¦F5·\u001b[15~¦F6·\u001b[17~¦F7·\u001b[18~¦F8·\u001b[19~¦F9·\u001b[20~¦F10·\u001b[21~¦F11·\u001b[23~¦F12·\u001b[24~".split(
        '¦'
    ).associate { it.substringBefore('·') to it.substringAfter('·') }
private val symbols = "`~·1!·2@·3#·4$·5%·6^·7&·8*·9(·0)·-_·=+·[{·]}·\\|·;:·'\"·,<·.>·/?".split('·')
    .associate { it[0].toString() to it[1].toString() }
private val mods = setOf("Ctrl", "Shift", "Alt", "Caps")
private val wide = setOf("Ctrl", "Alt", "Home", "End")

private fun setMod(label: String, active: Boolean) = when (label) {
    "Ctrl" -> IMEState.isCtrlActive = active
    "Shift" -> IMEState.isShiftActive = active
    "Alt" -> IMEState.isAltActive = active
    "Caps" -> IMEState.isCapsActive = active
    else -> Unit
}

private fun resetIme() {
    IMEState.isCtrlActive = false
    IMEState.isShiftActive = false
    IMEState.isAltActive = false
    IMEState.isCapsActive = false
    IMEState.isFullKeyboardVisible = false
    IMEState.isFloating = false
    IMEState.keyboardOffset = IntOffset.Zero
}

@Composable
fun TTYIME() {
    val session = LocalSession.current
    val orientation = LocalConfiguration.current.orientation
    val full = IMEState.isFullKeyboardVisible
    val rows = if (full) fullRows else compactRows
    val portrait = orientation == Configuration.ORIENTATION_PORTRAIT
    val h = if (full) if (portrait) 0.36f else 0.54f else 0f
    val w = if (full && IMEState.isFloating && !portrait) 0.36f else 1f
    LaunchedEffect(orientation) { resetIme() }
    BackHandler(full || IMEState.isFloating) { resetIme() }
    Box(Modifier.run {
        when {
            IMEState.isFloating && full -> offset { IMEState.keyboardOffset }
                .fillMaxWidth(w)
                .fillMaxHeight(h)

            IMEState.isFloating -> offset { IMEState.keyboardOffset }.size(
                324.dp, 24.dp * rows.size
            )

            full -> fillMaxWidth().fillMaxHeight(h)
            else -> fillMaxWidth().height(24.dp * rows.size)
        }
    }) {
        Column(Modifier.fillMaxSize()) {
            rows.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    row.forEach { Key(session, it, full) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(session: TerminalSession?, label: String, full: Boolean) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val ctrl = if (full) label.isEmpty() else label == "·"
    val mod = label in mods
    Box(
        Modifier
            .weight(if (full && label == " ") 4.2f else if (full && label in wide) 1.2f else 1f)
            .fillMaxHeight()
            .pointerInput(label, ctrl) {
                if (ctrl) detectDragGestures(onDragStart = {
                    IMEState.isFloating = true
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }, onDragEnd = {}, onDragCancel = {}) { change, drag ->
                    if (IMEState.isFloating) {
                        change.consume()
                        IMEState.keyboardOffset += IntOffset(
                            drag.x.roundToInt(), drag.y.roundToInt()
                        )
                    }
                }
            }
            .pointerInput(label, ctrl, mod, session) {
                detectTapGestures(onPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val job = if (!ctrl && !mod) {
                        send(session, label)
                        scope.launch {
                            delay(270.milliseconds)
                            while (isActive) {
                                send(session, label)
                                delay(30.milliseconds)
                            }
                        }
                    } else null
                    if (mod) setMod(label, true)
                    try {
                        awaitRelease()
                    } finally {
                        job?.cancel()
                        if (mod) setMod(label, false)
                    }
                }, onTap = {
                    if (ctrl) {
                        if (IMEState.isFloating) {
                            IMEState.isFloating = false
                            IMEState.keyboardOffset = IntOffset.Zero
                        } else IMEState.isFullKeyboardVisible = !IMEState.isFullKeyboardVisible
                    }
                })
            }, contentAlignment = Alignment.Center
    ) {
        val text = when {
            ctrl || label.isEmpty() -> ""
            mod || label.length > 1 || (!label.first()
                .isLetter() && !symbols.containsKey(label)) -> label

            IMEState.isShiftActive -> symbols[label] ?: label.uppercase()
            IMEState.isCapsActive && label.first().isLetter() -> label.uppercase()
            else -> label
        }
        Text(text = text, color = Color.Gray, fontSize = 12.sp, softWrap = false)
    }
}

private fun send(session: TerminalSession?, label: String) {
    var text = codes[label] ?: run {
        var t = if (IMEState.isShiftActive) symbols[label]
            ?: label.uppercase() else if (IMEState.isCapsActive && label.length == 1 && label.first()
                .isLetter()
        ) label.uppercase() else label.lowercase()
        if (IMEState.isCtrlActive && t.length == 1) t.uppercase()[0].let {
            if (it in '@'..'_') t = (it.code - '@'.code).toChar().toString()
        }
        t
    }
    if (IMEState.isAltActive) text = "\u001b$text"
    session?.write(text)
}
