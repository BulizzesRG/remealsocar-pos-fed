package com.posfab.shared.features.reports.sales

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
import com.posfab.shared.features.reports.common.ReportsRepositoryImpl
import com.posfab.shared.network.reports.DailyBreakdownDto
import com.posfab.shared.network.reports.DailySalesTotalsDto
import com.posfab.shared.network.reports.DebtorItemDto
import com.posfab.shared.network.reports.IntegrityCheckDto
import com.posfab.shared.network.reports.KitchenBreakdownRowDto
import com.posfab.shared.network.reports.ReportsApi
import com.posfab.shared.network.reports.SaleDetailDto
import com.posfab.shared.network.reports.SaleDetailLineDto
import com.posfab.shared.network.reports.SaleHistoryItemDto
import com.posfab.shared.network.reports.WasteItemDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class ReportsRepositoryImplTest {
    private val day = LocalDate(2026, 10, 16)

    @Test
    fun sales_of_day_map_payment_channel_and_credit_customer() = runTest {
        val api = FakeReportsApi(
            sales = AppResult.Success(
                listOf(
                    item(1, "CASH", "PAID", "TAKEAWAY", "120.50"),
                    item(2, "CREDIT", "ON_CREDIT", "ONSITE", "18.00", "Lupe Hernández", "Doña Lupe"),
                    item(3, "CARD", "PAID", "TAKEAWAY", "1234.00", "Juan Pérez", null),
                ),
            ),
        )

        val sales = assertIs<AppResult.Success<List<SaleListItem>>>(repository(api).salesOfDay(day)).value

        assertEquals(listOf<Pair<String, Long?>>("2026-10-16" to null), api.salesRequests)
        assertEquals(
            SaleListItem(
                id = 1,
                folio = "S-000001",
                soldAt = Instant.parse("2026-10-17T01:15:00Z"),
                payment = SalePayment.CASH,
                channel = SaleChannel.TAKEAWAY,
                total = 120.5,
                customerName = null,
            ),
            sales[0],
        )
        assertEquals(SalePayment.CREDIT, sales[1].payment)
        assertEquals(SaleChannel.ONSITE, sales[1].channel)
        assertEquals("Lupe Hernández (Doña Lupe)", sales[1].customerName)
        assertEquals("Juan Pérez", sales[2].customerName)
        assertEquals(1234.0, sales[2].total)
    }

    @Test
    fun unknown_payment_type_is_a_failure() = runTest {
        val api = FakeReportsApi(sales = AppResult.Success(listOf(item(1, "BITCOIN", "PAID", "TAKEAWAY", "10.00"))))

        assertIs<AppResult.Failure>(repository(api).salesOfDay(day))
    }

    @Test
    fun unparsable_amount_or_instant_is_a_failure() = runTest {
        val badAmount = FakeReportsApi(sales = AppResult.Success(listOf(item(1, "CASH", "PAID", "TAKEAWAY", "abc"))))
        val badTime = FakeReportsApi(
            sales = AppResult.Success(listOf(item(1, "CASH", "PAID", "TAKEAWAY", "1.00").copy(soldAt = "ayer"))),
        )

        assertIs<AppResult.Failure>(repository(badAmount).salesOfDay(day))
        assertIs<AppResult.Failure>(repository(badTime).salesOfDay(day))
    }

    @Test
    fun units_sum_by_kitchen_kind_with_null_as_tienda() = runTest {
        val api = FakeReportsApi(
            breakdown = AppResult.Success(
                DailyBreakdownDto(
                    byPayment = emptyList(),
                    byKitchen = listOf(
                        kitchen(1, "TIENDA", null, "100.00"),
                        kitchen(2, "FONDA", "FONDA", "75.00"),
                        kitchen(3, "TORTERIA", "TORTERIA", "50.00"),
                        kitchen(1, "TIENDA", "FONDA", "25.50"),
                        kitchen(2, "FONDA", null, "4.50"),
                    ),
                ),
            ),
        )

        val units = assertIs<AppResult.Success<DayUnits>>(repository(api).unitsOfDay(day)).value

        assertEquals(listOf("2026-10-16" to "2026-10-16"), api.breakdownRequests)
        assertEquals(104.5, units.tienda, 0.001)
        assertEquals(100.5, units.fonda, 0.001)
        assertEquals(50.0, units.torteria, 0.001)
    }

    @Test
    fun unknown_kitchen_kind_is_a_failure() = runTest {
        val api = FakeReportsApi(
            breakdown = AppResult.Success(DailyBreakdownDto(emptyList(), listOf(kitchen(1, "TIENDA", "PANADERIA", "1.00")))),
        )

        assertIs<AppResult.Failure>(repository(api).unitsOfDay(day))
    }

    @Test
    fun detail_maps_header_and_lines() = runTest {
        val api = FakeReportsApi(detail = AppResult.Success(detailDto()))

        val detail = assertIs<AppResult.Success<SaleDetail>>(repository(api).saleDetail(7L)).value

        assertEquals(listOf(7L), api.detailRequests)
        assertEquals("S-000007", detail.folio)
        assertEquals("POS1", detail.terminalCode)
        assertEquals(SalePayment.CREDIT, detail.payment)
        assertEquals(SaleChannel.ONSITE, detail.channel)
        assertEquals(161.0, detail.subtotal)
        assertEquals(6.3, detail.cashDiscount)
        assertEquals(154.7, detail.total)
        assertEquals("Lupe Hernández (Doña Lupe)", detail.customerName)
        assertEquals(
            listOf(
                SaleDetailLine("Coca cola", 2.0, "PZA", SalePriceType.PIECE, 21.0, 42.0),
                SaleDetailLine("Jamón", 0.25, "KG", SalePriceType.WEIGHT, 126.0, 31.5),
                SaleDetailLine("Varios", 1.0, "PZA", SalePriceType.OPEN, 87.5, 87.5),
            ),
            detail.lines,
        )
    }

    @Test
    fun detail_without_customer_has_no_name() = runTest {
        val api = FakeReportsApi(
            detail = AppResult.Success(
                detailDto().copy(paymentType = "CASH", paymentStatus = "PAID", customerId = null, customerName = null, customerNickname = null),
            ),
        )

        val detail = assertIs<AppResult.Success<SaleDetail>>(repository(api).saleDetail(7L)).value

        assertEquals(SalePayment.CASH, detail.payment)
        assertNull(detail.customerName)
    }

    @Test
    fun unknown_price_type_and_channel_degrade_instead_of_failing() = runTest {
        val dto = detailDto()
        val api = FakeReportsApi(
            detail = AppResult.Success(
                dto.copy(channel = "DELIVERY", lines = listOf(dto.lines.first().copy(priceType = "BULK", unitCode = "PAQ"))),
            ),
        )

        val detail = assertIs<AppResult.Success<SaleDetail>>(repository(api).saleDetail(7L)).value

        assertEquals(SaleChannel.TAKEAWAY, detail.channel)
        assertEquals(SalePriceType.OTHER, detail.lines.single().priceType)
        assertEquals("2 paq", qtyLabel(detail.lines.single().qty, detail.lines.single().unitCode, detail.lines.single().priceType))
    }

    @Test
    fun unknown_channel_in_the_list_reads_as_takeaway() = runTest {
        val api = FakeReportsApi(sales = AppResult.Success(listOf(item(1, "CASH", "PAID", "DELIVERY", "10.00"))))

        val sales = assertIs<AppResult.Success<List<SaleListItem>>>(repository(api).salesOfDay(day)).value

        assertEquals(SaleChannel.TAKEAWAY, sales.single().channel)
    }

    @Test
    fun api_failure_passes_through() = runTest {
        val api = FakeReportsApi(detail = AppResult.Failure(AppError.Validation("Sale not found")))

        val failure = assertIs<AppResult.Failure>(repository(api).saleDetail(7L))
        assertEquals(AppError.Validation("Sale not found"), failure.error)
    }

    private suspend fun repository(api: FakeReportsApi): ReportsRepositoryImpl {
        val manager = SessionManager(InMemorySessionStorage())
        manager.set(
            UserSession(
                tokens = AuthTokens("access", "refresh"),
                user = AuthUser("u1", "gerente", setOf(UserRole.MANAGER), emptySet()),
                terminalCode = "POS1",
            ),
        )
        return ReportsRepositoryImpl(api, AuthorizedApiExecutor(NoRefreshAuthRepository(manager), manager))
    }
}

