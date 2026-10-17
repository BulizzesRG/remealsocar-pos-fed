package com.posfab.shared.features.reports.common

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.reports.manager.DebtorItem
import com.posfab.shared.features.reports.manager.IntegrityIssue
import com.posfab.shared.features.reports.manager.IntegrityIssueSample
import com.posfab.shared.features.reports.manager.IntegrityResult
import com.posfab.shared.features.reports.manager.ManagerDailyTotal
import com.posfab.shared.features.reports.manager.ManagerRepository
import com.posfab.shared.features.reports.manager.WasteItem
import com.posfab.shared.features.reports.sales.DayUnits
import com.posfab.shared.features.reports.sales.SaleChannel
import com.posfab.shared.features.reports.sales.SaleDetail
import com.posfab.shared.features.reports.sales.SaleDetailLine
import com.posfab.shared.features.reports.sales.SaleListItem
import com.posfab.shared.features.reports.sales.SalePayment
import com.posfab.shared.features.reports.sales.SalePriceType
import com.posfab.shared.features.reports.sales.SalesRepository
import com.posfab.shared.features.reports.sales.customerDisplayName
import com.posfab.shared.network.reports.KitchenBreakdownRowDto
import com.posfab.shared.network.reports.ReportsApi
import com.posfab.shared.network.reports.SaleDetailDto
import com.posfab.shared.network.reports.SaleDetailLineDto
import com.posfab.shared.network.reports.SaleHistoryItemDto
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DateTimeComponents

