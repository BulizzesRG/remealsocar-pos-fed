package com.posfab.shared.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun PosTheme(
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkMode) PosDarkColorScheme else PosLightColorScheme,
        typography = PosTypography,
        shapes = PosShapes,
        content = content,
    )
}
