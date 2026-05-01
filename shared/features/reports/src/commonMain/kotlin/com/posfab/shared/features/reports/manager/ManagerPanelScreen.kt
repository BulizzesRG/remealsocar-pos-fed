package com.posfab.shared.features.reports.manager

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
fun ManagerPanelScreen(viewModel: ManagerPanelViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Text(
            "Panel gerencial",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        PosSectionCard(
            title = "Rango de fechas",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.dateFrom,
                    onValueChange = viewModel::onDateFromChange,
                    label = "Fecha inicial (YYYY-MM-DD)",
                    modifier = Modifier.width(180.dp),
                )
                PosTextField(
                    value = state.dateTo,
                    onValueChange = viewModel::onDateToChange,
                    label = "Fecha final (YYYY-MM-DD)",
                    modifier = Modifier.width(180.dp),
                )
                PosSecondaryButton(
                    text = "Refrescar",
                    onClick = viewModel::refreshDashboard,
                    enabled = !state.isLoadingDashboard,
                    modifier = Modifier.width(140.dp),
                )
            }
        }

        PosSectionCard(
            title = "Totales diarios",
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoadingDashboard) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.dailyTotals.isEmpty()) {
                EmptyReportText("Sin datos para el rango seleccionado.")
            } else {
                state.dailyTotals.forEachIndexed { index, item ->
                    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                        Text(
                            "${item.terminalId} · ${item.businessUnit ?: "-"}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Ventas: ${item.salesCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PosMoneyText(item.total)
                        }
                    }
                    if (index < state.dailyTotals.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }

        PosSectionCard(
            title = "Clientes con mayor deuda",
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.debtors.isEmpty()) {
                EmptyReportText("Sin datos de deuda.")
            } else {
                state.debtors.forEachIndexed { index, debtor ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                debtor.customerName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                debtor.customerId,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PosMoneyText(debtor.debtTotal)
                    }
                    if (index < state.debtors.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }

        PosSectionCard(
            title = "Mermas acumuladas",
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.waste.isEmpty()) {
                EmptyReportText("Sin datos de merma.")
            } else {
                state.waste.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                item.productName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                item.productId,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${item.wasteQty} ${item.unit}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                    if (index < state.waste.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }

        PosSectionCard(
            title = "Integrity check",
            modifier = Modifier.fillMaxWidth(),
            actions = {
                PosSecondaryButton(
                    text = "Refrescar",
                    onClick = viewModel::refreshIntegrity,
                    enabled = !state.isLoadingIntegrity,
                    modifier = Modifier.width(140.dp),
                )
            },
        ) {
            Text(
                "Ultima revision: ${state.integrityLastCheckedAt ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val integrity = state.integrity
            if (state.isLoadingIntegrity) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (integrity == null) {
                EmptyReportText("Sin validacion ejecutada.")
            } else {
                val hasCriticalIssues = integrity.issues.any { it.count > 0 }
                Text(
                    if (integrity.ok) "Estado: OK" else "Estado: Revisar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hasCriticalIssues) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )

                if (integrity.issues.isEmpty()) {
                    EmptyReportText("No se reportaron incidencias.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    ) {
                        items(integrity.issues) { issue ->
                            Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                                Text(
                                    "${issue.key}: ${issue.count}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (issue.count > 0) FontWeight.Bold else FontWeight.Normal,
                                )
                                issue.samples.take(5).forEach { sample ->
                                    Text(
                                        sample,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
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
private fun EmptyReportText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
