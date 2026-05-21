package net.number42.dutchtrains.ui.theme

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

// NS corporate blue as static seed fallback (Android < 12)
private val NsBlue = Color(0xFF003082)
private val NsBlueDark = Color(0xFF7AB3FF)

val AppScreenBackground = Color(0xFFEBF1FF)
val AppCardBackground = Color.White

private val LightColorScheme = lightColorScheme(
    primary = NsBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001550),
)

private val DarkColorScheme = darkColorScheme(
    primary = NsBlueDark,
    onPrimary = Color(0xFF002780),
    primaryContainer = Color(0xFF003DAF),
    onPrimaryContainer = Color(0xFFD8E2FF),
)

@Composable
fun DutchTrainsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
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
        typography = AppTypography,
        content = content,
    )
}
