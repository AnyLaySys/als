package sui.k.als.agl

import sui.k.als.app.qemu.gunyah.parseQemuGunyahConfigJson
import sui.k.als.app.qemu.gunyah.toQemuGunyahArgs
import sui.k.als.app.qemu.gzvm.parseQemuGzvmConfigJson
import sui.k.als.qemu.gunyah.QemuGunyahPreflight
import sui.k.als.qemu.gzvm.QemuGzvmPreflight
import sui.k.als.qemu.gzvm.toQemuGzvmArgs

internal data class AglLaunch(
    val width: Int,
    val height: Int,
    val workDir: String,
    val backend: AglNativeBackend,
    val configuration: String,
    val hideKeyboard: Boolean = false,
    val softKeyboard: Boolean = false,
    val consolePid: Int = -1,
)

internal data class AglPreparedLaunch(
    val args: Array<String>, val preflight: () -> Unit
)

internal fun AglNativeBackend.prepare(configuration: String): AglPreparedLaunch = when (this) {
    AglNativeBackend.Gunyah -> parseQemuGunyahConfigJson(configuration).let {
        AglPreparedLaunch(it.toQemuGunyahArgs()) { QemuGunyahPreflight.run(it) }
    }

    AglNativeBackend.Gzvm -> parseQemuGzvmConfigJson(configuration).let {
        AglPreparedLaunch(it.toQemuGzvmArgs()) { QemuGzvmPreflight.run(it) }
    }

}
