package sui.k.als.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import sui.k.als.*

@Composable
fun ALSList(
    text: String,
    value: String? = null,
    checked: Boolean = true,
    first: Boolean = false,
    last: Boolean = false,
    background: Color? = null,
    onValueChange: ((String) -> Unit)? = null,
    iconContent: @Composable (RowScope.() -> Unit)? = null,
    onClick: (String) -> Unit = {}
) {
    val style = ALSListTextStyle()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clickable(remember { MutableInteractionSource() }, null) { onClick(text) },
        color = background?.copy(alpha = 0.22f) ?: Color(0xFF1B1B1F),
        shape = ALSListShape(first, last)
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                Modifier.weight(4f),
                color = if (checked) Color(0xFFE3E2E6) else Color(0xFF8E8E93),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            value?.let { ALSListValue(it, onValueChange, style) }
            iconContent?.invoke(this)
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    options: List<String>,
    first: Boolean = false,
    last: Boolean = false,
    onSelected: (String) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    ALSList(label, value = value, first = first, last = last, onClick = { show = true })
    if (show) {
        ALSListDialog(options, { show = false }) {
            onSelected(it)
            show = false
        }
    }
}

@Composable
private fun ALSListDialog(items: List<String>, onDismiss: () -> Unit, onClick: (String) -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            Alignment.Center
        ) {
            val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.9f).toDp() }
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = maxHeight)
                    .padding(8.dp)
                    .clickable(enabled = false) {},
                color = Color(0xFF1B1B1F),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    Modifier
                        .padding(4.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    items.forEachIndexed { index, item ->
                        ALSList(item, first = index == 0, last = index == items.lastIndex) {
                            onClick(it)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ALSListValue(value: String, onValueChange: ((String) -> Unit)?, style: TextStyle) {
    Box(Modifier.weight(6f), Alignment.CenterEnd) {
        if (onValueChange != null) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = style.copy(textAlign = TextAlign.End, color = Color.White),
                cursorBrush = SolidColor(Color(0xFFA8C7FA))
            )
        } else {
            Text(
                value,
                color = Color(0xFFC7C6CA),
                style = style,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ALSListTextStyle() = TextStyle(
    fontSize = 10.sp,
    color = Color.White,
    fontFamily = localFont.current,
    fontWeight = FontWeight.Medium
)

private fun ALSListShape(first: Boolean, last: Boolean): RoundedCornerShape {
    val top = if (first) 9.dp else 0.dp
    val bottom = if (last) 9.dp else 0.dp
    return RoundedCornerShape(top, top, bottom, bottom)
}
