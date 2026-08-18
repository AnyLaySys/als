package sui.k.als.tty

import com.termux.terminal.*
import sui.k.als.ui.*

object TTYEnv {
    val args = arrayOf("-i")
    val env: Array<String> by lazy {
        val systemEnv = System.getenv().toMutableMap()
        systemEnv["TERM"] = "xterm-direct"
        systemEnv.map { "${it.key}=${it.value}" }.toTypedArray()
    }
}

fun TerminalSession(env: TTYEnv, rows: Int, client: TerminalSessionClient): TerminalSession =
    TerminalSession("sh", alsDir, env.args, env.env, rows, client)