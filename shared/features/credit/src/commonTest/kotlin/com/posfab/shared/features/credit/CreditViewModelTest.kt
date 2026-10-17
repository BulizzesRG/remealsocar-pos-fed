package com.posfab.shared.features.credit

import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.domain.CustomerEdit
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
import com.posfab.shared.features.credit.ui.CustomerEditConfirm
import com.posfab.shared.features.credit.ui.CustomerEditForm
import com.posfab.shared.features.credit.ui.CustomerEditState
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

    private val lupe = CustomerStatement(
        id = 13,
        name = "Lupe Hernández",
        phone = "5551122896",
        active = true,
        balance = 379.0,
        entries = emptyList(),
        code = "30000117",
        nickname = "Doña Lupe",
        address = "Av juarez 5",
    )
    private val lupeDebtor = debtor(13, "Lupe Hernández", 379.0)
    private val mariaCustomer = CustomerStatement(2, "María López", null, true, 0.0, emptyList(), code = "30000025")
    private val mariaMatchInactive = CustomerMatch(2, "María López", null, 0.0, active = false, code = "30000025")

    @Test
    fun list_search_waits_for_two_letters_and_the_debounce() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(mariaMatchInactive))
        val vm = manager(repo)
        vm.load()
        advanceUntilIdle()

        vm.onFilterChange("m")
        advanceUntilIdle()
        assertTrue(repo.searchCalls.isEmpty())
        assertFalse(vm.state.value.isSearching)

        vm.onFilterChange("ma")
        assertTrue(vm.state.value.isSearching)
        advanceTimeBy(CreditViewModel.CUSTOMER_SEARCH_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue(repo.searchCalls.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf("ma" to CreditViewModel.LIST_SEARCH_LIMIT), repo.searchCalls)
        assertEquals(listOf(true), repo.searchIncludeInactive)
        assertEquals(listOf(mariaMatchInactive), vm.state.value.otherCustomers)
        assertFalse(vm.state.value.isSearching)
    }

    @Test
    fun list_search_without_manage_credit_asks_for_active_only() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(mariaMatch))
        val vm = viewModel(repo, setOf(Permission.VIEW_CREDIT))

        vm.onFilterChange("mar")
        advanceUntilIdle()

        assertEquals(listOf(false), repo.searchIncludeInactive)
        assertFalse(vm.state.value.canEditCustomers)
    }

    @Test
    fun list_search_trims_the_query() = runTest(dispatcher) {
        val repo = FakeCreditRepository()
        val vm = manager(repo)

        vm.onFilterChange("  ma  ")
        advanceUntilIdle()
        vm.onFilterChange(" m ")
        advanceUntilIdle()

        assertEquals(listOf("ma" to CreditViewModel.LIST_SEARCH_LIMIT), repo.searchCalls)
    }

    @Test
    fun other_customers_leave_out_those_already_owing() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            debtors = AppResult.Success(listOf(juan)),
            customers = listOf(juanMatch, mariaMatch),
        )
        val vm = manager(repo)
        vm.load()
        advanceUntilIdle()

        vm.onFilterChange("lopez")
        advanceUntilIdle()

        assertEquals(listOf(juanMatch, mariaMatch), vm.state.value.searchResults)
        assertEquals(listOf(mariaMatch), vm.state.value.otherCustomers)
        assertFalse(vm.state.value.showsNoMatches)
    }

    @Test
    fun a_stale_list_search_is_dropped_when_the_filter_changed() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeCreditRepository(customers = listOf(mariaMatch), searchGates = mapOf("ma" to gate))
        val vm = manager(repo)

        vm.onFilterChange("ma")
        advanceTimeBy(CreditViewModel.CUSTOMER_SEARCH_DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(1, repo.searchCalls.size)
        vm.onFilterChange("x")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(emptyList(), vm.state.value.searchResults)
        assertFalse(vm.state.value.isSearching)
    }

    @Test
    fun clearing_the_filter_clears_the_results() = runTest(dispatcher) {
        val vm = manager(FakeCreditRepository(customers = listOf(mariaMatch)))
        vm.onFilterChange("mar")
        advanceUntilIdle()
        assertEquals(listOf(mariaMatch), vm.state.value.searchResults)

        vm.onFilterChange("")

        assertEquals(emptyList(), vm.state.value.searchResults)
        assertFalse(vm.state.value.isSearching)
        assertFalse(vm.state.value.showsNoMatches)
    }

    @Test
    fun no_matches_shows_only_after_the_search_answers() = runTest(dispatcher) {
        val vm = manager(FakeCreditRepository(debtors = AppResult.Success(listOf(juan))))
        vm.load()
        advanceUntilIdle()

        vm.onFilterChange("zz")
        assertFalse(vm.state.value.showsNoMatches)
        advanceUntilIdle()

        assertTrue(vm.state.value.showsNoMatches)
    }

    @Test
    fun a_failed_list_search_shows_the_error_instead_of_no_matches() = runTest(dispatcher) {
        val repo = object : CreditRepository by FakeCreditRepository() {
            override suspend fun searchCustomers(
                query: String,
                limit: Int,
                includeInactive: Boolean,
            ): AppResult<List<CustomerMatch>> = AppResult.Failure(AppError.Network("down"))
        }
        val vm = CreditViewModel(CreditUseCases(repo), setOf(Permission.MANAGE_CREDIT), now = { now }, dispatcher = dispatcher)

        vm.onFilterChange("mar")
        advanceUntilIdle()

        val failed = vm.state.value
        assertEquals(emptyList(), failed.searchResults)
        assertFalse(failed.isSearching)
        assertTrue(failed.searchFailed)
        assertEquals(CreditErrorText.from(AppError.Network("down")), failed.errorMessage)
        assertFalse(failed.showsNoMatches)

        vm.onFilterChange("mari")
        assertFalse(vm.state.value.searchFailed)
    }

    @Test
    fun changing_the_query_drops_the_previous_other_customers_at_once() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(mariaMatch))
        val vm = manager(repo)

        vm.onFilterChange("mar")
        advanceUntilIdle()
        assertEquals(listOf(mariaMatch.id), vm.state.value.searchResults.map { it.id })

        vm.onFilterChange("mari")
        assertEquals(emptyList(), vm.state.value.searchResults)
        assertTrue(vm.state.value.isSearching)
        assertFalse(vm.state.value.showsNoMatches)
    }

    @Test
    fun selecting_someone_without_debt_shows_the_sheet() = runTest(dispatcher) {
        val repo = FakeCreditRepository(statements = mapOf(2L to AppResult.Success(mariaCustomer)))
        val vm = manager(repo)

        vm.select(2)
        advanceUntilIdle()

        val selected = assertNotNull(vm.state.value.selected)
        assertEquals(0.0, selected.balance, 0.001)
        assertTrue(selected.entries.isEmpty())
        assertNull(vm.state.value.selectedOwesSince)
        assertEquals("Número 30000025", CreditStrings.customerLine(selected.code, selected.phone, selected.address))
    }

    @Test
    fun selected_debtor_exposes_its_owes_since() = runTest(dispatcher) {
        val vm = manager(lupeRepo())
        vm.load()
        advanceUntilIdle()

        vm.select(13)
        advanceUntilIdle()

        assertEquals(lupeDebtor.owesSince, vm.state.value.selectedOwesSince)
    }

    @Test
    fun start_edit_needs_manage_credit() = runTest(dispatcher) {
        val vm = viewModel(lupeRepo(), setOf(Permission.VIEW_CREDIT))
        vm.select(13)
        advanceUntilIdle()

        vm.startEdit()

        assertNull(vm.state.value.edit)
    }

    @Test
    fun start_edit_does_nothing_while_the_sheet_loads() = runTest(dispatcher) {
        val gate = CompletableDeferred<AppResult<CustomerStatement>>()
        val vm = manager(FakeCreditRepository(gates = mapOf(13L to gate)))
        vm.select(13)
        runCurrent()

        vm.startEdit()
        assertNull(vm.state.value.edit)

        gate.complete(AppResult.Success(lupe))
        advanceUntilIdle()
        vm.startEdit()
        assertNotNull(vm.state.value.edit)
    }

    @Test
    fun start_edit_does_nothing_without_a_selected_customer() = runTest(dispatcher) {
        val vm = manager(lupeRepo())

        vm.startEdit()

        assertNull(vm.state.value.edit)
    }

    @Test
    fun start_edit_prefills_the_form_from_the_sheet() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())

        val edit = edit(vm)
        val expected = CustomerEditForm("Lupe Hernández", "Doña Lupe", "5551122896", "Av juarez 5", true)
        assertEquals(expected, edit.form)
        assertEquals(expected, edit.original)
        assertEquals("30000117", edit.code)
        assertFalse(edit.hasChanges)
        assertNull(edit.confirm)
    }

    @Test
    fun start_edit_prefills_missing_fields_as_empty() = runTest(dispatcher) {
        val repo = FakeCreditRepository(statements = mapOf(2L to AppResult.Success(mariaCustomer)))
        val vm = manager(repo)
        vm.select(2)
        advanceUntilIdle()

        vm.startEdit()

        assertEquals(CustomerEditForm("María López", "", "", "", true), edit(vm).form)
    }

    @Test
    fun edit_fields_are_sanitized_and_capped() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())

        vm.onEditPhone("55-51 12a2896999")
        vm.onEditNickname("x".repeat(70))
        vm.onEditAddress("y".repeat(300))
        vm.onEditName("z".repeat(300))

        val form = edit(vm).form
        assertEquals("5551122896", form.phone)
        assertEquals(60, form.nickname.length)
        assertEquals(255, form.address.length)
        assertEquals(255, form.name.length)
    }

    @Test
    fun save_validates_the_name_first_then_the_phone() = runTest(dispatcher) {
        val repo = lupeRepo()
        val vm = editingLupe(repo)

        vm.onEditName("   ")
        vm.onEditPhone("555")
        vm.saveEdit()
        assertEquals(CreditStrings.NAME_REQUIRED, edit(vm).errorMessage)
        assertNull(edit(vm).confirm)

        vm.onEditName("12345")
        vm.saveEdit()
        assertEquals(CreditStrings.NAME_REQUIRED, edit(vm).errorMessage)

        vm.onEditName("Lupe")
        assertNull(edit(vm).errorMessage)
        vm.saveEdit()
        assertEquals(CreditStrings.PHONE_INVALID, edit(vm).errorMessage)
        assertNull(edit(vm).confirm)
        assertTrue(repo.updateCalls.isEmpty())
    }

    @Test
    fun an_empty_phone_is_valid() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())

        vm.onEditPhone("")
        vm.saveEdit()

        assertNull(edit(vm).errorMessage)
        assertEquals(CustomerEditConfirm.SAVE, edit(vm).confirm)
    }

    @Test
    fun save_without_changes_just_closes_the_form() = runTest(dispatcher) {
        val repo = lupeRepo()
        val vm = editingLupe(repo)

        vm.saveEdit()

        assertNull(vm.state.value.edit)
        assertTrue(repo.updateCalls.isEmpty())
    }

    @Test
    fun save_with_changes_asks_to_confirm() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())

        vm.onEditNickname("Lupita")
        vm.saveEdit()

        assertEquals(CustomerEditConfirm.SAVE, edit(vm).confirm)
        assertEquals("¿Guardar los cambios de Lupe Hernández (Doña Lupe)?", CreditStrings.saveTitle(lupe.displayName))
    }

    @Test
    fun turning_active_off_asks_to_deactivate() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())

        vm.onEditActive(false)
        vm.saveEdit()

        assertEquals(CustomerEditConfirm.DEACTIVATE, edit(vm).confirm)
        assertEquals("¿Dar de baja a María López (Mary)?", CreditStrings.deactivateTitle("María López (Mary)"))
    }

    @Test
    fun reactivating_uses_the_normal_save_confirm() = runTest(dispatcher) {
        val inactive = lupe.copy(active = false)
        val vm = manager(FakeCreditRepository(statements = mapOf(13L to AppResult.Success(inactive))))
        vm.select(13)
        advanceUntilIdle()
        vm.startEdit()

        vm.onEditActive(true)
        vm.saveEdit()

        assertEquals(CustomerEditConfirm.SAVE, edit(vm).confirm)
    }

    @Test
    fun fields_are_locked_while_a_confirm_is_open() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())
        vm.onEditNickname("Lupita")
        vm.saveEdit()

        vm.onEditName("Otra")

        assertEquals("Lupe Hernández", edit(vm).form.name)
    }

    @Test
    fun confirming_sends_trimmed_values_with_nulls_for_blanks() = runTest(dispatcher) {
        val repo = lupeRepo()
        val vm = editingLupe(repo)

        vm.onEditName("  Lupe Hernández  ")
        vm.onEditNickname("  ")
        vm.onEditPhone("")
        vm.onEditAddress(" Calle 1 ")
        vm.saveEdit()
        vm.confirmEdit()
        advanceUntilIdle()

        assertEquals(
            listOf(13L to CustomerEdit("Lupe Hernández", null, null, "Calle 1", true)),
            repo.updateCalls,
        )
    }

    @Test
    fun a_successful_save_closes_the_form_and_reloads_sheet_and_list() = runTest(dispatcher) {
        val repo = lupeRepo()
        val vm = editingLupe(repo)
        val debtorsBefore = repo.debtorsCalls
        val statementsBefore = repo.statementCalls.size

        vm.onEditActive(false)
        vm.saveEdit()
        vm.confirmEdit()
        assertTrue(edit(vm).isSaving)
        assertNull(edit(vm).confirm)
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.edit)
        assertEquals(CreditStrings.SAVED, state.savedNotice)
        assertEquals(13L, state.selectedId)
        assertEquals(statementsBefore + 1, repo.statementCalls.size)
        assertEquals(debtorsBefore + 1, repo.debtorsCalls)
        assertEquals(false, repo.updateCalls.single().second.active)
    }

    @Test
    fun a_successful_save_refreshes_the_list_search() = runTest(dispatcher) {
        val repo = lupeRepo()
        val vm = manager(repo)
        vm.load()
        advanceUntilIdle()
        vm.onFilterChange("lupe")
        advanceUntilIdle()
        vm.select(13)
        advanceUntilIdle()
        vm.startEdit()
        val searchesBefore = repo.searchCalls.size

        vm.onEditNickname("Lupita")
        vm.saveEdit()
        vm.confirmEdit()
        advanceUntilIdle()

        assertEquals(searchesBefore + 1, repo.searchCalls.size)
    }

    @Test
    fun confirm_twice_sends_one_update() = runTest(dispatcher) {
        val repo = lupeRepo()
        val vm = editingLupe(repo)
        vm.onEditNickname("Lupita")
        vm.saveEdit()

        vm.confirmEdit()
        vm.confirmEdit()
        advanceUntilIdle()

        assertEquals(1, repo.updateCalls.size)
    }

    @Test
    fun confirm_without_an_open_confirm_does_nothing() = runTest(dispatcher) {
        val repo = lupeRepo()
        val vm = editingLupe(repo)
        vm.onEditNickname("Lupita")

        vm.confirmEdit()
        advanceUntilIdle()

        assertTrue(repo.updateCalls.isEmpty())
        assertFalse(edit(vm).isSaving)
    }

    @Test
    fun a_server_validation_error_keeps_the_form_with_spanish_text() = runTest(dispatcher) {
        val repo = lupeRepo(updateFailures = mutableListOf(AppError.Validation("phone must have 10 digits")))
        val vm = editingLupe(repo)

        vm.onEditNickname("Lupita")
        vm.saveEdit()
        vm.confirmEdit()
        advanceUntilIdle()

        val edit = edit(vm)
        assertFalse(edit.isSaving)
        assertEquals(CreditStrings.PHONE_INVALID, edit.errorMessage)
        assertEquals("Lupita", edit.form.nickname)
        assertNull(vm.state.value.savedNotice)
    }

    @Test
    fun a_network_error_keeps_the_form_and_can_be_retried() = runTest(dispatcher) {
        val repo = lupeRepo(updateFailures = mutableListOf(AppError.Network("down")))
        val vm = editingLupe(repo)

        vm.onEditNickname("Lupita")
        vm.saveEdit()
        vm.confirmEdit()
        advanceUntilIdle()
        assertEquals(CreditStrings.NETWORK, edit(vm).errorMessage)

        vm.saveEdit()
        vm.confirmEdit()
        advanceUntilIdle()

        assertNull(vm.state.value.edit)
        assertEquals(2, repo.updateCalls.size)
        assertEquals(CreditStrings.SAVED, vm.state.value.savedNotice)
    }

    @Test
    fun cancel_with_changes_asks_to_discard_and_stays_on_the_sheet() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())
        vm.onEditNickname("Lupita")

        vm.cancelEdit()
        assertEquals(CustomerEditConfirm.DISCARD, edit(vm).confirm)
        vm.confirmDiscard()

        assertNull(vm.state.value.edit)
        assertEquals(13L, vm.state.value.selectedId)
    }

    @Test
    fun cancel_without_changes_closes_directly() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())

        vm.cancelEdit()

        assertNull(vm.state.value.edit)
        assertEquals(13L, vm.state.value.selectedId)
    }

    @Test
    fun back_with_changes_asks_and_confirming_leaves_the_sheet() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())
        vm.onEditAddress("Calle 2")

        vm.back()
        assertEquals(CustomerEditConfirm.DISCARD, edit(vm).confirm)
        assertEquals(13L, vm.state.value.selectedId)
        vm.confirmDiscard()

        assertNull(vm.state.value.edit)
        assertNull(vm.state.value.selectedId)
        assertNull(vm.state.value.selected)
    }

    @Test
    fun keep_editing_closes_the_discard_confirm() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())
        vm.onEditAddress("Calle 2")
        vm.back()

        vm.dismissEditConfirm()

        assertNull(edit(vm).confirm)
        assertFalse(edit(vm).leaveOnDiscard)
        assertEquals("Calle 2", edit(vm).form.address)
        assertEquals(13L, vm.state.value.selectedId)
    }

    @Test
    fun back_without_changes_leaves_the_sheet() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())

        vm.back()

        assertNull(vm.state.value.edit)
        assertNull(vm.state.value.selectedId)
    }

    @Test
    fun escape_asks_to_discard_and_escape_again_keeps_editing() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())
        vm.onEditName("Lupe H.")

        vm.onEditEscape()
        assertEquals(CustomerEditConfirm.DISCARD, edit(vm).confirm)
        vm.onEditEscape()

        assertNull(edit(vm).confirm)
        assertEquals("Lupe H.", edit(vm).form.name)
    }

    @Test
    fun escape_without_changes_closes_the_form() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())

        vm.onEditEscape()

        assertNull(vm.state.value.edit)
    }

    @Test
    fun confirm_discard_only_works_on_a_discard_confirm() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())
        vm.onEditNickname("Lupita")
        vm.saveEdit()

        vm.confirmDiscard()

        assertEquals(CustomerEditConfirm.SAVE, edit(vm).confirm)
    }

    @Test
    fun nothing_leaves_the_form_while_saving() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = lupeRepo(updateGate = gate)
        val vm = editingLupe(repo)
        vm.onEditNickname("Lupita")
        vm.saveEdit()
        vm.confirmEdit()
        runCurrent()
        assertTrue(edit(vm).isSaving)

        vm.back()
        vm.cancelEdit()
        vm.onEditEscape()
        vm.onEditName("Otra")

        val saving = edit(vm)
        assertTrue(saving.isSaving)
        assertNull(saving.confirm)
        assertEquals("Lupe Hernández", saving.form.name)
        assertEquals(13L, vm.state.value.selectedId)

        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(vm.state.value.edit)
    }

    @Test
    fun back_while_a_save_confirm_is_open_does_nothing() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())
        vm.onEditNickname("Lupita")
        vm.saveEdit()

        vm.back()

        assertEquals(CustomerEditConfirm.SAVE, edit(vm).confirm)
        assertEquals(13L, vm.state.value.selectedId)
    }

    @Test
    fun selecting_another_customer_drops_the_edit_and_the_notice() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            debtors = AppResult.Success(listOf(lupeDebtor)),
            statements = mapOf(13L to AppResult.Success(lupe), 2L to AppResult.Success(mariaCustomer)),
        )
        val vm = editingLupe(repo)
        vm.onEditNickname("Lupita")

        vm.select(2)
        advanceUntilIdle()

        assertNull(vm.state.value.edit)
        assertEquals(2L, vm.state.value.selectedId)
        vm.startEdit()
        assertEquals(CustomerEditForm("María López", "", "", "", true), edit(vm).form)
    }

    @Test
    fun the_saved_notice_clears_when_leaving_or_editing_again() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())
        vm.onEditNickname("Lupita")
        vm.saveEdit()
        vm.confirmEdit()
        advanceUntilIdle()
        assertEquals(CreditStrings.SAVED, vm.state.value.savedNotice)

        vm.startEdit()
        assertNull(vm.state.value.savedNotice)
        vm.back()
        assertNull(vm.state.value.savedNotice)
    }

    @Test
    fun libreta_suggestions_never_ask_for_inactive_customers() = runTest(dispatcher) {
        val repo = FakeCreditRepository(customers = listOf(juanMatch))
        val vm = openedLibreta(repo)

        vm.onLibretaName("juan")
        advanceUntilIdle()

        assertEquals(listOf(false), repo.searchIncludeInactive)
    }

    @Test
    fun an_inactive_debtor_stays_in_the_owing_list() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            debtors = AppResult.Success(listOf(debtor(13, "Lupe Hernández", 379.0, active = false))),
        )
        val vm = manager(repo)

        vm.load()
        advanceUntilIdle()
        vm.onFilterChange("lupe")
        advanceUntilIdle()

        assertEquals(listOf(false), vm.state.value.visibleDebtors.map { it.active })
        assertEquals(379.0, vm.state.value.totalOwed, 0.001)
    }

    @Test
    fun refreshing_while_editing_keeps_the_typed_form() = runTest(dispatcher) {
        val repo = lupeRepo()
        val vm = editingLupe(repo)
        vm.onEditNickname("Lupita")
        vm.onEditPhone("55512")

        vm.refresh()
        advanceUntilIdle()

        assertEquals("Lupita", edit(vm).form.nickname)
        assertEquals("55512", edit(vm).form.phone)
        assertEquals(13L, vm.state.value.selectedId)
        assertFalse(vm.state.value.isStatementLoading)
    }

    @Test
    fun selecting_the_same_customer_again_keeps_the_edit() = runTest(dispatcher) {
        val vm = editingLupe(lupeRepo())
        vm.onEditAddress("Calle 1")

        vm.select(13)
        advanceUntilIdle()

        assertEquals("Calle 1", edit(vm).form.address)
    }

    @Test
    fun going_back_after_discarding_from_back_clears_the_edit_for_the_next_customer() = runTest(dispatcher) {
        val repo = FakeCreditRepository(
            debtors = AppResult.Success(listOf(lupeDebtor)),
            statements = mapOf(13L to AppResult.Success(lupe), 2L to AppResult.Success(mariaCustomer)),
        )
        val vm = editingLupe(repo)
        vm.onEditNickname("Lupita")
        vm.back()
        vm.confirmDiscard()
        vm.select(2)
        advanceUntilIdle()
        vm.startEdit()

        assertEquals(CustomerEditForm("María López", "", "", "", true), edit(vm).form)
        assertFalse(edit(vm).hasChanges)
    }

    @Test
    fun edit_errors_translate_from_a_closed_table() {
        assertEquals(CreditStrings.NOT_FOUND, CreditErrorText.edit(AppError.Validation("Customer not found")))
        assertEquals(CreditStrings.PHONE_INVALID, CreditErrorText.edit(AppError.Validation("phone must have 10 digits")))
        assertEquals(CreditStrings.NICKNAME_TOO_LONG, CreditErrorText.edit(AppError.Validation("nickname too long")))
        assertEquals(CreditStrings.ADDRESS_TOO_LONG, CreditErrorText.edit(AppError.Validation("address too long")))
        assertEquals(CreditStrings.NAME_TOO_LONG, CreditErrorText.edit(AppError.Validation("name too long")))
        assertEquals(CreditStrings.NAME_REQUIRED, CreditErrorText.edit(AppError.Validation("name is required")))
        assertEquals(CreditStrings.NO_EDIT_PERMISSION, CreditErrorText.edit(AppError.Forbidden))
        assertEquals(CreditStrings.NETWORK, CreditErrorText.edit(AppError.Network("down")))
        assertEquals(CreditStrings.SAVE_FAILED, CreditErrorText.edit(AppError.Validation("something else")))
        assertEquals(CreditStrings.SAVE_FAILED, CreditErrorText.edit(AppError.Conflict))
        assertEquals(CreditStrings.SAVE_FAILED, CreditErrorText.edit(AppError.Unknown("boom")))
    }

    private fun lupeRepo(
        updateFailures: MutableList<AppError> = mutableListOf(),
        updateGate: CompletableDeferred<Unit>? = null,
    ) = FakeCreditRepository(
        debtors = AppResult.Success(listOf(lupeDebtor)),
        statements = mapOf(13L to AppResult.Success(lupe)),
        customers = listOf(CustomerMatch(13, lupe.displayName, lupe.phone, lupe.balance, code = lupe.code)),
        updateFailures = updateFailures,
        updateGate = updateGate,
    )

    private fun TestScope.editingLupe(repo: FakeCreditRepository): CreditViewModel {
        val vm = manager(repo)
        vm.select(13)
        advanceUntilIdle()
        vm.startEdit()
        return vm
    }

    private fun edit(vm: CreditViewModel): CustomerEditState = assertNotNull(vm.state.value.edit)

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
    private val searchGates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
    private val updateFailures: MutableList<AppError> = mutableListOf(),
    private val updateGate: CompletableDeferred<Unit>? = null,
) : CreditRepository {
    var debtorsCalls = 0
    val statementCalls = mutableListOf<Pair<Long, Int>>()
    val searchCalls = mutableListOf<Pair<String, Int>>()
    val createCalls = mutableListOf<Triple<String, String?, String?>>()
    val loadCalls = mutableListOf<LoadCall>()
    val loadKeys = mutableListOf<String>()
    val searchIncludeInactive = mutableListOf<Boolean>()
    val updateCalls = mutableListOf<Pair<Long, CustomerEdit>>()

    override suspend fun searchCustomers(
        query: String,
        limit: Int,
        includeInactive: Boolean,
    ): AppResult<List<CustomerMatch>> {
        searchCalls += query to limit
        searchIncludeInactive += includeInactive
        searchGates[query]?.await()
        return AppResult.Success(customers)
    }

    override suspend fun updateCustomer(customerId: Long, edit: CustomerEdit): AppResult<Unit> {
        updateCalls += customerId to edit
        updateGate?.await()
        if (updateFailures.isNotEmpty()) return AppResult.Failure(updateFailures.removeAt(0))
        return AppResult.Success(Unit)
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
