package com.posfab.app.e2e

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.usecase.LoginUseCase
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSessionStatus
import com.posfab.shared.features.cash.domain.DailyCashReport
import com.posfab.shared.features.cash.repository.CashRepository
import com.posfab.shared.features.cash.usecase.CashUseCases
import com.posfab.shared.features.reports.common.ReportsUseCases
import com.posfab.shared.features.reports.daily.DailyHistoryRepository
import com.posfab.shared.features.reports.daily.DailySaleItem
import com.posfab.shared.features.reports.manager.DebtorItem
import com.posfab.shared.features.reports.manager.IntegrityIssue
import com.posfab.shared.features.reports.manager.IntegrityResult
import com.posfab.shared.features.reports.manager.ManagerDailyTotal
import com.posfab.shared.features.reports.manager.ManagerRepository
import com.posfab.shared.features.reports.manager.WasteItem
import com.posfab.shared.features.sale.domain.CashCheckoutResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.SaleTotals
import com.posfab.shared.features.sale.repository.SaleRepository
import com.posfab.shared.features.sale.usecase.SaleUseCases
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StoreRolloutSmokeTest {
    @Test
    fun e2e_happy_path_smoke_suite() = runBlocking {
        val backend = FakeStoreBackend()
        val login = LoginUseCase(backend)
        val cash = CashUseCases(backend)
        val sale = SaleUseCases(backend)
        val reports = ReportsUseCases(backend, backend)

        val cashierSession = (login("cashier", "pwd", "POS1") as AppResult.Success).value
        assertTrue(cashierSession.user.roles.contains(UserRole.CASHIER))

        val opened = cash.open(1L, 1L, 1000.0)
        assertIs<AppResult.Success<CashSession>>(opened)

        assertEquals(CashSessionCheck.Open, (sale.cashSession() as AppResult.Success).value)

        val draft = (sale.openDrafts() as AppResult.Success).value.first()
        val product = (sale.searchProducts("cafe", 5) as AppResult.Success).value.first()
        val add = sale.addLine(draft.id, product, 1.0, draft.version)
        assertIs<SaleMutationResult.Success>(add)

        val checkoutCash = sale.checkoutCash(draft.id, 50.0, "idem-cash-1")
        assertIs<AppResult.Success<CashCheckoutResult>>(checkoutCash)
        assertEquals(30.0, checkoutCash.value.cashTotal)
        assertEquals(20.0, checkoutCash.value.change)

        val closed = cash.close(1L, 1060.0)
        assertIs<AppResult.Success<CashSession>>(closed)

        val managerSession = (login("manager", "pwd", "ADMIN") as AppResult.Success).value
        assertTrue(managerSession.user.roles.contains(UserRole.MANAGER))

        val integrity = reports.managerRepository.integrityCheck()
        assertIs<AppResult.Success<IntegrityResult>>(integrity)
        assertTrue(integrity.value.ok)
    }

    @Test
    fun resilience_checkout_network_failure_retry_no_duplicate_sale() = runBlocking {
        val backend = FakeStoreBackend().apply { failFirstCheckout = true }
        val sale = SaleUseCases(backend)

        val draft = (sale.openDrafts() as AppResult.Success).value.first()
        val product = (sale.searchProducts("cafe", 5) as AppResult.Success).value.first()
        sale.addLine(draft.id, product, 1.0, draft.version)

        val key = "idem-checkout-xyz"
        val first = sale.checkoutCash(draft.id, 30.0, key)
        assertIs<AppResult.Failure>(first)

        val second = sale.checkoutCash(draft.id, 30.0, key)
        assertIs<AppResult.Success<CashCheckoutResult>>(second)

        assertEquals(2, backend.checkoutCalls)
        assertEquals(1, backend.processedCheckoutKeys.size)
    }
}

