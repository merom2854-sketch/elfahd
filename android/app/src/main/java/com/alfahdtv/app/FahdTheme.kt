package com.alfahdtv.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object FahdColors {
    val Background = Color(0xFF08090D)
    val Surface = Color(0xFF111319)
    val SurfaceHigh = Color(0xFF191B23)
    val Red = Color(0xFFEF233C)
    val Gold = Color(0xFFF2B640)
    val Text = Color(0xFFF8F8FA)
    val Muted = Color(0xFFA6A8B2)
    val Divider = Color(0x1FFFFFFF)
}

private val FahdScheme = darkColorScheme(
    primary = FahdColors.Red,
    onPrimary = Color.White,
    secondary = FahdColors.Gold,
    background = FahdColors.Background,
    onBackground = FahdColors.Text,
    surface = FahdColors.Surface,
    onSurface = FahdColors.Text,
    surfaceVariant = FahdColors.SurfaceHigh,
    onSurfaceVariant = FahdColors.Muted,
    outline = FahdColors.Divider,
)

private val FahdTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp),
)

@Composable
fun FahdTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FahdScheme, typography = FahdTypography, content = content)
}