private fun item(
    id: Long,
    paymentType: String,
    paymentStatus: String,
    channel: String,
    total: String,
    customerName: String? = null,
    customerNickname: String? = null,
) = SaleHistoryItemDto(
    id = id,
    folio = "S-${id.toString().padStart(6, '0')}",
    terminalId = 1,
    businessUnitId = 1,
    paymentType = paymentType,
    paymentStatus = paymentStatus,
    soldAt = "2026-10-17T01:15:00Z",
    totalAmount = total,
    cashDiscount = "0.00",
    channel = channel,
    customerName = customerName,
    customerNickname = customerNickname,
)

private fun kitchen(unitId: Long, unitCode: String, kind: String?, amount: String) = KitchenBreakdownRowDto(
    saleDate = "2026-10-16",
    businessUnitId = unitId,
    businessUnitCode = unitCode,
    kitchenKind = kind,
    lineCount = 1,
    amount = amount,
)

private fun detailDto() = SaleDetailDto(
    id = 7,
    folio = "S-000007",
    soldAt = "2026-10-17T01:15:00Z",
    terminalId = 1,
    terminalCode = "POS1",
    businessUnitId = 1,
    businessUnitCode = "TIENDA",
    paymentType = "CREDIT",
    paymentStatus = "ON_CREDIT",
    channel = "ONSITE",
    subtotal = "161.00",
    cashDiscount = "6.30",
    totalAmount = "154.70",
    customerId = 13,
    customerName = "Lupe Hernández",
    customerNickname = "Doña Lupe",
    lines = listOf(
        SaleDetailLineDto(1, 1, "Coca cola", "PZA", "PIECE", "2.000000", "21.00", "42.00"),
        SaleDetailLineDto(2, 13, "Jamón", "KG", "WEIGHT", "0.250000", "126.00", "31.50"),
        SaleDetailLineDto(3, 3, "Varios", "PZA", "OPEN", "1.000000", "87.50", "87.50"),
    ),
)

