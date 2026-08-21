package sui.k.als.agl

import android.view.*
import sui.k.als.log.*
import sui.k.als.vm.VMBackend

/**
 * JNI binding carrier for the prebuilt QEMU libraries.
 *
 * The .so files (libqemu-gunyah.so / libqemu-gzvm.so) export their JNI entry
 * points hardcoded to this exact fully-qualified class name
 * (Java_sui_k_als_agl_AglNative_*). Renaming this class or its package breaks
 * native linkage with UnsatisfiedLinkError. All other code should go through
 * [sui.k.als.vm.VMNative], which delegates here.
 */
internal object AglNative {
    private var loadedLibrary: String? = null

    @Synchronized
    fun load(backend: VMBackend) {
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
        refreshRate: Float,
        audioInputFd: Int,
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
