package com.puroaudio.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PuroBackground = Color(0xFF08080A)
val PuroSurface = Color(0xFF101014)
val PuroSurfaceHigh = Color(0xFF18181F)
val PuroOutline = Color(0xFF2A2A33)
val PuroAmber = Color(0xFFF5C451)
val PuroAmberSoft = Color(0xFFFFF1C9)
val PuroText = Color(0xFFF1F1F3)
val PuroTextDim = Color(0xFF9A9AA5)
val PuroGreen = Color(0xFF6EE7A8)

private val PuroColors = darkColorScheme(
    primary = PuroAmber,
    onPrimary = Color(0xFF1A1400),
    primaryContainer = Color(0xFF3A2E0A),
    onPrimaryContainer = PuroAmberSoft,
    secondary = Color(0xFF9DB2C7),
    onSecondary = Color(0xFF0B1620),
    background = PuroBackground,
    onBackground = PuroText,
    surface = PuroSurface,
    onSurface = PuroText,
    surfaceVariant = PuroSurfaceHigh,
    onSurfaceVariant = PuroTextDim,
    outline = PuroOutline,
    error = Color(0xFFFF6B6B),
)

private val PuroTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun PuroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PuroColors,
        typography = PuroTypography,
        content = content,
    )
}
