package com.posfab.shared.features.reports.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.components.PosMoneyText
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun DailyHistoryScreen(viewModel: DailyHistoryViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Text(
            "Historial diario",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        PosSectionCard(
            title = "Filtros",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.dateInput,
                    onValueChange = viewModel::onDateChange,
                    label = "Fecha (YYYY-MM-DD)",
                    modifier = Modifier.width(180.dp),
                )
                PosTextField(
                    value = state.terminalId,
                    onValueChange = viewModel::onTerminalChange,
                    enabled = state.canSwitchTerminal,
                    label = "Terminal",
                    modifier = Modifier.width(160.dp),
                )
                PosSecondaryButton(
                    text = "Refrescar",
                    onClick = viewModel::refreshNow,
                    modifier = Modifier.width(140.dp),
                )
            }
        }

        PosSectionCard(
            title = "Resumen de caja",
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoadingCashSummary) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val cash = state.cashSummary
                if (cash == null) {
                    EmptyDailyText("Sin resumen disponible.")
                } else {
                    DailyMoneyRow("Apertura de caja", cash.openingCash)
                    DailyMoneyRow("Entradas", cash.movementIn)
                    DailyMoneyRow("Salidas", cash.movementOut)
                    DailyMoneyRow("Efectivo esperado", cash.expectedClose)
                    DailyMoneyRow("Efectivo contado", cash.countedClose ?: 0.0)
                }
            }
        }

        PosSectionCard(
            title = "Ventas del dia",
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoadingSales) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.sales.isEmpty()) {
                EmptyDailyText("Sin ventas para la fecha seleccionada.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                ) {
                    items(state.sales) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                                Text(
                                    item.folio,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${item.createdAt} · ${item.terminalId}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    item.paymentStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                PosMoneyText(item.total)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        PosNoticeRow(
            notice = state.notice,
            errorMessage = state.errorMessage,
        )
    }
}

@Composable
private fun DailyMoneyRow(label: String, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PosMoneyText(amount)
    }
}

@Composable
private fun EmptyDailyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
