package com.posfab.shared.features.cash

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.session.InMemorySessionStorage
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashCloseResult
import com.posfab.shared.features.cash.domain.CashDrawerStatus
import com.posfab.shared.features.cash.domain.CashPayout
import com.posfab.shared.features.cash.domain.CashSummary
import com.posfab.shared.features.cash.domain.PayoutReason
import com.posfab.shared.features.cash.repository.CashRepositoryImpl
import com.posfab.shared.network.cash.CashApi
import com.posfab.shared.network.cash.CashCloseResponseDto
import com.posfab.shared.network.cash.CashIdResponseDto
import com.posfab.shared.network.cash.CashPayoutItemDto
import com.posfab.shared.network.cash.CashPayoutRequestDto
import com.posfab.shared.network.cash.CashPayoutResponseDto
import com.posfab.shared.network.cash.CashSessionResponseDto
import com.posfab.shared.network.cash.CashSessionSummaryDto
import com.posfab.shared.network.cash.CashSummaryResponseDto
import com.posfab.shared.network.cash.CloseCashSessionRequestDto
import com.posfab.shared.network.cash.OpenCashSessionRequestDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class CashRepositoryImplTest {
    @Test
    fun open_summary_maps_amounts_payouts_and_instants() = runTest {
        val api = FakeCashApi(summary = AppResult.Success(CashSummaryResponseDto("OPEN", "900.00", summaryDto())))

        val summary = assertIs<AppResult.Success<CashSummary>>(repository(api).summary(1L)).value

        assertEquals(CashDrawerStatus.OPEN, summary.status)
        assertEquals(900.0, summary.previousClosingAmount)
        val session = assertNotNull(summary.session)
        assertEquals(Instant.parse("2026-10-16T14:15:00Z"), session.openedAt)
        assertEquals(1250.5, session.expectedInDrawer)
        assertEquals(2, session.openAccounts)
        val payout = session.payoutList.single()
        assertEquals(PayoutReason.SURTIDO, payout.reason)
        assertNull(payout.notes)
    }

    @Test
    fun closed_summary_without_previous_amount() = runTest {
        val api = FakeCashApi(summary = AppResult.Success(CashSummaryResponseDto("CLOSED", null, null)))

        val summary = assertIs<AppResult.Success<CashSummary>>(repository(api).summary(1L)).value

        assertEquals(CashSummary(CashDrawerStatus.CLOSED, null, null), summary)
    }

    @Test
    fun unparsable_amount_fails_instead_of_reading_zero() = runTest {
        val api = FakeCashApi(
            summary = AppResult.Success(CashSummaryResponseDto("OPEN", null, summaryDto(expected = "abc"))),
        )

        assertIs<AppResult.Failure>(repository(api).summary(1L))
    }

    @Test
    fun open_and_close_send_two_decimal_amounts() = runTest {
        val api = FakeCashApi()
        val repo = repository(api)

        repo.openSession(1L, 2L, 1250.0)
        val closed = repo.closeSession(1L, 1480.5)

        assertEquals(OpenCashSessionRequestDto(1L, 2L, "1250.00"), api.opened.single())
        assertEquals(CloseCashSessionRequestDto(1L, "1480.50"), api.closed.single())
        val result = assertIs<AppResult.Success<CashCloseResult>>(closed).value
        assertEquals(1480.5, result.countedAmount)
        assertEquals(-20.0, result.delta)
    }

    @Test
    fun payout_sends_key_reason_and_trimmed_notes() = runTest {
        val api = FakeCashApi()

        val payout = repository(api).payout(1L, "k1", 500.0, PayoutReason.OTRO, "  Gas ")

        assertEquals("k1" to CashPayoutRequestDto(1L, "500.00", "OTRO", "Gas"), api.payouts.single())
        assertEquals(PayoutReason.OTRO, assertIs<AppResult.Success<CashPayout>>(payout).value.reason)
    }

    private suspend fun repository(api: FakeCashApi): CashRepositoryImpl {
        val manager = SessionManager(InMemorySessionStorage())
        manager.set(
            UserSession(
                tokens = AuthTokens("access", "refresh"),
                user = AuthUser("u1", "gerente", setOf(UserRole.MANAGER), emptySet()),
                terminalCode = "POS1",
            ),
        )
        return CashRepositoryImpl(api, AuthorizedApiExecutor(NoRefreshAuthRepository(manager), manager))
    }
}

private fun summaryDto(expected: String = "1250.50") = CashSessionSummaryDto(
    sessionId = 7L,
    openedAt = "2026-10-16T14:15:00Z",
    openingAmount = "1000.00",
    cashSales = "600.50",
    creditPayments = "150.00",
    payouts = "500.00",
    otherOut = "0.00",
    expectedInDrawer = expected,
    transferSales = "50.00",
    cardSales = "30.00",
    creditSales = "20.00",
    openAccounts = 2,
    payoutList = listOf(CashPayoutItemDto(1L, "500.00", "SURTIDO", null, "2026-10-16T16:30:00Z")),
)

private class NoRefreshAuthRepository(private val manager: SessionManager) : AuthRepository {
    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> =
        AppResult.Failure(AppError.Unauthorized)

    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(manager.current())

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeCashApi(
    private val summary: AppResult<CashSummaryResponseDto> = AppResult.Failure(AppError.Unknown("unused")),
) : CashApi {
    val opened = mutableListOf<OpenCashSessionRequestDto>()
    val closed = mutableListOf<CloseCashSessionRequestDto>()
    val payouts = mutableListOf<Pair<String, CashPayoutRequestDto>>()

    override suspend fun openSession(
        accessToken: String,
        request: OpenCashSessionRequestDto,
    ): AppResult<CashIdResponseDto> {
        opened += request
        return AppResult.Success(CashIdResponseDto(1L))
    }

    override suspend fun closeSession(
        accessToken: String,
        request: CloseCashSessionRequestDto,
    ): AppResult<CashCloseResponseDto> {
        closed += request
        return AppResult.Success(
            CashCloseResponseDto(
                id = 7L,
                terminalId = request.terminalId,
                openedAt = "2026-10-16T14:15:00Z",
                closedAt = "2026-10-17T02:00:00Z",
                summary = summaryDto(expected = "1500.50"),
                countedAmount = request.closingAmount,
                delta = "-20.00",
            ),
        )
    }

    override suspend fun currentSession(accessToken: String, terminalId: Long): AppResult<CashSessionResponseDto?> =
        AppResult.Success(null)

    override suspend fun summary(accessToken: String, terminalId: Long): AppResult<CashSummaryResponseDto> = summary

    override suspend fun payout(
        accessToken: String,
        idempotencyKey: String,
        request: CashPayoutRequestDto,
    ): AppResult<CashPayoutResponseDto> {
        payouts += idempotencyKey to request
        return AppResult.Success(
            CashPayoutResponseDto(1L, request.amount, request.reason, request.notes, "2026-10-16T16:30:00Z", "750.50"),
        )
    }
}
