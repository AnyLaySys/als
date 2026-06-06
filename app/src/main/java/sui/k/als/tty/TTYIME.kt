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
    "Esc·F1·F2·F3·F4·F5·F6··F7·F8·F9·F10·F11·F12·Del¦`·1·2·3·4·5·6·7·8·9·0·-·=·Back¦Tab·Q·W·E·R·T·Y·U·I·O·P·[·]·\\¦Caps·A·S·D·F·G·H·J·K·L·;·'·Enter¦Shift·Z·X·C·V·B·N·M·,·.·↑·/¦Ctrl·Alt·Home· ·End·←·↓·→"
        .split('¦')
        .map { row -> row.split('·') }

@Composable
fun TTYIME() {
    val session = LocalSession.current
    val isFullKeyboardVisible = IMEState.isFullKeyboardVisible
    val rows = if (isFullKeyboardVisible) fullKeyboardRows else compactKeyboardRows
    val totalHeight = 18.dp * rows.size
    BackHandler(isFullKeyboardVisible || IMEState.isFloating) { resetKeyboardPanel() }
    Box(Modifier.keyboardPanelPlacement(totalHeight)) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .background(Color.Black.copy(0.7f))
        ) {
            rows.forEach { row ->
                KeyboardRow(session, row, isFullKeyboardVisible)
            }
        }
    }
}

@Composable
private fun ColumnScope.KeyboardRow(session: TerminalSession?, labels: List<String>, isFullKeyboardVisible: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        labels.forEach { label ->
            KeyboardKey(session, label, isFullKeyboardVisible)
        }
    }
}

@Composable
private fun RowScope.KeyboardKey(session: TerminalSession?, label: String, isFullKeyboardVisible: Boolean) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentView = LocalView.current
    val isControlKey = label.isKeyboardControlKey(isFullKeyboardVisible)
    val isModifier = label.isKeyboardModifier()
    Box(
        modifier = Modifier
            .weight(label.keyboardWeight(isFullKeyboardVisible))
            .fillMaxHeight()
            .floatingKeyboardDrag(label, isControlKey, currentView) { isPressed = it }
            .keyboardPress(label, isControlKey, isModifier, session, scope, currentView) { isPressed = it }
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.keyboardDisplayText(isControlKey),
            color = if (isPressed || label.isKeyboardModifierActive()) Color.Gray else Color.White,
            fontSize = 9.sp,
            softWrap = false
        )
    }
}

private fun Modifier.keyboardPanelPlacement(totalHeight: Dp) = if (IMEState.isFloating) {
    offset { IMEState.keyboardOffset }.size(360.dp, totalHeight)
} else {
    fillMaxWidth().wrapContentHeight()
}

private fun Modifier.floatingKeyboardDrag(
    label: String,
    isControlKey: Boolean,
    currentView: View,
    setPressed: (Boolean) -> Unit
) = pointerInput(label, isControlKey) {
    if (isControlKey) detectDragGestures(
        onDragStart = {
            setPressed(true)
            IMEState.isFloating = true
            currentView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        },
        onDragEnd = { setPressed(false) },
        onDragCancel = { setPressed(false) }
    ) { change, dragAmount ->
        if (IMEState.isFloating) {
            change.consume()
            IMEState.keyboardOffset += IntOffset(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
        }
    }
}

private fun Modifier.keyboardPress(
    label: String,
    isControlKey: Boolean,
    isModifier: Boolean,
    session: TerminalSession?,
    scope: CoroutineScope,
    currentView: View,
    setPressed: (Boolean) -> Unit
) = pointerInput(label, isControlKey, isModifier, session) {
    detectTapGestures(
        onPress = {
            setPressed(true)
            currentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val repeatJob = if (!isControlKey && !isModifier) scope.launch { repeatKeyboardKey(session, label) } else null
            if (isModifier) label.setKeyboardModifierActive(true)
            try {
                awaitRelease()
            } finally {
                repeatJob?.cancel()
                if (isModifier) label.setKeyboardModifierActive(false)
                setPressed(false)
            }
        },
        onTap = {
            if (isControlKey) toggleKeyboardPanelMode()
        }
    )
}

private fun resetKeyboardPanel() {
    IMEState.isFloating = false
    IMEState.isFullKeyboardVisible = false
    IMEState.keyboardOffset = IntOffset.Zero
}

private fun toggleKeyboardPanelMode() {
    if (IMEState.isFloating) {
        IMEState.isFloating = false
        IMEState.keyboardOffset = IntOffset.Zero
    } else {
        IMEState.isFullKeyboardVisible = !IMEState.isFullKeyboardVisible
    }
}
