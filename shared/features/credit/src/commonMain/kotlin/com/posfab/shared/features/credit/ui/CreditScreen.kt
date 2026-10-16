package com.posfab.shared.features.credit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor
import com.posfab.shared.features.credit.domain.LedgerEntry
import com.posfab.shared.features.credit.domain.LedgerEntryType
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun CreditScreen(viewModel: CreditViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) { viewModel.load() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize()
                .padding(PosLayout.contentPadding),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
        ) {
            if (state.showsDetail) {
                StatementView(
                    state = state,
                    onBack = viewModel::back,
                )
            } else {
                DebtorList(
                    state = state,
                    onFilterChange = viewModel::onFilterChange,
                    onRefresh = viewModel::refresh,
                    onSelect = viewModel::select,
                )
            }
        }
    }
}

@Composable
private fun DebtorList(
    state: CreditState,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val hasDebtors = state.debtors.isNotEmpty()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (hasDebtors) OwedSummary(state.totalOwed, state.debtorCount)
        }
        PosSecondaryButton(
            text = CreditStrings.REFRESH,
            onClick = onRefresh,
            enabled = !state.isLoading,
            icon = Icons.Filled.Refresh,
        )
    }

    if (hasDebtors) {
        PosTextField(
            value = state.filter,
            onValueChange = onFilterChange,
            label = CreditStrings.SEARCH_LABEL,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )
    }

    state.errorMessage?.let { PosErrorBanner(it) }

    val visible = state.visibleDebtors
    when {
        state.isLoading && !hasDebtors -> CenteredLoading()
        !hasDebtors && state.errorMessage == null -> CenteredMessage(
            text = CreditStrings.EMPTY,
            style = MaterialTheme.typography.headlineSmall,
        )
        !hasDebtors -> Unit
        visible.isEmpty() -> CenteredMessage(
            text = CreditStrings.NO_MATCHES,
            style = MaterialTheme.typography.titleMedium,
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
        ) {
            items(visible, key = { it.id }) { debtor ->
                DebtorRow(
                    debtor = debtor,
                    nowEpochMillis = state.nowEpochMillis,
                    onClick = { onSelect(debtor.id) },
                )
            }
        }
    }
}

@Composable
private fun OwedSummary(total: Double, count: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val summary = CreditStrings.owedSummary(total, count)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = summary },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Column {
            Text(
                text = CreditStrings.OWED_LABEL,
                style = MaterialTheme.typography.labelLarge,
                color = muted,
            )
            Text(
                text = CreditStrings.money(total),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = CreditStrings.clientCount(count),
            style = MaterialTheme.typography.bodyLarge,
            color = muted,
            modifier = Modifier.padding(bottom = PosSpacing.xs),
        )
    }
}

@Composable
private fun DebtorRow(
    debtor: Debtor,
    nowEpochMillis: Long,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
            ) {
                CustomerName(
                    name = debtor.name,
                    active = debtor.active,
                    style = MaterialTheme.typography.titleMedium,
                )
                sinceLabel(debtor.owesSince, nowEpochMillis)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = CreditStrings.money(debtor.balance),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CustomerName(
    name: String,
    active: Boolean,
    style: TextStyle,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            append(name)
            if (!active) {
                withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) {
                    append(CreditStrings.SEPARATOR)
                    append(CreditStrings.INACTIVE)
                }
            }
        },
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatementView(
    state: CreditState,
    onBack: () -> Unit,
) {
    PosSecondaryButton(
        text = CreditStrings.BACK,
        onClick = onBack,
        icon = Icons.AutoMirrored.Filled.ArrowBack,
    )

    state.errorMessage?.let { PosErrorBanner(it) }

    val statement = state.selected
    if (statement == null) {
        if (state.isStatementLoading) CenteredLoading()
        return
    }

    StatementHeader(statement)
    HorizontalDivider()

    if (statement.entries.isEmpty()) {
        CenteredMessage(
            text = CreditStrings.NO_MOVEMENTS,
            style = MaterialTheme.typography.titleMedium,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
    ) {
        items(statement.entries, key = { it.id }) { entry -> EntryRow(entry) }
    }
}

@Composable
private fun StatementHeader(statement: CustomerStatement) {
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            ) {
                CustomerName(
                    name = statement.name,
                    active = statement.active,
                    style = MaterialTheme.typography.headlineSmall,
                )
                statement.phone?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CreditStrings.BALANCE,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = CreditStrings.money(statement.balance),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = CreditStrings.HOW_TO_PAY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryRow(entry: LedgerEntry) {
    val isPayment = entry.type == LedgerEntryType.PAYMENT
    val title = if (isPayment) {
        CreditStrings.PAYMENT
    } else {
        CreditStrings.entryTitle(CreditStrings.PURCHASE, entry.saleFolio)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entryDateLabel(entry.createdAt)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (isPayment) CreditStrings.paymentAmount(entry.amount) else CreditStrings.money(entry.amount),
                style = MaterialTheme.typography.titleMedium,
                color = if (isPayment) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxWidth().padding(PosSpacing.xl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(text: String, style: TextStyle) {
    Box(modifier = Modifier.fillMaxWidth().padding(PosSpacing.xl), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
