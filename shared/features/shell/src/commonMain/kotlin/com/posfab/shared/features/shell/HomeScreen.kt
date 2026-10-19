package com.posfab.shared.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun HomeScreen(
    firstName: String,
    modules: List<ShellRoute>,
    cashStatus: HomeCashStatus,
    onNavigate: (ShellRoute) -> Unit,
    onRefreshCash: () -> Unit,
    onHelp: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefreshCash() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PosSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.xl),
    ) {
        Text(
            text = if (firstName.isBlank()) ShellStrings.GREETING_ALONE else "${ShellStrings.GREETING_PREFIX}$firstName",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (cashStatus != HomeCashStatus.Hidden) {
            CashStrip(status = cashStatus, onClick = { onNavigate(ShellRoute.CASH) })
        }

        if (modules.isNotEmpty()) {
            ModuleGrid(modules = modules, onNavigate = onNavigate)
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onHelp) {
            Text(
                text = ShellStrings.HELP,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModuleGrid(modules: List<ShellRoute>, onNavigate: (ShellRoute) -> Unit) {
    val rows = moduleRows(modules)
    val fullWeight = rows.maxOf { row -> row.sumOf { tileWeight(it).toDouble() } }.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.lg)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MODULE_ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
            ) {
                row.forEach { route ->
                    ModuleTile(route = route, onClick = { onNavigate(route) })
                }
                val missing = fullWeight - row.sumOf { tileWeight(it).toDouble() }.toFloat()
                if (missing > 0f) {
                    Spacer(Modifier.weight(missing))
                }
            }
        }
    }
}

internal fun moduleRows(modules: List<ShellRoute>): List<List<ShellRoute>> =
    if (modules.size > SINGLE_ROW_MAX_MODULES) modules.chunked(TILES_PER_ROW) else listOf(modules)

private fun tileWeight(route: ShellRoute): Float = if (route == ShellRoute.SELL) SELL_WEIGHT else 1f

private const val SINGLE_ROW_MAX_MODULES = 6
private const val TILES_PER_ROW = 4
private const val SELL_WEIGHT = 2f
private val MODULE_ROW_HEIGHT = 200.dp

private data class StripLook(
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val text: String,
)

@Composable
private fun stripLook(status: HomeCashStatus): StripLook {
    val colors = MaterialTheme.colorScheme
    return when (status) {
        HomeCashStatus.Closed ->
            StripLook(Icons.Filled.Lock, colors.primaryContainer, colors.onPrimaryContainer, ShellStrings.CASH_CLOSED)
        is HomeCashStatus.Open ->
            StripLook(Icons.Filled.LockOpen, colors.tertiaryContainer, colors.onTertiaryContainer, openCashLabel(status))
        HomeCashStatus.Unavailable ->
            StripLook(Icons.Filled.Warning, colors.errorContainer, colors.onErrorContainer, ShellStrings.CASH_UNAVAILABLE)
        HomeCashStatus.Loading, HomeCashStatus.Hidden ->
            StripLook(Icons.Filled.Schedule, colors.surfaceVariant, colors.onSurfaceVariant, ShellStrings.CASH_LOADING)
    }
}

@Composable
private fun CashStrip(status: HomeCashStatus, onClick: () -> Unit) {
    val look = stripLook(status)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = CardDefaults.cardColors(
            containerColor = look.container,
            contentColor = look.content,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PosSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Icon(imageVector = look.icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(text = look.text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun RowScope.ModuleTile(route: ShellRoute, onClick: () -> Unit) {
    val isSell = route == ShellRoute.SELL
    val colors = MaterialTheme.colorScheme
    val container = if (isSell) colors.primary else colors.surfaceVariant
    val content = if (isSell) colors.onPrimary else colors.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = Modifier
            .weight(tileWeight(route))
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PosSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = routeIcon(route),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = if (isSell) content else colors.primary,
            )
            Spacer(Modifier.height(PosSpacing.md))
            Text(
                text = route.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
