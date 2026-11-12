package com.refluxcare.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F0D2),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4E6356),
    secondaryContainer = Color(0xFFD0E8D8),
    tertiary = Color(0xFF3B6470),
    tertiaryContainer = Color(0xFFBFE9F7),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF6FBF6),
    surface = Color(0xFFF6FBF6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED6B0),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005236),
    onPrimaryContainer = Color(0xFFB6F0D2),
    secondary = Color(0xFFB4CCBC),
    secondaryContainer = Color(0xFF364B3F),
    tertiary = Color(0xFFA3CDDB),
    tertiaryContainer = Color(0xFF214C57),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A)
)

@Composable
fun RefluxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
