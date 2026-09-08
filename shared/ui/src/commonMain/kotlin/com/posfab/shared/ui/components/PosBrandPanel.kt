package com.posfab.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.theme.PosAmberPrimary
import com.posfab.shared.ui.theme.PosNeutral950

@Composable
fun PosBrandPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .drawBehind {
                drawRect(color = PosNeutral950)
                val step = 24.dp.toPx()
                val stroke = 1f
                val color = PosAmberPrimary.copy(alpha = 0.06f)
                var x = 0f
                while (x < size.width + size.height) {
                    drawLine(
                        color = color,
                        start = Offset(x, 0f),
                        end = Offset(x - size.height, size.height),
                        strokeWidth = stroke,
                    )
                    x += step
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
