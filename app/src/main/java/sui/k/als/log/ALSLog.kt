package sui.k.als.log

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter

internal object ALSLog {
    private const val path = "/data/local/tmp/als/als.log"
    private val lock = Any()
    private lateinit var pending: File
    private var installed = false

    fun install(context: Context) {
        synchronized(lock) {
            if (installed) {
                return
            }
            pending = File(context.filesDir, "als.log.pending")
            installed = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                error("ALS", "uncaught exception on ${thread.name}", error)
                previous?.uncaughtException(thread, error)
            }
            writeLocked(record("I", "ALS", "process started"))
        }
        runCatching { System.loadLibrary("als-log") }.onFailure {
            error("ALS", "native log capture could not load", it)
        }
    }

    fun info(tag: String, message: String) {
        write("I", tag, message)
    }

    fun error(tag: String, message: String, error: Throwable? = null) {
        val detail = if (error == null) message else "$message\n${error.stackTraceText()}"
        write("E", tag, detail)
    }

    fun flush() {
        synchronized(lock) {
            if (installed) {
                flushPendingLocked()
            }
        }
    }

    private fun write(level: String, tag: String, message: String) {
        synchronized(lock) {
            if (installed) {
                writeLocked(record(level, tag, message))
            }
        }
    }

    private fun writeLocked(line: String) {
        if (flushPendingLocked() && append(File(path), line)) {
            return
        }
        append(pending, line)
    }

    private fun flushPendingLocked(): Boolean {
        if (!pending.isFile) {
            return true
        }
        val text = runCatching { pending.readText() }.getOrNull() ?: return false
        if (!append(File(path), text)) {
            return false
        }
        pending.delete()
        return true
    }

    private fun append(file: File, text: String): Boolean = runCatching {
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use { it.append(text) }
    }.isSuccess

    private fun record(level: String, tag: String, message: String): String =
        "${System.currentTimeMillis()} $level/$tag [${Thread.currentThread().name}]: $message\n"

    private fun Throwable.stackTraceText(): String {
        val output = StringWriter()
        printStackTrace(PrintWriter(output))
        return output.toString()
    }
}
