package sui.k.als.tty

import com.termux.terminal.*
import java.util.concurrent.*

internal val ttyIO = Executors.newSingleThreadExecutor()
fun cmd(session: TerminalSession, command: String) {
    ttyIO.execute { session.write("$command\n") }
}
