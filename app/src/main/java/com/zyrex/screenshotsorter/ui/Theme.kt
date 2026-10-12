package com.zyrex.screenshotsorter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// One green identity, the same on every phone and in line with the launcher icon (#1F6A4D). Every role is set
// on purpose: left alone, Material fills the rest in with its purple-grey, which clashes with green.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6A4D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F2CC),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4C6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9D8),
    onSecondaryContainer = Color(0xFF092016),
    background = Color(0xFFFAFDF8),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFAFDF8),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    surfaceDim = Color(0xFFD9DED8),
    surfaceBright = Color(0xFFFAFDF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EF),
    surfaceContainer = Color(0xFFEBF0EA),
    surfaceContainerHigh = Color(0xFFE5EAE3),
    surfaceContainerHighest = Color(0xFFE0E4DE),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2E312E),
    inverseOnSurface = Color(0xFFEFF1EC),
    inversePrimary = Color(0xFF8CD5B1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5B1),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFA8F2CC),
    secondary = Color(0xFFB3CCBD),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCEE9D8),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE4DD),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDFE4DD),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceDim = Color(0xFF0F1511),
    surfaceBright = Color(0xFF353B36),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF262B27),
    surfaceContainerHighest = Color(0xFF303632),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFDFE4DD),
    inverseOnSurface = Color(0xFF2C322E),
    inversePrimary = Color(0xFF1F6A4D),
)

@Composable
fun SorterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
