package com.posfab.shared.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun HomeScreen(
    userName: String,
    allowedRoutes: List<ShellRoute>,
    onNavigate: (ShellRoute) -> Unit,
) {
    val modules = allowedRoutes.filter { it != ShellRoute.HOME }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PosSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            Text(
                text = "Bienvenido, $userName",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Selecciona un módulo para continuar",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(modules) { route ->
                ModuleTile(route = route, onClick = { onNavigate(route) })
            }
        }
    }
}

@Composable
private fun ModuleTile(route: ShellRoute, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp,
        ),
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
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(PosSpacing.md))
            Text(
                text = routeDisplayName(route),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
