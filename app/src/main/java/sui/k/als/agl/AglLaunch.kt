package sui.k.als.agl

internal data class AglLaunch(
    val width: Int,
    val height: Int,
    val workDir: String,
    val backend: AglNativeBackend,
    val prepare: () -> AglPreparedLaunch
)

internal data class AglPreparedLaunch(
    val args: Array<String>,
    val preflight: () -> Unit
)
