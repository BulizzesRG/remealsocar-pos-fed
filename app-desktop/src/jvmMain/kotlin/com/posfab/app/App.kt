package com.posfab.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.posfab.shared.auth.usecase.LoginUseCase
import com.posfab.shared.auth.usecase.LogoutUseCase
import com.posfab.shared.features.catalog.common.CatalogUseCases
import com.posfab.shared.features.catalog.ui.CatalogScreen
import com.posfab.shared.features.catalog.ui.CatalogViewModel
import com.posfab.shared.features.cash.ui.CashSessionScreen
import com.posfab.shared.features.cash.ui.CashSessionViewModel
import com.posfab.shared.features.cash.usecase.CashUseCases
import com.posfab.shared.features.login.LoginScreen
import com.posfab.shared.features.login.LoginViewModel
import com.posfab.shared.features.operations.common.OperationsUseCases
import com.posfab.shared.features.operations.ui.OperationsScreen
import com.posfab.shared.features.operations.ui.OperationsViewModel
import com.posfab.shared.features.reports.common.ReportsUseCases
import com.posfab.shared.features.reports.daily.DailyHistoryScreen
import com.posfab.shared.features.reports.daily.DailyHistoryViewModel
import com.posfab.shared.features.reports.manager.ManagerPanelScreen
import com.posfab.shared.features.reports.manager.ManagerPanelViewModel
import com.posfab.shared.features.sale.ui.CashierSaleScreen
import com.posfab.shared.features.sale.ui.CashierSaleViewModel
import com.posfab.shared.features.sale.usecase.SaleUseCases
import com.posfab.shared.features.shell.ShellScreen
import com.posfab.shared.features.shell.ShellViewModel
import com.posfab.shared.ui.theme.PosTheme
import kotlinx.coroutines.flow.collect
import org.koin.core.context.GlobalContext

@Composable
fun PosApp(controller: AppStateController) {
    val current by controller.screen.collectAsState()

    PosTheme {
        when (val screen = current) {
            AppScreen.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            AppScreen.Login -> {
                val koin = GlobalContext.get()
                val loginViewModel = remember {
                    LoginViewModel(koin.get<LoginUseCase>())
                }
                LaunchedEffect(loginViewModel) {
                    loginViewModel.loggedIn.collect { session ->
                        controller.onLoggedIn(session)
                    }
                }
                LoginScreen(loginViewModel)
            }
            is AppScreen.Shell -> {
                val koin = GlobalContext.get()
                val logoutUseCase: LogoutUseCase = koin.get()
                val shellViewModel = remember(screen.session) {
                    ShellViewModel(screen.session, logoutUseCase)
                }
                val saleViewModel = remember(screen.session) {
                    CashierSaleViewModel(koin.get<SaleUseCases>())
                }
                val cashViewModel = remember(screen.session) {
                    CashSessionViewModel(screen.session, koin.get<CashUseCases>())
                }
                val historyViewModel = remember(screen.session) {
                    DailyHistoryViewModel(screen.session, koin.get<ReportsUseCases>())
                }
                val managerViewModel = remember {
                    ManagerPanelViewModel(koin.get<ReportsUseCases>())
                }
                val catalogViewModel = remember {
                    CatalogViewModel(koin.get<CatalogUseCases>())
                }
                val operationsViewModel = remember(screen.session) {
                    OperationsViewModel(screen.session, koin.get<OperationsUseCases>())
                }
                ShellScreen(
                    viewModel = shellViewModel,
                    onLoggedOut = controller::onLoggedOut,
                    posContent = { CashierSaleScreen(saleViewModel) },
                    cashContent = { CashSessionScreen(cashViewModel) },
                    historyContent = { DailyHistoryScreen(historyViewModel) },
                    catalogContent = { CatalogScreen(catalogViewModel) },
                    operationsContent = { OperationsScreen(operationsViewModel) },
                    reportsContent = { ManagerPanelScreen(managerViewModel) },
                )
            }
        }
    }
}