private class NoRefreshAuthRepository(private val manager: SessionManager) : AuthRepository {
    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> =
        AppResult.Failure(AppError.Unauthorized)

    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(manager.current())

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeReportsApi(
    private val sales: AppResult<List<SaleHistoryItemDto>> = AppResult.Success(emptyList()),
    private val breakdown: AppResult<DailyBreakdownDto> = AppResult.Success(DailyBreakdownDto(emptyList(), emptyList())),
    private val detail: AppResult<SaleDetailDto> = AppResult.Failure(AppError.Validation("Sale not found")),
) : ReportsApi {
    val salesRequests = mutableListOf<Pair<String, Long?>>()
    val breakdownRequests = mutableListOf<Pair<String, String>>()
    val detailRequests = mutableListOf<Long>()

    override suspend fun dailySales(accessToken: String, date: String, terminalId: Long?): AppResult<List<SaleHistoryItemDto>> {
        salesRequests += date to terminalId
        return sales
    }

    override suspend fun saleDetail(accessToken: String, id: Long): AppResult<SaleDetailDto> {
        detailRequests += id
        return detail
    }

    override suspend fun dailyBreakdown(accessToken: String, dateFrom: String, dateTo: String): AppResult<DailyBreakdownDto> {
        breakdownRequests += dateFrom to dateTo
        return breakdown
    }

    override suspend fun dailyTotals(accessToken: String, dateFrom: String, dateTo: String): AppResult<List<DailySalesTotalsDto>> =
        AppResult.Success(emptyList())

    override suspend fun topDebtors(accessToken: String, limit: Int?): AppResult<List<DebtorItemDto>> =
        AppResult.Success(emptyList())

    override suspend fun wasteTotals(accessToken: String, dateFrom: String, dateTo: String): AppResult<List<WasteItemDto>> =
        AppResult.Success(emptyList())

    override suspend fun integrityCheck(accessToken: String): AppResult<IntegrityCheckDto> =
        AppResult.Success(IntegrityCheckDto(ok = true))
}
