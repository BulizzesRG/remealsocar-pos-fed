package com.posfab.shared.features.reports.daily

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
fun DailyHistoryScreen(viewModel: DailyHistoryViewModel) {
    val state by viewModel.state.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Daily History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.dateInput,
                onValueChange = viewModel::onDateChange,
                label = { Text("Fecha") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.terminalId,
                onValueChange = viewModel::onTerminalChange,
                enabled = state.canSwitchTerminal,
                label = { Text("Terminal") },
                singleLine = true,
            )
            Button(onClick = viewModel::refreshNow) { Text("Refrescar") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cash Summary", fontWeight = FontWeight.Bold)
                if (state.isLoadingCashSummary) {
                    Text("Cargando resumen de caja...")
                } else {
                    val cash = state.cashSummary
                    if (cash == null) {
                        Text("Sin resumen disponible")
                    } else {
                        Text("opening_cash: ${cash.openingCash}")
                        Text("movement_in: ${cash.movementIn}")
                        Text("movement_out: ${cash.movementOut}")
                        Text("expected_close: ${cash.expectedClose}")
                        Text("counted_close: ${cash.countedClose ?: 0.0}")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Daily Sales", fontWeight = FontWeight.Bold)
                if (state.isLoadingSales) {
                    Text("Cargando ventas...")
                } else if (state.sales.isEmpty()) {
                    Text("Sin ventas para la fecha seleccionada")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.sales) { item ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.folio)
                                Text(item.paymentStatus)
                                Text(item.createdAt)
                                Text(item.total.toString())
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
