package sui.k.als.qvm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import sui.k.als.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun QvmDashboard(
    configs: List<QvmConfig>,
    onExit: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (QvmConfig) -> Unit,
    onOpenVnc: (QvmConfig) -> Unit,
    onStart: (QvmConfig) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        QvmDashboardToolbar(configs, onExit, onCreate)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (configs.isEmpty()) item { QvmEmptyState(onCreate) }
            items(configs, key = { it.name }) { virtualMachine ->
                QvmConfigurationRow(
                    virtualMachine = virtualMachine,
                    onEdit = { onEdit(virtualMachine) },
                    onOpenVnc = { onOpenVnc(virtualMachine) },
                    onStart = { onStart(virtualMachine) })
            }
        }
    }
}

@Composable
private fun QvmDashboardToolbar(
    configs: List<QvmConfig>, onExit: () -> Unit, onCreate: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "QVM",
            color = Color(0xFFE9EEF5),
            fontSize = 17.sp,
            fontFamily = localFont.current,
            fontWeight = FontWeight.Black
        )
        QvmTinyPill(
            "${configs.count { it.isRunning }}/${configs.size}",
            if (configs.any { it.isRunning }) Color(0xFF5DFFB0) else Color(0xFF748091)
        )
        Spacer(Modifier.weight(1f))
        QvmIconAction(R.drawable.power, Color(0xFFFF7D7D), onExit)
        QvmIconAction(R.drawable.add, Color(0xFF9AD7FF), onCreate)
    }
}

@Composable
private fun QvmConfigurationRow(
    virtualMachine: QvmConfig, onEdit: () -> Unit, onOpenVnc: () -> Unit, onStart: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable { onEdit() },
        color = Color(0xFF111318),
        contentColor = Color(0xFFE9EEF5),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            QvmStatusStripe(virtualMachine.isRunning)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        virtualMachine.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = localFont.current,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    QvmTinyPill(
                        if (virtualMachine.isRunning) "RUN" else "STOP",
                        if (virtualMachine.isRunning) Color(0xFF5DFFB0) else Color(0xFF748091)
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QvmMetric("CPU", virtualMachine.raw?.optString("smp", "4") ?: "4")
                    QvmMetric("RAM", virtualMachine.raw?.optString("mem", "6G") ?: "6G")
                    QvmMetric("VNC", virtualMachine.raw?.optString("vnc_port", ":0") ?: ":0")
                    QvmMetric("NET", "${virtualMachine.raw?.optJSONArray("net")?.length() ?: 0}")
                    QvmMetric("DSK", "${virtualMachine.raw?.optJSONArray("disk")?.length() ?: 0}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QvmIconAction(R.drawable.square, Color(0xFFB8F7FF), onOpenVnc)
                QvmIconAction(
                    R.drawable.power,
                    if (virtualMachine.isRunning) Color(0xFFFFC857) else Color(0xFF5DFFB0),
                    onStart
                )
            }
        }
    }
}

@Composable
private fun QvmStatusStripe(isRunning: Boolean) {
    Box(
        Modifier
            .width(3.dp)
            .fillMaxHeight(0.68f)
            .graphicsLayer { shape = RoundedCornerShape(4.dp); clip = true }
            .background(if (isRunning) Color(0xFF5DFFB0) else Color(0xFF3B4652)))
}

@Composable
private fun QvmIconAction(icon: Int, tint: Color, onClick: () -> Unit) {
    ALSButton(
        icon = icon,
        size = 34.dp,
        iconSize = 18.dp,
        regColor = tint,
        pressedColor = Color(0xFF8E8E93),
        click = onClick
    )
}

@Composable
private fun QvmMetric(label: String, value: String) {
    Surface(
        color = Color(0xFF1B1B1F),
        contentColor = Color(0xFFC8D3DF),
        shape = RoundedCornerShape(7.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 8.sp,
                color = Color(0xFF91A0AF),
                fontFamily = localFont.current,
                fontWeight = FontWeight.Bold
            )
            Text(
                value,
                fontSize = 8.sp,
                color = Color.White,
                fontFamily = localFont.current,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QvmTinyPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f), contentColor = color, shape = RoundedCornerShape(7.dp)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            fontSize = 8.sp,
            fontFamily = localFont.current,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun QvmEmptyState(onCreate: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onCreate() },
        color = Color(0xFF111318),
        contentColor = Color(0xFFE9EEF5),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painterResource(R.drawable.add),
                null,
                Modifier.size(19.dp),
                tint = Color(0xFF9AD7FF)
            )
            Text(
                "Create QVM",
                fontSize = 12.sp,
                fontFamily = localFont.current,
                fontWeight = FontWeight.Black
            )
        }
    }
}
