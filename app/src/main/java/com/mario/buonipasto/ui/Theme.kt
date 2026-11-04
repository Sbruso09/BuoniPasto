package com.mario.buonipasto.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Cobalt = Color(0xFF2346C7)
private val CobaltLight = Color(0xFFA9BBFF)
private val Berry = Color(0xFFB0145F)
private val BerryLight = Color(0xFFFF9CC8)

private val LightColors = lightColorScheme(
    primary = Cobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF0E1F66),
    secondary = Berry,
    onSecondary = Color.White,
    background = Color(0xFFF1F4F9),
    onBackground = Color(0xFF131A2B),
    surface = Color(0xFFF1F4F9),
    onSurface = Color(0xFF131A2B),
    surfaceVariant = Color(0xFFE2E7F0),
    onSurfaceVariant = Color(0xFF4A5468),
    surfaceContainerLow = Color(0xFFFFFFFF),
    outline = Color(0xFF7B869B),
)

private val DarkColors = darkColorScheme(
    primary = CobaltLight,
    onPrimary = Color(0xFF0E1F66),
    primaryContainer = Color(0xFF233A91),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = BerryLight,
    onSecondary = Color(0xFF5B0030),
    background = Color(0xFF10141F),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF10141F),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF2A3142),
    onSurfaceVariant = Color(0xFFB6BED0),
    surfaceContainerLow = Color(0xFF191E2C),
    outline = Color(0xFF8891A5),
)

@Composable
fun BuoniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** Each person keeps the same color on both phones, because members are sorted by id. */
@Composable
fun personColor(index: Int): Color =
    if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
