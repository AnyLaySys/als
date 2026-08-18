package sui.k.als

import android.os.*
import androidx.activity.*
import androidx.activity.compose.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.text.font.*
import sui.k.als.log.*
import sui.k.als.ui.*

const val UI_FONT_ASSET = "GoogleSansFlex.ttf"
const val CODE_FONT_ASSET = "GoogleSansCode.ttf"

val localFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ALSLog.info("ALS", "MainActivity created")
        setContent {
            val font = remember {
                runCatching {
                    FontFamily(Font(UI_FONT_ASSET, assets))
                }.getOrDefault(FontFamily.Default)
            }
            var showSplash by rememberSaveable { mutableStateOf(true) }
            CompositionLocalProvider(localFont provides font) {
                ALSTheme {
                    if (showSplash) {
                        Splash { showSplash = false }
                    } else {
                        Hub { finish() }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ALSLog.info("ALS", "MainActivity destroyed")
        super.onDestroy()
    }
}