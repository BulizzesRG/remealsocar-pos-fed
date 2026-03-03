package com.posfab.shared.features.reports.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel) {
    val state by viewModel.state.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("version: ${state.appVersion}")
                Text("build: ${state.appBuild}")
                Text("env: ${state.environment}")
                Text("api: ${state.apiBaseUrl}")
                Text("terminal: ${state.terminal}")
                Text("user: ${state.username} (${state.roles})")
                Text("last sync: ${state.lastSyncAt ?: "-"}")
                Text("last failure: ${state.lastFailureAt ?: "-"}")
                Text(
                    if (state.offline) "NETWORK: OFFLINE" else "NETWORK: ONLINE",
                    color = if (state.offline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Button(onClick = viewModel::buildSupportBundle) { Text("Build support bundle") }

        if (state.supportBundleText.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Support bundle (sanitized)", fontWeight = FontWeight.Bold)
                    Text(state.supportBundleText)
                }
            }
        }

        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
