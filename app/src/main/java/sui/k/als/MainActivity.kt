package sui.k.als

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import sui.k.als.log.ALSLog
import sui.k.als.ui.ALSTheme
import sui.k.als.ui.Hub
import sui.k.als.ui.Splash

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
