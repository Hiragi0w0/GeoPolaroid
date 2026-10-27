package com.hiragi0w0.geopolaroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Sepia,
    onPrimary = Color.White,
    primaryContainer = SepiaContainer,
    onPrimaryContainer = OnSepiaContainer,
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = Brick,
    onTertiary = Color.White,
    tertiaryContainer = BrickContainer,
    onTertiaryContainer = OnBrickContainer,
    background = Cream,
    onBackground = SepiaInk,
    surface = Cream,
    onSurface = SepiaInk,
    surfaceVariant = CreamHigh,
    onSurfaceVariant = SepiaInkVariant,
    surfaceDim = CreamHighest,
    surfaceBright = Cream,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = CreamLow,
    surfaceContainer = CreamContainer,
    surfaceContainerHigh = CreamHigh,
    surfaceContainerHighest = CreamHighest,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = ErrorRed,
    inverseSurface = UmberHigh,
    inverseOnSurface = Parchment,
    inversePrimary = SepiaLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = SepiaLight,
    onPrimary = OnSepiaLight,
    primaryContainer = SepiaDarkContainer,
    onPrimaryContainer = OnSepiaDarkContainer,
    secondary = SageLight,
    onSecondary = OnSageLight,
    secondaryContainer = SageDarkContainer,
    onSecondaryContainer = SageContainer,
    tertiary = BrickLight,
    onTertiary = OnBrickLight,
    tertiaryContainer = BrickDarkContainer,
    onTertiaryContainer = BrickContainer,
    background = Umber,
    onBackground = Parchment,
    surface = Umber,
    onSurface = Parchment,
    surfaceVariant = UmberHigh,
    onSurfaceVariant = ParchmentVariant,
    surfaceDim = Umber,
    surfaceBright = UmberHighest,
    surfaceContainerLowest = UmberLowest,
    surfaceContainerLow = UmberLow,
    surfaceContainer = UmberContainer,
    surfaceContainerHigh = UmberHigh,
    surfaceContainerHighest = UmberHighest,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorLight,
    inverseSurface = Parchment,
    inverseOnSurface = UmberHigh,
    inversePrimary = Sepia,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun GeoPolaroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
