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
import com.posfab.shared.features.credit.domain.CustomerMatch
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
import com.posfab.shared.network.customers.OpeningBalanceRequestDto
import com.posfab.shared.network.customers.OpeningBalanceResponseDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val BUSINESS_UNIT_ID = 1L

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

    @Test
    fun opening_balance_entries_map_to_their_type() = runTest {
        val api = FakeCustomersApi(
            ledger = AppResult.Success(
                CustomerLedgerDto(
                    customer = CustomerDto(id = 1, code = "30000011", name = "Juan", nickname = "el Wero", balance = "350.00"),
                    entries = listOf(
                        LedgerEntryDto(4, "CHARGE", "50.00", "2026-10-14T10:00:00Z", referenceType = "SALE", saleId = 9),
                        LedgerEntryDto(3, "CHARGE", "300.00", "2026-09-01T18:00:00Z", referenceType = "OPENING_BALANCE"),
                    ),
                ),
            ),
        )

        val statement = assertIs<AppResult.Success<CustomerStatement>>(repository(api).statement(1, 100)).value

        assertEquals(listOf(LedgerEntryType.CHARGE, LedgerEntryType.OPENING_BALANCE), statement.entries.map { it.type })
        assertEquals("30000011", statement.code)
        assertEquals("Juan (el Wero)", statement.displayName)
    }

    @Test
    fun display_name_includes_the_nickname() = runTest {
        val api = FakeCustomersApi(
            debtors = AppResult.Success(
                listOf(
                    DebtorDto(id = 1, code = "30000011", name = "Juan López", nickname = "el Wero", balance = "350.00"),
                    DebtorDto(id = 2, name = "María", nickname = " ", balance = "10.00"),
                ),
            ),
            customers = listOf(
                CustomerDto(id = 1, name = "Juan López", nickname = "el Wero", phone = "5551234", balance = "350.00"),
                CustomerDto(id = 3, name = "Baja", active = false, balance = "0.00"),
            ),
        )
        val repo = repository(api)

        val debtors = assertIs<AppResult.Success<List<Debtor>>>(repo.debtors()).value
        val matches = assertIs<AppResult.Success<List<CustomerMatch>>>(repo.searchCustomers(" wero ", 5)).value

        assertEquals(listOf("Juan López (el Wero)", "María"), debtors.map { it.displayName })
        assertEquals(listOf(CustomerMatch(1, "Juan López (el Wero)", "5551234", 350.0)), matches)
        assertEquals(listOf<Pair<String?, Int>>("wero" to 5), api.searchCalls)
    }

    @Test
    fun create_and_load_send_trimmed_fields_and_two_decimals() = runTest {
        val api = FakeCustomersApi()
        val repo = repository(api)

        val created = assertIs<AppResult.Success<CustomerMatch>>(repo.createCustomer(" Rosa ", " la de la esquina ", " ")).value
        val balance = assertIs<AppResult.Success<Double>>(repo.loadOpeningBalance(created.id, 350.5, "2026-09-01", "k1")).value

        assertEquals(CreateCustomerRequestDto("Rosa", "la de la esquina", null), api.created.single())
        assertEquals("Rosa (la de la esquina)", created.displayName)
        assertEquals(Triple(created.id, "k1", OpeningBalanceRequestDto(BUSINESS_UNIT_ID, "350.50", "2026-09-01")), api.loads.single())
        assertEquals(350.5, balance, 0.001)
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
        return CreditRepositoryImpl(api, AuthorizedApiExecutor(NoRefreshAuthRepository(manager), manager), BUSINESS_UNIT_ID)
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
    private val customers: List<CustomerDto> = emptyList(),
) : CustomersApi {
    val ledgerCalls = mutableListOf<Pair<Long, Int>>()
    val searchCalls = mutableListOf<Pair<String?, Int>>()
    val created = mutableListOf<CreateCustomerRequestDto>()
    val loads = mutableListOf<Triple<Long, String, OpeningBalanceRequestDto>>()

    override suspend fun search(accessToken: String, query: String?, limit: Int): AppResult<List<CustomerDto>> {
        searchCalls += query to limit
        return AppResult.Success(customers)
    }

    override suspend fun get(accessToken: String, customerId: Long): AppResult<CustomerDto> =
        AppResult.Failure(AppError.Validation("Customer not found"))

    override suspend fun create(accessToken: String, request: CreateCustomerRequestDto): AppResult<CustomerDto> {
        created += request
        return AppResult.Success(
            CustomerDto(id = 40, code = "30000040", name = request.name, nickname = request.nickname, phone = request.phone, balance = "0.00"),
        )
    }

    override suspend fun loadOpeningBalance(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: OpeningBalanceRequestDto,
    ): AppResult<OpeningBalanceResponseDto> {
        loads += Triple(customerId, idempotencyKey, request)
        return AppResult.Success(OpeningBalanceResponseDto(1, customerId, request.amount, request.owedSince, request.amount))
    }

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
