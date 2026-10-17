package com.posfab.shared.features.reports.sales

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.reports.common.ReportsUseCases
import com.posfab.shared.features.reports.manager.DebtorItem
import com.posfab.shared.features.reports.manager.IntegrityResult
import com.posfab.shared.features.reports.manager.ManagerDailyTotal
import com.posfab.shared.features.reports.manager.ManagerRepository
import com.posfab.shared.features.reports.manager.WasteItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = TimeZone.of("America/Mexico_City")
    private val now = Instant.parse("2026-10-17T01:00:00Z")
    private val today = LocalDate(2026, 10, 16)
    private val yesterday = LocalDate(2026, 10, 15)

    private val cash = sale(1, SalePayment.CASH, 100.0)
    private val transfer = sale(2, SalePayment.TRANSFER, 40.5)
    private val card = sale(3, SalePayment.CARD, 21.0)
    private val credit = sale(4, SalePayment.CREDIT, 18.0, "Lupe Hernández (Doña Lupe)")
    private val cash2 = sale(5, SalePayment.CASH, 9.5)

    @Test
    fun load_shows_today_in_the_business_zone() = runTest(dispatcher) {
        val repo = FakeSalesRepository(sales = mapOf(today to listOf(cash, transfer)))
        val vm = viewModel(repo)

        vm.load()
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(today, state.day)
        assertEquals(today, state.today)
        assertTrue(state.isToday)
        assertFalse(state.isLoading)
        assertEquals(listOf(cash, transfer), state.sales)
        assertEquals(140.5, state.total, 0.001)
        assertEquals(2, state.count)
        assertEquals(now.toEpochMilliseconds(), state.nowEpochMillis)
        assertEquals(listOf(today), repo.salesCalls)
        assertEquals(listOf(today), repo.unitsCalls)
    }

    @Test
    fun by_payment_sums_every_payment_type() = runTest(dispatcher) {
        val repo = FakeSalesRepository(sales = mapOf(today to listOf(cash, transfer, card, credit, cash2)))
        val vm = loaded(repo)

        val byPayment = vm.state.value.byPayment
        assertEquals(109.5, byPayment.getValue(SalePayment.CASH), 0.001)
        assertEquals(40.5, byPayment.getValue(SalePayment.TRANSFER), 0.001)
        assertEquals(21.0, byPayment.getValue(SalePayment.CARD), 0.001)
        assertEquals(18.0, byPayment.getValue(SalePayment.CREDIT), 0.001)
        assertEquals(189.0, vm.state.value.total, 0.001)
    }

    @Test
    fun by_payment_is_zero_for_an_empty_day() = runTest(dispatcher) {
        val vm = loaded(FakeSalesRepository())

        assertEquals(SalePayment.entries.toSet(), vm.state.value.byPayment.keys)
        assertTrue(vm.state.value.byPayment.values.all { it == 0.0 })
        assertEquals(0, vm.state.value.count)
    }

    @Test
    fun units_come_from_the_repository() = runTest(dispatcher) {
        val units = DayUnits(tienda = 120.0, fonda = 75.0, torteria = 50.0)
        val vm = loaded(FakeSalesRepository(units = mapOf(today to units)))

        assertEquals(units, vm.state.value.units)
    }

    @Test
    fun previous_and_next_day_move_one_day_and_stop_at_today() = runTest(dispatcher) {
        val repo = FakeSalesRepository(sales = mapOf(yesterday to listOf(card)))
        val vm = loaded(repo)

        vm.nextDay()
        advanceUntilIdle()
        assertEquals(today, vm.state.value.day)
        assertEquals(1, repo.salesCalls.size)

        vm.previousDay()
        advanceUntilIdle()
        assertEquals(yesterday, vm.state.value.day)
        assertFalse(vm.state.value.isToday)
        assertEquals(listOf(card), vm.state.value.sales)

        vm.previousDay()
        advanceUntilIdle()
        assertEquals(LocalDate(2026, 10, 14), vm.state.value.day)
        assertTrue(vm.state.value.sales.isEmpty())

        vm.nextDay()
        vm.nextDay()
        advanceUntilIdle()
        assertEquals(today, vm.state.value.day)
        assertTrue(vm.state.value.isToday)
        vm.nextDay()
        advanceUntilIdle()
        assertEquals(today, vm.state.value.day)
        assertEquals(listOf(today, yesterday, LocalDate(2026, 10, 14), yesterday, today), repo.salesCalls)
    }

    @Test
    fun date_input_is_masked_with_the_caret_at_the_end() = runTest(dispatcher) {
        val vm = loaded(FakeSalesRepository())

        vm.onDateInput(TextFieldValue("16102026"))

        assertEquals("16/10/2026", vm.state.value.dateInput.text)
        assertEquals(TextRange(10), vm.state.value.dateInput.selection)
    }

    @Test
    fun typed_valid_date_loads_that_day_and_clears_the_field() = runTest(dispatcher) {
        val repo = FakeSalesRepository(sales = mapOf(LocalDate(2026, 9, 1) to listOf(cash)))
        val vm = loaded(repo)

        vm.onDateInput(TextFieldValue("01092026"))
        vm.goToTypedDate()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(LocalDate(2026, 9, 1), state.day)
        assertEquals("", state.dateInput.text)
        assertNull(state.notice)
        assertEquals(listOf(cash), state.sales)
    }

    @Test
    fun typed_invalid_date_shows_the_format_without_loading() = runTest(dispatcher) {
        val repo = FakeSalesRepository()
        val vm = loaded(repo)

        vm.onDateInput(TextFieldValue("1610"))
        vm.goToTypedDate()
        advanceUntilIdle()

        assertEquals(SalesStrings.DATE_INVALID, vm.state.value.notice)
        assertEquals(today, vm.state.value.day)
        assertEquals(1, repo.salesCalls.size)

        vm.onDateInput(TextFieldValue("31022026"))
        assertNull(vm.state.value.notice)
        vm.goToTypedDate()
        assertEquals(SalesStrings.DATE_INVALID, vm.state.value.notice)
    }

    @Test
    fun typed_future_date_is_rejected() = runTest(dispatcher) {
        val repo = FakeSalesRepository()
        val vm = loaded(repo)

        vm.onDateInput(TextFieldValue("17102026"))
        vm.goToTypedDate()
        advanceUntilIdle()

        assertEquals(SalesStrings.DATE_FUTURE, vm.state.value.notice)
        assertEquals("17/10/2026", vm.state.value.dateInput.text)
        assertEquals(today, vm.state.value.day)
        assertEquals(1, repo.salesCalls.size)
    }

    @Test
    fun select_shows_the_detail_and_back_returns_to_the_list() = runTest(dispatcher) {
        val repo = FakeSalesRepository(sales = mapOf(today to listOf(cash)), details = mapOf(1L to detail(1)))
        val vm = loaded(repo)

        vm.select(1L)
        assertTrue(vm.state.value.showsDetail)
        assertTrue(vm.state.value.isDetailLoading)
        advanceUntilIdle()

        assertEquals(detail(1), vm.state.value.detail)
        assertFalse(vm.state.value.isDetailLoading)

        vm.back()

        val state = vm.state.value
        assertFalse(state.showsDetail)
        assertNull(state.detail)
        assertNull(state.selectedId)
        assertEquals(listOf(cash), state.sales)
    }

    @Test
    fun arrows_do_nothing_while_the_detail_is_open() = runTest(dispatcher) {
        val repo = FakeSalesRepository(details = mapOf(1L to detail(1)))
        val vm = loaded(repo)
        vm.select(1L)
        advanceUntilIdle()

        vm.previousDay()
        advanceUntilIdle()

        assertEquals(today, vm.state.value.day)
        assertTrue(vm.state.value.showsDetail)
    }

    @Test
    fun stale_detail_is_dropped_after_back() = runTest(dispatcher) {
        val gate = CompletableDeferred<AppResult<SaleDetail>>()
        val repo = FakeSalesRepository(detailGates = mapOf(1L to gate))
        val vm = loaded(repo)

        vm.select(1L)
        runCurrent()
        vm.back()
        gate.complete(AppResult.Success(detail(1)))
        advanceUntilIdle()

        assertNull(vm.state.value.detail)
        assertFalse(vm.state.value.showsDetail)
    }

    @Test
    fun stale_detail_is_dropped_when_another_sale_is_selected() = runTest(dispatcher) {
        val first = CompletableDeferred<AppResult<SaleDetail>>()
        val repo = FakeSalesRepository(details = mapOf(2L to detail(2)), detailGates = mapOf(1L to first))
        val vm = loaded(repo)

        vm.select(1L)
        runCurrent()
        vm.select(2L)
        advanceUntilIdle()
        first.complete(AppResult.Success(detail(1)))
        advanceUntilIdle()

        assertEquals(2L, vm.state.value.selectedId)
        assertEquals(detail(2), vm.state.value.detail)
    }

    @Test
    fun missing_sale_shows_a_message_in_the_detail() = runTest(dispatcher) {
        val vm = loaded(FakeSalesRepository())

        vm.select(9L)
        advanceUntilIdle()

        assertEquals(SalesStrings.SALE_NOT_FOUND, vm.state.value.detailError)
        assertNull(vm.state.value.dayError)
        assertNull(vm.state.value.detail)
        assertFalse(vm.state.value.isDetailLoading)

        vm.back()
        assertNull(vm.state.value.detailError)
    }

    @Test
    fun network_failure_sets_the_message_and_refresh_retries() = runTest(dispatcher) {
        val repo = FakeSalesRepository(
            sales = mapOf(today to listOf(cash)),
            units = mapOf(today to DayUnits(100.0, 0.0, 0.0)),
        )
        repo.failSales = true
        val vm = loaded(repo)

        assertEquals(SalesStrings.NETWORK, vm.state.value.dayError)
        assertTrue(vm.state.value.sales.isEmpty())
        assertFalse(vm.state.value.showsAmounts)
        assertEquals(DayUnits(100.0, 0.0, 0.0), vm.state.value.units)

        repo.failSales = false
        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.state.value.dayError)
        assertTrue(vm.state.value.showsAmounts)
        assertEquals(listOf(cash), vm.state.value.sales)
        assertEquals(2, repo.salesCalls.size)
    }

    @Test
    fun units_failure_keeps_the_sales_and_shows_the_message() = runTest(dispatcher) {
        val repo = FakeSalesRepository(sales = mapOf(today to listOf(cash)))
        repo.failUnits = true
        val vm = loaded(repo)

        assertEquals(listOf(cash), vm.state.value.sales)
        assertNull(vm.state.value.units)
        assertEquals(SalesStrings.NETWORK, vm.state.value.dayError)
        assertTrue(vm.state.value.showsAmounts)
    }

    @Test
    fun a_stale_day_load_is_discarded() = runTest(dispatcher) {
        val slow = CompletableDeferred<AppResult<List<SaleListItem>>>()
        val repo = FakeSalesRepository(
            sales = mapOf(yesterday to listOf(card)),
            salesGates = mapOf(today to slow),
        )
        val vm = viewModel(repo)

        vm.load()
        runCurrent()
        val generation = vm.state.value.loadGeneration
        vm.previousDay()
        advanceUntilIdle()
        slow.complete(AppResult.Success(listOf(cash)))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(generation + 1, state.loadGeneration)
        assertEquals(yesterday, state.day)
        assertEquals(listOf(card), state.sales)
        assertFalse(state.isLoading)
    }

    @Test
    fun load_closes_an_open_detail_and_clears_the_typed_date() = runTest(dispatcher) {
        val gate = CompletableDeferred<AppResult<SaleDetail>>()
        val repo = FakeSalesRepository(sales = mapOf(yesterday to listOf(card)), detailGates = mapOf(1L to gate))
        val vm = loaded(repo)
        vm.previousDay()
        advanceUntilIdle()
        vm.onDateInput(TextFieldValue("1610"))
        vm.goToTypedDate()
        vm.select(1L)
        runCurrent()

        vm.load()
        gate.complete(AppResult.Success(detail(1)))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(today, state.day)
        assertFalse(state.showsDetail)
        assertNull(state.detail)
        assertFalse(state.isDetailLoading)
        assertNull(state.detailError)
        assertNull(state.notice)
        assertEquals("", state.dateInput.text)
    }

    @Test
    fun back_after_a_day_error_keeps_the_day_error() = runTest(dispatcher) {
        val repo = FakeSalesRepository(details = mapOf(1L to detail(1)))
        repo.failSales = true
        val vm = loaded(repo)
        vm.select(1L)
        advanceUntilIdle()
        assertEquals(SalesStrings.NETWORK, vm.state.value.dayError)
        assertNull(vm.state.value.detailError)

        vm.back()

        assertEquals(SalesStrings.NETWORK, vm.state.value.dayError)
    }

    @Test
    fun a_day_failure_while_the_detail_is_open_goes_to_the_day_error() = runTest(dispatcher) {
        val gate = CompletableDeferred<AppResult<List<SaleListItem>>>()
        val repo = FakeSalesRepository(salesGates = mapOf(today to gate), details = mapOf(1L to detail(1)))
        val vm = viewModel(repo)
        vm.load()
        runCurrent()
        vm.select(1L)
        advanceUntilIdle()

        gate.complete(AppResult.Failure(AppError.Network("offline")))
        advanceUntilIdle()

        assertEquals(SalesStrings.NETWORK, vm.state.value.dayError)
        assertNull(vm.state.value.detailError)
        assertEquals(detail(1), vm.state.value.detail)
    }

    @Test
    fun a_throwing_repository_ends_in_the_day_error() = runTest(dispatcher) {
        val repo = FakeSalesRepository()
        repo.throwSales = true
        val vm = loaded(repo)

        assertEquals(SalesStrings.LOAD_FAILED, vm.state.value.dayError)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun both_calls_failing_show_one_message_and_no_amounts() = runTest(dispatcher) {
        val repo = FakeSalesRepository()
        repo.failSales = true
        repo.failUnits = true
        val vm = loaded(repo)

        val state = vm.state.value
        assertEquals(SalesStrings.NETWORK, state.dayError)
        assertTrue(state.sales.isEmpty())
        assertNull(state.units)
        assertFalse(state.showsAmounts)
    }

    @Test
    fun next_day_after_midnight_moves_to_the_new_today() = runTest(dispatcher) {
        var clock = now
        val repo = FakeSalesRepository()
        val vm = SalesViewModel(ReportsUseCases(repo, NoManagerRepository), { clock }, zone, dispatcher)
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.state.value.isToday)

        clock = Instant.parse("2026-10-17T07:00:00Z")
        vm.nextDay()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(LocalDate(2026, 10, 17), state.day)
        assertEquals(LocalDate(2026, 10, 17), state.today)
        assertTrue(state.isToday)
    }

    @Test
    fun refresh_with_a_detail_open_reloads_only_the_detail() = runTest(dispatcher) {
        val repo = FakeSalesRepository(details = mapOf(1L to detail(1)))
        val vm = loaded(repo)
        vm.select(1L)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, repo.salesCalls.size)
        assertEquals(listOf(1L, 1L), repo.detailCalls)
        assertEquals(detail(1), vm.state.value.detail)
    }

    private fun TestScope.loaded(repo: FakeSalesRepository): SalesViewModel {
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()
        return vm
    }

    private fun viewModel(repo: FakeSalesRepository) =
        SalesViewModel(ReportsUseCases(repo, NoManagerRepository), { now }, zone, dispatcher)

    private fun sale(id: Long, payment: SalePayment, total: Double, customer: String? = null) = SaleListItem(
        id = id,
        folio = "S-${id.toString().padStart(6, '0')}",
        soldAt = Instant.parse("2026-10-16T20:00:00Z"),
        payment = payment,
        channel = SaleChannel.TAKEAWAY,
        total = total,
        customerName = customer,
    )

    private fun detail(id: Long) = SaleDetail(
        id = id,
        folio = "S-${id.toString().padStart(6, '0')}",
        soldAt = Instant.parse("2026-10-16T20:00:00Z"),
        terminalCode = "POS1",
        businessUnitCode = "TIENDA",
        payment = SalePayment.CASH,
        channel = SaleChannel.TAKEAWAY,
        subtotal = 21.0,
        cashDiscount = 1.0,
        total = 20.0,
        customerName = null,
        lines = listOf(SaleDetailLine("Coca cola", 1.0, "PZA", SalePriceType.PIECE, 21.0, 21.0)),
    )
}

