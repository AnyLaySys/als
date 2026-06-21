package sui.k.als.qvm.gunyah

import android.content.Context
import java.io.File

object QvmGunyahConfigStore {
    private fun file(context: Context): File = File(context.filesDir, "qvm/gunyah/config.json")

    fun load(context: Context): QvmGunyahConfig {
        val file: File = file(context)
        return if (file.isFile) runCatching { parseQvmGunyahConfigJson(file.readText()) }.getOrDefault(
            QvmGunyahConfig()
        ) else QvmGunyahConfig()
    }

    fun save(context: Context, config: QvmGunyahConfig) {
        val file: File = file(context)
        file.parentFile?.mkdirs()
        file.writeText(config.toQvmGunyahJson())
    }
}
