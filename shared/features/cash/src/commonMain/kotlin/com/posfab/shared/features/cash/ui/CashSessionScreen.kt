package com.posfab.shared.features.cash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosMoneyText
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosStatusBadge
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun CashSessionScreen(viewModel: CashSessionViewModel) {
    val state by viewModel.state.collectAsState()
    val session = state.currentSession

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
    ) {
        Text(
            "Caja",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            PosTextField(
                value = state.terminalId,
                onValueChange = viewModel::onTerminalChange,
                label = "Terminal",
                enabled = state.canSwitchTerminal,
                modifier = Modifier.width(180.dp),
            )
            PosSecondaryButton(
                text = "Refrescar",
                onClick = viewModel::refreshCurrentSession,
                enabled = !state.isLoading,
                modifier = Modifier.width(140.dp),
            )
        }

        PosSectionCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Estado de caja",
            actions = {
                PosStatusBadge(status = session?.status?.name ?: "NONE")
            },
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else if (session == null) {
                Text(
                    "No hay sesion activa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LabeledRow("Apertura de caja") {
                    PosMoneyText(session.openingCash)
                }
                LabeledRow("Entradas") {
                    PosMoneyText(session.movementIn)
                }
                LabeledRow("Salidas") {
                    PosMoneyText(session.movementOut)
                }
                LabeledRow("Efectivo esperado") {
                    PosMoneyText(session.expectedClose)
                }
                LabeledRow("Disponible en caja") {
                    PosMoneyText(session.openingCash + session.movementIn - session.movementOut)
                }
                HorizontalDivider()
                LabeledRow("Abierta por") {
                    Text(session.openedBy ?: "-", style = MaterialTheme.typography.bodyMedium)
                }
                LabeledRow("Hora de apertura") {
                    Text(session.openedAt ?: "-", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
            verticalAlignment = Alignment.Top,
        ) {
            PosSectionCard(
                modifier = Modifier.weight(1f),
                title = "Abrir sesion",
            ) {
                PosTextField(
                    value = state.openingCashInput,
                    onValueChange = viewModel::onOpeningCashChange,
                    label = "Efectivo de apertura",
                    enabled = !state.isOpenSubmitting,
                    modifier = Modifier.width(220.dp),
                )
                PosPrimaryButton(
                    text = "Abrir sesion",
                    onClick = viewModel::openSession,
                    enabled = !state.isOpenSubmitting,
                    isLoading = state.isOpenSubmitting,
                    modifier = Modifier.width(180.dp),
                )
            }

            PosSectionCard(
                modifier = Modifier.weight(1f),
                title = "Cerrar sesion",
            ) {
                PosTextField(
                    value = state.countedCashInput,
                    onValueChange = viewModel::onCountedCashChange,
                    label = "Efectivo contado",
                    enabled = !state.isCloseSubmitting,
                    modifier = Modifier.width(220.dp),
                )
                PosPrimaryButton(
                    text = "Cerrar sesion",
                    onClick = viewModel::requestCloseSession,
                    enabled = !state.isCloseSubmitting,
                    isLoading = state.isCloseSubmitting,
                    modifier = Modifier.width(180.dp),
                )
            }
        }

        session?.let {
            if (it.countedClose != null || it.delta != null) {
                PosSectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Reconciliacion",
                ) {
                    LabeledRow("Efectivo esperado") {
                        PosMoneyText(it.expectedClose)
                    }
                    LabeledRow("Efectivo contado") {
                        PosMoneyText(it.countedClose ?: 0.0)
                    }
                    LabeledRow("Diferencia") {
                        PosMoneyText(it.delta ?: 0.0)
                    }
                }
            }
        }

        HorizontalDivider()

        PosSectionCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Reporte diario",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                PosTextField(
                    value = state.reportDateInput,
                    onValueChange = viewModel::onReportDateChange,
                    label = "Fecha (YYYY-MM-DD)",
                    modifier = Modifier.width(180.dp),
                    enabled = !state.isReportLoading,
                )
                PosSecondaryButton(
                    text = "Consultar",
                    onClick = viewModel::fetchDailyReport,
                    enabled = !state.isReportLoading,
                    modifier = Modifier.width(140.dp),
                )
            }

            state.dailyReport?.let { report ->
                HorizontalDivider()
                Text(
                    "Resumen ${report.date} · ${report.terminalId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                LabeledRow("Apertura de caja") {
                    PosMoneyText(report.openingCash)
                }
                LabeledRow("Entradas") {
                    PosMoneyText(report.movementIn)
                }
                LabeledRow("Salidas") {
                    PosMoneyText(report.movementOut)
                }
                LabeledRow("Ventas en efectivo") {
                    PosMoneyText(report.salesCash)
                }
                LabeledRow("Efectivo esperado") {
                    PosMoneyText(report.expectedClose)
                }
                LabeledRow("Efectivo contado") {
                    PosMoneyText(report.countedClose ?: 0.0)
                }
                LabeledRow("Diferencia") {
                    PosMoneyText(report.delta ?: 0.0)
                }
            }
        }

        PosNoticeRow(
            notice = state.notice,
            errorMessage = state.errorMessage,
        )
    }

    state.closeConfirmDialog?.let { dialog ->
        PosConfirmDialog(
            title = dialog.title,
            message = dialog.message,
            confirmLabel = dialog.confirmLabel,
            cancelLabel = dialog.cancelLabel,
            onConfirm = viewModel::closeSession,
            onDismiss = viewModel::dismissCloseSessionDialog,
        )
    }
}

@Composable
private fun LabeledRow(
    label: String,
    content: @Composable () -> Unit,
) {
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
        content()
    }
}
