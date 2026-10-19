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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.posfab.shared.auth.usecase.LoginUseCase
import com.posfab.shared.auth.usecase.LogoutUseCase
import com.posfab.shared.core.device.DeviceStorage
import com.posfab.shared.core.diagnostics.ScreenTracker
import com.posfab.shared.features.catalog.common.CatalogUseCases
import com.posfab.shared.features.catalog.ui.CatalogScreen
import com.posfab.shared.features.catalog.ui.CatalogViewModel
import com.posfab.shared.features.cash.ui.CashScreen
import com.posfab.shared.features.cash.ui.CashViewModel
import com.posfab.shared.features.cash.usecase.CashUseCases
import com.posfab.shared.features.credit.ui.CreditScreen
import com.posfab.shared.features.credit.ui.CreditViewModel
import com.posfab.shared.features.credit.usecase.CreditUseCases
import com.posfab.shared.features.orders.ui.OrdersScreen
import com.posfab.shared.features.orders.ui.OrdersViewModel
import com.posfab.shared.features.orders.usecase.OrdersUseCases
import com.posfab.shared.features.shopping.ui.ShoppingScreen
import com.posfab.shared.features.shopping.ui.ShoppingViewModel
import com.posfab.shared.features.shopping.usecase.ShoppingUseCases
import com.posfab.shared.features.devicesetup.DeviceSetupScreen
import com.posfab.shared.features.devicesetup.DeviceSetupViewModel
import com.posfab.shared.features.login.LoginScreen
import com.posfab.shared.features.login.LoginViewModel
import com.posfab.shared.features.reports.diagnostics.DiagnosticsScreen
import com.posfab.shared.features.reports.diagnostics.DiagnosticsViewModel
import com.posfab.shared.features.reports.common.ReportsUseCases
import com.posfab.shared.features.reports.sales.SalesScreen
import com.posfab.shared.features.reports.sales.SalesViewModel
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
                    SideEffect { ScreenTracker.current = AppScreenNames.LOADING }
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
                    SideEffect { ScreenTracker.current = AppScreenNames.DEVICE_SETUP }
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
                    SideEffect { ScreenTracker.current = AppScreenNames.LOGIN }
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
                            cardPaymentAdapter = koin.get<CardPaymentAdapter>(),
                            receiptPrinterAdapter = koin.get<ReceiptPrinterAdapter>(),
                            onNavigateToCash = { shellViewModel.select(ShellRoute.CASH) },
                        )
                    }
                    val cashViewModel = remember(screen.session) {
                        CashViewModel(
                            cashUseCases = koin.get<CashUseCases>(),
                            terminalId = device.terminalId,
                            businessUnitId = device.businessUnitId,
                            permissions = screen.session.user.permissions,
                        )
                    }
                    val creditViewModel = remember(screen.session) {
                        CreditViewModel(koin.get<CreditUseCases>(), screen.session.user.permissions)
                    }
                    val salesViewModel = remember(screen.session) {
                        SalesViewModel(koin.get<ReportsUseCases>())
                    }
                    val ordersViewModel = remember(screen.session) {
                        OrdersViewModel(koin.get<OrdersUseCases>())
                    }
                    val shoppingViewModel = remember(screen.session) {
                        ShoppingViewModel(koin.get<ShoppingUseCases>(), screen.session.user.permissions)
                    }
                    val diagnosticsViewModel = remember(screen.session) {
                        DiagnosticsViewModel(screen.session, koin.get(), koin.get())
                    }
                    val catalogViewModel = remember(screen.session) {
                        CatalogViewModel(koin.get<CatalogUseCases>())
                    }
                    ShellScreen(
                        viewModel = shellViewModel,
                        onLoggedOut = controller::onLoggedOut,
                        sellContent = { CashierSaleScreen(saleViewModel) },
                        cashContent = { CashScreen(cashViewModel) },
                        fiadoContent = { CreditScreen(creditViewModel) },
                        dailySalesContent = { SalesScreen(salesViewModel) },
                        pedidosContent = { OrdersScreen(ordersViewModel) },
                        productsContent = { CatalogScreen(catalogViewModel) },
                        surtidoContent = { ShoppingScreen(shoppingViewModel) },
                        helpContent = { DiagnosticsScreen(diagnosticsViewModel) },
                    )
                }
            }
        }
    }
}

object AppScreenNames {
    const val LOADING = "Carga"
    const val DEVICE_SETUP = "Registro del equipo"
    const val LOGIN = "Entrar"
}
