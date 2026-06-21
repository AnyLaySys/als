package sui.k.als.tty

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
    onSelect: (TTYInstance) -> Unit,
    onDelete: (TTYInstance) -> Unit,
    onCreate: () -> Unit
) = Column(
    Modifier
        .fillMaxSize()
        .background(Color.Black)
) {
    Column(
        Modifier
            .weight(1f)
            .padding(9.dp)
            .verticalScroll(rememberScrollState())
    ) {
        sessions.forEachIndexed { i, tty ->
            ALSList(
                stringResource(R.string.session) + (i + 1),
                first = i == 0,
                last = i == sessions.size - 1,
                iconContent = { ALSButton(R.drawable.delete) { onDelete(tty) } }) { onSelect(tty) }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp), Alignment.Center
    ) {
        ALSButton(R.drawable.add) { onCreate() }
    }
}
