package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Extra semantic colors and gradients that fall outside the standard Material3 color roles.
 * Access via [MaterialTheme]-style helper: `LocalExtendedColors.current`.
 */
data class ExtendedColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val wifiAccent: Color,
    val mobileAccent: Color,
    val heroGradient: Brush,
    val cardBorder: Color,
    val textSecondary: Color,
    val textMedium: Color,
)

private val LightExtendedColors = ExtendedColors(
    success = SuccessGreen,
    successContainer = SuccessGreenContainerLight,
    warning = WarningAmber,
    wifiAccent = WifiBlue,
    mobileAccent = MobileOrange,
    heroGradient = Brush.linearGradient(listOf(BluePrimary, Color(0xFF1338A8))),
    cardBorder = BorderLight,
    textSecondary = TextSecondary,
    textMedium = TextMedium,
)

private val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF4ADE80),
    successContainer = SuccessGreenContainerDark,
    warning = WarningAmber,
    wifiAccent = Color(0xFF60A5FA),
    mobileAccent = Color(0xFFFF8A5C),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF1B3FCF), Color(0xFF0B1740))),
    cardBorder = BorderDark,
    textSecondary = TextSecondaryDark,
    textMedium = TextMediumDark,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF00256E),
    primaryContainer = Color(0xFF16327D),
    onPrimaryContainer = BlueContainer,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    error = ErrorRed,
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BlueContainer,
    onPrimaryContainer = BlueOnContainer,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    error = ErrorRed,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep false by default to showcase our specific premium brand colors.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
