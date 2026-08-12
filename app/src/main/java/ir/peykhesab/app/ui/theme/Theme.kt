package ir.peykhesab.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF635BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E7FF),
    onPrimaryContainer = Color(0xFF271F8C),
    secondary = Color(0xFF00A884),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F8EF),
    tertiary = Color(0xFFFF8A3D),
    tertiaryContainer = Color(0xFFFFE5D3),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F1F8),
    outline = Color(0xFFD5D7E2),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B4FF),
    secondary = Color(0xFF68DCC0),
    tertiary = Color(0xFFFFB487),
    background = Color(0xFF101116),
    surface = Color(0xFF181A22),
    surfaceVariant = Color(0xFF222530)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
)

@Composable
fun PeykHesabTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
            ),
            content = content
        )
    }
}
