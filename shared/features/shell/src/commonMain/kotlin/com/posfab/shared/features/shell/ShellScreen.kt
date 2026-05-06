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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.posfab.shared.core.diagnostics.NetworkHealthTracker
import com.posfab.shared.ui.components.PosOfflineBanner
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
    val isHome = state.selectedRoute == ShellRoute.HOME

    Column(modifier = Modifier.fillMaxSize()) {
        // TOP BAR
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = PosSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left — back to home or app name
                    if (isHome) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                        ) {
                            Icon(
                                Icons.Filled.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "POS Fonda",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        TextButton(onClick = { viewModel.select(ShellRoute.HOME) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(PosSpacing.xs))
                            Text("Inicio")
                        }
                    }

                    // Center — module name (only when not on home)
                    if (!isHome) {
                        Text(
                            text = routeDisplayName(state.selectedRoute),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Box(Modifier) // placeholder para mantener SpaceBetween
                    }

                    // Right — user info + logout
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = state.session.user.username,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = "Terminal: ${state.session.terminal}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.logout(onLoggedOut) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Cerrar sesión",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (network.isOffline) {
                    PosOfflineBanner(lastFailureAt = network.lastFailureAt?.toString())
                }
                HorizontalDivider()
            }
        }

        // CONTENT — full width
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.selectedRoute) {
                ShellRoute.HOME -> HomeScreen(
                    userName = state.session.user.username,
                    allowedRoutes = state.allowedRoutes,
                    onNavigate = { viewModel.select(it) },
                )
                ShellRoute.POS -> posContent()
                ShellRoute.CASH -> cashContent()
                ShellRoute.HISTORY -> historyContent()
                ShellRoute.CATALOG -> catalogContent()
                ShellRoute.OPERATIONS -> operationsContent()
                ShellRoute.REPORTS -> reportsContent()
                ShellRoute.DIAGNOSTICS -> diagnosticsContent()
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
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

internal fun routeDisplayName(route: ShellRoute): String = when (route) {
    ShellRoute.HOME -> "Inicio"
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

internal fun routeIcon(route: ShellRoute): ImageVector = when (route) {
    ShellRoute.HOME -> Icons.Filled.Home
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
