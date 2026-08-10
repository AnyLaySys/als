package sui.k.als.agl

internal data class AglLaunch(
    val width: Int,
    val height: Int,
    val workDir: String,
    val args: Array<String>,
    val backend: AglNativeBackend,
    val preflight: () -> Unit
)
