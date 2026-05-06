package app.fixd.messaging.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette pulled from the public Fixd.fun site.
private val FixdBlue = Color(0xFF3F3CFF)
private val FixdIndigo = Color(0xFF5B54FF)
private val FixdPurple = Color(0xFF7C3AED)
private val FixdViolet = Color(0xFF9333EA)
private val FixdGold = Color(0xFFFFD768)
private val FixdInk = Color(0xFF1A1A2E)
private val FixdSurface = Color(0xFFFFFFFF)
private val FixdSurfaceDark = Color(0xFF0F1228)

private val LightColors = lightColorScheme(
    primary = FixdIndigo,
    onPrimary = Color.White,
    primaryContainer = FixdBlue,
    onPrimaryContainer = Color.White,
    secondary = FixdPurple,
    onSecondary = Color.White,
    tertiary = FixdGold,
    onTertiary = FixdInk,
    background = FixdSurface,
    onBackground = FixdInk,
    surface = FixdSurface,
    onSurface = FixdInk
)

private val DarkColors = darkColorScheme(
    primary = FixdIndigo,
    onPrimary = Color.White,
    primaryContainer = FixdViolet,
    onPrimaryContainer = Color.White,
    secondary = FixdPurple,
    onSecondary = Color.White,
    tertiary = FixdGold,
    onTertiary = FixdInk,
    background = FixdSurfaceDark,
    onBackground = Color.White,
    surface = FixdSurfaceDark,
    onSurface = Color.White
)

@Composable
fun FixdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
