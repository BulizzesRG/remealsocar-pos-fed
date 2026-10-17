package com.posfab.shared.network.customers

import com.posfab.shared.config.PosConfig
import com.posfab.shared.config.PosEnv
import com.posfab.shared.core.logging.LogLevel
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class CustomersContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    private val config = PosConfig(
        env = PosEnv.DEV,
        apiBaseUrl = "http://localhost:8080",
        logLevel = LogLevel.DEBUG,
        requestTimeoutMs = 5_000,
        enableVerboseLogs = false,
    )

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    @Test
    fun customer_with_code_and_phone_decodes() {
        val body = """
            {"id":4,"code":"123456","name":"Juan Pérez","phone":"5512345678",
             "creditLimit":"0.00","active":true,"balance":"350.00"}
        """.trimIndent()
        val dto = json.decodeFromString<CustomerDto>(body)
        assertEquals(4L, dto.id)
        assertEquals("123456", dto.code)
        assertEquals("5512345678", dto.phone)
        assertEquals("0.00", dto.creditLimit)
        assertEquals("350.00", dto.balance)
    }

    @Test
    fun customer_with_nickname_decodes() {
        val body = """{"id":6,"code":"30000011","name":"Juan López","nickname":"el Wero","active":true,"balance":"350.00"}"""
        val dto = json.decodeFromString<CustomerDto>(body)
        assertEquals("30000011", dto.code)
        assertEquals("el Wero", dto.nickname)
    }

    @Test
    fun customer_without_code_and_phone_decodes_with_nulls() {
        val body = """{"id":5,"name":"María","active":false,"balance":"0"}"""
        val dto = json.decodeFromString<CustomerDto>(body)
        assertNull(dto.code)
        assertNull(dto.nickname)
        assertNull(dto.phone)
        assertNull(dto.creditLimit)
        assertFalse(dto.active)
        assertEquals("0", dto.balance)
    }

    @Test
    fun ledger_decodes_charge_with_folio_and_payment_without() {
        val body = """
            {"customer":{"id":4,"name":"Juan","balance":"250.00"},
             "entries":[
              {"id":21,"entryType":"PAYMENT","amount":"100.00","createdAt":"2026-10-15T10:00:00-06:00","notes":"abono"},
              {"id":20,"entryType":"CHARGE","amount":"350.00","createdAt":"2026-10-14T09:30:00-06:00",
               "referenceType":"SALE","saleId":88,"saleFolio":"S-000088"},
              {"id":19,"entryType":"CHARGE","amount":"500.00","createdAt":"2026-09-01T12:00:00-06:00",
               "referenceType":"OPENING_BALANCE"}
             ]}
        """.trimIndent()
        val dto = json.decodeFromString<CustomerLedgerDto>(body)
        assertEquals("250.00", dto.customer.balance)
        val payment = dto.entries[0]
        assertEquals("PAYMENT", payment.entryType)
        assertNull(payment.saleId)
        assertNull(payment.saleFolio)
        assertEquals("abono", payment.notes)
        val charge = dto.entries[1]
        assertEquals("CHARGE", charge.entryType)
        assertEquals(88L, charge.saleId)
        assertEquals("S-000088", charge.saleFolio)
        assertEquals("SALE", charge.referenceType)
        val opening = dto.entries[2]
        assertEquals("OPENING_BALANCE", opening.referenceType)
        assertNull(opening.saleId)
        assertNull(payment.referenceType)
    }

    @Test
    fun search_encodes_accented_query_and_limit() = runTest {
        var encodedQuery = ""
        var decodedQ: String? = null
        val engine = MockEngine { request ->
            encodedQuery = request.url.encodedQuery
            decodedQ = request.url.parameters["q"]
            respond("""[{"id":1,"name":"María","balance":"10.00"}]""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = CustomersApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.search("tok", "María", 20)

        val success = assertIs<AppResult.Success<List<CustomerDto>>>(result)
        assertEquals("María", success.value.single().name)
        assertEquals("q=Mar%C3%ADa&limit=20", encodedQuery)
        assertEquals("María", decodedQ)
    }

    @Test
    fun search_omits_blank_query() = runTest {
        var path = ""
        var encodedQuery = ""
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            encodedQuery = request.url.encodedQuery
            respond("[]", HttpStatusCode.OK, jsonHeaders)
        }
        val api = CustomersApiClient(HttpClientFactory.create(config, engine), config)

        api.search("tok", "   ", 20)

        assertEquals("/customers", path)
        assertEquals("limit=20", encodedQuery)
    }

    @Test
    fun record_payment_sends_idempotency_key_and_body() = runTest {
        var method: HttpMethod? = null
        var path = ""
        var key: String? = null
        var auth: String? = null
        var sentBody = ""
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            key = request.headers["Idempotency-Key"]
            auth = request.headers["Authorization"]
            sentBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                """{"id":9,"customerId":4,"amount":"100.00","balance":"250.00"}""",
                HttpStatusCode.Created,
                jsonHeaders,
            )
        }
        val api = CustomersApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.recordPayment(
            accessToken = "tok",
            customerId = 4L,
            idempotencyKey = "PAY-abc",
            request = CustomerPaymentRequestDto(businessUnitId = 1L, terminalId = 2L, amount = "100.00"),
        )

        val success = assertIs<AppResult.Success<CustomerPaymentResponseDto>>(result)
        assertEquals("250.00", success.value.balance)
        assertEquals(HttpMethod.Post, method)
        assertEquals("/customers/4/payments", path)
        assertEquals("PAY-abc", key)
        assertEquals("Bearer tok", auth)
        val sent = json.parseToJsonElement(sentBody).jsonObject
        assertEquals("1", sent.getValue("businessUnitId").jsonPrimitive.content)
        assertEquals("2", sent.getValue("terminalId").jsonPrimitive.content)
        assertEquals("100.00", sent.getValue("amount").jsonPrimitive.content)
        assertFalse(sent.containsKey("notes"))
    }

    @Test
    fun load_opening_balance_sends_idempotency_key_and_body() = runTest {
        var method: HttpMethod? = null
        var path = ""
        var key: String? = null
        var sentBody = ""
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            key = request.headers["Idempotency-Key"]
            sentBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                """{"id":12,"customerId":6,"amount":"350.00","owedSince":"2026-09-01","balance":"350.00"}""",
                HttpStatusCode.Created,
                jsonHeaders,
            )
        }
        val api = CustomersApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.loadOpeningBalance(
            accessToken = "tok",
            customerId = 6L,
            idempotencyKey = "LIBRETA-abc",
            request = OpeningBalanceRequestDto(businessUnitId = 1L, amount = "350.00", owedSince = "2026-09-01"),
        )

        val success = assertIs<AppResult.Success<OpeningBalanceResponseDto>>(result)
        assertEquals("2026-09-01", success.value.owedSince)
        assertEquals("350.00", success.value.balance)
        assertEquals(HttpMethod.Post, method)
        assertEquals("/customers/6/opening-balance", path)
        assertEquals("LIBRETA-abc", key)
        val sent = json.parseToJsonElement(sentBody).jsonObject
        assertEquals("1", sent.getValue("businessUnitId").jsonPrimitive.content)
        assertEquals("350.00", sent.getValue("amount").jsonPrimitive.content)
        assertEquals("2026-09-01", sent.getValue("owedSince").jsonPrimitive.content)
        assertFalse(sent.containsKey("notes"))
    }

    @Test
    fun ledger_sends_limit_parameter() = runTest {
        var path = ""
        val engine = MockEngine { request ->
            path = "${request.url.encodedPath}?${request.url.encodedQuery}"
            respond("""{"customer":{"id":4,"name":"Juan","balance":"0.00"}}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = CustomersApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.ledger("tok", 4L, 100)

        val success = assertIs<AppResult.Success<CustomerLedgerDto>>(result)
        assertEquals(emptyList(), success.value.entries)
        assertEquals("/customers/4/ledger?limit=100", path)
    }

    @Test
    fun customer_with_address_decodes() {
        val body = """{"id":7,"name":"Rosa","address":"Calle 5 #12","balance":"0.00"}"""
        val dto = json.decodeFromString<CustomerDto>(body)
        assertEquals("Calle 5 #12", dto.address)
        assertNull(json.decodeFromString<CustomerDto>("""{"id":8,"name":"Lupe","balance":"0.00"}""").address)
    }

    @Test
    fun create_sends_address_when_present_and_omits_it_otherwise() = runTest {
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond("""{"id":7,"name":"Rosa","address":"Calle 5 #12","balance":"0.00"}""", HttpStatusCode.Created, jsonHeaders)
        }
        val api = CustomersApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.create("tok", CreateCustomerRequestDto(name = "Rosa", address = "Calle 5 #12"))
        api.create("tok", CreateCustomerRequestDto(name = "Lupe"))

        assertEquals("Calle 5 #12", assertIs<AppResult.Success<CustomerDto>>(result).value.address)
        assertEquals("""{"name":"Rosa","address":"Calle 5 #12"}""", bodies[0])
        assertEquals("""{"name":"Lupe"}""", bodies[1])
    }

    @Test
    fun update_address_puts_address_body() = runTest {
        var method: HttpMethod? = null
        var path = ""
        var auth: String? = null
        var sentBody = ""
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            auth = request.headers["Authorization"]
            sentBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond("""{"id":7,"name":"Rosa","address":"Calle 9","balance":"0.00"}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = CustomersApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.updateAddress("tok", 7L, "Calle 9")

        assertEquals("Calle 9", assertIs<AppResult.Success<CustomerDto>>(result).value.address)
        assertEquals(HttpMethod.Put, method)
        assertEquals("/customers/7/address", path)
        assertEquals("Bearer tok", auth)
        assertEquals("""{"address":"Calle 9"}""", sentBody)
    }
}
