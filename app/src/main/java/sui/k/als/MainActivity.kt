package sui.k.als

import android.content.*
import android.os.*
import androidx.activity.*
import androidx.activity.compose.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.text.font.*
import sui.k.als.ui.*

const val UI_FONT_ASSET = "GoogleSansFlex.ttf"
const val CODE_FONT_ASSET = "GoogleSansCode.ttf"

val localFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

class MainActivity : ComponentActivity() {
    private val destination
        get() = intent.getStringExtra("destination")?.let {
            runCatching { Destination.valueOf(it) }.getOrNull()
        } ?: Destination.Backends

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.info("ALS", "MainActivity created")
        val destination = destination
        setContent {
            val font = remember {
                runCatching {
                    FontFamily(Font(UI_FONT_ASSET, assets))
                }.getOrDefault(FontFamily.Default)
            }
            var showSplash by rememberSaveable { mutableStateOf(destination == Destination.Backends) }
            CompositionLocalProvider(localFont provides font) {
                ALSTheme {
                    if (showSplash) {
                        Splash { showSplash = false }
                    } else {
                        Hub(destination) {
                            startActivity(Intent(this, MainActivity::class.java).putExtra("destination", it.name))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (destination == Destination.Backends && isFinishing) HubState.close()
        Log.info("ALS", "MainActivity destroyed")
        super.onDestroy()
    }
}
