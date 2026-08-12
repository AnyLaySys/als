package sui.k.als.tty

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import sui.k.als.R
import sui.k.als.ui.ALSScaffold

@Composable
fun TTYHub(
    sessions: List<TTYInstance>,
    onBack: () -> Unit,
    onSelect: (TTYInstance) -> Unit,
    onDelete: (TTYInstance) -> Unit,
    onCreate: () -> Unit
) {
    ALSScaffold(title = "终端会话", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                sessions.forEachIndexed { index, tty ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clickable { onSelect(tty) },
                        color = Color(0xFF1B1B1F),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("会话 ${index + 1}", color = Color.White, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDelete(tty) }) {
                                Icon(painterResource(R.drawable.delete), "删除", Modifier.size(24.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                Alignment.Center
            ) {
                IconButton(onClick = onCreate) {
                    Icon(painterResource(R.drawable.add), "新建", Modifier.size(24.dp), tint = Color.White)
                }
            }
        }
    }
}
