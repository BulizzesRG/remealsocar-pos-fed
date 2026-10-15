package com.posfab.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.posfab.shared.auth.usecase.LoginUseCase
import com.posfab.shared.auth.usecase.LogoutUseCase
import com.posfab.shared.core.device.DeviceStorage
import com.posfab.shared.features.catalog.common.CatalogUseCases
import com.posfab.shared.features.catalog.ui.CatalogScreen
import com.posfab.shared.features.catalog.ui.CatalogViewModel
import com.posfab.shared.features.cash.ui.CashSessionScreen
import com.posfab.shared.features.cash.ui.CashSessionViewModel
import com.posfab.shared.features.cash.usecase.CashUseCases
import com.posfab.shared.features.devicesetup.DeviceSetupScreen
import com.posfab.shared.features.devicesetup.DeviceSetupViewModel
import com.posfab.shared.features.login.LoginScreen
import com.posfab.shared.features.login.LoginViewModel
import com.posfab.shared.features.reports.diagnostics.DiagnosticsScreen
import com.posfab.shared.features.reports.diagnostics.DiagnosticsViewModel
import com.posfab.shared.features.reports.common.ReportsUseCases
import com.posfab.shared.features.reports.daily.DailyHistoryScreen
import com.posfab.shared.features.reports.daily.DailyHistoryViewModel
import com.posfab.shared.features.sale.ui.CashierSaleScreen
import com.posfab.shared.features.sale.ui.CashierSaleViewModel
import com.posfab.shared.features.sale.ui.CardPaymentAdapter
import com.posfab.shared.features.sale.ui.ReceiptPrinterAdapter
import com.posfab.shared.features.sale.usecase.SaleUseCases
import com.posfab.shared.features.shell.ShellRoute
import com.posfab.shared.features.shell.ShellScreen
import com.posfab.shared.features.shell.ShellViewModel
import com.posfab.shared.network.device.DeviceApi
import com.posfab.shared.ui.theme.PosSpacing
import com.posfab.shared.ui.theme.PosTheme
import org.koin.core.context.GlobalContext

@Composable
fun PosApp(controller: AppStateController) {
    val current by controller.screen.collectAsState()

    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val screen = current) {
                AppScreen.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Iniciando sistema...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                AppScreen.DeviceSetup -> {
                    val koin = GlobalContext.get()
                    val setupViewModel = remember {
                        DeviceSetupViewModel(koin.get<DeviceApi>(), koin.get<DeviceStorage>())
                    }
                    LaunchedEffect(setupViewModel) {
                        setupViewModel.registrationComplete.collect { registration ->
                            controller.onDeviceRegistered(registration)
                        }
                    }
                    DeviceSetupScreen(setupViewModel)
                }
                AppScreen.Login -> {
                    val koin = GlobalContext.get()
                    val device = controller.deviceRegistration.value
                        ?: error("Login screen reached before device registration")
                    val loginViewModel = remember {
                        LoginViewModel(koin.get<LoginUseCase>(), koin.get<LogoutUseCase>(), device)
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
                    val device = controller.deviceRegistration.value
                        ?: error("Shell screen reached before device registration")
                    val logoutUseCase: LogoutUseCase = koin.get()
                    val shellViewModel = remember(screen.session) {
                        ShellViewModel(screen.session, logoutUseCase, koin.get<CashUseCases>(), device.terminalId)
                    }
                    val saleViewModel = remember(screen.session) {
                        CashierSaleViewModel(
                            saleUseCases = koin.get<SaleUseCases>(),
                            saleUiStateStore = koin.get(),
                            cardPaymentAdapter = koin.get<CardPaymentAdapter>(),
                            receiptPrinterAdapter = koin.get<ReceiptPrinterAdapter>(),
                            onNavigateToCash = { shellViewModel.select(ShellRoute.CASH) },
                        )
                    }
                    val cashViewModel = remember(screen.session) {
                        CashSessionViewModel(
                            terminalId = device.terminalId,
                            businessUnitId = device.businessUnitId,
                            cashUseCases = koin.get<CashUseCases>(),
                            onNavigateToPos = { shellViewModel.select(ShellRoute.SELL) },
                        )
                    }
                    val historyViewModel = remember(screen.session) {
                        DailyHistoryViewModel(screen.session, koin.get<ReportsUseCases>())
                    }
                    val diagnosticsViewModel = remember(screen.session) {
                        DiagnosticsViewModel(screen.session, koin.get(), koin.get())
                    }
                    val catalogViewModel = remember(device.businessUnitId) {
                        CatalogViewModel(koin.get<CatalogUseCases>(), defaultBusinessUnitId = device.businessUnitId)
                    }
                    ShellScreen(
                        viewModel = shellViewModel,
                        onLoggedOut = controller::onLoggedOut,
                        sellContent = { CashierSaleScreen(saleViewModel) },
                        cashContent = { CashSessionScreen(cashViewModel) },
                        dailySalesContent = { DailyHistoryScreen(historyViewModel) },
                        productsContent = { CatalogScreen(catalogViewModel) },
                        helpContent = { DiagnosticsScreen(diagnosticsViewModel) },
                    )
                }
            }
        }
    }
}
