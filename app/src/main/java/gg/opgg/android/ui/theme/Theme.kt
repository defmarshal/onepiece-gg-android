package gg.opgg.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFEE6DCA),
    onPrimary = Color(0xFF23131E),
    secondary = Color(0xFF5865F2),
    background = Color(0xFF13171B),
    onBackground = Color(0xFFE6E9EE),
    surface = Color(0xFF1B2026),
    onSurface = Color(0xFFE6E9EE),
    surfaceVariant = Color(0xFF20252B),
    onSurfaceVariant = Color(0xFFB0BAC5),
    outline = Color(0xFF434A56),
    error = Color(0xFFFF8A80),
)

@Composable
fun OpggTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
