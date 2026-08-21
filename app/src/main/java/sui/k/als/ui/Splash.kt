package sui.k.als.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.content.*
import androidx.core.graphics.drawable.*
import kotlinx.coroutines.*
import sui.k.als.R
import sui.k.als.qemu.gunyah.*
import sui.k.als.qemu.gzvm.*

var suDir by mutableStateOf("su")
val su get() = suDir

@Composable
fun Splash(onTimeout: () -> Unit) {
    val context = LocalContext.current
    var checkCount by remember { mutableIntStateOf(0) }
    var inputPath by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val appIcon = remember {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
    }
    val transition = rememberInfiniteTransition(label = "splash")
    val scale by transition.animateFloat(
        initialValue = 0.81f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "scale"
    )

    LaunchedEffect(checkCount) {
        checking = true
        failed = false
        val preferences = context.getSharedPreferences("su", 0)
        suDir = preferences.getString("su", "su") ?: "su"
        if (inputPath.isEmpty()) inputPath = suDir
        val worked = withContext(Dispatchers.IO) {
            runCatching {
                Runtime.getRuntime().exec(arrayOf(suDir, "-v")).waitFor() == 0
            }.getOrDefault(false)
        }
        checking = false
        if (!worked) {
            failed = true
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            QemuGunyahConfigStore.load(context)
            QemuGzvmConfigStore.load(context)
        }
        delay(650)
        onTimeout()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(targetState = failed, label = "startup") { needsPath ->
            if (needsPath) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 522.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Column(
                        Modifier.padding(27.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Image(appIcon, null, Modifier.size(24.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.su_command_title),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.su_command_failed),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        ALSTextField(
                            label = stringResource(R.string.su_command_path_label),
                            value = inputPath,
                            supporting = stringResource(R.string.su_command_path_hint),
                            onValueChange = { inputPath = it })
                        Button(
                            onClick = {
                                suDir = inputPath.trim().ifEmpty { "su" }
                                context.getSharedPreferences("su", 0)
                                    .edit { putString("su", suDir) }
                                checkCount++
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(27.dp)
                        ) {
                            Text(
                                stringResource(R.string.su_verify),
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        appIcon, null, Modifier
                            .scale(scale)
                            .size(81.dp)
                    )
                    if (checking) {
                        Spacer(Modifier.height(27.dp))
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}