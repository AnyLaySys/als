package sui.k.als.app.qemu.vm

internal enum class VMBackend(internal val libraryName: String) {
    Gunyah("qemu-gunyah"), Gzvm("qemu-gzvm")
}

internal object VMNative {
    private var loadedLibrary: String? = null

    @Synchronized
    fun load(backend: VMBackend) {
        val library = backend.libraryName
        check(loadedLibrary == null || loadedLibrary == library) {
            "VM backend is already loaded: $loadedLibrary"
        }
        if (loadedLibrary == null) {
            System.loadLibrary(library)
            loadedLibrary = library
        }
    }

    external fun grantRoot(): Int
    external fun run(workDir: String, args: Array<String>, audioInputFd: Int): Int
    external fun pointer(x: Float, y: Float, buttons: Int)
    external fun scroll(x: Float, y: Float)
    external fun key(scanCode: Int, down: Boolean)
    external fun stop()
    external fun redirectStdio(consolePid: Int): Int
    external fun rebindOutput(consolePid: Int): Int
    external fun restoreStdio()
}
