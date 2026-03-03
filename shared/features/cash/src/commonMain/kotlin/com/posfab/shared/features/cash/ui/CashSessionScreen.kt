package com.posfab.shared.features.cash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
fun CashSessionScreen(viewModel: CashSessionViewModel) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cash Session", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.terminalId,
                onValueChange = viewModel::onTerminalChange,
                label = { Text("Terminal") },
                enabled = state.canSwitchTerminal,
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )
            Button(onClick = viewModel::refreshCurrentSession, enabled = !state.isLoading) { Text("Refrescar") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val session = state.currentSession
                val statusLabel = session?.status?.name ?: "NONE"
                Text("Estado: $statusLabel", fontWeight = FontWeight.Bold)
                Text("Apertura: ${session?.openingCash ?: 0.0}")
                Text("Abierta en: ${session?.openedAt ?: "-"}")
                Text("Usuario: ${session?.openedBy ?: "-"}")
                Text("Entradas: ${session?.movementIn ?: 0.0}")
                Text("Salidas: ${session?.movementOut ?: 0.0}")
                Text("Esperado cierre: ${session?.expectedClose ?: 0.0}")
                Text("Efectivo en caja (running): ${(session?.openingCash ?: 0.0) + (session?.movementIn ?: 0.0) - (session?.movementOut ?: 0.0)}")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.openingCashInput,
                onValueChange = viewModel::onOpeningCashChange,
                label = { Text("opening_cash") },
                singleLine = true,
                modifier = Modifier.width(180.dp),
                enabled = !state.isOpenSubmitting,
            )
            Button(onClick = viewModel::openSession, enabled = !state.isOpenSubmitting) { Text("Abrir sesion") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.countedCashInput,
                onValueChange = viewModel::onCountedCashChange,
                label = { Text("counted_cash") },
                singleLine = true,
                modifier = Modifier.width(180.dp),
                enabled = !state.isCloseSubmitting,
            )
            Button(onClick = viewModel::closeSession, enabled = !state.isCloseSubmitting) { Text("Cerrar sesion") }
        }

        state.currentSession?.let {
            if (it.countedClose != null || it.delta != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Reconciliacion", fontWeight = FontWeight.Bold)
                        Text("expected_close: ${it.expectedClose}")
                        Text("counted_close: ${it.countedClose ?: 0.0}")
                        Text("delta: ${it.delta ?: 0.0}")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.reportDateInput,
                onValueChange = viewModel::onReportDateChange,
                label = { Text("Fecha (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.width(180.dp),
            )
            Button(onClick = viewModel::fetchDailyReport, enabled = !state.isReportLoading) { Text("Reporte diario") }
        }

        state.dailyReport?.let { report ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Daily report ${report.date} - ${report.terminalId}", fontWeight = FontWeight.Bold)
                    Text("opening_cash: ${report.openingCash}")
                    Text("movement_in: ${report.movementIn}")
                    Text("movement_out: ${report.movementOut}")
                    Text("sales_cash: ${report.salesCash}")
                    Text("expected_close: ${report.expectedClose}")
                    Text("counted_close: ${report.countedClose ?: 0.0}")
                    Text("delta: ${report.delta ?: 0.0}")
                }
            }
        }

        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
