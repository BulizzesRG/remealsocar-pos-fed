package com.posfab.shared.features.credit

import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.domain.CustomerMatch
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor
import com.posfab.shared.features.credit.domain.LedgerEntry
import com.posfab.shared.features.credit.domain.LedgerEntryType
import com.posfab.shared.features.credit.domain.LoadedCustomer
import com.posfab.shared.features.credit.domain.customerDisplayName
import com.posfab.shared.features.credit.repository.CreditRepository
import com.posfab.shared.features.credit.ui.CreditErrorText
import com.posfab.shared.features.credit.ui.CreditStrings
import com.posfab.shared.features.credit.ui.CreditViewModel
import com.posfab.shared.features.credit.ui.LibretaForm
import com.posfab.shared.features.credit.ui.LibretaState
import com.posfab.shared.features.credit.usecase.CreditUseCases
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
    private val juanMatch = CustomerMatch(10, "Juan López (el Wero)", "5551234", 350.0)
    private val mariaMatch = CustomerMatch(11, "María López", null, 0.0)

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

    @Test
    fun cashier_cannot_open_libreta() = runTest(dispatcher) {
        val vm = viewModel(FakeCreditRepository(), setOf(Permission.SELL, Permission.VIEW_CREDIT))

        vm.openLibreta()

        assertFalse(vm.state.value.canLoadLibreta)
        assertFalse(vm.state.value.showsLibreta)
    }

    @Test
    fun manager_opens_libreta_with_an_empty_form() = runTest(dispatcher) {
        val vm = manager(FakeCreditRepository())

        vm.openLibreta()

        val libreta = assertNotNull(vm.state.value.libreta)
        assertTrue(vm.state.value.canLoadLibreta)
        assertEquals(LibretaForm(), libreta.form)
        assertTrue(libreta.loadedToday.isEmpty())
        assertNull(libreta.errorMessage)
    }

    @Test
    fun typing_a_name_suggests_existing_customers_after_the_debounce() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(juanMatch, mariaMatch))
        val vm = openedLibreta(repo)

        vm.onLibretaName("j")
        advanceUntilIdle()
        assertTrue(repo.searchCalls.isEmpty())

        vm.onLibretaName("ju")
        advanceTimeBy(CreditViewModel.CUSTOMER_SEARCH_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue(repo.searchCalls.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf("ju" to CreditViewModel.CUSTOMER_SEARCH_LIMIT), repo.searchCalls)
        assertEquals(listOf(juanMatch, mariaMatch), libreta(vm).suggestions)
        assertEquals("debe $350.00", CreditStrings.owes(juanMatch.balance))
    }

    @Test
    fun picking_a_suggestion_locks_the_fields() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(juanMatch))
        val vm = openedLibreta(repo)
        vm.onLibretaName("juan")
        advanceUntilIdle()

        vm.pickSuggestion(juanMatch.id)
        vm.onLibretaName("juan l")
        advanceUntilIdle()

        val libreta = libreta(vm)
        assertEquals(juanMatch, libreta.form.selected)
        assertTrue(libreta.suggestions.isEmpty())
        assertEquals(1, repo.searchCalls.size)
        assertEquals("Juan López (el Wero) · 5551234", CreditStrings.withPhone(juanMatch.displayName, juanMatch.phone))

        vm.clearSelection()

        assertNull(libreta(vm).form.selected)
        assertEquals("", libreta(vm).form.name)
    }

    @Test
    fun save_creates_and_loads_a_new_customer() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = openedLibreta(repo)
        vm.onLibretaName("Rosa Martínez")
        vm.onLibretaNickname("la de la esquina")
        vm.onLibretaAmount("350")
        advanceUntilIdle()

        vm.saveLibreta()
        assertTrue(libreta(vm).form.isSaving)
        advanceUntilIdle()

        val libreta = libreta(vm)
        assertEquals(listOf<Triple<String, String?, String?>>(Triple("Rosa Martínez", "la de la esquina", "")), repo.createCalls)
        assertEquals(listOf(LoadCall(NEW_ID, 350.0, null)), repo.loadCalls)
        assertEquals(listOf(LoadedCustomer(NEW_ID, "Rosa Martínez (la de la esquina)", 350.0)), libreta.loadedToday)
        assertEquals(LibretaForm(), libreta.form)
        assertNull(libreta.errorMessage)
        assertNull(libreta.pendingKey)
    }

    @Test
    fun save_loads_only_for_a_selected_customer() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(juanMatch))
        val vm = openedLibreta(repo)
        pickJuan(vm)
        vm.onLibretaAmount("100.5")

        vm.saveLibreta()
        advanceUntilIdle()

        assertTrue(repo.createCalls.isEmpty())
        assertEquals(listOf(LoadCall(juanMatch.id, 100.5, null)), repo.loadCalls)
        assertEquals(listOf(LoadedCustomer(juanMatch.id, juanMatch.displayName, 100.5, "5551234")), libreta(vm).loadedToday)
    }

    @Test
    fun failed_load_after_create_retries_without_creating_again() = runTest(dispatcher) {
        val repo = FakeCreditRepository(loadFailures = mutableListOf(AppError.Network("down")))
        val vm = openedLibreta(repo)
        vm.onLibretaName("Rosa")
        vm.onLibretaAmount("200")
        advanceUntilIdle()

        vm.saveLibreta()
        advanceUntilIdle()
        assertEquals(CreditStrings.NETWORK, libreta(vm).errorMessage)
        assertEquals(NEW_ID, libreta(vm).form.selected?.id)
        assertFalse(libreta(vm).form.isSaving)

        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(1, repo.createCalls.size)
        assertEquals(listOf(NEW_ID, NEW_ID), repo.loadCalls.map { it.customerId })
        assertEquals(listOf(LoadedCustomer(NEW_ID, "Rosa", 200.0)), libreta(vm).loadedToday)
    }

    @Test
    fun save_rejects_blank_or_digits_only_name() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = openedLibreta(repo)
        vm.onLibretaAmount("10")

        vm.onLibretaName("   ")
        vm.saveLibreta()
        assertEquals(CreditStrings.NAME_REQUIRED, libreta(vm).errorMessage)

        vm.onLibretaName("123 456")
        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(CreditStrings.NAME_REQUIRED, libreta(vm).errorMessage)
        assertTrue(repo.createCalls.isEmpty())
        assertTrue(repo.loadCalls.isEmpty())
    }

    @Test
    fun save_rejects_zero_or_three_decimals() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = openedLibreta(repo)
        vm.onLibretaName("Rosa")
        advanceUntilIdle()

        vm.onLibretaAmount("0")
        vm.saveLibreta()
        assertEquals(CreditStrings.AMOUNT_REQUIRED, libreta(vm).errorMessage)

        vm.onLibretaAmount("10.505")
        assertEquals("10.50", libreta(vm).form.amount)
        assertTrue(libreta(vm).form.amountInputRejected)
        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(CreditStrings.INPUT_REJECTED, libreta(vm).errorMessage)
        assertEquals("", libreta(vm).form.amount)
        assertTrue(repo.createCalls.isEmpty())
        assertTrue(repo.loadCalls.isEmpty())
    }

    @Test
    fun save_rejects_a_bad_or_future_date() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = openedLibreta(repo)
        vm.onLibretaName("Rosa")
        vm.onLibretaAmount("50")
        advanceUntilIdle()

        mapOf(
            "31/02/2026" to "31/02/2026",
            "00/09/2026" to "00/09/2026",
            "15/00/2026" to "15/00/2026",
            "01/09/26" to "01/09/26",
            "0109" to "01/09/",
        ).forEach { (typed, masked) ->
            vm.onLibretaOwedSince("")
            vm.onLibretaOwedSince(typed)
            assertEquals(masked, libreta(vm).form.owedSince, typed)
            vm.saveLibreta()
            assertEquals(CreditStrings.DATE_INVALID, libreta(vm).errorMessage, typed)
        }

        vm.onLibretaOwedSince("")

        vm.onLibretaOwedSince("01/01/2027")
        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(CreditStrings.DATE_FUTURE, libreta(vm).errorMessage)
        assertTrue(repo.createCalls.isEmpty())
        assertTrue(repo.loadCalls.isEmpty())
    }

    @Test
    fun since_date_is_sent_as_iso() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(juanMatch))
        val vm = openedLibreta(repo)
        pickJuan(vm)
        vm.onLibretaAmount("80")
        vm.onLibretaOwedSince("01092026")
        assertEquals("01/09/2026", libreta(vm).form.owedSince)

        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(listOf(LoadCall(juanMatch.id, 80.0, "2026-09-01")), repo.loadCalls)
    }

    @Test
    fun correct_reopens_the_customer_with_its_amount() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = openedLibreta(repo)
        vm.onLibretaName("Rosa")
        vm.onLibretaAmount("350")
        advanceUntilIdle()
        vm.saveLibreta()
        advanceUntilIdle()

        vm.correct(NEW_ID)

        val form = libreta(vm).form
        assertEquals(NEW_ID, form.selected?.id)
        assertEquals("Rosa", form.selected?.displayName)
        assertEquals("350.00", form.amount)

        vm.onLibretaAmount("375")
        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(1, repo.createCalls.size)
        assertEquals(listOf(350.0, 375.0), repo.loadCalls.map { it.amount })
        assertEquals(listOf(LoadedCustomer(NEW_ID, "Rosa", 375.0)), libreta(vm).loadedToday)
    }

    @Test
    fun customer_with_movements_shows_the_message() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            customers = listOf(juanMatch),
            loadFailures = mutableListOf(AppError.Validation("Customer already has movements")),
        )
        val vm = openedLibreta(repo)
        pickJuan(vm)
        vm.onLibretaAmount("100")

        vm.saveLibreta()
        advanceUntilIdle()

        val libreta = libreta(vm)
        assertEquals(CreditStrings.HAS_MOVEMENTS, libreta.errorMessage)
        assertEquals(juanMatch, libreta.form.selected)
        assertEquals("100", libreta.form.amount)
        assertNull(libreta.pendingKey)
        assertTrue(libreta.loadedToday.isEmpty())
    }

    @Test
    fun network_failure_keeps_the_key() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            customers = listOf(juanMatch),
            loadFailures = mutableListOf(AppError.Network("down"), AppError.Validation("boom")),
        )
        val vm = openedLibreta(repo)
        pickJuan(vm)
        vm.onLibretaAmount("100")

        vm.saveLibreta()
        advanceUntilIdle()
        assertNotNull(libreta(vm).pendingKey)
        vm.saveLibreta()
        advanceUntilIdle()
        assertEquals(CreditStrings.SAVE_FAILED, libreta(vm).errorMessage)
        assertNull(libreta(vm).pendingKey)
        vm.saveLibreta()
        advanceUntilIdle()

        val keys = repo.loadKeys
        assertEquals(3, keys.size)
        assertEquals(keys[0], keys[1])
        assertNotEquals(keys[1], keys[2])
    }

    @Test
    fun close_refreshes_the_list() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeCreditRepository(debtors = AppResult.Success(listOf(juan)), loadGate = gate)
        val vm = manager(repo)
        vm.load()
        advanceUntilIdle()
        vm.openLibreta()
        vm.onLibretaName("Rosa")
        vm.onLibretaAmount("10")
        advanceUntilIdle()

        vm.saveLibreta()
        runCurrent()
        vm.closeLibreta()
        assertTrue(vm.state.value.showsLibreta)

        gate.complete(Unit)
        advanceUntilIdle()
        vm.closeLibreta()
        advanceUntilIdle()

        assertFalse(vm.state.value.showsLibreta)
        assertEquals(2, repo.debtorsCalls)
    }

    @Test
    fun filter_and_rows_use_the_nickname() = runTest(dispatcher) {
        val wero = debtor(20, "Juan López", 90.0).copy(nickname = "el Wero", code = "30000011")
        val vm = viewModel(FakeCreditRepository(debtors = AppResult.Success(listOf(juan, wero))))
        vm.load()
        advanceUntilIdle()

        vm.onFilterChange("wero")

        assertEquals(listOf(wero), vm.state.value.visibleDebtors)
        assertEquals("Juan López (el Wero)", wero.displayName)
        assertEquals("Número 30000011", CreditStrings.customerNumber("30000011"))
    }

    @Test
    fun correct_keeps_the_since_date() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(juanMatch))
        val vm = openedLibreta(repo)
        pickJuan(vm)
        vm.onLibretaAmount("80")
        vm.onLibretaOwedSince("01/09/2026")
        vm.saveLibreta()
        advanceUntilIdle()

        vm.correct(juanMatch.id)
        assertEquals("01/09/2026", libreta(vm).form.owedSince)
        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(listOf("2026-09-01", "2026-09-01"), repo.loadCalls.map { it.owedSince })
        assertEquals("2026-09-01", libreta(vm).loadedToday.single().owedSince)
    }

    @Test
    fun failed_save_requests_focus_again() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            customers = listOf(juanMatch),
            loadFailures = mutableListOf(AppError.Validation("Customer is inactive")),
        )
        val vm = openedLibreta(repo)
        pickJuan(vm)
        vm.onLibretaAmount("10")
        val before = libreta(vm).focusRequest

        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(before + 1, libreta(vm).focusRequest)
        assertFalse(libreta(vm).form.isSaving)
    }

    @Test
    fun libreta_errors_map_to_spanish() {
        val cases = mapOf(
            AppError.Validation("owedSince cannot be in the future") to CreditStrings.DATE_FUTURE,
            AppError.Validation("owedSince must be YYYY-MM-DD") to CreditStrings.DATE_INVALID,
            AppError.Validation("nickname too long") to CreditStrings.NICKNAME_TOO_LONG,
            AppError.Validation("Customer already has movements") to CreditStrings.HAS_MOVEMENTS,
            AppError.Validation("Customer is inactive") to CreditStrings.INACTIVE_CUSTOMER,
            AppError.Validation("Customer not found") to CreditStrings.NOT_FOUND,
            AppError.Forbidden to CreditStrings.NO_PERMISSION,
            AppError.Network("down") to CreditStrings.NETWORK,
            AppError.Validation("amount must be > 0") to CreditStrings.SAVE_FAILED,
            AppError.Unknown("boom") to CreditStrings.SAVE_FAILED,
        )
        cases.forEach { (error, text) -> assertEquals(text, CreditErrorText.libreta(error), error.toString()) }
    }

    @Test
    fun nickname_is_capped_at_sixty_characters() = runTest(dispatcher) {
        val vm = openedLibreta(FakeCreditRepository())

        vm.onLibretaNickname("x".repeat(80))

        assertEquals(60, libreta(vm).form.nickname.length)
    }

    @Test
    fun inactive_and_missing_customers_show_their_message() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            customers = listOf(juanMatch),
            loadFailures = mutableListOf(
                AppError.Validation("Customer is inactive"),
                AppError.Validation("Customer not found"),
            ),
        )
        val vm = openedLibreta(repo)
        pickJuan(vm)
        vm.onLibretaAmount("10")

        vm.saveLibreta()
        advanceUntilIdle()
        assertEquals(CreditStrings.INACTIVE_CUSTOMER, libreta(vm).errorMessage)

        vm.saveLibreta()
        advanceUntilIdle()
        assertEquals(CreditStrings.NOT_FOUND, libreta(vm).errorMessage)
    }

    @Test
    fun editing_after_a_network_failure_uses_a_new_key() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            customers = listOf(juanMatch),
            loadFailures = mutableListOf(AppError.Network("down")),
        )
        val vm = openedLibreta(repo)
        pickJuan(vm)
        vm.onLibretaAmount("10")
        vm.saveLibreta()
        advanceUntilIdle()
        assertNotNull(libreta(vm).pendingKey)

        vm.onLibretaAmount("12")
        assertNull(libreta(vm).pendingKey)
        vm.saveLibreta()
        advanceUntilIdle()

        assertNotEquals(repo.loadKeys[0], repo.loadKeys[1])
    }

    @Test
    fun create_failure_leaves_the_form_intact() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            createFailures = mutableListOf(AppError.Network("down"), AppError.Validation("nickname too long")),
        )
        val vm = openedLibreta(repo)
        vm.onLibretaName("Rosa")
        vm.onLibretaNickname("la de la esquina")
        vm.onLibretaAmount("50")
        advanceUntilIdle()

        vm.saveLibreta()
        advanceUntilIdle()
        assertEquals(CreditStrings.NETWORK, libreta(vm).errorMessage)
        assertEquals(LibretaForm(name = "Rosa", nickname = "la de la esquina", amount = "50"), libreta(vm).form)

        vm.saveLibreta()
        advanceUntilIdle()
        assertEquals(CreditStrings.NICKNAME_TOO_LONG, libreta(vm).errorMessage)
        assertEquals(LibretaForm(name = "Rosa", nickname = "la de la esquina", amount = "50"), libreta(vm).form)
        assertTrue(repo.loadCalls.isEmpty())
        assertEquals(2, repo.createCalls.size)
    }

    @Test
    fun typing_the_name_with_a_selected_customer_does_not_search() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(juanMatch))
        val vm = openedLibreta(repo)
        pickJuan(vm)
        repo.searchCalls.clear()

        vm.onLibretaName("María")
        advanceUntilIdle()

        assertTrue(repo.searchCalls.isEmpty())
        assertFalse(libreta(vm).isSearching)
    }

    @Test
    fun escape_clears_a_filled_form_and_closes_an_empty_one() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = openedLibreta(repo)
        vm.onLibretaName("Rosa")
        vm.onLibretaAmount("50")
        advanceUntilIdle()

        vm.onLibretaEscape()
        assertTrue(vm.state.value.showsLibreta)
        assertEquals(LibretaForm(), libreta(vm).form)

        vm.onLibretaEscape()
        advanceUntilIdle()
        assertFalse(vm.state.value.showsLibreta)
        assertEquals(1, repo.debtorsCalls)
    }

    @Test
    fun saved_notice_shows_until_the_next_keystroke_and_newest_is_first() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = openedLibreta(repo)
        vm.onLibretaName("María López")
        vm.onLibretaNickname("Doña Mary")
        vm.onLibretaAmount("350")
        advanceUntilIdle()
        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals("Guardado: María López (Doña Mary) · $350.00", libreta(vm).savedNotice)

        vm.onLibretaName("R")
        assertNull(libreta(vm).savedNotice)
        vm.onLibretaName("Rosa")
        vm.onLibretaAmount("20")
        advanceUntilIdle()
        vm.saveLibreta()
        advanceUntilIdle()
        assertEquals(listOf("Rosa", "María López (Doña Mary)"), libreta(vm).loadedToday.map { it.displayName })

        vm.correct(NEW_ID)
        vm.onLibretaAmount("375")
        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(listOf(375.0, 20.0), libreta(vm).loadedToday.map { it.amount })
        assertEquals("Cargados hoy (2)", CreditStrings.loadedToday(libreta(vm).loadedToday.size))
    }

    private fun viewModel(repo: FakeCreditRepository, permissions: Set<Permission> = emptySet()) =
        CreditViewModel(CreditUseCases(repo), permissions, now = { now }, dispatcher = dispatcher)

    private fun manager(repo: FakeCreditRepository) =
        viewModel(repo, setOf(Permission.SELL, Permission.VIEW_CREDIT, Permission.MANAGE_CREDIT))

    @Test
    fun phone_keeps_only_ten_digits() = runTest(dispatcher) {
        val vm = openedLibreta(FakeCreditRepository())

        vm.onLibretaPhone("55-12a34 5678 90")

        assertEquals("5512345678", libreta(vm).form.phone)
    }

    @Test
    fun save_rejects_a_short_phone_for_a_new_customer() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = openedLibreta(repo)
        vm.onLibretaName("Rosa")
        vm.onLibretaAmount("50")
        vm.onLibretaPhone("5512345")
        advanceUntilIdle()

        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals(CreditStrings.PHONE_INVALID, libreta(vm).errorMessage)
        assertTrue(repo.createCalls.isEmpty())
        assertTrue(repo.loadCalls.isEmpty())
    }

    @Test
    fun save_accepts_a_full_phone_for_a_new_customer() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = openedLibreta(repo)
        vm.onLibretaName("Rosa")
        vm.onLibretaAmount("50")
        vm.onLibretaPhone("5512345678")
        advanceUntilIdle()

        vm.saveLibreta()
        advanceUntilIdle()

        assertEquals("5512345678", repo.createCalls.single().third)
        assertEquals(1, repo.loadCalls.size)
    }

    @Test
    fun selected_customer_with_a_short_phone_still_saves() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(juanMatch))
        val vm = openedLibreta(repo)
        pickJuan(vm)
        vm.onLibretaAmount("80")

        vm.saveLibreta()
        advanceUntilIdle()

        assertNull(libreta(vm).errorMessage)
        assertEquals(listOf(LoadCall(juanMatch.id, 80.0, null)), repo.loadCalls)
    }

    @Test
    fun typing_a_high_day_digit_pads_it_and_adds_the_slash() = runTest(dispatcher) {
        val vm = openedLibreta(FakeCreditRepository())

        vm.onLibretaOwedSince("4")

        assertEquals("04/", libreta(vm).form.owedSince)
    }

    private fun openedLibreta(repo: FakeCreditRepository): CreditViewModel =
        manager(repo).also { it.openLibreta() }

    private fun TestScope.pickJuan(vm: CreditViewModel) {
        vm.onLibretaName("juan")
        advanceUntilIdle()
        vm.pickSuggestion(juanMatch.id)
    }

    private fun libreta(vm: CreditViewModel): LibretaState = assertNotNull(vm.state.value.libreta)

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

