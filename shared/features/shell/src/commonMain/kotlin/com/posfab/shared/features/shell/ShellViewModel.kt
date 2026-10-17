package com.posfab.shared.features.shell

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.usecase.LogoutUseCase
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSessionStatus
import com.posfab.shared.features.cash.usecase.CashUseCases
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShellState(
    val session: UserSession,
    val selectedRoute: ShellRoute,
    val modules: List<ShellRoute>,
    val cashStatus: HomeCashStatus,
)

class ShellViewModel(
    session: UserSession,
    private val logoutUseCase: LogoutUseCase,
    private val cashUseCases: CashUseCases,
    private val terminalId: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val canSeeCash = Permission.OPEN_CASH in session.user.permissions
    private var cashRefresh: Job? = null

    private val _state = MutableStateFlow(
        ShellState(
            session = session,
            selectedRoute = initialRoute(session),
            modules = RouteGuard.modules(session),
            cashStatus = if (canSeeCash) HomeCashStatus.Loading else HomeCashStatus.Hidden,
        ),
    )
    val state: StateFlow<ShellState> = _state.asStateFlow()

    fun select(route: ShellRoute) {
        if (!RouteGuard.canAccess(_state.value.session, route)) return
        _state.update { it.copy(selectedRoute = route) }
    }

    fun refreshCashStatus() {
        if (!canSeeCash) {
            _state.update { it.copy(cashStatus = HomeCashStatus.Hidden) }
            return
        }
        _state.update { it.copy(cashStatus = it.cashStatus.whileRevalidating()) }
        cashRefresh?.cancel()
        cashRefresh = scope.launch {
            val status = when (val result = cashUseCases.current(terminalId)) {
                is AppResult.Success -> result.value.toHomeCashStatus()
                is AppResult.Failure -> HomeCashStatus.Unavailable
            }
            _state.update { it.copy(cashStatus = status) }
        }
    }

    private fun HomeCashStatus.whileRevalidating(): HomeCashStatus = when (this) {
        HomeCashStatus.Hidden, HomeCashStatus.Loading, HomeCashStatus.Unavailable -> HomeCashStatus.Loading
        HomeCashStatus.Closed, is HomeCashStatus.Open -> this
    }

    private fun initialRoute(session: UserSession): ShellRoute =
        if (UserRole.ORDERS in session.user.roles && RouteGuard.canAccess(session, ShellRoute.PEDIDOS)) {
            ShellRoute.PEDIDOS
        } else {
            ShellRoute.HOME
        }

    fun logout(onDone: () -> Unit) {
        scope.launch {
            logoutUseCase()
            onDone()
        }
    }

    private fun CashSession?.toHomeCashStatus(): HomeCashStatus = when {
        this == null -> HomeCashStatus.Closed
        status == CashSessionStatus.OPEN -> HomeCashStatus.Open(openedAt, openingAmount)
        else -> HomeCashStatus.Closed
    }
}
