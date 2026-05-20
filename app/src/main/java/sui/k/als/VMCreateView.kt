package sui.k.als

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(9.dp)
        ) {
            Column(
                Modifier
                    .width(27.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                icons.forEachIndexed { i, icon ->
                    val active = activeIndex == i
                    ALSButton(
                        icon = icon,
                        regColor = if (active) Color.Gray else Color.DarkGray,
                        iconTint = if (active) Color.White else Color.LightGray,
                        longClick = { onLongClick(i) }) { onIndexChange(i) }
                }
                ALSButton(R.drawable.save) { onAction() }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 9.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                color = Color.Black
            ) {
                AnimatedContent(
                    targetState = activeIndex, transitionSpec = {
                        fadeIn(
                            tween(
                                90, easing = LinearEasing
                            )
                        ).togetherWith(fadeOut(tween(90, easing = LinearEasing)))
                            .using(SizeTransform(false))
                    }, label = ""
                ) { target ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        content(
                            target
                        )
                    }
                }
            }
        }
    }
}