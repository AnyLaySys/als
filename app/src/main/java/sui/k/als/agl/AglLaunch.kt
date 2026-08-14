package sui.k.als.agl

internal data class AglLaunch(
    val width: Int,
    val height: Int,
    val workDir: String,
    val backend: AglNativeBackend,
    val consolePid: Int = -1,
    val prepare: () -> AglPreparedLaunch
)

internal data class AglPreparedLaunch(
    val args: Array<String>,
    val preflight: () -> Unit
)