private class FakeStoreBackend :
    AuthRepository,
    CashRepository,
    SaleRepository,
    DailyHistoryRepository,
    ManagerRepository {

    var failFirstCheckout: Boolean = false
    var checkoutCalls: Int = 0
    val processedCheckoutKeys = mutableSetOf<String>()

    private var draftCounter = 1L
    private var lineCounter = 1L

    var currentDraft = SaleDraft(
        id = 1L,
        status = "OPEN",
        version = 1,
        lines = emptyList(),
        totals = SaleTotals(cashTotal = 0.0),
    )

    private val product = SaleProduct(
        id = 1L,
        name = "Cafe",
        barcode = "7501",
        unitId = 1L,
        cashPrice = 30.0,
    )

    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> {
        val manager = username == "manager"
        val roles = if (manager) setOf(UserRole.MANAGER) else setOf(UserRole.CASHIER)
        val permissions = if (manager) Permission.entries.toSet() - Permission.MANAGE_USERS else setOf(Permission.VIEW_PRODUCTS, Permission.SELL, Permission.OPEN_CASH)
        return AppResult.Success(
            UserSession(
                tokens = AuthTokens("access-$username", "refresh-$username"),
                user = AuthUser("u-$username", username, roles, permissions),
                terminalCode = terminalCode,
                terminalType = if (terminalCode.equals("ADMIN", ignoreCase = true)) "ADMIN" else "POS",
            )
        )
    }

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> = AppResult.Failure(AppError.Unauthorized)
    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(null)
    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun getCurrentSession(terminalId: Long): AppResult<CashSession?> = AppResult.Success(
        CashSession(
            id = 1L,
            status = CashSessionStatus.OPEN,
            terminalId = terminalId,
            openingAmount = 1000.0,
            closingAmount = null,
            openedAt = "2026-03-01T08:00:00",
            closedAt = null,
        )
    )

    override suspend fun openSession(
        terminalId: Long,
        businessUnitId: Long,
        openingAmount: Double,
    ): AppResult<CashSession> = AppResult.Success(
        CashSession(
            id = 1L,
            status = CashSessionStatus.OPEN,
            terminalId = terminalId,
            openingAmount = openingAmount,
            closingAmount = null,
            openedAt = "2026-03-01T08:00:00",
            closedAt = null,
        )
    )

    override suspend fun closeSession(terminalId: Long, closingAmount: Double): AppResult<CashSession> = AppResult.Success(
        CashSession(
            id = 1L,
            status = CashSessionStatus.CLOSED,
            terminalId = terminalId,
            openingAmount = 1000.0,
            closingAmount = closingAmount,
            openedAt = "2026-03-01T08:00:00",
            closedAt = "2026-03-01T18:00:00",
        )
    )

    override suspend fun getDailyReport(date: String, terminalId: Long): AppResult<DailyCashReport> = AppResult.Success(
        DailyCashReport(
            date = date,
            terminalId = terminalId,
            totalIn = 100.0,
            totalOut = 40.0,
            movements = 5,
        )
    )

    override suspend fun cashSession(): AppResult<CashSessionCheck> = AppResult.Success(CashSessionCheck.Open)

    override suspend fun draft(draftId: Long): AppResult<SaleDraft> =
        if (draftId == currentDraft.id) {
            AppResult.Success(currentDraft)
        } else {
            AppResult.Failure(AppError.Validation("Request rejected: 404"))
        }

    override suspend fun openDrafts(): AppResult<List<SaleDraft>> {
        if (currentDraft.status == "CHECKED_OUT") {
            draftCounter += 1
            currentDraft = SaleDraft(
                id = draftCounter,
                status = "OPEN",
                version = 1,
                lines = emptyList(),
                totals = SaleTotals(cashTotal = 0.0),
            )
        }
        return AppResult.Success(listOf(currentDraft))
    }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> = AppResult.Success(listOf(product))
    override suspend fun kitchenProducts(): AppResult<List<SaleProduct>> = AppResult.Success(emptyList())
    override suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> = AppResult.Success(product)

    override suspend fun addLine(
        draftId: Long,
        product: SaleProduct,
        qty: Double,
        expectedVersion: Int,
    ): SaleMutationResult {
        val price = product.cashPrice ?: 0.0
        val line = SaleLine(
            id = lineCounter++,
            productId = product.id,
            productName = product.name,
            qty = qty,
            unitId = product.unitId,
            cashUnitPrice = price,
            lineTotal = qty * price,
        )
        currentDraft = currentDraft.copy(
            version = currentDraft.version + 1,
            lines = listOf(line),
            totals = SaleTotals(cashTotal = qty * price),
        )
        return SaleMutationResult.Success(currentDraft)
    }

    override suspend fun updateLineQty(
        draftId: Long,
        lineId: Long,
        qty: Double,
        expectedVersion: Int,
    ): SaleMutationResult = SaleMutationResult.Success(currentDraft)

    override suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult =
        SaleMutationResult.Success(currentDraft)

    override suspend fun resolveLots(draftId: Long): AppResult<SaleDraft> = AppResult.Success(currentDraft)

    override suspend fun cancelDraft(draftId: Long, expectedVersion: Int): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun checkoutCash(
        draftId: Long,
        paidAmount: Double,
        idempotencyKey: String,
    ): AppResult<CashCheckoutResult> {
        checkoutCalls += 1
        if (failFirstCheckout && checkoutCalls == 1) {
            return AppResult.Failure(AppError.Network("timeout"))
        }
        val total = currentDraft.totals.cashTotal
        if (idempotencyKey !in processedCheckoutKeys) {
            processedCheckoutKeys += idempotencyKey
            currentDraft = currentDraft.copy(status = "CHECKED_OUT")
        }
        return AppResult.Success(
            CashCheckoutResult(
                saleId = processedCheckoutKeys.size.toLong(),
                folio = "F-${processedCheckoutKeys.size}",
                cashTotal = total,
                paidAmount = paidAmount,
            )
        )
    }

    override suspend fun newDraft(): AppResult<SaleDraft> {
        draftCounter += 1
        currentDraft = SaleDraft(
            id = draftCounter,
            status = "OPEN",
            version = 1,
            lines = emptyList(),
            totals = SaleTotals(cashTotal = 0.0),
        )
        return AppResult.Success(currentDraft)
    }

    override suspend fun salesByDate(date: String, terminalId: String): AppResult<List<DailySaleItem>> = AppResult.Success(emptyList())
    override suspend fun cashSummary(date: String, terminalId: String): AppResult<DailyCashReport> =
        getDailyReport(date, terminalId.toLongOrNull() ?: 1L)

    override suspend fun dailyTotals(dateFrom: String, dateTo: String): AppResult<List<ManagerDailyTotal>> = AppResult.Success(emptyList())
    override suspend fun topDebtors(limit: Int?): AppResult<List<DebtorItem>> = AppResult.Success(emptyList())
    override suspend fun wasteTotals(dateFrom: String, dateTo: String): AppResult<List<WasteItem>> = AppResult.Success(emptyList())
    override suspend fun integrityCheck(): AppResult<IntegrityResult> = AppResult.Success(
        IntegrityResult(
            ok = true,
            issues = listOf(IntegrityIssue(code = "all", count = 0, samples = emptyList())),
        )
    )
}
