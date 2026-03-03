package com.posfab.app.e2e

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.usecase.LoginUseCase
import com.posfab.shared.core.model.TerminalCode
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
import com.posfab.shared.features.sale.domain.CheckoutMode
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.DraftValidation
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

        val cashierSession = (login("cashier", "pwd", TerminalCode.POS1) as AppResult.Success).value
        assertTrue(cashierSession.user.roles.contains(UserRole.CASHIER))

        val opened = cash.open("POS1", 1000.0)
        assertIs<AppResult.Success<CashSession>>(opened)

        val draft = (sale.openDraft() as AppResult.Success).value
        val product = (sale.searchProducts("cafe", 5) as AppResult.Success).value.first()
        val add = sale.addLine(draft.id, product, 1.0, "EA", 30.0, null, draft.version)
        assertIs<SaleMutationResult.Success>(add)

        val resolved = sale.resolveLots(draft.id)
        assertIs<AppResult.Success<SaleDraft>>(resolved)

        val checkoutCash = sale.checkout(draft.id, CheckoutMode.CASH, resolved.value.version, "idem-cash-1")
        assertIs<AppResult.Success<CheckoutResult>>(checkoutCash)

        val nextDraft = (sale.openDraft() as AppResult.Success).value
        sale.addLine(nextDraft.id, product, 1.0, "EA", 30.0, null, nextDraft.version)
        val checkoutCredit = sale.checkout(nextDraft.id, CheckoutMode.ON_CREDIT, backend.currentDraft.version, "idem-credit-1")
        assertIs<AppResult.Success<CheckoutResult>>(checkoutCredit)

        val closed = cash.close("POS1", 1060.0)
        assertIs<AppResult.Success<CashSession>>(closed)

        val managerSession = (login("manager", "pwd", TerminalCode.ADMIN) as AppResult.Success).value
        assertTrue(managerSession.user.roles.contains(UserRole.MANAGER))

        val integrity = reports.managerRepository.integrityCheck()
        assertIs<AppResult.Success<IntegrityResult>>(integrity)
        assertTrue(integrity.value.ok)
    }

    @Test
    fun resilience_checkout_network_failure_retry_no_duplicate_sale() = runBlocking {
        val backend = FakeStoreBackend().apply { failFirstCheckout = true }
        val sale = SaleUseCases(backend)

        val draft = (sale.openDraft() as AppResult.Success).value
        val product = (sale.searchProducts("cafe", 5) as AppResult.Success).value.first()
        sale.addLine(draft.id, product, 1.0, "EA", 30.0, null, draft.version)

        val key = "idem-checkout-xyz"
        val first = sale.checkout(draft.id, CheckoutMode.CASH, backend.currentDraft.version, key)
        assertIs<AppResult.Failure>(first)

        val second = sale.checkout(draft.id, CheckoutMode.CASH, backend.currentDraft.version, key)
        assertIs<AppResult.Success<CheckoutResult>>(second)

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

    var currentDraft = SaleDraft(
        id = "draft-1",
        status = "OPEN",
        type = "SALE",
        terminalCode = "POS1",
        version = 1,
        lines = emptyList(),
        totals = SaleTotals(0.0, 0.0, 0.0),
    )

    private val product = SaleProduct(
        id = "p1",
        name = "Cafe",
        sku = null,
        barcode = "7501",
        defaultUnit = "EA",
        unitPrice = 30.0,
        lotTracked = true,
    )

    override suspend fun login(username: String, password: String, terminal: TerminalCode): AppResult<UserSession> {
        val roles = if (username == "manager") setOf(UserRole.MANAGER) else setOf(UserRole.CASHIER)
        return AppResult.Success(
            UserSession(
                tokens = AuthTokens("access-$username", "refresh-$username"),
                user = AuthUser("u-$username", username, roles),
                terminal = terminal,
            )
        )
    }

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> = AppResult.Failure(AppError.Unauthorized)
    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(null)
    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun getCurrentSession(terminalId: String): AppResult<CashSession?> = AppResult.Success(
        CashSession(
            id = "cash-1",
            status = CashSessionStatus.OPEN,
            terminalId = terminalId,
            openedAt = "2026-03-01T08:00:00",
            openedBy = "cashier",
            openingCash = 1000.0,
            movementIn = 100.0,
            movementOut = 40.0,
            expectedClose = 1060.0,
            countedClose = null,
            delta = null,
        )
    )

    override suspend fun openSession(terminalId: String, openingCash: Double): AppResult<CashSession> = AppResult.Success(
        CashSession(
            id = "cash-1",
            status = CashSessionStatus.OPEN,
            terminalId = terminalId,
            openedAt = "2026-03-01T08:00:00",
            openedBy = "cashier",
            openingCash = openingCash,
            movementIn = 0.0,
            movementOut = 0.0,
            expectedClose = openingCash,
            countedClose = null,
            delta = null,
        )
    )

    override suspend fun closeSession(terminalId: String, countedCash: Double): AppResult<CashSession> = AppResult.Success(
        CashSession(
            id = "cash-1",
            status = CashSessionStatus.CLOSED,
            terminalId = terminalId,
            openedAt = "2026-03-01T08:00:00",
            openedBy = "cashier",
            openingCash = 1000.0,
            movementIn = 100.0,
            movementOut = 40.0,
            expectedClose = 1060.0,
            countedClose = countedCash,
            delta = countedCash - 1060.0,
        )
    )

    override suspend fun getDailyReport(date: String, terminalId: String): AppResult<DailyCashReport> = AppResult.Success(
        DailyCashReport(
            date = date,
            terminalId = terminalId,
            openingCash = 1000.0,
            movementIn = 100.0,
            movementOut = 40.0,
            salesCash = 60.0,
            expectedClose = 1060.0,
            countedClose = 1060.0,
            delta = 0.0,
        )
    )

    override suspend fun openOrCreateCurrentSaleDraft(): AppResult<SaleDraft> {
        if (currentDraft.status == "COMPLETED") {
            currentDraft = currentDraft.copy(
                id = "draft-${currentDraft.version + 1}",
                status = "OPEN",
                version = currentDraft.version + 1,
                lines = emptyList(),
                totals = SaleTotals(0.0, 0.0, 0.0),
            )
        }
        return AppResult.Success(currentDraft)
    }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> = AppResult.Success(listOf(product))
    override suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> = AppResult.Success(product)

    override suspend fun addLine(
        draftId: String,
        product: SaleProduct,
        qty: Double,
        unit: String,
        unitPrice: Double,
        lotId: String?,
        expectedVersion: Long,
    ): SaleMutationResult {
        val line = SaleLine(
            id = "line-1",
            productId = product.id,
            productName = product.name,
            barcode = product.barcode,
            qty = qty,
            unit = unit,
            unitPrice = unitPrice,
            lineTotal = qty * unitPrice,
            lotId = lotId,
            lotTracked = true,
        )
        currentDraft = currentDraft.copy(
            version = currentDraft.version + 1,
            lines = listOf(line),
            totals = SaleTotals(subtotal = 30.0, tax = 4.8, total = 34.8),
        )
        return SaleMutationResult.Success(currentDraft)
    }

    override suspend fun updateLine(
        draftId: String,
        lineId: String,
        qty: Double,
        unit: String,
        unitPrice: Double,
        lotId: String?,
        expectedVersion: Long,
    ): SaleMutationResult = SaleMutationResult.Success(currentDraft)

    override suspend fun removeLine(draftId: String, lineId: String, expectedVersion: Long): SaleMutationResult = SaleMutationResult.Success(currentDraft)

    override suspend fun validateDraft(draftId: String): AppResult<DraftValidation> = AppResult.Success(
        DraftValidation(issues = emptyList())
    )

    override suspend fun resolveLots(draftId: String): AppResult<SaleDraft> {
        currentDraft = currentDraft.copy(
            version = currentDraft.version + 1,
            lines = currentDraft.lines.map { it.copy(lotId = it.lotId ?: "LOT-1") },
        )
        return AppResult.Success(currentDraft)
    }

    override suspend fun checkout(
        draftId: String,
        mode: CheckoutMode,
        expectedVersion: Long,
        idempotencyKey: String,
    ): AppResult<CheckoutResult> {
        checkoutCalls += 1
        if (failFirstCheckout && checkoutCalls == 1) {
            return AppResult.Failure(AppError.Network("timeout"))
        }
        if (idempotencyKey !in processedCheckoutKeys) {
            processedCheckoutKeys += idempotencyKey
            currentDraft = currentDraft.copy(status = "COMPLETED")
        }
        return AppResult.Success(
            CheckoutResult(
                saleId = "sale-${processedCheckoutKeys.size}",
                folio = "F-${processedCheckoutKeys.size}",
                total = currentDraft.totals.total,
                mode = mode,
            )
        )
    }

    override suspend fun refetchCurrentDraft(): AppResult<SaleDraft> = AppResult.Success(currentDraft)

    override suspend fun salesByDate(date: String, terminalId: String): AppResult<List<DailySaleItem>> = AppResult.Success(emptyList())
    override suspend fun cashSummary(date: String, terminalId: String): AppResult<DailyCashReport> = getDailyReport(date, terminalId)

    override suspend fun dailyTotals(dateFrom: String, dateTo: String): AppResult<List<ManagerDailyTotal>> = AppResult.Success(emptyList())
    override suspend fun topDebtors(dateFrom: String, dateTo: String): AppResult<List<DebtorItem>> = AppResult.Success(emptyList())
    override suspend fun wasteTotals(dateFrom: String, dateTo: String): AppResult<List<WasteItem>> = AppResult.Success(emptyList())
    override suspend fun integrityCheck(): AppResult<IntegrityResult> = AppResult.Success(
        IntegrityResult(
            ok = true,
            issues = listOf(IntegrityIssue(key = "all", count = 0, samples = emptyList())),
            checkedAt = "2026-03-01T20:00:00",
        )
    )
}
