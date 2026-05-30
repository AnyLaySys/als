package sui.k.als

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.*
import androidx.core.content.*
import androidx.core.graphics.drawable.*
import kotlinx.coroutines.*
import sui.k.als.tty.*
import sui.k.als.ui.*

var suPath by mutableStateOf("su")
val su get() = suPath

@Composable
fun Splash(
    modifier: Modifier = Modifier, instance: TTYInstance? = null, onTimeout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var internalInstance by remember { mutableStateOf<TTYInstance?>(null) }
    val active = instance ?: internalInstance
    var showIcon by remember { mutableStateOf(false) }
    var showSuInput by remember { mutableStateOf(false) }
    var checkCount by remember { mutableIntStateOf(0) }
    var inputPath by remember { mutableStateOf("") }

    val appIcon = remember {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
    }

    LaunchedEffect(checkCount) {
        val sp = context.getSharedPreferences("su", 0)
        suPath = sp.getString("su", "su") ?: "su"
        inputPath = suPath

        val suWorked = try {
            Runtime.getRuntime().exec(arrayOf(suPath, "-v")).waitFor() == 0
        } catch (_: Exception) {
            false
        }

        if (!suWorked) {
            showSuInput = true
            return@LaunchedEffect
        }

        showSuInput = false
        if (active == null) {
            val created = createTTYInstance(context, TTYSessionStub(), TTYViewStub())
            internalInstance = created
            launch {
                delay(90)
                cmd(created.session, shellQuote(su))
                cmd(created.session, "clear")
            }
        }
        showIcon = true
        delay(900)
        onTimeout?.invoke()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black), Alignment.Center
    ) {
        active?.let { tty ->
            AndroidView(factory = {
                val parent = tty.view.parent as? android.view.ViewGroup
                parent?.removeView(tty.view)
                tty.view
            }, modifier = Modifier.fillMaxSize(), update = { it.onScreenUpdated() })
        }

        if (showSuInput) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black), Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(1f / 3f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ALSList(
                        data = "",
                        value = inputPath,
                        first = true,
                        last = true,
                        onValueChange = { inputPath = it })
                    Spacer(Modifier.height(9.dp))
                    ALSButton(R.drawable.arrow_forward) {
                        suPath = inputPath
                        context.getSharedPreferences("su", 0).edit { putString("su", suPath) }
                        checkCount++
                    }
                }
            }
        } else if (showIcon) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                ALSButton(appIcon, iconSize = 27.dp)
            }
        }
    }
}
