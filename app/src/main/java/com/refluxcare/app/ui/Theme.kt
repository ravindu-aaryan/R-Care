package com.refluxcare.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val Teal = Color(0xFF14B8A6)
private val Violet = Color(0xFF8B5CF6)
private val Pink = Color(0xFFF472B6)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D9488),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F5EA),
    onPrimaryContainer = Color(0xFF042F2A),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4D8FF),
    onSecondaryContainer = Color(0xFF2A0F66),
    tertiary = Color(0xFFDB2777),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD6E8),
    onTertiaryContainer = Color(0xFF4A0A27),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF1FBF8),
    onBackground = Color(0xFF14213D),
    surface = Color(0xFFF7F5FF),
    onSurface = Color(0xFF14213D),
    surfaceVariant = Color(0xFFE3E4F3),
    onSurfaceVariant = Color(0xFF4A5068),
    outline = Color(0xFF8A90A8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF00332D),
    primaryContainer = Color(0xFF0F5F58),
    onPrimaryContainer = Color(0xFFB2F5EA),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF2A0F66),
    secondaryContainer = Color(0xFF4C2D96),
    onSecondaryContainer = Color(0xFFE4D8FF),
    tertiary = Color(0xFFF9A8D4),
    onTertiary = Color(0xFF4A0A27),
    tertiaryContainer = Color(0xFF8A2A5B),
    onTertiaryContainer = Color(0xFFFFD6E8),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF0B1224),
    onBackground = Color(0xFFEDEFFA),
    surface = Color(0xFF141A33),
    onSurface = Color(0xFFEDEFFA),
    surfaceVariant = Color(0xFF2A3050),
    onSurfaceVariant = Color(0xFFC3C8E0),
    outline = Color(0xFF8E94B0)
)

private val base = Typography()

private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

/** Glassmorphism design tokens; all getters follow the system dark mode. */
object Glass {
    val Teal500: Color get() = Teal
    val Violet500: Color get() = Violet
    val Pink400: Color get() = Pink

    /** Full-screen background gradient. */
    val background: Brush
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF053B3A), Color(0xFF1B2A6B), Color(0xFF4A1D8F), Color(0xFF8A2A6B)
                ),
                start = Offset(0f, 0f),
                end = Offset(1200f, 2400f)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFD5F7EC), Color(0xFFE2E4FF), Color(0xFFEBDDFF), Color(0xFFFFE0D2)
                ),
                start = Offset(0f, 0f),
                end = Offset(1200f, 2400f)
            )
        }

    /** Translucent card fill. */
    val surface: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)

    /** Translucent card border. */
    val border: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.60f)

    /** Top highlight colour used for the glass sheen. */
    val highlight: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.50f)

    /** Horizontal accent gradient primary -> secondary -> tertiary. */
    val accent: Brush
        @Composable @ReadOnlyComposable
        get() = Brush.horizontalGradient(
            if (isSystemInDarkTheme()) {
                listOf(Color(0xFF14B8A6), Color(0xFF8B5CF6), Color(0xFFF472B6))
            } else {
                listOf(Color(0xFF0D9488), Color(0xFF7C3AED), Color(0xFFDB2777))
            }
        )

    /** Colour for the large soft blobs behind the content. */
    val blobColors: List<Color>
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) {
            listOf(Color(0xFF14B8A6).copy(alpha = 0.45f), Color(0xFF8B5CF6).copy(alpha = 0.45f), Color(0xFFF472B6).copy(alpha = 0.35f))
        } else {
            listOf(Color(0xFF5EEAD4).copy(alpha = 0.55f), Color(0xFFA78BFA).copy(alpha = 0.45f), Color(0xFFFDA4AF).copy(alpha = 0.50f))
        }

    /** Translucent bar used for navigation bar. */
    val navBar: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFF0B1224).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.60f)
}

@Composable
fun RefluxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
