package sui.k.als.qvm.gunyah

import android.content.*
import sui.k.als.app.qvm.gunyah.QvmGunyahConfig
import sui.k.als.app.qvm.gunyah.parseQvmGunyahConfigJson
import sui.k.als.app.qvm.gunyah.toQvmGunyahJson
import java.io.*

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