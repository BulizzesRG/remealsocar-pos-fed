package com.posfab.shared.features.credit

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
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor
import com.posfab.shared.features.credit.domain.LedgerEntryType
import com.posfab.shared.features.credit.repository.CreditRepositoryImpl
import com.posfab.shared.network.customers.CreateCustomerRequestDto
import com.posfab.shared.network.customers.CustomerDto
import com.posfab.shared.network.customers.CustomerLedgerDto
import com.posfab.shared.network.customers.CustomerPaymentRequestDto
import com.posfab.shared.network.customers.CustomerPaymentResponseDto
import com.posfab.shared.network.customers.CustomersApi
import com.posfab.shared.network.customers.DebtorDto
import com.posfab.shared.network.customers.LedgerEntryDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CreditRepositoryImplTest {
    @Test
    fun debtors_map_balance_and_blank_phone() = runTest {
        val api = FakeCustomersApi(
            debtors = AppResult.Success(
                listOf(
                    DebtorDto(id = 1, name = "Juan", phone = " ", active = false, balance = "350.50", owesSince = "2026-10-03T10:00:00Z"),
                ),
            ),
        )

        val result = repository(api).debtors()

        val debtor = assertIs<AppResult.Success<List<Debtor>>>(result).value.single()
        assertEquals(350.5, debtor.balance, 0.001)
        assertEquals(null, debtor.phone)
        assertEquals(false, debtor.active)
    }

    @Test
    fun unparsable_debtor_balance_fails_instead_of_reading_zero() = runTest {
        val api = FakeCustomersApi(
            debtors = AppResult.Success(
                listOf(
                    DebtorDto(id = 1, name = "Juan", balance = "350.00"),
                    DebtorDto(id = 2, name = "María", balance = "abc"),
                ),
            ),
        )

        val result = repository(api).debtors()

        assertEquals(AppError.Unknown("balance"), assertIs<AppResult.Failure>(result).error)
    }

    @Test
    fun unparsable_ledger_amount_fails() = runTest {
        val api = FakeCustomersApi(
            ledger = AppResult.Success(
                CustomerLedgerDto(
                    customer = CustomerDto(id = 1, name = "Juan", balance = "100.00"),
                    entries = listOf(LedgerEntryDto(1, "CHARGE", "", "2026-10-03T10:00:00Z")),
                ),
            ),
        )

        assertIs<AppResult.Failure>(repository(api).statement(1, 100))
    }

    @Test
    fun statement_maps_entry_types_and_passes_the_limit() = runTest {
        val api = FakeCustomersApi(
            ledger = AppResult.Success(
                CustomerLedgerDto(
                    customer = CustomerDto(id = 1, name = "Juan", phone = "5551234", balance = "350.00"),
                    entries = listOf(
                        LedgerEntryDto(3, "PAYMENT", "100.00", "2026-10-14T10:00:00Z"),
                        LedgerEntryDto(2, "CHARGE", "450.00", "2026-10-03T10:00:00Z", saleId = 7, saleFolio = "S-00012"),
                    ),
                ),
            ),
        )

        val result = repository(api).statement(1, 100)

        val statement = assertIs<AppResult.Success<CustomerStatement>>(result).value
        assertEquals(listOf(LedgerEntryType.PAYMENT, LedgerEntryType.CHARGE), statement.entries.map { it.type })
        assertEquals("S-00012", statement.entries[1].saleFolio)
        assertEquals(350.0, statement.balance, 0.001)
        assertEquals(listOf(1L to 100), api.ledgerCalls)
    }

    private suspend fun repository(api: FakeCustomersApi): CreditRepositoryImpl {
        val manager = SessionManager(InMemorySessionStorage())
        manager.set(
            UserSession(
                tokens = AuthTokens("access", "refresh"),
                user = AuthUser("u1", "gerente", setOf(UserRole.MANAGER), emptySet()),
                terminalCode = "POS1",
            ),
        )
        return CreditRepositoryImpl(api, AuthorizedApiExecutor(NoRefreshAuthRepository(manager), manager))
    }
}

private class NoRefreshAuthRepository(private val manager: SessionManager) : AuthRepository {
    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> =
        AppResult.Failure(AppError.Unauthorized)

    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(manager.current())

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeCustomersApi(
    private val debtors: AppResult<List<DebtorDto>> = AppResult.Success(emptyList()),
    private val ledger: AppResult<CustomerLedgerDto> = AppResult.Failure(AppError.Validation("Customer not found")),
) : CustomersApi {
    val ledgerCalls = mutableListOf<Pair<Long, Int>>()

    override suspend fun search(accessToken: String, query: String?, limit: Int): AppResult<List<CustomerDto>> =
        AppResult.Success(emptyList())

    override suspend fun get(accessToken: String, customerId: Long): AppResult<CustomerDto> =
        AppResult.Failure(AppError.Validation("Customer not found"))

    override suspend fun create(accessToken: String, request: CreateCustomerRequestDto): AppResult<CustomerDto> =
        AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun recordPayment(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: CustomerPaymentRequestDto,
    ): AppResult<CustomerPaymentResponseDto> = AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun debtors(accessToken: String): AppResult<List<DebtorDto>> = debtors

    override suspend fun ledger(accessToken: String, customerId: Long, limit: Int): AppResult<CustomerLedgerDto> {
        ledgerCalls += customerId to limit
        return ledger
    }
}
