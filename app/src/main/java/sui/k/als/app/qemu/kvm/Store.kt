package sui.k.als.app.qemu.kvm

import android.content.Context
import java.io.File

object QemuKvmConfigStore {
    private var cached: QemuKvmConfig? = null
    private fun file(context: Context): File = File(context.filesDir, "qemu/kvm/config.json")

    fun load(context: Context): QemuKvmConfig {
        cached?.let { return it }
        val file = file(context)
        return (if (file.isFile) runCatching { parseQemuKvmConfigJson(file.readText()) }.getOrDefault(
            QemuKvmConfig()
        ) else QemuKvmConfig()).also { cached = it }
    }

    fun save(context: Context, config: QemuKvmConfig) {
        cached = config
        val file = file(context)
        file.parentFile?.mkdirs()
        file.writeText(config.toQemuKvmJson())
    }
}
