package no.ntnu.prog2007.questbook.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

/** Forest-green palette with readable surfaces in dark mode. */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFABD3B5), onPrimary = Color(0xFF173B28),
    secondary = Color(0xFFB5CCBA), onSecondary = Color(0xFF20392A),
    secondaryContainer = Color(0xFF344D3C), onSecondaryContainer = Color(0xFFD0E9D5),
    primaryContainer = Color(0xFF294D38), onPrimaryContainer = Color(0xFFCCEBD2),
    background = Color(0xFF121713), onBackground = Color(0xFFE1E8E0),
    surface = Color(0xFF1B231D), onSurface = Color(0xFFE1E8E0),
    surfaceVariant = Color(0xFF303C33), onSurfaceVariant = Color(0xFFBFCABD),
    outlineVariant = Color(0xFF3F4C42)
)

/** Near-white surfaces and restrained green accents from the QuestBook wireframes. */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF426B50), onPrimary = Color.White,
    secondary = Color(0xFF526F5A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EDE2), onSecondaryContainer = Color(0xFF24452F),
    primaryContainer = Color(0xFFE0EDE2), onPrimaryContainer = Color(0xFF24452F),
    background = Color(0xFFFAFBF9), onBackground = Color(0xFF1D2B22),
    surface = Color.White, onSurface = Color(0xFF1D2B22),
    surfaceVariant = Color(0xFFEDF1EB), onSurfaceVariant = Color(0xFF58655B),
    outline = Color(0xFF78857A), outlineVariant = Color(0xFFDEE5DC)
)

@Composable
fun QuestBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}