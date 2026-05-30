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
import kotlinx.coroutines.*
import kotlin.math.*

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

private val keyCodes =
    "Tab·\t¦Esc·\u001b¦Enter·\r¦Back·\u007f¦ · ¦↑·\u001b[A¦↓·\u001b[B¦←·\u001b[D¦→·\u001b[C¦Home·\u001b[1~¦End·\u001b[4~¦Del·\u001b[3~¦F1·\u001bOP¦F2·\u001bOQ¦F3·\u001bOR¦F4·\u001bOS¦F5·\u001b[15~¦F6·\u001b[17~¦F7·\u001b[18~¦F8·\u001b[19~¦F9·\u001b[20~¦F10·\u001b[21~¦F11·\u001b[23~¦F12·\u001b[24~".split(
        '¦'
    ).associate { entry -> entry.split('·').let { parts -> parts[0] to parts[1] } }
private val symbolMap =
    "`~·1!·2@·3#·4$·5%·6^·7&·8*·9(·0)·-_·=+·[{·]}·\\|·;:·'\"·,<·.>·/?".split('·')
        .associate { pair -> pair[0].toString() to pair[1].toString() }

@Composable
fun TTYIME() {
    val session = LocalSession.current
    val rowHeight = 18.dp
    val isFull = IMEState.isFullKeyboardVisible
    val rows = if (!isFull) listOf(
        listOf("Esc", "F1", "F2", "F3", "·", "F4", "F5", "F6", "Del"),
        listOf("Shift", "F7", "F8", "F9", "↑", "F10", "F11", "F12", "Back"),
        listOf("Tab", "Ctrl", "Alt", "←", "↓", "→", "Home", "End", "Enter")
    ) else "Esc·F1·F2·F3·F4·F5·F6··F7·F8·F9·F10·F11·F12·Del¦`·1·2·3·4·5·6·7·8·9·0·-·=·Back¦Tab·Q·W·E·R·T·Y·U·I·O·P·[·]·\\¦Caps·A·S·D·F·G·H·J·K·L·;·'·Enter¦Shift·Z·X·C·V·B·N·M·,·.·↑·/¦Ctrl·Alt·Home· ·End·←·↓·→".split(
        '¦'
    ).map { rowStr -> rowStr.split('·') }
    val totalHeight = rowHeight * rows.size; BackHandler(isFull || IMEState.isFloating) {
        IMEState.isFloating = false; IMEState.isFullKeyboardVisible =
        false; IMEState.keyboardOffset = IntOffset.Zero
    }
    Box(modifier = if (IMEState.isFloating) Modifier
        .offset { IMEState.keyboardOffset }
        .size(360.dp, totalHeight) else Modifier
        .fillMaxWidth()
        .wrapContentHeight()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(0.7f))
                .height(totalHeight)
        ) {
            rows.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    row.forEach { label ->
                        KeyBase(
                            session,
                            label,
                            if (isFull) when (label) {
                                " " -> 3.9f; "Ctrl", "Alt", "Home", "End" -> 1.2f; else -> 1f
                            } else 1f,
                            label in listOf("Ctrl", "Shift", "Alt", "Caps"),
                            if (isFull) label == "" else label == "·"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyBase(
    session: com.termux.terminal.TerminalSession?,
    label: String,
    weight: Float,
    isModifier: Boolean = false,
    isControlKey: Boolean = false
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentView = LocalView.current
    val isModifierActive = when (label) {
        "Ctrl" -> IMEState.isCtrlActive; "Shift" -> IMEState.isShiftActive; "Alt" -> IMEState.isAltActive; "Caps" -> IMEState.isCapsActive; else -> false
    }
    val displayText = when {
        isControlKey -> ""; isModifier || label.length > 1 || (!label[0].isLetter() && !symbolMap.containsKey(
            label
        )) -> label; IMEState.isShiftActive -> symbolMap[label]
            ?: label.uppercase(); IMEState.isCapsActive && label[0].isLetter() -> label.uppercase(); else -> label
    }
    Box(modifier = Modifier
        .weight(weight)
        .fillMaxHeight()
        .pointerInput(label) {
            if (isControlKey) detectDragGestures(
                onDragStart = {
                isPressed = true; IMEState.isFloating = true; currentView.performHapticFeedback(
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
        .pointerInput(label) {
            detectTapGestures(onPress = {
                isPressed =
                    true; currentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); if (!isControlKey) {
                if (isModifier) {
                    val updateModifier: (Boolean) -> Unit = { active ->
                        when (label) {
                            "Ctrl" -> IMEState.isCtrlActive =
                                active; "Shift" -> IMEState.isShiftActive =
                            active; "Alt" -> IMEState.isAltActive =
                            active; "Caps" -> IMEState.isCapsActive = active
                        }
                    }
                    updateModifier(true); try {
                        awaitRelease()
                    } finally {
                        updateModifier(false); isPressed = false
                    }
                } else {
                    val repeatJob = scope.launch {
                        processKey(session, label); delay(270); while (true) {
                        processKey(session, label); delay(30)
                    }
                    }
                    try {
                        awaitRelease()
                    } finally {
                        repeatJob.cancel(); isPressed = false
                    }
                }
            } else {
                try {
                    awaitRelease()
                } finally {
                    isPressed = false
                }
            }
            }, onTap = {
                if (isControlKey) {
                    if (IMEState.isFloating) {
                        IMEState.isFloating = false; IMEState.keyboardOffset = IntOffset.Zero
                    } else {
                        IMEState.isFullKeyboardVisible = !IMEState.isFullKeyboardVisible
                    }
                }
            })
        }
        .background(Color.Transparent), contentAlignment = Alignment.Center) {
        Text(
            displayText,
            color = if (isPressed || isModifierActive) Color.Gray else Color.White,
            fontSize = 9.sp,
            softWrap = false
        )
    }
}

private fun processKey(session: com.termux.terminal.TerminalSession?, label: String) {
    keyCodes[label]?.let { code -> sendToTTY(session, if (IMEState.isAltActive && label != "Alt") "\u001b$code" else code) }
        ?: run {
            val isUpperCase =
                IMEState.isShiftActive || (IMEState.isCapsActive && label.length == 1 && label[0].isLetter())
            var charText = if (IMEState.isShiftActive) (symbolMap[label]
                ?: label.uppercase()) else if (isUpperCase) label.uppercase() else label.lowercase(); if (IMEState.isCtrlActive && charText.length == 1) charText.uppercase()[0].let { char ->
            if (char in '@'..'_') charText = (char.code - '@'.code).toChar().toString()
        }
            sendToTTY(session, if (IMEState.isAltActive) "\u001b$charText" else charText)
        }
}

private fun sendToTTY(session: com.termux.terminal.TerminalSession?, data: String) = session?.write(data)
