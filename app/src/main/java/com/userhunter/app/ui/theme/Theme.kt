package com.userhunter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF062A28),
    secondary = Color(0xFF93C5FD),
    background = Color(0xFF101827),
    surface = Color(0xFF182235),
    surfaceVariant = Color(0xFF263449),
    onSurface = Color(0xFFE5EEF8),
    onBackground = Color(0xFFE5EEF8)
)

private val DayColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    secondary = Color(0xFF2563EB),
    background = Color(0xFFF5F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE5ECF4),
    onSurface = Color(0xFF152238),
    onBackground = Color(0xFF152238)
)

@Composable
fun UserHunterTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NightColors else DayColors,
        typography = androidx.compose.material3.MaterialTheme.typography,
        content = content
    )
}
