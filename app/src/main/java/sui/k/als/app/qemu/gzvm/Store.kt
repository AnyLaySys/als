package sui.k.als.qemu.gzvm

import android.content.Context
import sui.k.als.app.qemu.gzvm.QemuGzvmConfig
import sui.k.als.app.qemu.gzvm.parseQemuGzvmConfigJson
import sui.k.als.app.qemu.gzvm.toQemuGzvmJson
import java.io.File

object QemuGzvmConfigStore {
    private var cached: QemuGzvmConfig? = null
    private fun file(context: Context): File = File(context.filesDir, "qemu/gzvm/config.json")

    fun load(context: Context): QemuGzvmConfig {
        cached?.let { return it }
        val file = file(context)
        return (if (file.isFile) runCatching { parseQemuGzvmConfigJson(file.readText()) }.getOrDefault(
            QemuGzvmConfig()
        ) else QemuGzvmConfig()).also { cached = it }
    }

    fun save(context: Context, config: QemuGzvmConfig) {
        cached = config
        val file = file(context)
        file.parentFile?.mkdirs()
        file.writeText(config.toQemuGzvmJson())
    }
}
