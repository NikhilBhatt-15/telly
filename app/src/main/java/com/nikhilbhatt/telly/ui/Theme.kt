package com.nikhilbhatt.telly.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0E0F13)
    val Surface = Color(0xFF16181D)
    val SurfaceHigh = Color(0xFF1F2229)
    val SurfaceHigher = Color(0xFF292D36)
    val Outline = Color(0xFF2E323B)
    val Text = Color(0xFFECEDEE)
    val Muted = Color(0xFF8B9099)
    val Accent = Color(0xFFFF6B4A)
    val Power = Color(0xFFE5484D)
    val Good = Color(0xFF46A758)
    val Warn = Color(0xFFF5A524)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2219),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFB9C3D6),
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigher,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Power,
)

private val typography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun TellyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

val SectionLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = Palette.Muted)
