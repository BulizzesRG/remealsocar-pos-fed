package com.posfab.shared.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.posfab.shared.core.diagnostics.NetworkHealthTracker

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

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier.width(220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("User: ${state.session.user.username}")
            Text("Terminal: ${state.session.terminal}")
            if (network.isOffline) {
                Text(
                    text = "Offline mode. Last failure: ${network.lastFailureAt ?: "-"}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.allowedRoutes.forEach { route ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.select(route) },
                ) {
                    Text(route.title)
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                onClick = { viewModel.logout(onLoggedOut) },
            ) {
                Text("Logout")
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(start = 24.dp)) {
            when (state.selectedRoute) {
                ShellRoute.POS -> posContent()
                ShellRoute.CASH -> cashContent()
                ShellRoute.HISTORY -> historyContent()
                ShellRoute.CATALOG -> catalogContent()
                ShellRoute.OPERATIONS -> operationsContent()
                ShellRoute.REPORTS -> reportsContent()
                ShellRoute.DIAGNOSTICS -> diagnosticsContent()
                else -> {
                    Text(text = "${state.selectedRoute.title} Screen")
                    Text(text = "Placeholder. Feature implementation pending.")
                }
            }
        }
    }
}
