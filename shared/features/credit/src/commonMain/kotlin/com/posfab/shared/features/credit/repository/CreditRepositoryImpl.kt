package com.posfab.shared.features.credit.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor
import com.posfab.shared.features.credit.domain.LedgerEntry
import com.posfab.shared.features.credit.domain.LedgerEntryType
import com.posfab.shared.network.customers.CustomerLedgerDto
import com.posfab.shared.network.customers.CustomersApi
import com.posfab.shared.network.customers.DebtorDto
import com.posfab.shared.network.customers.LedgerEntryDto

class CreditRepositoryImpl(
    private val customersApi: CustomersApi,
    private val executor: AuthorizedApiExecutor,
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

    private fun <T, R> mapAll(items: List<T>, transform: (T) -> R?): AppResult<List<R>> {
        val mapped = items.map { transform(it) ?: return malformed() }
        return AppResult.Success(mapped)
    }

    private fun malformed(): AppResult.Failure = AppResult.Failure(AppError.Unknown(MALFORMED_AMOUNT))

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
        )
    }

    private fun LedgerEntryDto.toDomain(): LedgerEntry? {
        val parsedAmount = amount.toDoubleOrNull() ?: return null
        return LedgerEntry(
            id = id,
            type = if (entryType.equals(PAYMENT, ignoreCase = true)) LedgerEntryType.PAYMENT else LedgerEntryType.CHARGE,
            amount = parsedAmount,
            createdAt = createdAt,
            saleId = saleId,
            saleFolio = saleFolio?.takeIf { it.isNotBlank() },
            notes = notes,
        )
    }

    private companion object {
        const val PAYMENT = "PAYMENT"
        const val MALFORMED_AMOUNT = "balance"
    }
}
