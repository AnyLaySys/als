package sui.k.als.app

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

object X11 {
    internal fun prepare(context: Context): File {
        val root = File(context.cacheDir, "x11/xkb")
        if (File(root, "rules/evdev").exists() && File(root, "symbols/us").exists()) return root
        root.deleteRecursively()
        root.mkdirs()
        ZipInputStream(context.assets.open("xkb.zip")).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val file = File(root, entry.name.replace('\\', '/'))
                if (entry.isDirectory) file.mkdirs() else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        return root
    }
}
