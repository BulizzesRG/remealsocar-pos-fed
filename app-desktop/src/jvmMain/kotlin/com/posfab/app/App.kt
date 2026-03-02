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
import com.posfab.shared.features.login.LoginScreen
import com.posfab.shared.features.login.LoginViewModel
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
                val logoutUseCase: LogoutUseCase = GlobalContext.get().get()
                val shellViewModel = remember(screen.session) {
                    ShellViewModel(screen.session, logoutUseCase)
                }
                ShellScreen(
                    viewModel = shellViewModel,
                    onLoggedOut = controller::onLoggedOut,
                )
            }
        }
    }
}
