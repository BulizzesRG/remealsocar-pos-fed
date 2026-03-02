package com.posfab.shared.features.shell

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.usecase.LogoutUseCase
import com.posfab.shared.core.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShellState(
    val session: UserSession,
    val selectedRoute: ShellRoute,
    val allowedRoutes: List<ShellRoute>,
)

class ShellViewModel(
    session: UserSession,
    private val logoutUseCase: LogoutUseCase,
) : BaseViewModel() {
    private val allowed = RouteGuard.allowedRoutes(session)
    private val _state = MutableStateFlow(
        ShellState(
            session = session,
            selectedRoute = allowed.firstOrNull() ?: ShellRoute.POS,
            allowedRoutes = allowed,
        )
    )
    val state: StateFlow<ShellState> = _state.asStateFlow()

    fun select(route: ShellRoute) {
        if (!RouteGuard.canAccess(_state.value.session, route)) return
        _state.value = _state.value.copy(selectedRoute = route)
    }

    fun logout(onDone: () -> Unit) {
        scope.launch {
            logoutUseCase()
            onDone()
        }
    }
}