private const val NEW_ID = 40L

private data class LoadCall(val customerId: Long, val amount: Double, val owedSince: String?)

private class FakeCreditRepository(
    private val debtors: AppResult<List<Debtor>> = AppResult.Success(emptyList()),
    private val statements: Map<Long, AppResult<CustomerStatement>> = emptyMap(),
    private val gates: Map<Long, CompletableDeferred<AppResult<CustomerStatement>>> = emptyMap(),
    private val customers: List<CustomerMatch> = emptyList(),
    private val loadFailures: MutableList<AppError> = mutableListOf(),
    private val loadGate: CompletableDeferred<Unit>? = null,
    private val createFailures: MutableList<AppError> = mutableListOf(),
) : CreditRepository {
    var debtorsCalls = 0
    val statementCalls = mutableListOf<Pair<Long, Int>>()
    val searchCalls = mutableListOf<Pair<String, Int>>()
    val createCalls = mutableListOf<Triple<String, String?, String?>>()
    val loadCalls = mutableListOf<LoadCall>()
    val loadKeys = mutableListOf<String>()

    override suspend fun searchCustomers(query: String, limit: Int): AppResult<List<CustomerMatch>> {
        searchCalls += query to limit
        return AppResult.Success(customers)
    }

    override suspend fun createCustomer(name: String, nickname: String?, phone: String?): AppResult<CustomerMatch> {
        createCalls += Triple(name, nickname, phone)
        if (createFailures.isNotEmpty()) return AppResult.Failure(createFailures.removeAt(0))
        return AppResult.Success(CustomerMatch(NEW_ID + createCalls.size - 1, customerDisplayName(name.trim(), nickname), null, 0.0))
    }

    override suspend fun loadOpeningBalance(
        customerId: Long,
        amount: Double,
        owedSince: String?,
        idempotencyKey: String,
    ): AppResult<Double> {
        loadCalls += LoadCall(customerId, amount, owedSince)
        loadKeys += idempotencyKey
        loadGate?.await()
        if (loadFailures.isNotEmpty()) return AppResult.Failure(loadFailures.removeAt(0))
        return AppResult.Success(amount)
    }

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
