package com.posfab.shared.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.posfab.shared.core.diagnostics.NetworkHealthTracker
import com.posfab.shared.core.diagnostics.ScreenTracker
import com.posfab.shared.core.model.terminalDisplayName
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosOfflineBanner
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun ShellScreen(
    viewModel: ShellViewModel,
    onLoggedOut: () -> Unit,
    sellContent: @Composable () -> Unit,
    cashContent: @Composable () -> Unit,
    fiadoContent: @Composable () -> Unit,
    dailySalesContent: @Composable () -> Unit,
    pedidosContent: @Composable () -> Unit,
    productsContent: @Composable () -> Unit,
    surtidoContent: @Composable () -> Unit,
    helpContent: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val network by NetworkHealthTracker.state.collectAsState()
    SideEffect { ScreenTracker.current = state.selectedRoute.title }

    if (state.confirmLogout) {
        PosConfirmDialog(
            title = ShellStrings.LOGOUT_CONFIRM_TITLE,
            message = ShellStrings.LOGOUT_CONFIRM_MESSAGE,
            confirmLabel = ShellStrings.LOGOUT,
            cancelLabel = ShellStrings.LOGOUT_CONFIRM_STAY,
            onConfirm = { viewModel.logout(onLoggedOut) },
            onDismiss = viewModel::dismissLogout,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column {
                ShellTopBar(
                    route = state.selectedRoute,
                    terminalName = terminalDisplayName(state.session.terminalCode),
                    firstName = firstNameOf(state.session.user.username),
                    onBackHome = { viewModel.select(ShellRoute.HOME) },
                    onLogout = viewModel::requestLogout,
                )
                if (network.isOffline) {
                    PosOfflineBanner(lastFailureAt = network.lastFailureAt?.toString())
                }
                HorizontalDivider()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (state.selectedRoute) {
                ShellRoute.HOME -> HomeScreen(
                    firstName = firstNameOf(state.session.user.username),
                    modules = state.modules,
                    cashStatus = state.cashStatus,
                    onNavigate = viewModel::select,
                    onRefreshCash = viewModel::refreshCashStatus,
                    onHelp = { viewModel.select(ShellRoute.HELP) },
                )
                ShellRoute.SELL -> sellContent()
                ShellRoute.CASH -> cashContent()
                ShellRoute.FIADO -> fiadoContent()
                ShellRoute.DAILY_SALES -> dailySalesContent()
                ShellRoute.PEDIDOS -> pedidosContent()
                ShellRoute.PRODUCTS -> productsContent()
                ShellRoute.SURTIDO -> surtidoContent()
                ShellRoute.HELP -> helpContent()
            }
        }
    }
}

@Composable
private fun ShellTopBar(
    route: ShellRoute,
    terminalName: String,
    firstName: String,
    onBackHome: () -> Unit,
    onLogout: () -> Unit,
) {
    val isHome = route == ShellRoute.HOME
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = PosSpacing.md),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            if (isHome) {
                Text(
                    text = terminalName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(onClick = onBackHome) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(PosSpacing.xs))
                    Text(ShellStrings.BACK_HOME)
                }
            }
        }

        if (!isHome) {
            Text(
                text = route.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            Text(
                text = firstName,
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = onLogout) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(PosSpacing.xs))
                Text(ShellStrings.LOGOUT)
            }
        }
    }
}

internal fun firstNameOf(username: String): String = username.trim().substringBefore(' ')

internal fun routeIcon(route: ShellRoute): ImageVector = when (route) {
    ShellRoute.HOME -> Icons.Filled.Home
    ShellRoute.SELL -> Icons.Filled.ShoppingCart
    ShellRoute.CASH -> Icons.Filled.Payments
    ShellRoute.FIADO -> Icons.Filled.AccountBalanceWallet
    ShellRoute.DAILY_SALES -> Icons.Filled.BarChart
    ShellRoute.PEDIDOS -> Icons.Filled.DeliveryDining
    ShellRoute.PRODUCTS -> Icons.Filled.Inventory2
    ShellRoute.SURTIDO -> Icons.Filled.Checklist
    ShellRoute.HELP -> Icons.AutoMirrored.Filled.HelpOutline
}
