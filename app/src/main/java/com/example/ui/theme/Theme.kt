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
    primary = ElectricGreen,
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005237),
    onPrimaryContainer = Color(0xFF6BFFC4),
    secondary = ElectricCyan,
    onSecondary = Color(0xFF003548),
    secondaryContainer = Color(0xFF004D68),
    onSecondaryContainer = Color(0xFFBCE9FF),
    tertiary = ElectricAmber,
    onTertiary = Color(0xFF452B00),
    background = DarkBg,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceSubtle,
    error = ElectricRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4FCE0),
    onPrimaryContainer = Color(0xFF002113),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF001F2B),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceSubtle,
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek tech dark mode for battery conservation
    dynamicColor: Boolean = false, // Keep high-contrast custom telemetry palette
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
        typography = Typography,
        content = content
    )
}
