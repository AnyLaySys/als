package sui.k.als.tty

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun TTYHub(
    sessions: List<TTYInstance>,
    onBack: () -> Unit,
    onSelect: (TTYInstance) -> Unit,
    onDelete: (TTYInstance) -> Unit,
    onCreate: () -> Unit
) {
    ALSScaffold(title = stringResource(R.string.terminal_sessions), onBack = onBack) { padding ->
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
                            Text(
                                "${stringResource(R.string.terminal_session)} ${index + 1}",
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDelete(tty) }) {
                                Icon(
                                    painterResource(R.drawable.delete),
                                    stringResource(R.string.terminal_delete),
                                    Modifier.size(24.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp), Alignment.Center
            ) {
                IconButton(onClick = onCreate) {
                    Icon(
                        painterResource(R.drawable.add),
                        stringResource(R.string.terminal_new),
                        Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}