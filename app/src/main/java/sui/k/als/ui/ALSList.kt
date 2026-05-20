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
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import sui.k.als.*

@Composable
fun ALSList(
    data: Any?,
    show: Boolean = false,
    value: String? = null,
    checked: Boolean = true,
    first: Boolean = false,
    last: Boolean = false,
    background: Color? = null,
    onDismiss: () -> Unit = {},
    onValueChange: ((String) -> Unit)? = null,
    iconContent: @Composable (RowScope.() -> Unit)? = null,
    onClick: (String) -> Unit = {}
) {
    if (data is List<*> && show) {
        Dialog(
            onDismiss,
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
                Alignment.Center
            ) {
                val height =
                    with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.9f).toDp() }
                Column(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(max = height)
                        .padding(3.dp)
                        .graphicsLayer { shape = RoundedCornerShape(3.dp); clip = true }
                        .background(Color(0xFF222222))
                        .clickable(enabled = false) {}
                        .verticalScroll(rememberScrollState())) {
                    data.forEachIndexed { i, item ->
                        ALSList(
                            item,
                            first = i == 0,
                            last = i == data.size - 1,
                            onClick = { s -> onClick(s); onDismiss() })
                    }
                }
            }
        }
        return
    }
    val text = (data as? String) ?: data?.toString() ?: ""
    val shape = RoundedCornerShape(
        topStart = if (first) 3.dp else 0.dp,
        topEnd = if (first) 3.dp else 0.dp,
        bottomStart = if (last) 3.dp else 0.dp,
        bottomEnd = if (last) 3.dp else 0.dp
    )
    val style = TextStyle(fontSize = 9.sp, color = Color.White, fontFamily = localFont.current)
    Row(Modifier
        .fillMaxWidth()
        .height(24.dp)
        .graphicsLayer { this.shape = shape; clip = true }
        .background(background ?: Color(0xFF1A1A1A))
        .clickable(remember { MutableInteractionSource() }, null) { onClick(text) }
        .padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (value == null && onValueChange != null) BasicTextField(
            text,
            onValueChange,
            Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Color.White)
        )
        else {
            Text(
                text,
                Modifier.weight(4f),
                color = if (checked) Color.White else Color.Gray,
                fontSize = 9.sp,
                fontFamily = localFont.current,
                maxLines = 1
            )
            value?.let { v ->
                Box(Modifier.weight(6f), Alignment.CenterEnd) {
                    if (onValueChange != null) BasicTextField(
                        v,
                        onValueChange,
                        Modifier.fillMaxWidth(),
                        true,
                        textStyle = style.copy(textAlign = TextAlign.End),
                        cursorBrush = SolidColor(Color.White)
                    )
                    else Text(
                        v,
                        color = Color(0xFFE0E0E0),
                        fontSize = 9.sp,
                        textAlign = TextAlign.End,
                        fontFamily = localFont.current,
                        maxLines = 1
                    )
                }
            }
            iconContent?.invoke(this)
        }
    }
}