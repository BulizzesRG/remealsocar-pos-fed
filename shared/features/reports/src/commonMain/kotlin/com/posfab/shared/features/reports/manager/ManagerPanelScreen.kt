package com.posfab.shared.features.reports.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ManagerPanelScreen(viewModel: ManagerPanelViewModel) {
    val state by viewModel.state.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Manager Control Panel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = state.dateFrom, onValueChange = viewModel::onDateFromChange, label = { Text("Date from") }, singleLine = true)
            OutlinedTextField(value = state.dateTo, onValueChange = viewModel::onDateToChange, label = { Text("Date to") }, singleLine = true)
            Button(onClick = viewModel::refreshDashboard, enabled = !state.isLoadingDashboard) { Text("Refrescar") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Daily totals", fontWeight = FontWeight.Bold)
                if (state.isLoadingDashboard) {
                    Text("Cargando dashboard...")
                } else if (state.dailyTotals.isEmpty()) {
                    Text("Sin datos")
                } else {
                    state.dailyTotals.forEach {
                        Text("${it.terminalId} ${it.businessUnit ?: "-"} count=${it.salesCount} total=${it.total}")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Top debtors", fontWeight = FontWeight.Bold)
                if (state.debtors.isEmpty()) Text("Sin datos")
                state.debtors.forEach { Text("${it.customerName} (${it.customerId}) -> ${it.debtTotal}") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Waste totals", fontWeight = FontWeight.Bold)
                if (state.waste.isEmpty()) Text("Sin datos")
                state.waste.forEach { Text("${it.productName}: ${it.wasteQty} ${it.unit}") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Integrity Check", fontWeight = FontWeight.Bold)
                    Button(onClick = viewModel::refreshIntegrity, enabled = !state.isLoadingIntegrity) { Text("Manual refresh") }
                }
                Text("Last checked: ${state.integrityLastCheckedAt ?: "-"}")

                val integrity = state.integrity
                if (integrity == null) {
                    Text("Sin validacion ejecutada")
                } else {
                    val isCritical = integrity.issues.any { it.count > 0 }
                    Text(
                        if (integrity.ok) "OK" else "NOT OK",
                        color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(integrity.issues) { issue ->
                            Column {
                                Text("${issue.key}: count=${issue.count}", fontWeight = if (issue.count > 0) FontWeight.Bold else FontWeight.Normal)
                                issue.samples.take(5).forEach { sample -> Text("- $sample") }
                            }
                        }
                    }
                }
            }
        }

        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
