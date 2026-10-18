package com.posfab.shared.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val SCROLLBAR_THICKNESS = 12.dp
private val SCROLLBAR_GAP = 4.dp
private val SCROLLBAR_MIN_THUMB = 40.dp
private const val SCROLLBAR_IDLE_ALPHA = 0.55f

val PosScrollbarReserve = SCROLLBAR_THICKNESS + SCROLLBAR_GAP

@Composable
fun PosScrollableColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = PosScrollbarReserve)
                .verticalScroll(state)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
        PosVerticalScrollbar(rememberScrollbarAdapter(state))
    }
}

@Composable
fun PosLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(end = PosScrollbarReserve),
            state = state,
            verticalArrangement = verticalArrangement,
            contentPadding = contentPadding,
            content = content,
        )
        PosVerticalScrollbar(rememberScrollbarAdapter(state))
    }
}

@Composable
fun BoxScope.PosVerticalScrollbar(adapter: ScrollbarAdapter) {
    if (adapter.contentSize <= adapter.viewportSize) return
    val colors = MaterialTheme.colorScheme
    VerticalScrollbar(
        adapter = adapter,
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        style = ScrollbarStyle(
            minimalHeight = SCROLLBAR_MIN_THUMB,
            thickness = SCROLLBAR_THICKNESS,
            shape = RoundedCornerShape(SCROLLBAR_THICKNESS / 2),
            hoverDurationMillis = 150,
            unhoverColor = colors.onSurfaceVariant.copy(alpha = SCROLLBAR_IDLE_ALPHA),
            hoverColor = colors.primary,
        ),
    )
}
