package com.posfab.shared.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PosColorScheme = darkColorScheme()

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PosColorScheme,
        content = content,
    )
}
