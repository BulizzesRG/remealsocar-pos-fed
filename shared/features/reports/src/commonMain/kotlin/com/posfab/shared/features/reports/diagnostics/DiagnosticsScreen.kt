package com.posfab.shared.features.reports.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.posfab.shared.core.model.terminalDisplayName
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.theme.PosSpacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel) {
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Text(
            DiagnosticsStrings.TITLE,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        PosSectionCard(
            title = DiagnosticsStrings.STATUS_CARD,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DiagnosticRow(DiagnosticsStrings.ROW_VERSION, state.appVersion)
            DiagnosticRow(DiagnosticsStrings.ROW_TERMINAL, terminalDisplayName(state.terminal))
            DiagnosticRow(DiagnosticsStrings.ROW_USER, state.username)
            DiagnosticRow(
                DiagnosticsStrings.ROW_CONNECTION,
                if (state.offline) DiagnosticsStrings.DISCONNECTED else DiagnosticsStrings.CONNECTED,
                highlight = state.offline,
            )
            DiagnosticRow(DiagnosticsStrings.ROW_LAST_CONNECTION, lastConnectionLabel(state.lastSyncAt))
        }

        PosSectionCard(
            title = DiagnosticsStrings.SUPPORT_CARD,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PosPrimaryButton(
                text = DiagnosticsStrings.COPY_SUPPORT,
                onClick = { clipboardManager.setText(AnnotatedString(viewModel.buildSupportBundle())) },
                icon = Icons.Filled.ContentCopy,
            )
        }

        PosNoticeRow(
            notice = state.notice,
            errorMessage = null,
        )
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

internal fun lastConnectionLabel(iso: String?): String = iso?.let { raw ->
    runCatching {
        val local = Instant.parse(raw).toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.hour}:${local.minute.toString().padStart(2, '0')}"
    }.getOrNull()
} ?: DiagnosticsStrings.EMPTY_VALUE
