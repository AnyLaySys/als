package sui.k.als.tty

import android.view.*
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import com.termux.terminal.*
import kotlinx.coroutines.*
import kotlin.math.*
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

private val compactKeyboardRows = listOf(
    listOf("Esc", "F1", "F2", "F3", "·", "F4", "F5", "F6", "Del"),
    listOf("Shift", "F7", "F8", "F9", "↑", "F10", "F11", "F12", "Back"),
    listOf("Tab", "Ctrl", "Alt", "←", "↓", "→", "Home", "End", "Enter")
)
private val fullKeyboardRows =
    "Esc·F1·F2·F3·F4·F5·F6··F7·F8·F9·F10·F11·F12·Del¦`·1·2·3·4·5·6·7·8·9·0·-·=·Back¦Tab·Q·W·E·R·T·Y·U·I·O·P·[·]·\\¦Caps·A·S·D·F·G·H·J·K·L·;·'·Enter¦Shift·Z·X·C·V·B·N·M·,·.·↑·/¦Ctrl·Alt·Home· ·End·←·↓·→".split(
        '¦'
    ).map { it.split('·') }
private val keyboardCodes =
    "Tab·\t¦Esc·\u001b¦Enter·\r¦Back·\u007f¦ · ¦↑·\u001b[A¦↓·\u001b[B¦←·\u001b[D¦→·\u001b[C¦Home·\u001b[1~¦End·\u001b[4~¦Del·\u001b[3~¦F1·\u001bOP¦F2·\u001bOQ¦F3·\u001bOR¦F4·\u001bOS¦F5·\u001b[15~¦F6·\u001b[17~¦F7·\u001b[18~¦F8·\u001b[19~¦F9·\u001b[20~¦F10·\u001b[21~¦F11·\u001b[23~¦F12·\u001b[24~".split(
        '¦'
    ).associate { it.substringBefore('·') to it.substringAfter('·') }
private val keyboardSymbols =
    "`~·1!·2@·3#·4$·5%·6^·7&·8*·9(·0)·-_·=+·[{·]}·\\|·;:·'\"·,<·.>·/?".split('·')
        .associate { it[0].toString() to it[1].toString() }

private fun isModifier(label: String) = label in listOf("Ctrl", "Shift", "Alt", "Caps")
private fun isModifierActive(label: String) = when (label) {
    "Ctrl" -> IMEState.isCtrlActive; "Shift" -> IMEState.isShiftActive; "Alt" -> IMEState.isAltActive; "Caps" -> IMEState.isCapsActive; else -> false
}

private fun setModifierActive(label: String, active: Boolean) {
    when (label) {
        "Ctrl" -> IMEState.isCtrlActive = active; "Shift" -> IMEState.isShiftActive =
        active; "Alt" -> IMEState.isAltActive = active; "Caps" -> IMEState.isCapsActive = active
    }
}

@Composable
fun TTYIME() {
    val session = LocalSession.current
    val isFull = IMEState.isFullKeyboardVisible
    val rows = if (isFull) fullKeyboardRows else compactKeyboardRows
    BackHandler(isFull || IMEState.isFloating) {
        IMEState.isFloating = false; IMEState.isFullKeyboardVisible =
        false; IMEState.keyboardOffset = IntOffset.Zero
    }
    Box(Modifier.run {
        if (IMEState.isFloating) offset { IMEState.keyboardOffset }.size(
            360.dp, 18.dp * rows.size
        ) else fillMaxWidth().wrapContentHeight()
    }) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(18.dp * rows.size)
                .background(Color.Black.copy(0.7f))
        ) {
            rows.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { row.forEach { label -> KeyboardKey(session, label, isFull) } }
            }
        }
    }
}

@Composable
private fun RowScope.KeyboardKey(session: TerminalSession?, label: String, isFull: Boolean) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val isCtrlKey = if (isFull) label.isEmpty() else label == "·"
    val isMod = isModifier(label)
    val keyWeight = if (isFull) when (label) {
        " " -> 4.2f; "Ctrl", "Alt", "Home", "End" -> 1.2f; else -> 1f
    } else 1f
    Box(modifier = Modifier
        .weight(keyWeight)
        .fillMaxHeight()
        .pointerInput(label, isCtrlKey) {
            if (isCtrlKey) detectDragGestures(
                onDragStart = {
                isPressed = true; IMEState.isFloating = true; view.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS
            )
            },
                onDragEnd = { isPressed = false },
                onDragCancel = { isPressed = false }) { change, dragAmount ->
                if (IMEState.isFloating) {
                    change.consume(); IMEState.keyboardOffset += IntOffset(
                        dragAmount.x.roundToInt(), dragAmount.y.roundToInt()
                    )
                }
            }
        }
        .pointerInput(label, isCtrlKey, isMod, session) {
            detectTapGestures(onPress = {
                isPressed = true
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val job = if (!isCtrlKey && !isMod) scope.launch {
                    sendKeyboardKey(
                        session, label
                    ); delay(270.milliseconds); while (isActive) {
                    sendKeyboardKey(session, label); delay(30.milliseconds)
                }
                } else null
                if (isMod) setModifierActive(label, true)
                try {
                    awaitRelease()
                } finally {
                    job?.cancel(); if (isMod) setModifierActive(label, false); isPressed = false
                }
            }, onTap = {
                if (isCtrlKey) {
                    if (IMEState.isFloating) {
                        IMEState.isFloating = false; IMEState.keyboardOffset = IntOffset.Zero
                    } else IMEState.isFullKeyboardVisible = !IMEState.isFullKeyboardVisible
                }
            })
        }
        .background(Color.Transparent), contentAlignment = Alignment.Center) {
        val display = when {
            isCtrlKey || label.isEmpty() -> ""
            isMod || label.length > 1 || (!label.first().isLetter() && !keyboardSymbols.containsKey(
                label
            )) -> label

            IMEState.isShiftActive -> keyboardSymbols[label] ?: label.uppercase()
            IMEState.isCapsActive && label.first().isLetter() -> label.uppercase()
            else -> label
        }
        Text(
            text = display,
            color = if (isPressed || isModifierActive(label)) Color.Gray else Color.White,
            fontSize = 9.sp,
            softWrap = false
        )
    }
}

private fun sendKeyboardKey(session: TerminalSession?, label: String) {
    keyboardCodes[label]?.let { code -> session?.write(if (IMEState.isAltActive && label != "Alt") "\u001b$code" else code); return }
    val isUpperCase =
        IMEState.isShiftActive || (IMEState.isCapsActive && label.length == 1 && label.first()
            .isLetter())
    var text = when {
        IMEState.isShiftActive -> keyboardSymbols[label]
            ?: label.uppercase(); isUpperCase -> label.uppercase(); else -> label.lowercase()
    }
    if (IMEState.isCtrlActive && text.length == 1) text.uppercase()[0].let { char ->
        if (char in '@'..'_') text = (char.code - '@'.code).toChar().toString()
    }
    session?.write(if (IMEState.isAltActive) "\u001b$text" else text)
}