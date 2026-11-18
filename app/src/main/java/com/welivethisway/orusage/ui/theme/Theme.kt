package com.welivethisway.orusage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFF97316)
private val OrangeDark = Color(0xFFC2410C)
private val Slate = Color(0xFF1E293B)
private val SlateLight = Color(0xFFF8FAFC)

private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1A1005),
    secondary = Color(0xFF94A3B8),
    background = Color(0xFF0B1120),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1F2937),
    error = Color(0xFFF87171),
)

private val LightColors = lightColorScheme(
    primary = OrangeDark,
    onPrimary = Color.White,
    secondary = Slate,
    background = SlateLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFE2E8F0),
    error = Color(0xFFB91C1C),
)

@Composable
fun ORUsageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
