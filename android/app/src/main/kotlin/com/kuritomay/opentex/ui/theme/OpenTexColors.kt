package com.kuritomay.opentex.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object OpenTexColor {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF141A21)
    val SurfaceVariant = Color(0xFF19212B)
    val SurfaceElevated = Color(0xFF202A35)
    val Primary = Color(0xFF9CCAFF)
    val Secondary = Color(0xFF89D5D0)
    val Violet = Color(0xFFCAB8FF)
    val Success = Color(0xFF8BD5A2)
    val Warning = Color(0xFFFFC979)
    val Error = Color(0xFFFFB4AB)
    val Info = Color(0xFF9CCAFF)
    val AnnotationYellow = Color(0xFFFFD84D)
    val AnnotationBlue = Color(0xFF72B8FF)
    val AnnotationGreen = Color(0xFF8DD7A4)
    val AnnotationPink = Color(0xFFFFA6C5)
    val AnnotationPurple = Color(0xFFCBB5FF)
}

val OpenTexDark: ColorScheme = darkColorScheme(
    primary = OpenTexColor.Primary,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF17476F),
    onPrimaryContainer = Color(0xFFD0E6FF),
    secondary = OpenTexColor.Secondary,
    onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF174D4A),
    onSecondaryContainer = Color(0xFFA5F3ED),
    tertiary = OpenTexColor.Violet,
    background = OpenTexColor.Background,
    onBackground = Color(0xFFE1E7EF),
    surface = OpenTexColor.Surface,
    onSurface = Color(0xFFE1E7EF),
    surfaceVariant = OpenTexColor.SurfaceVariant,
    onSurfaceVariant = Color(0xFFC1C8D1),
    surfaceContainer = OpenTexColor.SurfaceVariant,
    surfaceContainerHigh = OpenTexColor.SurfaceElevated,
    outline = Color(0xFF697481),
    error = OpenTexColor.Error,
)
