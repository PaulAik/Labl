package com.paulaik.labl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = ScannerTeal,
    onPrimary = Color.Black,
    secondary = ScannerTealDim,
    surface = Color(0xFF121212),
    background = Color.Black,
    onSurface = Color.White,
    onBackground = Color.White
)

@Composable
fun LablTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content
    )
}
