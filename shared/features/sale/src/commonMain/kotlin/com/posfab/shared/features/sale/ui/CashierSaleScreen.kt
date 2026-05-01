package com.posfab.shared.features.sale.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosDestructiveButton
import com.posfab.shared.ui.components.PosLoadingOverlay
import com.posfab.shared.ui.components.PosMoneyText
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.components.PosTotalDisplay
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun CashierSaleScreen(viewModel: CashierSaleViewModel) {
    val state by viewModel.state.collectAsState()

    if (state.isInitializing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
            ) {
                CircularProgressIndicator()
                Text("Cargando venta...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(PosLayout.contentPadding)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    if (state.confirmDialog != null) return@onPreviewKeyEvent false
                    if (state.isCheckoutInFlight) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter -> {
                            if (state.barcodeInput.isNotBlank()) {
                                viewModel.addByBarcode()
                            } else {
                                viewModel.searchProducts()
                            }
                            true
                        }
                        Key.F5 -> {
                            viewModel.validateDraft()
                            true
                        }
                        Key.F9 -> {
                            viewModel.checkoutCash()
                            true
                        }
                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                Text(
                    "Buscar producto",
                    style = MaterialTheme.typography.titleMedium,
                )

                PosTextField(
                    value = state.barcodeInput,
                    onValueChange = viewModel::onBarcodeChange,
                    label = "Codigo de barras (Enter)",
                    enabled = !state.isBusy,
                    leadingIcon = {
                        androidx.compose.material3.Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                        )
                    },
                )

                PosTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    label = "Buscar por nombre",
                    enabled = !state.isBusy,
                    leadingIcon = {
                        androidx.compose.material3.Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                )

                PosSectionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    title = if (state.searchResults.isEmpty()) {
                        "Resultados"
                    } else {
                        "Resultados (${state.searchResults.size})"
                    },
                ) {
                    if (state.searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = PosSpacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Sin resultados",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                        ) {
                            items(state.searchResults) { product ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !state.isBusy) {
                                            viewModel.addProduct(product)
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(PosSpacing.sm),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = product.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = listOfNotNull(product.sku, product.barcode)
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        PosMoneyText(
                                            amount = product.unitPrice,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Enter: agregar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "F5: validar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "F9: cobrar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                val canEditLine = !state.isBusy &&
                    !state.isCheckoutInFlight &&
                    state.selectedLineId != null
                val canRunSecondary = !state.isBusy && !state.isCheckoutInFlight
                val canCheckout = !state.isBusy && !state.isCheckoutInFlight

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Venta en caja",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                        PosSecondaryButton(
                            text = "+",
                            onClick = viewModel::incrementSelectedLineQty,
                            enabled = canEditLine,
                            modifier = Modifier.width(56.dp),
                        )
                        PosSecondaryButton(
                            text = "-",
                            onClick = viewModel::decrementSelectedLineQty,
                            enabled = canEditLine,
                            modifier = Modifier.width(56.dp),
                        )
                        PosDestructiveButton(
                            text = "Eliminar",
                            onClick = viewModel::removeSelectedLine,
                            enabled = canEditLine,
                            icon = Icons.Filled.Delete,
                        )
                    }
                }

                PosSectionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val lines = state.draft?.lines.orEmpty()
                    if (lines.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = PosSpacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Venta vacia. Busca o escanea un producto.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                        ) {
                            items(lines) { line ->
                                val isSelected = line.id == state.selectedLineId
                                val background = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                                val contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(background, MaterialTheme.shapes.small)
                                        .clickable { viewModel.selectLine(line.id) }
                                        .padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = line.productName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (line.lotTracked) {
                                                "Lote: ${line.lotId ?: "pendiente"}"
                                            } else {
                                                "Sin lote"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (line.lotTracked && line.lotId == null) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                    Text(
                                        text = formatDisplayQty(line.qty, line.unit),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = PosSpacing.sm),
                                        color = contentColor,
                                    )
                                    PosMoneyText(
                                        amount = line.lineTotal,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = contentColor,
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.selectedLineId != null) {
                    PosSectionCard(
                        title = "Editar linea seleccionada",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            PosTextField(
                                value = state.editQtyInput,
                                onValueChange = viewModel::onEditQtyChange,
                                label = "Cantidad",
                                modifier = Modifier.width(100.dp),
                                enabled = canRunSecondary,
                            )
                            PosTextField(
                                value = state.editUnitInput,
                                onValueChange = viewModel::onEditUnitChange,
                                label = "Unidad",
                                modifier = Modifier.width(110.dp),
                                enabled = canRunSecondary,
                            )
                            PosTextField(
                                value = state.editPriceInput,
                                onValueChange = viewModel::onEditPriceChange,
                                label = "Precio",
                                modifier = Modifier.width(120.dp),
                                enabled = canRunSecondary,
                            )
                            PosTextField(
                                value = state.editLotInput,
                                onValueChange = viewModel::onEditLotChange,
                                label = "Lote",
                                modifier = Modifier.width(140.dp),
                                enabled = canRunSecondary,
                            )
                            PosPrimaryButton(
                                text = "Aplicar",
                                onClick = viewModel::applySelectedLineEdits,
                                enabled = canRunSecondary,
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                }

                PosTotalDisplay(
                    subtotal = state.draft?.totals?.subtotal ?: 0.0,
                    tax = state.draft?.totals?.tax ?: 0.0,
                    total = state.draft?.totals?.total ?: 0.0,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.validationIssues.isNotEmpty()) {
                    PosSectionCard(
                        title = "Observaciones",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        state.validationIssues.forEach { issue ->
                            Text(
                                text = "- ${issue.lineId ?: "General"}: ${issue.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PosSecondaryButton(
                        text = "Validar",
                        onClick = viewModel::validateDraft,
                        enabled = canRunSecondary,
                        icon = Icons.Filled.CheckCircle,
                        modifier = Modifier.weight(1f),
                    )
                    PosSecondaryButton(
                        text = "Lotes",
                        onClick = viewModel::resolveLots,
                        enabled = canRunSecondary,
                        icon = Icons.Filled.Inventory,
                        modifier = Modifier.weight(1f),
                    )
                    PosSecondaryButton(
                        text = "Nueva venta",
                        onClick = viewModel::startNewSale,
                        enabled = canRunSecondary,
                        icon = Icons.Filled.Add,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(PosSpacing.lg))
                    PosPrimaryButton(
                        text = "Cobrar efectivo  F9",
                        onClick = viewModel::checkoutCash,
                        enabled = canCheckout,
                        icon = Icons.Filled.Payments,
                        isLoading = state.isCheckoutInFlight,
                        modifier = Modifier.weight(1.5f),
                    )
                    PosPrimaryButton(
                        text = "Cobrar credito",
                        onClick = viewModel::checkoutCredit,
                        enabled = canCheckout,
                        icon = Icons.Filled.CreditCard,
                        isLoading = state.isCheckoutInFlight,
                        modifier = Modifier.weight(1.5f),
                    )
                }

                state.checkoutResult?.let { result ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(PosSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = "Venta completada. Folio: ${result.folio}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                PosNoticeRow(
                    notice = state.notice,
                    errorMessage = state.errorMessage,
                )
            }
        }

        PosLoadingOverlay(
            isLoading = state.isCheckoutInFlight,
            message = "Procesando cobro...",
        )

        state.confirmDialog?.let { dialog ->
            PosConfirmDialog(
                title = dialog.title,
                message = dialog.message,
                confirmLabel = dialog.confirmLabel,
                cancelLabel = dialog.cancelLabel,
                isDestructive = dialog.isDestructive,
                onConfirm = viewModel::confirmStartNewSale,
                onDismiss = viewModel::dismissConfirmDialog,
            )
        }
    }
}

private fun formatDisplayQty(qty: Double, unit: String): String {
    val normalized = if (qty % 1.0 == 0.0) qty.toInt().toString() else qty.toString()
    return "$normalized $unit"
}
