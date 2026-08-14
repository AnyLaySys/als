package sui.k.als.agl

import android.view.Surface
import sui.k.als.log.ALSLog

internal object AglNative {
    private var loadedLibrary: String? = null

    @Synchronized
    fun load(backend: AglNativeBackend) {
        val library = backend.libraryName
        check(loadedLibrary == null || loadedLibrary == library) {
            "AGL is already bound to $loadedLibrary"
        }
        if (loadedLibrary == null) {
            ALSLog.info("AGL", "loading $library")
            System.loadLibrary(library)
            loadedLibrary = library
            ALSLog.info("AGL", "loaded $library")
        }
    }

    external fun grantRoot(): Int
    external fun run(
        workDir: String,
        args: Array<String>,
        surface: Surface?,
        refreshRate: Float
    ): Int

    external fun redirectStdio(consolePid: Int): Int
    external fun rebindOutput(consolePid: Int): Int
    external fun restoreStdio()
    external fun setSurface(surface: Surface?, refreshRate: Float)
    external fun pointer(x: Float, y: Float, buttons: Int)
    external fun scroll(x: Float, y: Float)
    external fun key(scanCode: Int, down: Boolean)
    external fun stop()
}

internal enum class AglNativeBackend(internal val libraryName: String) {
    Gunyah("qemu-gunyah"),
    Gzvm("qemu-gzvm"),
    Kvm("qemu-kvm")
}
