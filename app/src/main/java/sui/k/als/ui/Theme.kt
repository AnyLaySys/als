package sui.k.als.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import sui.k.als.localFont

private val Colors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF424751),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF12161D),
    surfaceContainer = Color(0xFF181C23),
    surfaceContainerHigh = Color(0xFF21252C),
    surfaceContainerHighest = Color(0xFF2A2E35),
    onSurface = Color(0xFFE2E2E9),
    onSurfaceVariant = Color(0xFFC3C6D0),
    outline = Color(0xFF8D9099),
    outlineVariant = Color(0xFF424751),
    error = Color(0xFFFFB4AB)
)

@Composable
fun ALSTheme(content: @Composable () -> Unit) {
    val family = localFont.current
    fun style(fontSize: Int, lineHeight: Int) = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp
    )
    MaterialTheme(
        colorScheme = Colors,
        typography = Typography(
            displayLarge = style(57, 63),
            displayMedium = style(48, 54),
            displaySmall = style(45, 51),
            headlineLarge = style(36, 42),
            headlineMedium = style(30, 36),
            headlineSmall = style(27, 33),
            titleLarge = style(24, 30),
            titleMedium = style(18, 24),
            titleSmall = style(15, 21),
            bodyLarge = style(18, 24),
            bodyMedium = style(15, 21),
            bodySmall = style(12, 18),
            labelLarge = style(15, 21),
            labelMedium = style(12, 18),
            labelSmall = style(12, 15)
        ),
        content = content
    )
}
