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
import com.posfab.shared.network.auth.AuthApi
import com.posfab.shared.network.auth.AuthApiClient
import com.posfab.shared.network.http.HttpClientFactory
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
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single { LoginUseCase(get()) }
    single { RestoreSessionUseCase(get()) }
    single { LogoutUseCase(get()) }
    single { AuthorizedApiExecutor(get(), get()) }
    single { AppStateController(get()) }
}
