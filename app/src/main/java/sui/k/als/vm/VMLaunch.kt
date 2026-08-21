package sui.k.als.vm

import sui.k.als.app.qemu.gunyah.parseQemuGunyahConfigJson
import sui.k.als.app.qemu.gunyah.toQemuGunyahArgs
import sui.k.als.app.qemu.gzvm.parseQemuGzvmConfigJson
import sui.k.als.qemu.gunyah.QemuGunyahPreflight
import sui.k.als.qemu.gzvm.QemuGzvmPreflight
import sui.k.als.qemu.gzvm.toQemuGzvmArgs

internal data class VMLaunch(
    val width: Int,
    val height: Int,
    val workDir: String,
    val backend: VMBackend,
    val configuration: String,
    val hideKeyboard: Boolean = false,
    val softKeyboard: Boolean = false,
    val consolePid: Int = -1,
)

internal data class VMPreparedLaunch(
    val args: Array<String>, val preflight: () -> Unit
)

internal fun VMBackend.prepare(configuration: String): VMPreparedLaunch = when (this) {
    VMBackend.Gunyah -> parseQemuGunyahConfigJson(configuration).let {
        VMPreparedLaunch(it.toQemuGunyahArgs()) { QemuGunyahPreflight.run(it) }
    }

    VMBackend.Gzvm -> parseQemuGzvmConfigJson(configuration).let {
        VMPreparedLaunch(it.toQemuGzvmArgs()) { QemuGzvmPreflight.run(it) }
    }

}
