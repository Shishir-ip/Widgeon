package com.widgeon.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.widgeon.app.data.Prefs

// ---------------------------------------------------------------- palette
// Strict black & white professional identity: near-black ink on paper white,
// pure black canvas at night, monochrome grays everywhere between.

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A0A0A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDEDED),
    onPrimaryContainer = Color(0xFF111111),
    inversePrimary = Color(0xFFE0E0E0),
    secondary = Color(0xFF3A3A3A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF222222),
    tertiary = Color(0xFF2A2A2A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF0B0B0B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF666666),
    surfaceTint = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF171717),
    inverseOnSurface = Color(0xFFF4F4F4),
    outline = Color(0xFFE2E2E2),
    outlineVariant = Color(0xFFEDEDED),
    scrim = Color(0xFF000000),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE8E8E8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color(0xFFF0F0F0),
    inversePrimary = Color(0xFF2A2A2A),
    secondary = Color(0xFFCFCFCF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = Color(0xFFDDDDDD),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF0C0C0C),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF9C9C9C),
    surfaceTint = Color(0xFF0C0C0C),
    inverseSurface = Color(0xFFF0F0F0),
    inverseOnSurface = Color(0xFF121212),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF1C1C1C),
    scrim = Color(0xFF000000),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    surfaceContainerLowest = Color(0xFF070707),
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF171717),
    surfaceContainerHighest = Color(0xFF1D1D1D),
)

private val WidgeonTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.8).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = (-0.4).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(letterSpacing = 0.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(letterSpacing = 0.1.sp, lineHeight = 20.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    )
}

private val WidgeonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun WidgeonTheme(
    themeMode: String,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        Prefs.THEME_DARK -> true
        Prefs.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = WidgeonTypography,
        shapes = WidgeonShapes,
        content = content,
    )
}
