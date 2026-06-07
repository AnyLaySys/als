package sui.k.als

import android.os.*
import androidx.activity.*
import androidx.activity.compose.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.text.font.*
import androidx.core.view.*

val localFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val font = remember {
                runCatching {
                    FontFamily(
                        Font(
                            "GoogleSansFlex.ttf", assets
                        )
                    )
                }.getOrDefault(FontFamily.Default)
            }
            var showSplash by rememberSaveable { mutableStateOf(true) }
            CompositionLocalProvider(localFont provides font) {
                if (showSplash) {
                    Splash { showSplash = false }
                } else {
                    Hub { finish() }
                }
            }
        }
    }
}