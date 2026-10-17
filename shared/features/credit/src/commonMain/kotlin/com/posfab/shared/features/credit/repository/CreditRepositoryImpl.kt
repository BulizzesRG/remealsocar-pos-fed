package com.posfab.shared.features.credit.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.domain.CustomerEdit
import com.posfab.shared.features.credit.domain.CustomerMatch
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor
import com.posfab.shared.features.credit.domain.LedgerEntry
import com.posfab.shared.features.credit.domain.LedgerEntryType
import com.posfab.shared.features.credit.domain.customerDisplayName
import com.posfab.shared.network.customers.CreateCustomerRequestDto
import com.posfab.shared.network.customers.CustomerDto
import com.posfab.shared.network.customers.CustomerLedgerDto
import com.posfab.shared.network.customers.CustomersApi
import com.posfab.shared.network.customers.DebtorDto
import com.posfab.shared.network.customers.LedgerEntryDto
import com.posfab.shared.network.customers.OpeningBalanceRequestDto
import com.posfab.shared.network.customers.UpdateCustomerRequestDto
import kotlin.math.abs
import kotlin.math.round

class CreditRepositoryImpl(
    private val customersApi: CustomersApi,
    private val executor: AuthorizedApiExecutor,
    private val businessUnitId: Long,
) : CreditRepository {
    override suspend fun debtors(): AppResult<List<Debtor>> =
        when (val result = executor.execute { customersApi.debtors(it) }) {
            is AppResult.Success -> mapAll(result.value) { it.toDomain() }
            is AppResult.Failure -> result
        }

    override suspend fun statement(customerId: Long, limit: Int): AppResult<CustomerStatement> =
        when (val result = executor.execute { customersApi.ledger(it, customerId, limit) }) {
            is AppResult.Success -> result.value.toDomain()?.let { AppResult.Success(it) } ?: malformed()
            is AppResult.Failure -> result
        }

    override suspend fun searchCustomers(
        query: String,
        limit: Int,
        includeInactive: Boolean,
    ): AppResult<List<CustomerMatch>> {
        val trimmed = query.trim().takeIf { it.isNotEmpty() }
        return when (val result = executor.execute { customersApi.search(it, trimmed, limit, includeInactive) }) {
            is AppResult.Success -> {
                val visible = if (includeInactive) result.value else result.value.filter { it.active }
                mapAll(visible) { it.toMatch() }
            }
            is AppResult.Failure -> result
        }
    }

    override suspend fun updateCustomer(customerId: Long, edit: CustomerEdit): AppResult<Unit> {
        val request = UpdateCustomerRequestDto(
            name = edit.name.trim(),
            nickname = edit.nickname.trimmedOrNull(),
            phone = edit.phone.trimmedOrNull(),
            address = edit.address.trimmedOrNull(),
            active = edit.active,
        )
        return when (val result = executor.execute { customersApi.update(it, customerId, request) }) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    override suspend fun createCustomer(name: String, nickname: String?, phone: String?): AppResult<CustomerMatch> {
        val request = CreateCustomerRequestDto(
            name = name.trim(),
            nickname = nickname?.trim()?.takeIf { it.isNotEmpty() },
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
        )
        return when (val result = executor.execute { customersApi.create(it, request) }) {
            is AppResult.Success -> result.value.toMatch()?.let { AppResult.Success(it) } ?: malformed()
            is AppResult.Failure -> result
        }
    }

    override suspend fun loadOpeningBalance(
        customerId: Long,
        amount: Double,
        owedSince: String?,
        idempotencyKey: String,
    ): AppResult<Double> {
        val request = OpeningBalanceRequestDto(
            businessUnitId = businessUnitId,
            amount = formatMoney(amount),
            owedSince = owedSince?.takeIf { it.isNotBlank() },
        )
        val result = executor.execute { customersApi.loadOpeningBalance(it, customerId, idempotencyKey, request) }
        return when (result) {
            is AppResult.Success -> result.value.balance.toDoubleOrNull()?.let { AppResult.Success(it) } ?: malformed()
            is AppResult.Failure -> result
        }
    }

    private fun <T, R> mapAll(items: List<T>, transform: (T) -> R?): AppResult<List<R>> {
        val mapped = items.map { transform(it) ?: return malformed() }
        return AppResult.Success(mapped)
    }

    private fun malformed(): AppResult.Failure = AppResult.Failure(AppError.Unknown(MALFORMED_AMOUNT))

    private fun CustomerDto.toMatch(): CustomerMatch? {
        val parsedBalance = balance.toDoubleOrNull() ?: return null
        return CustomerMatch(
            id = id,
            displayName = customerDisplayName(name, nickname),
            phone = phone?.takeIf { it.isNotBlank() },
            balance = parsedBalance,
            active = active,
            code = code?.takeIf { it.isNotBlank() },
        )
    }

    private fun DebtorDto.toDomain(): Debtor? {
        val parsedBalance = balance.toDoubleOrNull() ?: return null
        return Debtor(
            id = id,
            name = name,
            phone = phone?.takeIf { it.isNotBlank() },
            active = active,
            balance = parsedBalance,
            owesSince = owesSince,
            lastMovementAt = lastMovementAt,
            code = code?.takeIf { it.isNotBlank() },
            nickname = nickname?.takeIf { it.isNotBlank() },
        )
    }

    private fun CustomerLedgerDto.toDomain(): CustomerStatement? {
        val parsedBalance = customer.balance.toDoubleOrNull() ?: return null
        val parsedEntries = entries.map { it.toDomain() ?: return null }
        return CustomerStatement(
            id = customer.id,
            name = customer.name,
            phone = customer.phone?.takeIf { it.isNotBlank() },
            active = customer.active,
            balance = parsedBalance,
            entries = parsedEntries,
            code = customer.code?.takeIf { it.isNotBlank() },
            nickname = customer.nickname?.takeIf { it.isNotBlank() },
            address = customer.address?.takeIf { it.isNotBlank() },
        )
    }

    private fun LedgerEntryDto.toDomain(): LedgerEntry? {
        val parsedAmount = amount.toDoubleOrNull() ?: return null
        return LedgerEntry(
            id = id,
            type = entryType(),
            amount = parsedAmount,
            createdAt = createdAt,
            saleId = saleId,
            saleFolio = saleFolio?.takeIf { it.isNotBlank() },
            notes = notes,
        )
    }

    private fun LedgerEntryDto.entryType(): LedgerEntryType = when {
        entryType.equals(PAYMENT, ignoreCase = true) -> LedgerEntryType.PAYMENT
        referenceType.equals(OPENING_BALANCE, ignoreCase = true) -> LedgerEntryType.OPENING_BALANCE
        else -> LedgerEntryType.CHARGE
    }

    private fun formatMoney(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    private companion object {
        const val PAYMENT = "PAYMENT"
        const val OPENING_BALANCE = "OPENING_BALANCE"
        const val MALFORMED_AMOUNT = "balance"
    }
}