class ReportsRepositoryImpl(
    private val reportsApi: ReportsApi,
    private val executor: AuthorizedApiExecutor,
) : SalesRepository, ManagerRepository {
    override suspend fun salesOfDay(date: LocalDate): AppResult<List<SaleListItem>> =
        when (val result = executor.execute { reportsApi.dailySales(it, date.toString(), null) }) {
            is AppResult.Success -> mapAll(result.value) { it.toListItem() }
            is AppResult.Failure -> result
        }

    override suspend fun unitsOfDay(date: LocalDate): AppResult<DayUnits> {
        val day = date.toString()
        return when (val result = executor.execute { reportsApi.dailyBreakdown(it, day, day) }) {
            is AppResult.Success -> sumUnits(result.value.byKitchen)?.let { AppResult.Success(it) } ?: malformed()
            is AppResult.Failure -> result
        }
    }

    override suspend fun saleDetail(id: Long): AppResult<SaleDetail> =
        when (val result = executor.execute { reportsApi.saleDetail(it, id) }) {
            is AppResult.Success -> result.value.toDetail()?.let { AppResult.Success(it) } ?: malformed()
            is AppResult.Failure -> result
        }

    override suspend fun dailyTotals(dateFrom: String, dateTo: String): AppResult<List<ManagerDailyTotal>> {
        return when (val result = executor.execute { reportsApi.dailyTotals(it, dateFrom, dateTo) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.map {
                    ManagerDailyTotal(
                        saleDate = it.saleDate,
                        terminalId = it.terminalId.toString(),
                        businessUnitId = it.businessUnitId.toString(),
                        salesCount = it.salesCount.toInt(),
                        total = it.totalAmount.toDouble(),
                    )
                }
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun topDebtors(limit: Int?): AppResult<List<DebtorItem>> {
        return when (val result = executor.execute { reportsApi.topDebtors(it, limit) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.map {
                    DebtorItem(
                        customerId = it.customerId.toString(),
                        customerName = it.customerName,
                        outstandingBalance = it.outstandingBalance.toDouble(),
                    )
                }
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun wasteTotals(dateFrom: String, dateTo: String): AppResult<List<WasteItem>> {
        return when (val result = executor.execute { reportsApi.wasteTotals(it, dateFrom, dateTo) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.map {
                    WasteItem(
                        wasteDate = it.wasteDate,
                        productId = it.productId.toString(),
                        totalWasteQtyBase = it.totalWasteQtyBase.toDouble(),
                    )
                }
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun integrityCheck(): AppResult<IntegrityResult> {
        return when (val result = executor.execute { reportsApi.integrityCheck(it) }) {
            is AppResult.Success -> AppResult.Success(
                IntegrityResult(
                    ok = result.value.ok,
                    issues = result.value.issues.map {
                        IntegrityIssue(
                            code = it.code,
                            count = it.count,
                            samples = it.samples.map { s ->
                                IntegrityIssueSample(
                                    entityType = s.entityType,
                                    entityId = s.entityId,
                                )
                            },
                        )
                    },
                )
            )
            is AppResult.Failure -> result
        }
    }

    private fun <T, R> mapAll(items: List<T>, transform: (T) -> R?): AppResult<List<R>> {
        val mapped = items.map { transform(it) ?: return malformed() }
        return AppResult.Success(mapped)
    }

    private fun malformed(): AppResult.Failure = AppResult.Failure(AppError.Unknown(MALFORMED))

    private fun SaleHistoryItemDto.toListItem(): SaleListItem? = SaleListItem(
        id = id,
        folio = folio,
        soldAt = parseInstant(soldAt) ?: return null,
        payment = payment(paymentType, paymentStatus) ?: return null,
        channel = channel(channel),
        total = totalAmount.toDoubleOrNull() ?: return null,
        customerName = customerName(customerName, customerNickname),
    )

    private fun SaleDetailDto.toDetail(): SaleDetail? = SaleDetail(
        id = id,
        folio = folio,
        soldAt = parseInstant(soldAt) ?: return null,
        terminalCode = terminalCode,
        businessUnitCode = businessUnitCode,
        payment = payment(paymentType, paymentStatus) ?: return null,
        channel = channel(channel),
        subtotal = subtotal.toDoubleOrNull() ?: return null,
        cashDiscount = cashDiscount.toDoubleOrNull() ?: return null,
        total = totalAmount.toDoubleOrNull() ?: return null,
        customerName = customerName(customerName, customerNickname),
        lines = lines.map { it.toLine() ?: return null },
    )

    private fun SaleDetailLineDto.toLine(): SaleDetailLine? = SaleDetailLine(
        productName = productName,
        qty = qty.toDoubleOrNull() ?: return null,
        unitCode = unitCode,
        priceType = enumOrNull<SalePriceType>(priceType) ?: SalePriceType.OTHER,
        unitPrice = unitPrice.toDoubleOrNull() ?: return null,
        lineTotal = lineTotal.toDoubleOrNull() ?: return null,
    )

    private fun sumUnits(rows: List<KitchenBreakdownRowDto>): DayUnits? {
        var tienda = 0.0
        var fonda = 0.0
        var torteria = 0.0
        rows.forEach { row ->
            val amount = row.amount.toDoubleOrNull() ?: return null
            when (row.kitchenKind?.uppercase()) {
                null -> tienda += amount
                FONDA -> fonda += amount
                TORTERIA -> torteria += amount
                else -> return null
            }
        }
        return DayUnits(tienda = tienda, fonda = fonda, torteria = torteria)
    }

    private fun payment(type: String, status: String): SalePayment? =
        if (status.equals(ON_CREDIT, ignoreCase = true)) SalePayment.CREDIT else enumOrNull<SalePayment>(type)

    private fun channel(value: String): SaleChannel = enumOrNull<SaleChannel>(value) ?: SaleChannel.TAKEAWAY

    private fun customerName(name: String?, nickname: String?): String? =
        name?.trim()?.takeIf { it.isNotEmpty() }?.let { customerDisplayName(it, nickname) }

    private inline fun <reified E : Enum<E>> enumOrNull(value: String): E? =
        enumValues<E>().firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

    private fun parseInstant(value: String): Instant? = runCatching {
        DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.parse(value).toInstantUsingOffset()
    }.getOrNull()

    private companion object {
        const val MALFORMED = "malformed sales payload"
        const val ON_CREDIT = "ON_CREDIT"
        const val FONDA = "FONDA"
        const val TORTERIA = "TORTERIA"
    }
}
