package com.posfab.shared.features.reports.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.theme.PosSpacing

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
            "Diagnosticos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        PosSectionCard(
            title = "Estado de la aplicacion",
            modifier = Modifier.fillMaxWidth(),
        ) {
            DiagnosticRow("Version", state.appVersion)
            DiagnosticRow("Build", state.appBuild)
            DiagnosticRow("Entorno", state.environment)
            DiagnosticRow("API", state.apiBaseUrl)
            DiagnosticRow("Terminal", state.terminal)
            DiagnosticRow("Usuario", "${state.username} (${state.roles})")
            DiagnosticRow("Ultima sincronizacion", state.lastSyncAt ?: "-")
            DiagnosticRow("Ultimo fallo", state.lastFailureAt ?: "-")
            DiagnosticRow(
                "Red",
                if (state.offline) "OFFLINE" else "ONLINE",
                highlight = state.offline,
            )
        }

        PosSectionCard(
            title = "Support bundle",
            modifier = Modifier.fillMaxWidth(),
        ) {
            PosPrimaryButton(
                text = "Generar support bundle",
                onClick = viewModel::buildSupportBundle,
                modifier = Modifier.width(240.dp),
            )

            if (state.supportBundleText.isNotBlank()) {
                OutlinedTextField(
                    value = state.supportBundleText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
                PosPrimaryButton(
                    text = "Copiar al portapapeles",
                    onClick = {
                        clipboardManager.setText(AnnotatedString(state.supportBundleText))
                    },
                    icon = Icons.Filled.ContentCopy,
                    modifier = Modifier.width(240.dp),
                )
            } else {
                Text(
                    "Genera un bundle para compartir diagnostico y logs recientes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    androidx.compose.foundation.layout.Row(
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
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
