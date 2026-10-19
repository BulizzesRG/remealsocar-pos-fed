package com.posfab.shared.features.shell

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.usecase.LogoutUseCase
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashCloseResult
import com.posfab.shared.features.cash.domain.CashPayout
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSessionStatus
import com.posfab.shared.features.cash.domain.CashSummary
import com.posfab.shared.features.cash.domain.PayoutReason
import com.posfab.shared.features.cash.repository.CashRepository
import com.posfab.shared.features.cash.usecase.CashUseCases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val terminalId = 1L

    private val cashierPermissions = setOf(Permission.VIEW_PRODUCTS, Permission.SELL, Permission.OPEN_CASH)
    private val stockerPermissions = setOf(Permission.VIEW_PRODUCTS, Permission.COUNT_STOCK)
    private val ordersPermissions = setOf(Permission.VIEW_PRODUCTS, Permission.MANAGE_ORDERS)
    private val adminPermissions = Permission.entries.toSet()

    @Test
    fun without_open_cash_permission_status_is_hidden_and_repo_not_called() = runTest(dispatcher) {
        val repo = FakeCashRepository(AppResult.Success(null))
        val vm = makeViewModel(repo, stockerPermissions)

        vm.refreshCashStatus()
        advanceUntilIdle()

        assertEquals(HomeCashStatus.Hidden, vm.state.value.cashStatus)
        assertEquals(0, repo.currentCalls)
    }

    @Test
    fun logout_asks_for_confirmation_first() = runTest(dispatcher) {
        val repo = FakeCashRepository(AppResult.Success(null))
        val vm = makeViewModel(repo, cashierPermissions)
        var loggedOut = 0

        vm.requestLogout()
        assertTrue(vm.state.value.confirmLogout)
        vm.dismissLogout()
        advanceUntilIdle()
        assertEquals(false, vm.state.value.confirmLogout)
        assertEquals(0, loggedOut)

        vm.requestLogout()
        vm.logout { loggedOut += 1 }
        advanceUntilIdle()
        assertEquals(false, vm.state.value.confirmLogout)
        assertEquals(1, loggedOut)
    }

    @Test
    fun no_session_maps_to_closed() = runTest(dispatcher) {
        val repo = FakeCashRepository(AppResult.Success(null))
        val vm = makeViewModel(repo, cashierPermissions)

        vm.refreshCashStatus()
        advanceUntilIdle()

        assertEquals(HomeCashStatus.Closed, vm.state.value.cashStatus)
        assertEquals(1, repo.currentCalls)
    }

    @Test
    fun open_session_maps_to_open_with_opened_at_and_amount() = runTest(dispatcher) {
        val repo = FakeCashRepository(AppResult.Success(cashSession(CashSessionStatus.OPEN)))
        val vm = makeViewModel(repo, cashierPermissions)

        vm.refreshCashStatus()
        advanceUntilIdle()

        assertEquals(HomeCashStatus.Open("2026-10-14T08:15:00-06:00", 500.0), vm.state.value.cashStatus)
        assertEquals(terminalId, repo.lastTerminalId)
    }

    @Test
    fun closed_session_maps_to_closed() = runTest(dispatcher) {
        val repo = FakeCashRepository(AppResult.Success(cashSession(CashSessionStatus.CLOSED)))
        val vm = makeViewModel(repo, cashierPermissions)

        vm.refreshCashStatus()
        advanceUntilIdle()

        assertEquals(HomeCashStatus.Closed, vm.state.value.cashStatus)
    }

    @Test
    fun network_failure_maps_to_unavailable() = runTest(dispatcher) {
        val repo = FakeCashRepository(AppResult.Failure(AppError.Network("down")))
        val vm = makeViewModel(repo, cashierPermissions)

        vm.refreshCashStatus()
        advanceUntilIdle()

        assertEquals(HomeCashStatus.Unavailable, vm.state.value.cashStatus)
    }

    @Test
    fun refresh_shows_loading_until_the_repository_answers() = runTest(dispatcher) {
        val repo = FakeCashRepository(AppResult.Success(null))
        val vm = makeViewModel(repo, cashierPermissions)

        vm.refreshCashStatus()
        assertEquals(HomeCashStatus.Loading, vm.state.value.cashStatus)
        advanceUntilIdle()

        assertEquals(HomeCashStatus.Closed, vm.state.value.cashStatus)
    }

    @Test
    fun refresh_again_reflects_the_new_cash_state() = runTest(dispatcher) {
        val repo = FakeCashRepository(AppResult.Success(null))
        val vm = makeViewModel(repo, cashierPermissions)
        vm.refreshCashStatus()
        advanceUntilIdle()

        repo.result = AppResult.Success(cashSession(CashSessionStatus.OPEN))
        vm.refreshCashStatus()
        advanceUntilIdle()

        assertTrue(vm.state.value.cashStatus is HomeCashStatus.Open)
        assertEquals(2, repo.currentCalls)
    }

    @Test
    fun select_of_allowed_route_changes_selected_route() = runTest(dispatcher) {
        val vm = makeViewModel(FakeCashRepository(AppResult.Success(null)), cashierPermissions)

        vm.select(ShellRoute.SELL)

        assertEquals(ShellRoute.SELL, vm.state.value.selectedRoute)
    }

    @Test
    fun select_of_route_not_allowed_leaves_selected_route_unchanged() = runTest(dispatcher) {
        val vm = makeViewModel(FakeCashRepository(AppResult.Success(null)), cashierPermissions)

        vm.select(ShellRoute.PRODUCTS)

        assertEquals(ShellRoute.HOME, vm.state.value.selectedRoute)
    }

    @Test
    fun state_exposes_modules_for_the_session() = runTest(dispatcher) {
        val vm = makeViewModel(FakeCashRepository(AppResult.Success(null)), cashierPermissions)

        assertEquals(listOf(ShellRoute.SELL, ShellRoute.CASH, ShellRoute.SURTIDO), vm.state.value.modules)
    }

    @Test
    fun cashier_can_open_surtido() = runTest(dispatcher) {
        val vm = makeViewModel(FakeCashRepository(AppResult.Success(null)), cashierPermissions)

        vm.select(ShellRoute.SURTIDO)

        assertEquals(ShellRoute.SURTIDO, vm.state.value.selectedRoute)
    }

    @Test
    fun orders_and_stocker_cannot_open_surtido() = runTest(dispatcher) {
        val orders = makeViewModel(FakeCashRepository(AppResult.Success(null)), ordersPermissions, setOf(UserRole.ORDERS))
        val stocker = makeViewModel(FakeCashRepository(AppResult.Success(null)), stockerPermissions)

        orders.select(ShellRoute.SURTIDO)
        stocker.select(ShellRoute.SURTIDO)

        assertEquals(ShellRoute.PEDIDOS, orders.state.value.selectedRoute)
        assertEquals(ShellRoute.HOME, stocker.state.value.selectedRoute)
        assertFalse(ShellRoute.SURTIDO in orders.state.value.modules)
        assertFalse(ShellRoute.SURTIDO in stocker.state.value.modules)
    }

    @Test
    fun orders_role_opens_on_pedidos() = runTest(dispatcher) {
        val vm = makeViewModel(FakeCashRepository(AppResult.Success(null)), ordersPermissions, setOf(UserRole.ORDERS))

        assertEquals(ShellRoute.PEDIDOS, vm.state.value.selectedRoute)
    }

    @Test
    fun cashier_opens_on_home() = runTest(dispatcher) {
        val vm = makeViewModel(FakeCashRepository(AppResult.Success(null)), cashierPermissions, setOf(UserRole.CASHIER))

        assertEquals(ShellRoute.HOME, vm.state.value.selectedRoute)
    }

    @Test
    fun admin_opens_on_home_even_with_manage_orders() = runTest(dispatcher) {
        val vm = makeViewModel(FakeCashRepository(AppResult.Success(null)), adminPermissions, setOf(UserRole.ADMIN))

        assertEquals(ShellRoute.HOME, vm.state.value.selectedRoute)
    }

    @Test
    fun orders_user_can_go_back_to_home_from_pedidos() = runTest(dispatcher) {
        val vm = makeViewModel(FakeCashRepository(AppResult.Success(null)), ordersPermissions, setOf(UserRole.ORDERS))

        vm.select(ShellRoute.HOME)

        assertEquals(ShellRoute.HOME, vm.state.value.selectedRoute)
        assertEquals(listOf(ShellRoute.PEDIDOS), vm.state.value.modules)
    }

    @Test
    fun orders_role_without_manage_orders_opens_on_home() = runTest(dispatcher) {
        val vm = makeViewModel(
            FakeCashRepository(AppResult.Success(null)),
            setOf(Permission.VIEW_PRODUCTS),
            setOf(UserRole.ORDERS),
        )

        assertEquals(ShellRoute.HOME, vm.state.value.selectedRoute)
    }

    @Test
    fun opened_at_time_label_formats_local_time_without_leading_zero() {
        val label = openedAtTimeLabel("2026-10-14T08:15:00-06:00")

        val local = Instant.parse("2026-10-14T14:15:00Z").toLocalDateTime(TimeZone.currentSystemDefault())
        val expected = "${local.hour}:${local.minute.toString().padStart(2, '0')}"
        assertNotNull(label)
        assertEquals(expected, label)
        assertTrue(Regex("\\d{1,2}:\\d{2}").matches(label))
    }

    @Test
    fun opened_at_time_label_accepts_utc_with_fraction() {
        val label = openedAtTimeLabel("2026-10-14T14:15:30.123Z")

        val local = Instant.parse("2026-10-14T14:15:30.123Z").toLocalDateTime(TimeZone.currentSystemDefault())
        assertEquals("${local.hour}:${local.minute.toString().padStart(2, '0')}", label)
    }

    @Test
    fun opened_at_time_label_returns_null_for_invalid_input() {
        assertNull(openedAtTimeLabel("not a date"))
        assertNull(openedAtTimeLabel(""))
    }

    private fun makeViewModel(
        repo: FakeCashRepository,
        permissions: Set<Permission>,
        roles: Set<UserRole> = setOf(UserRole.CASHIER),
    ) = ShellViewModel(
        session = UserSession(
            tokens = AuthTokens("a", "r"),
            user = AuthUser("1", "user", roles, permissions),
            terminalCode = "POS1",
        ),
        logoutUseCase = LogoutUseCase(FakeAuthRepository()),
        cashUseCases = CashUseCases(repo),
        terminalId = terminalId,
        dispatcher = dispatcher,
    )

    private fun cashSession(status: CashSessionStatus) = CashSession(
        id = 1L,
        status = status,
        terminalId = terminalId,
        openingAmount = 500.0,
        closingAmount = null,
        openedAt = "2026-10-14T08:15:00-06:00",
        closedAt = null,
    )
}

private class FakeCashRepository(
    var result: AppResult<CashSession?>,
) : CashRepository {
    var currentCalls = 0
        private set
    var lastTerminalId: Long? = null
        private set

    override suspend fun getCurrentSession(terminalId: Long): AppResult<CashSession?> {
        currentCalls++
        lastTerminalId = terminalId
        return result
    }

    override suspend fun summary(terminalId: Long): AppResult<CashSummary> =
        AppResult.Failure(AppError.Unknown("not implemented"))

    override suspend fun openSession(
        terminalId: Long,
        businessUnitId: Long,
        openingAmount: Double,
    ): AppResult<Unit> = AppResult.Failure(AppError.Unknown("not implemented"))

    override suspend fun closeSession(terminalId: Long, countedAmount: Double): AppResult<CashCloseResult> =
        AppResult.Failure(AppError.Unknown("not implemented"))

    override suspend fun payout(
        terminalId: Long,
        idempotencyKey: String,
        amount: Double,
        reason: PayoutReason,
        notes: String?,
    ): AppResult<CashPayout> = AppResult.Failure(AppError.Unknown("not implemented"))
}

private class FakeAuthRepository : AuthRepository {
    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("not implemented"))

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("not implemented"))

    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(null)

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
}
