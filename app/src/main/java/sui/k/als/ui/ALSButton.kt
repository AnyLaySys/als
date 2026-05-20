package sui.k.als.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import sui.k.als.*

@Composable
fun ALSButton(
    icon: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 27.dp,
    iconSize: Dp = 18.dp,
    regColor: Color = Color.White,
    pressedColor: Color = Color.Gray,
    iconTint: Color = Color.Unspecified,
    longClick: (() -> Unit)? = null,
    click: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint =
        if (iconTint != Color.Unspecified) iconTint else if (pressed) pressedColor else regColor; Box(
        modifier
            .size(size)
            .combinedClickable(interaction, null, onLongClick = longClick, onClick = click),
        Alignment.Center
    ) {
        when (icon) {
            is Int -> Icon(
                painterResource(icon), null, Modifier.size(iconSize), tint
            ); is ImageBitmap -> Image(icon, null, Modifier.size(iconSize)); is String -> Text(
            icon, color = tint, fontSize = 12.sp, fontFamily = localFont.current
        )
        }
    }
}