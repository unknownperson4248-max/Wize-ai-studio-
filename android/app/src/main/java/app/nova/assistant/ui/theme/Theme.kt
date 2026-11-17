package app.nova.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** JARVIS palette: black, silver and electric blue. */
object J {
    val Black = Color(0xFF000000)
    val Bg = Color(0xFF030508)
    val Panel = Color(0xFF0A0F16)
    val Panel2 = Color(0xFF0F1620)
    val Line = Color(0xFF1C2633)
    val Silver = Color(0xFFC9D1DC)
    val SilverDim = Color(0xFF8A94A3)
    val Text = Color(0xFFE8EEF5)
    val Blue = Color(0xFF1E9BFF)
    val Cyan = Color(0xFF3BD6FF)
    val BlueDeep = Color(0xFF0A3A66)
    val Green = Color(0xFF2FD27E)
    val Amber = Color(0xFFFFB020)
    val Red = Color(0xFFFF4D5E)
}

private val scheme = darkColorScheme(
    primary = J.Blue,
    onPrimary = J.Black,
    secondary = J.Silver,
    onSecondary = J.Black,
    tertiary = J.Cyan,
    background = J.Bg,
    onBackground = J.Text,
    surface = J.Panel,
    onSurface = J.Text,
    surfaceVariant = J.Panel2,
    onSurfaceVariant = J.SilverDim,
    outline = J.Line,
    error = J.Red,
    onError = J.Black,
)

private val type = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = 6.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.3.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.6.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.4.sp),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
