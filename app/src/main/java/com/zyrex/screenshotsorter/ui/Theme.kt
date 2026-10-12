package com.zyrex.screenshotsorter.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6A4D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F2CC),
    onPrimaryContainer = Color(0xFF002114),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5B1),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFA8F2CC),
)

@Composable
fun SorterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        // Android 12+ can take the colours from the user's wallpaper.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
