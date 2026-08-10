package sui.k.als.qemu.gzvm

import android.content.Context
import sui.k.als.app.qemu.gzvm.QemuGzvmConfig
import sui.k.als.app.qemu.gzvm.parseQemuGzvmConfigJson
import sui.k.als.app.qemu.gzvm.toQemuGzvmJson
import java.io.File

object QemuGzvmConfigStore {
    private fun file(context: Context): File = File(context.filesDir, "qemu/gzvm/config.json")

    fun load(context: Context): QemuGzvmConfig {
        val file = file(context)
        return if (file.isFile) runCatching { parseQemuGzvmConfigJson(file.readText()) }.getOrDefault(
            QemuGzvmConfig()
        ) else QemuGzvmConfig()
    }

    fun save(context: Context, config: QemuGzvmConfig) {
        val file = file(context)
        file.parentFile?.mkdirs()
        file.writeText(config.toQemuGzvmJson())
    }
}
