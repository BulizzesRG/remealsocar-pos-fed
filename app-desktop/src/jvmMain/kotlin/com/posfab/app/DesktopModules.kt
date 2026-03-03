package com.posfab.app

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.repository.AuthRepositoryImpl
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.auth.session.SessionStorage
import com.posfab.shared.auth.usecase.LoginUseCase
import com.posfab.shared.auth.usecase.LogoutUseCase
import com.posfab.shared.auth.usecase.RestoreSessionUseCase
import com.posfab.shared.config.PosConfig
import com.posfab.shared.features.catalog.common.CatalogUseCases
import com.posfab.shared.features.catalog.repository.CatalogRepository
import com.posfab.shared.features.catalog.repository.CatalogRepositoryImpl
import com.posfab.shared.features.cash.repository.CashRepository
import com.posfab.shared.features.cash.repository.CashRepositoryImpl
import com.posfab.shared.features.cash.usecase.CashUseCases
import com.posfab.shared.features.operations.common.OperationsUseCases
import com.posfab.shared.features.operations.repository.OperationsRepository
import com.posfab.shared.features.operations.repository.OperationsRepositoryImpl
import com.posfab.shared.features.reports.common.ReportsRepositoryImpl
import com.posfab.shared.features.reports.common.ReportsUseCases
import com.posfab.shared.features.reports.daily.DailyHistoryRepository
import com.posfab.shared.features.reports.manager.ManagerRepository
import com.posfab.shared.features.sale.repository.SaleRepository
import com.posfab.shared.features.sale.repository.SaleRepositoryImpl
import com.posfab.shared.features.sale.usecase.SaleUseCases
import com.posfab.shared.network.auth.AuthApi
import com.posfab.shared.network.auth.AuthApiClient
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.CatalogApiClient
import com.posfab.shared.network.cash.CashApi
import com.posfab.shared.network.cash.CashApiClient
import com.posfab.shared.network.http.HttpClientFactory
import com.posfab.shared.network.operations.OperationsApi
import com.posfab.shared.network.operations.OperationsApiClient
import com.posfab.shared.network.pos.PosApi
import com.posfab.shared.network.pos.PosApiClient
import com.posfab.shared.network.reports.ReportsApi
import com.posfab.shared.network.reports.ReportsApiClient
import io.ktor.client.engine.cio.CIO
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Paths

fun desktopModule(config: PosConfig): Module = module {
    single { config }
    single {
        val home = System.getProperty("user.home")
        DesktopSessionStorage(Paths.get(home, ".pos-fab-fed", "session.json")) as SessionStorage
    }
    single { SessionManager(get()) }
    single { HttpClientFactory.create(get(), CIO.create()) }
    single<AuthApi> { AuthApiClient(get(), get()) }
    single<CatalogApi> { CatalogApiClient(get(), get()) }
    single<PosApi> { PosApiClient(get(), get()) }
    single<OperationsApi> { OperationsApiClient(get(), get()) }
    single<CashApi> { CashApiClient(get(), get()) }
    single<ReportsApi> { ReportsApiClient(get(), get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<SaleRepository> { SaleRepositoryImpl(get(), get(), get()) }
    single<CatalogRepository> { CatalogRepositoryImpl(get(), get()) }
    single<OperationsRepository> { OperationsRepositoryImpl(get(), get()) }
    single<CashRepository> { CashRepositoryImpl(get(), get()) }
    single<ReportsRepositoryImpl> { ReportsRepositoryImpl(get(), get(), get()) }
    single<DailyHistoryRepository> { get<ReportsRepositoryImpl>() }
    single<ManagerRepository> { get<ReportsRepositoryImpl>() }
    single { LoginUseCase(get()) }
    single { RestoreSessionUseCase(get()) }
    single { LogoutUseCase(get()) }
    single { SaleUseCases(get()) }
    single { CatalogUseCases(get()) }
    single { OperationsUseCases(get()) }
    single { CashUseCases(get()) }
    single { ReportsUseCases(get(), get()) }
    single { AuthorizedApiExecutor(get(), get()) }
    single { AppStateController(get()) }
}
