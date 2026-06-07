package sui.k.als.tty

import com.termux.terminal.*
import java.util.concurrent.*

internal val ttyIO = Executors.newSingleThreadExecutor()

fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"

fun cmd(session: TerminalSession, command: String) {
    ttyIO.execute { session.write("$command\n") }
}