private class FakeSalesRepository(
    private val sales: Map<LocalDate, List<SaleListItem>> = emptyMap(),
    private val units: Map<LocalDate, DayUnits> = emptyMap(),
    private val details: Map<Long, SaleDetail> = emptyMap(),
    private val salesGates: Map<LocalDate, CompletableDeferred<AppResult<List<SaleListItem>>>> = emptyMap(),
    private val detailGates: Map<Long, CompletableDeferred<AppResult<SaleDetail>>> = emptyMap(),
) : SalesRepository {
    var failSales = false
    var failUnits = false
    var throwSales = false
    val detailCalls = mutableListOf<Long>()
    val salesCalls = mutableListOf<LocalDate>()
    val unitsCalls = mutableListOf<LocalDate>()

    override suspend fun salesOfDay(date: LocalDate): AppResult<List<SaleListItem>> {
        salesCalls += date
        salesGates[date]?.let { return it.await() }
        if (throwSales) throw RuntimeException("boom")
        if (failSales) return AppResult.Failure(AppError.Network("offline"))
        return AppResult.Success(sales[date].orEmpty())
    }

    override suspend fun unitsOfDay(date: LocalDate): AppResult<DayUnits> {
        unitsCalls += date
        if (failUnits) return AppResult.Failure(AppError.Network("offline"))
        return AppResult.Success(units[date] ?: DayUnits(0.0, 0.0, 0.0))
    }

    override suspend fun saleDetail(id: Long): AppResult<SaleDetail> {
        detailCalls += id
        detailGates[id]?.let { return it.await() }
        return details[id]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Validation("Sale not found"))
    }
}

private object NoManagerRepository : ManagerRepository {
    override suspend fun dailyTotals(dateFrom: String, dateTo: String): AppResult<List<ManagerDailyTotal>> =
        AppResult.Success(emptyList())

    override suspend fun topDebtors(limit: Int?): AppResult<List<DebtorItem>> = AppResult.Success(emptyList())

    override suspend fun wasteTotals(dateFrom: String, dateTo: String): AppResult<List<WasteItem>> =
        AppResult.Success(emptyList())

    override suspend fun integrityCheck(): AppResult<IntegrityResult> =
        AppResult.Success(IntegrityResult(ok = true, issues = emptyList()))
}
