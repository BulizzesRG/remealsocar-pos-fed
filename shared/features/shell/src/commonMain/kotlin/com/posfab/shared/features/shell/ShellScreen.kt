package com.posfab.shared.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.posfab.shared.core.diagnostics.NetworkHealthTracker
import com.posfab.shared.ui.components.PosOfflineBanner
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun ShellScreen(
    viewModel: ShellViewModel,
    onLoggedOut: () -> Unit,
    posContent: @Composable () -> Unit,
    cashContent: @Composable () -> Unit,
    historyContent: @Composable () -> Unit,
    catalogContent: @Composable () -> Unit,
    operationsContent: @Composable () -> Unit,
    reportsContent: @Composable () -> Unit,
    diagnosticsContent: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val network by NetworkHealthTracker.state.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .width(PosLayout.navWidth)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PosSpacing.md),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(PosSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
                        ) {
                            Text(
                                text = state.session.user.username,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Terminal: ${state.session.terminal}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(PosSpacing.sm))

                    if (network.isOffline) {
                        PosOfflineBanner(lastFailureAt = network.lastFailureAt?.toString())
                        Spacer(Modifier.height(PosSpacing.sm))
                    }

                    state.allowedRoutes.forEach { route ->
                        val isSelected = route == state.selectedRoute
                        NavigationDrawerItem(
                            label = { Text(routeDisplayName(route)) },
                            selected = isSelected,
                            onClick = { viewModel.select(route) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(routeIcon(route), contentDescription = null) },
                        )
                    }
                }

                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = PosSpacing.sm))
                    NavigationDrawerItem(
                        label = { Text("Cerrar sesion") },
                        selected = false,
                        onClick = { viewModel.logout(onLoggedOut) },
                        icon = {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.error,
                            unselectedTextColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PosSpacing.lg),
        ) {
            when (state.selectedRoute) {
                ShellRoute.POS -> posContent()
                ShellRoute.CASH -> cashContent()
                ShellRoute.HISTORY -> historyContent()
                ShellRoute.CATALOG -> catalogContent()
                ShellRoute.OPERATIONS -> operationsContent()
                ShellRoute.REPORTS -> reportsContent()
                ShellRoute.DIAGNOSTICS -> diagnosticsContent()
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "${routeDisplayName(state.selectedRoute)} — Proximamente",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun routeDisplayName(route: ShellRoute): String = when (route) {
    ShellRoute.POS -> "POS"
    ShellRoute.CASH -> "Caja"
    ShellRoute.HISTORY -> "Historial"
    ShellRoute.CATALOG -> "Catalogo"
    ShellRoute.OPERATIONS -> "Operaciones"
    ShellRoute.REPORTS -> "Reportes"
    ShellRoute.DIAGNOSTICS -> "Diagnosticos"
    ShellRoute.CREDIT -> "Credito"
    ShellRoute.ADMIN -> "Admin"
}

private fun routeIcon(route: ShellRoute): ImageVector = when (route) {
    ShellRoute.POS -> Icons.Filled.ShoppingCart
    ShellRoute.CASH -> Icons.Filled.Payments
    ShellRoute.HISTORY -> Icons.Filled.History
    ShellRoute.CATALOG -> Icons.Filled.Inventory2
    ShellRoute.OPERATIONS -> Icons.Filled.Warehouse
    ShellRoute.REPORTS -> Icons.Filled.BarChart
    ShellRoute.DIAGNOSTICS -> Icons.Filled.BugReport
    ShellRoute.CREDIT -> Icons.Filled.CreditCard
    ShellRoute.ADMIN -> Icons.Filled.AdminPanelSettings
}
