package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color.Black,
    primaryContainer = CyberBlue.copy(alpha = 0.2f),
    onPrimaryContainer = CyberCyan,
    secondary = HighEnergyAmber,
    onSecondary = Color.Black,
    tertiary = NeonEmerald,
    onTertiary = Color.Black,
    background = CarbonDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = SurfaceBorderDark,
    error = CrimsonAlert,
    onError = Color.White
)

private val LightColorScheme = DarkColorScheme // OBD-II Diagnostics Cockpit is best suited for dark high-contrast mode

@Composable
fun ThaiCarTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
