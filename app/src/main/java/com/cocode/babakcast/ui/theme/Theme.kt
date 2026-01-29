package com.cocode.babakcast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Shapes as per style guide: 12dp rounded corners for buttons
private val BabakCastShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // Buttons use medium shape
    large = RoundedCornerShape(16.dp)
)

// Dark-first color scheme as per style guide
private val DarkColorScheme = darkColorScheme(
    primary = BabakCastColors.PrimaryAccent,
    secondary = BabakCastColors.SecondaryAccent,
    tertiary = BabakCastColors.SecondaryAccent,
    background = BabakCastColors.BackgroundPrimary,
    surface = BabakCastColors.BackgroundSurface,
    surfaceVariant = BabakCastColors.BackgroundCard,
    onPrimary = BabakCastColors.BackgroundPrimary,
    onSecondary = BabakCastColors.BackgroundPrimary,
    onTertiary = BabakCastColors.BackgroundPrimary,
    onBackground = BabakCastColors.TextPrimary,
    onSurface = BabakCastColors.TextPrimary,
    onSurfaceVariant = BabakCastColors.TextSecondary,
    error = BabakCastColors.Error,
    onError = BabakCastColors.TextPrimary,
    errorContainer = BabakCastColors.Error.copy(alpha = 0.2f),
    onErrorContainer = BabakCastColors.Error,
    outline = BabakCastColors.TextDisabled,
    outlineVariant = BabakCastColors.TextDisabled.copy(alpha = 0.5f)
)

// Light theme (optional, dark-first design)
private val LightColorScheme = lightColorScheme(
    primary = BabakCastColors.PrimaryAccent,
    secondary = BabakCastColors.SecondaryAccent,
    tertiary = BabakCastColors.SecondaryAccent,
    background = BabakCastColors.TextPrimary,
    surface = BabakCastColors.TextPrimary,
    surfaceVariant = BabakCastColors.BackgroundCard,
    onPrimary = BabakCastColors.BackgroundPrimary,
    onSecondary = BabakCastColors.BackgroundPrimary,
    onTertiary = BabakCastColors.BackgroundPrimary,
    onBackground = BabakCastColors.BackgroundPrimary,
    onSurface = BabakCastColors.BackgroundPrimary,
    onSurfaceVariant = BabakCastColors.TextSecondary,
    error = BabakCastColors.Error,
    onError = BabakCastColors.TextPrimary,
    errorContainer = BabakCastColors.Error.copy(alpha = 0.2f),
    onErrorContainer = BabakCastColors.Error,
    outline = BabakCastColors.TextDisabled,
    outlineVariant = BabakCastColors.TextDisabled.copy(alpha = 0.5f)
)

@Composable
fun BabakCastTheme(
    darkTheme: Boolean = true, // Default to dark theme (dark-first design)
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = BabakCastShapes,
        content = content
    )
}