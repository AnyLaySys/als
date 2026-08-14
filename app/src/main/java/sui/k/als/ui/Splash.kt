package sui.k.als.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sui.k.als.app.qemu.kvm.QemuKvmConfigStore
import sui.k.als.qemu.gunyah.QemuGunyahConfigStore
import sui.k.als.qemu.gzvm.QemuGzvmConfigStore

var suPath by mutableStateOf("su")
val su get() = suPath

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
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "scale"
    )

    LaunchedEffect(checkCount) {
        checking = true
        failed = false
        val preferences = context.getSharedPreferences("su", 0)
        suPath = preferences.getString("su", "su") ?: "su"
        if (inputPath.isEmpty()) inputPath = suPath
        val worked = withContext(Dispatchers.IO) {
            runCatching {
                Runtime.getRuntime().exec(arrayOf(suPath, "-v")).waitFor() == 0
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
            QemuKvmConfigStore.load(context)
        }
        delay(650)
        onTimeout()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
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
                            Text("设置超级用户命令", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "未能执行当前 su 命令。请输入设备上可用的命令或完整路径。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        ALSTextField(
                            label = "su 命令或路径",
                            value = inputPath,
                            supporting = "例如 su、/system/xbin/su 或厂商提供的入口",
                            onValueChange = { inputPath = it }
                        )
                        Button(
                            onClick = {
                                suPath = inputPath.trim().ifEmpty { "su" }
                                context.getSharedPreferences("su", 0).edit { putString("su", suPath) }
                                checkCount++
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(27.dp)
                        ) {
                            Text("验证并继续", modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(appIcon, null, Modifier.scale(scale).size(24.dp))
                    Spacer(Modifier.height(18.dp))
                    Text("ALS", style = MaterialTheme.typography.headlineLarge)
                    if (checking) {
                        Spacer(Modifier.height(27.dp))
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}
