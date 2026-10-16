package com.posfab.shared.features.credit

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor
import com.posfab.shared.features.credit.domain.LedgerEntry
import com.posfab.shared.features.credit.domain.LedgerEntryType
import com.posfab.shared.features.credit.repository.CreditRepository
import com.posfab.shared.features.credit.ui.CreditStrings
import com.posfab.shared.features.credit.ui.CreditViewModel
import com.posfab.shared.features.credit.usecase.CreditUseCases
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CreditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-15T18:00:00Z")

    private val juan = debtor(10, "Juan Pérez", 350.0)
    private val maria = debtor(11, "María López", 120.5)
    private val pedro = debtor(12, "Pedro", 4_000.0, active = false)

    @Test
    fun load_lists_debtors_by_balance_and_sums_the_total() = runTest(dispatcher) {
        val repo = FakeCreditRepository(debtors = AppResult.Success(listOf(pedro, juan, maria)))
        val vm = viewModel(repo)

        vm.load()
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(pedro, juan, maria), state.visibleDebtors)
        assertEquals(4_470.5, state.totalOwed, 0.001)
        assertEquals(3, state.debtorCount)
        assertEquals(now.toEpochMilliseconds(), state.nowEpochMillis)
        assertEquals("Te deben $4,470.50 · 3 clientes", CreditStrings.owedSummary(state.totalOwed, state.debtorCount))
    }

    @Test
    fun filter_matches_name_ignoring_case() = runTest(dispatcher) {
        val vm = viewModel(FakeCreditRepository(debtors = AppResult.Success(listOf(juan, maria, pedro))))
        vm.load()
        advanceUntilIdle()

        vm.onFilterChange("  JUAN ")

        assertEquals(listOf(juan), vm.state.value.visibleDebtors)
        assertEquals(3, vm.state.value.debtorCount)
    }

    @Test
    fun filter_ignores_accents() = runTest(dispatcher) {
        val jose = debtor(13, "José Núñez", 80.0)
        val vm = viewModel(FakeCreditRepository(debtors = AppResult.Success(listOf(juan, maria, jose))))
        vm.load()
        advanceUntilIdle()

        vm.onFilterChange("jose")
        assertEquals(listOf(jose), vm.state.value.visibleDebtors)

        vm.onFilterChange("MARIA")
        assertEquals(listOf(maria), vm.state.value.visibleDebtors)

        vm.onFilterChange("nunez")
        assertEquals(emptyList(), vm.state.value.visibleDebtors)

        vm.onFilterChange("núñez")
        assertEquals(listOf(jose), vm.state.value.visibleDebtors)
    }

    @Test
    fun stale_statement_for_a_customer_no_longer_selected_is_dropped() = runTest(dispatcher) {
        val juanGate = CompletableDeferred<AppResult<CustomerStatement>>()
        val mariaStatement = CustomerStatement(maria.id, maria.name, null, true, 120.5, emptyList())
        val repo = FakeCreditRepository(
            debtors = AppResult.Success(listOf(juan, maria)),
            statements = mapOf(maria.id to AppResult.Success(mariaStatement)),
            gates = mapOf(juan.id to juanGate),
        )
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.select(juan.id)
        runCurrent()
        vm.back()
        vm.select(maria.id)
        advanceUntilIdle()
        juanGate.complete(AppResult.Success(CustomerStatement(juan.id, juan.name, null, true, 350.0, emptyList())))
        advanceUntilIdle()

        assertEquals(maria.id, vm.state.value.selectedId)
        assertEquals(mariaStatement, vm.state.value.selected)
    }

    @Test
    fun unknown_backend_text_is_never_shown() = runTest(dispatcher) {
        val vm = viewModel(FakeCreditRepository(debtors = AppResult.Failure(AppError.Validation("limit must be > 0"))))

        vm.load()
        advanceUntilIdle()

        assertEquals("No se pudo cargar el fiado, intenta de nuevo", vm.state.value.errorMessage)
    }

    @Test
    fun select_loads_the_statement_newest_first() = runTest(dispatcher) {
        val statement = CustomerStatement(
            id = juan.id,
            name = juan.name,
            phone = "5551234",
            active = true,
            balance = 350.0,
            entries = listOf(
                LedgerEntry(3, LedgerEntryType.PAYMENT, 100.0, "2026-10-14T10:00:00Z", null, null, null),
                LedgerEntry(2, LedgerEntryType.CHARGE, 450.0, "2026-10-03T10:00:00Z", 7, "S-00012", null),
            ),
        )
        val repo = FakeCreditRepository(
            debtors = AppResult.Success(listOf(juan)),
            statements = mapOf(juan.id to AppResult.Success(statement)),
        )
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.select(juan.id)
        assertTrue(vm.state.value.showsDetail)
        assertTrue(vm.state.value.isStatementLoading)
        advanceUntilIdle()

        assertEquals(statement, vm.state.value.selected)
        assertFalse(vm.state.value.isStatementLoading)
        assertEquals(listOf(juan.id to CreditViewModel.LEDGER_LIMIT), repo.statementCalls)
        assertEquals("Compra · S-00012", CreditStrings.entryTitle(CreditStrings.PURCHASE, "S-00012"))
        assertEquals("\u2212$100.00", CreditStrings.paymentAmount(100.0))
    }

    @Test
    fun back_returns_to_the_list() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            debtors = AppResult.Success(listOf(juan)),
            statements = mapOf(juan.id to AppResult.Failure(AppError.Validation("Customer not found"))),
        )
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()
        vm.select(juan.id)
        advanceUntilIdle()
        assertEquals("No se encontró el cliente", vm.state.value.errorMessage)

        vm.back()

        val state = vm.state.value
        assertFalse(state.showsDetail)
        assertNull(state.selected)
        assertNull(state.errorMessage)
        assertEquals(listOf(juan), state.visibleDebtors)
    }

    @Test
    fun load_failure_shows_the_error() = runTest(dispatcher) {
        val vm = viewModel(FakeCreditRepository(debtors = AppResult.Failure(AppError.Network("down"))))

        vm.load()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertEquals("No hay conexión con el servidor. Intenta de nuevo.", state.errorMessage)
        assertTrue(state.debtors.isEmpty())
    }

    @Test
    fun refresh_on_the_detail_reloads_list_and_statement() = runTest(dispatcher) {
        val statement = CustomerStatement(juan.id, juan.name, null, true, 350.0, emptyList())
        val repo = FakeCreditRepository(
            debtors = AppResult.Success(listOf(juan)),
            statements = mapOf(juan.id to AppResult.Success(statement)),
        )
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()
        vm.select(juan.id)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, repo.debtorsCalls)
        assertEquals(2, repo.statementCalls.size)
        assertEquals(statement, vm.state.value.selected)
    }

    @Test
    fun money_uses_thousands_separator_and_two_decimals() {
        assertEquals("$4,350.00", CreditStrings.money(4_350.0))
        assertEquals("$1,234,567.89", CreditStrings.money(1_234_567.89))
        assertEquals("$0.50", CreditStrings.money(0.5))
        assertEquals("Te deben $120.00 · 1 cliente", CreditStrings.owedSummary(120.0, 1))
    }

    private fun viewModel(repo: FakeCreditRepository) =
        CreditViewModel(CreditUseCases(repo), now = { now }, dispatcher = dispatcher)

    private fun debtor(id: Long, name: String, balance: Double, active: Boolean = true) = Debtor(
        id = id,
        name = name,
        phone = null,
        active = active,
        balance = balance,
        owesSince = "2026-10-03T10:00:00Z",
        lastMovementAt = "2026-10-14T10:00:00Z",
    )
}

private class FakeCreditRepository(
    private val debtors: AppResult<List<Debtor>> = AppResult.Success(emptyList()),
    private val statements: Map<Long, AppResult<CustomerStatement>> = emptyMap(),
    private val gates: Map<Long, CompletableDeferred<AppResult<CustomerStatement>>> = emptyMap(),
) : CreditRepository {
    var debtorsCalls = 0
    val statementCalls = mutableListOf<Pair<Long, Int>>()

    override suspend fun debtors(): AppResult<List<Debtor>> {
        debtorsCalls++
        return debtors
    }

    override suspend fun statement(customerId: Long, limit: Int): AppResult<CustomerStatement> {
        statementCalls += customerId to limit
        gates[customerId]?.let { return it.await() }
        return statements[customerId] ?: AppResult.Failure(AppError.Validation("Customer not found"))
    }
}
