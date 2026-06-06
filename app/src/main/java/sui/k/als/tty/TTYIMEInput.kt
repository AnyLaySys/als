package sui.k.als.tty

import com.termux.terminal.*
import kotlinx.coroutines.*

private val keyboardCodes =
    "Tab·\t¦Esc·\u001b¦Enter·\r¦Back·\u007f¦ · ¦↑·\u001b[A¦↓·\u001b[B¦←·\u001b[D¦→·\u001b[C¦Home·\u001b[1~¦End·\u001b[4~¦Del·\u001b[3~¦F1·\u001bOP¦F2·\u001bOQ¦F3·\u001bOR¦F4·\u001bOS¦F5·\u001b[15~¦F6·\u001b[17~¦F7·\u001b[18~¦F8·\u001b[19~¦F9·\u001b[20~¦F10·\u001b[21~¦F11·\u001b[23~¦F12·\u001b[24~"
        .split('¦')
        .associate { entry -> entry.split('·').let { parts -> parts[0] to parts[1] } }

private val keyboardSymbols =
    "`~·1!·2@·3#·4$·5%·6^·7&·8*·9(·0)·-_·=+·[{·]}·\\|·;:·'\"·,<·.>·/?"
        .split('·')
        .associate { pair -> pair[0].toString() to pair[1].toString() }

internal fun String.isKeyboardModifier() = this == "Ctrl" || this == "Shift" || this == "Alt" || this == "Caps"

internal fun String.isKeyboardControlKey(isFullKeyboardVisible: Boolean) =
    if (isFullKeyboardVisible) isEmpty() else this == "·"

internal fun String.keyboardWeight(isFullKeyboardVisible: Boolean) = if (isFullKeyboardVisible) {
    when (this) {
        " " -> 4.2f
        "Ctrl", "Alt", "Home", "End" -> 1.2f
        else -> 1f
    }
} else {
    1f
}

internal fun String.isKeyboardModifierActive() = when (this) {
    "Ctrl" -> IMEState.isCtrlActive
    "Shift" -> IMEState.isShiftActive
    "Alt" -> IMEState.isAltActive
    "Caps" -> IMEState.isCapsActive
    else -> false
}

internal fun String.setKeyboardModifierActive(active: Boolean) {
    when (this) {
        "Ctrl" -> IMEState.isCtrlActive = active
        "Shift" -> IMEState.isShiftActive = active
        "Alt" -> IMEState.isAltActive = active
        "Caps" -> IMEState.isCapsActive = active
    }
}

internal fun String.keyboardDisplayText(isControlKey: Boolean): String = when {
    isControlKey -> ""
    isKeyboardModifier() || length > 1 -> this
    isEmpty() -> ""
    !first().isLetter() && !keyboardSymbols.containsKey(this) -> this
    IMEState.isShiftActive -> keyboardSymbols[this] ?: uppercase()
    IMEState.isCapsActive && first().isLetter() -> uppercase()
    else -> this
}

internal suspend fun repeatKeyboardKey(session: TerminalSession?, label: String) {
    processKeyboardKey(session, label)
    delay(270)
    while (currentCoroutineContext().isActive) {
        processKeyboardKey(session, label)
        delay(30)
    }
}

private fun processKeyboardKey(session: TerminalSession?, label: String) {
    keyboardCodes[label]?.let { code ->
        session.sendKeyboardData(if (IMEState.isAltActive && label != "Alt") "\u001b$code" else code)
    } ?: run {
        var text = label.shiftedKeyboardText()
        if (IMEState.isCtrlActive && text.length == 1) {
            text.uppercase()[0].let { char ->
                if (char in '@'..'_') text = (char.code - '@'.code).toChar().toString()
            }
        }
        session.sendKeyboardData(if (IMEState.isAltActive) "\u001b$text" else text)
    }
}

private fun String.shiftedKeyboardText(): String {
    val isUpperCase = IMEState.isShiftActive || (IMEState.isCapsActive && length == 1 && first().isLetter())
    return when {
        IMEState.isShiftActive -> keyboardSymbols[this] ?: uppercase()
        isUpperCase -> uppercase()
        else -> lowercase()
    }
}

private fun TerminalSession?.sendKeyboardData(data: String) = this?.write(data)
