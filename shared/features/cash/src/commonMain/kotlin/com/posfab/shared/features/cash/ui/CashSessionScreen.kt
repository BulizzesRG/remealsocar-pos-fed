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
import com.posfab.shared.features.cash.domain.CashSessionStatus
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosMoneyText
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosStatusBadge
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosSpacing
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart

@Composable
fun CashSessionScreen(viewModel: CashSessionViewModel) {
    val state by viewModel.state.collectAsState()
    val session = state.currentSession
    val sessionStatus = session?.status ?: CashSessionStatus.NONE

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Caja",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Terminal: ${state.terminalId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PosSecondaryButton(
                    text = "Refrescar",
                    onClick = viewModel::refreshCurrentSession,
                    enabled = !state.isLoading,
                    modifier = Modifier.width(140.dp),
                )
            }
        }

        PosNoticeRow(
            notice = state.notice,
            errorMessage = state.errorMessage,
        )

        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            sessionStatus == CashSessionStatus.NONE -> {
                PosSectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Abrir sesion",
                ) {
                    PosTextField(
                        value = state.openingAmountInput,
                        onValueChange = viewModel::onOpeningAmountChange,
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
            }
            else -> {
                PosSectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Estado de caja",
                    actions = {
                        PosStatusBadge(status = sessionStatus.name)
                    },
                ) {
                    session?.let {
                        LabeledRow("Apertura de caja") {
                            PosMoneyText(it.openingAmount)
                        }
                        LabeledRow("Hora de apertura") {
                            Text(it.openedAt, style = MaterialTheme.typography.bodyMedium)
                        }
                        it.closingAmount?.let { closing ->
                            HorizontalDivider()
                            LabeledRow("Cierre de caja") {
                                PosMoneyText(closing)
                            }
                            it.closedAt?.let { closedAt ->
                                LabeledRow("Hora de cierre") {
                                    Text(closedAt, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                if (sessionStatus == CashSessionStatus.OPEN) {
                    PosPrimaryButton(
                        text = "Ir a venta",
                        onClick = viewModel.onNavigateToPos,
                        icon = Icons.Filled.ShoppingCart,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )

                    PosSectionCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Cerrar sesion",
                    ) {
                        PosTextField(
                            value = state.closingAmountInput,
                            onValueChange = viewModel::onClosingAmountChange,
                            label = "Efectivo de cierre",
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
                    "Resumen ${report.date} · Terminal ${report.terminalId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                LabeledRow("Total entradas") {
                    PosMoneyText(report.totalIn)
                }
                LabeledRow("Total salidas") {
                    PosMoneyText(report.totalOut)
                }
                LabeledRow("Movimientos") {
                    Text("${report.movements}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
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
