package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = BackpackPrimaryBrightBlue,
    onPrimary = Color.White,
    primaryContainer = BackpackDarkPillActive,
    onPrimaryContainer = BackpackDarkPillText,
    secondary = BackpackSkyAccent,
    onSecondary = Color.Black,
    background = BackpackDarkBg,
    onBackground = BackpackDarkTextMain,
    surface = BackpackDarkSurface,
    onSurface = BackpackDarkTextMain,
    surfaceVariant = BackpackDarkCardNested,
    onSurfaceVariant = BackpackDarkTextMuted,
    outline = BackpackDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = BackpackPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = BackpackLightHeaderTint,
    onPrimaryContainer = BackpackPrimaryBlue,
    secondary = BackpackPrimaryBrightBlue,
    onSecondary = Color.White,
    background = BackpackLightBg,
    onBackground = BackpackLightTextMain,
    surface = BackpackLightSurface,
    onSurface = BackpackLightTextMain,
    surfaceVariant = BackpackLightHeaderTint,
    onSurfaceVariant = BackpackLightTextMuted,
    outline = BackpackLightCardBorder
)

@Composable
fun BackpackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve web app Backpack Blue identity
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
