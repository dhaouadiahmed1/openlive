package app.openlive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// OpenLive v0.1 palette — dark blue (like the "blue" Drama Live build
// AHMED had, but this is a clean, original theme), light-first.
val OpenLivePrimary = Color(0xFF1D4ED8)
val OpenLiveAccent = Color(0xFF3B82F6)
val OpenLiveDeep = Color(0xFF1E3A8A)

private val LightColors = lightColorScheme(
    primary = OpenLivePrimary,
    onPrimary = Color.White,
    secondary = OpenLiveAccent,
    onSecondary = Color.White,
    tertiary = OpenLiveDeep,
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEFEFF2),
    onSurfaceVariant = Color(0xFF6B6B70),
    outline = Color(0xFFE2E2E8)
)

private val DarkColors = darkColorScheme(
    primary = OpenLiveAccent,
    onPrimary = Color(0xFF111111),
    secondary = OpenLiveAccent,
    onSecondary = Color(0xFF111111),
    tertiary = OpenLivePrimary,
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF273449),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF3A3A3E)
)

@Composable
fun OpenLiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
