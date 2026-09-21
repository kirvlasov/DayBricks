package app.daybricks.planner.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.daybricks.planner.domain.ThemePreference

private val Light = lightColorScheme(primary = Color(0xFF245D46), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EBBC), onPrimaryContainer = Color(0xFF183627),
    secondaryContainer = Color(0xFFE2E8E1), background = Color(0xFFF7F9F5), surface = Color(0xFFFCFDF9),
    surfaceContainer = Color(0xFFECEFEA), surfaceContainerHigh = Color(0xFFE4E8E2),
    surfaceContainerHighest = Color(0xFFDCE1DB), outlineVariant = Color(0xFFBFC8BF))
private val DarkAccent = Color(0xFF9CD5FF)
private val Dark = darkColorScheme(primary = DarkAccent, onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF164B66), onPrimaryContainer = Color(0xFFD0ECFF),
    secondaryContainer = Color(0xFF303832), background = Color(0xFF101411), surface = Color(0xFF161B17),
    surfaceContainer = Color(0xFF202621), surfaceContainerHigh = Color(0xFF29302A),
    surfaceContainerHighest = Color(0xFF333A34), outlineVariant = Color(0xFF465047))

@Composable
fun DayBricksTheme(preference: ThemePreference = ThemePreference.SYSTEM, dynamic: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context).copy(primary = DarkAccent) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, content = content)
}
