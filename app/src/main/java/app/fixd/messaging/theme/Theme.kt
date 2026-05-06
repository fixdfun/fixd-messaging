package app.fixd.messaging.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Fixd brand palette
val FixdIndigo = Color(0xFF3F3CFF)
val FixdIndigoLight = Color(0xFF5B54FF)
val FixdViolet = Color(0xFF7C3AED)
val FixdMagenta = Color(0xFF9333EA)
val FixdGold = Color(0xFFFFD768)
val FixdInk = Color(0xFF1A1A2E)
val FixdInkLight = Color(0xFF2A2A44)

private val LightColors = lightColorScheme(
    primary = FixdIndigo,
    onPrimary = Color.White,
    primaryContainer = FixdIndigoLight,
    onPrimaryContainer = Color.White,
    secondary = FixdViolet,
    onSecondary = Color.White,
    secondaryContainer = FixdMagenta,
    onSecondaryContainer = Color.White,
    tertiary = FixdGold,
    onTertiary = FixdInk,
    background = Color(0xFFF7F7FB),
    onBackground = FixdInk,
    surface = Color.White,
    onSurface = FixdInk
)

private val DarkColors = darkColorScheme(
    primary = FixdIndigoLight,
    onPrimary = Color.White,
    primaryContainer = FixdIndigo,
    onPrimaryContainer = Color.White,
    secondary = FixdViolet,
    onSecondary = Color.White,
    tertiary = FixdGold,
    onTertiary = FixdInk,
    background = FixdInk,
    onBackground = Color.White,
    surface = FixdInkLight,
    onSurface = Color.White
)

@Composable
fun FixdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
