package sui.k.als.app.qemu.gunyah

import android.content.Context
import sui.k.als.app.qemu.vm.QemuConfigStore
import java.io.File

object QemuGunyahConfigStore : QemuConfigStore<QemuGunyahConfig> {
    private var cached: QemuGunyahConfig? = null
    private fun file(context: Context): File = File(context.filesDir, "qemu/gunyah/config.json")
    override fun load(context: Context): QemuGunyahConfig {
        cached?.let { return it }
        val file: File = file(context)
        return (if (file.isFile) runCatching { parseQemuGunyahConfigJson(file.readText()) }.getOrDefault(
            QemuGunyahConfig()
        ) else QemuGunyahConfig()).also { cached = it }
    }

    override fun save(context: Context, config: QemuGunyahConfig) {
        cached = config
        val file: File = file(context)
        file.parentFile?.mkdirs()
        file.writeText(config.toQemuGunyahJson())
    }
}
