package sui.k.als

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import sui.k.als.ui.*

@Composable
fun ExpressiveCanvas(
    icons: List<Int>,
    activeIndex: Int,
    onIndexChange: (Int) -> Unit,
    onLongClick: (Int) -> Unit = {},
    onAction: () -> Unit,
    content: @Composable (Int) -> Unit
) {
    val background = Color(0xFF05070A)
    val panel = Color(0xFF111318)
    Surface(color = background, modifier = Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 6.dp)
        ) {
            Column(
                Modifier
                    .width(27.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                icons.forEachIndexed { itemIndex, icon ->
                    ExpressiveNavigationButton(
                        icon = icon,
                        active = activeIndex == itemIndex,
                        onLongClick = { onLongClick(itemIndex) }
                    ) { onIndexChange(itemIndex) }
                }
                ExpressiveNavigationButton(
                    icon = R.drawable.save,
                    active = true,
                    onClick = onAction
                )
            }
            ExpressiveContentPanel(
                activeIndex = activeIndex,
                panel = panel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 3.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ExpressiveContentPanel(
    activeIndex: Int,
    panel: Color,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    Surface(
        modifier = modifier,
        color = panel,
        shape = RoundedCornerShape(9.dp)
    ) {
        AnimatedContent(
            targetState = activeIndex,
            transitionSpec = {
                fadeIn(tween(90, easing = LinearEasing))
                    .togetherWith(fadeOut(tween(90, easing = LinearEasing)))
                    .using(SizeTransform(false))
            },
            label = ""
        ) { targetIndex ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(3.dp)
            ) {
                content(targetIndex)
            }
        }
    }
}

@Composable
private fun ExpressiveNavigationButton(
    icon: Int,
    active: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    ALSButton(
        icon = icon,
        regColor = if (active) Color(0xFFA8C7FA) else Color(0xFFC7C6CA),
        pressedColor = Color(0xFF8E8E93),
        longClick = onLongClick,
        click = onClick
    )
}
