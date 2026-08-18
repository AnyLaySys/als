package sui.k.als.qemu.gunyah

import android.content.Context
import sui.k.als.app.qemu.gunyah.QemuGunyahConfig
import sui.k.als.app.qemu.gunyah.parseQemuGunyahConfigJson
import sui.k.als.app.qemu.gunyah.toQemuGunyahJson
import java.io.File

object QemuGunyahConfigStore {
    private var cached: QemuGunyahConfig? = null
    private fun file(context: Context): File = File(context.filesDir, "qemu/gunyah/config.json")
    fun load(context: Context): QemuGunyahConfig {
        cached?.let { return it }
        val file: File = file(context)
        return (if (file.isFile) runCatching { parseQemuGunyahConfigJson(file.readText()) }.getOrDefault(
            QemuGunyahConfig()
        ) else QemuGunyahConfig()).also { cached = it }
    }

    fun save(context: Context, config: QemuGunyahConfig) {
        cached = config
        val file: File = file(context)
        file.parentFile?.mkdirs()
        file.writeText(config.toQemuGunyahJson())
    }
}
