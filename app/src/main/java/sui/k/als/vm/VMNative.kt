package sui.k.als.vm

import android.view.*
import sui.k.als.agl.AglNative

internal enum class VMBackend(internal val libraryName: String) {
    Gunyah("qemu-gunyah"), Gzvm("qemu-gzvm")
}

/**
 * Kotlin-facing facade for the QEMU in-process runner.
 *
 * All external declarations live on [AglNative] because the prebuilt .so files
 * export JNI symbols frozen to that class name. This object delegates to it so
 * the rest of the app can use the VM* naming without touching native linkage.
 */
internal object VMNative {
    fun load(backend: VMBackend) = AglNative.load(backend)
    fun grantRoot(): Int = AglNative.grantRoot()
    fun run(
        workDir: String,
        args: Array<String>,
        surface: Surface?,
        refreshRate: Float,
        audioInputFd: Int,
    ): Int = AglNative.run(workDir, args, surface, refreshRate, audioInputFd)

    fun redirectStdio(consolePid: Int): Int = AglNative.redirectStdio(consolePid)
    fun rebindOutput(consolePid: Int): Int = AglNative.rebindOutput(consolePid)
    fun restoreStdio() = AglNative.restoreStdio()
    fun setSurface(surface: Surface?, refreshRate: Float) = AglNative.setSurface(surface, refreshRate)
    fun pointer(x: Float, y: Float, buttons: Int) = AglNative.pointer(x, y, buttons)
    fun scroll(x: Float, y: Float) = AglNative.scroll(x, y)
    fun key(scanCode: Int, down: Boolean) = AglNative.key(scanCode, down)
    fun stop() = AglNative.stop()
}
