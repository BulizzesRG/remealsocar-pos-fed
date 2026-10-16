package com.posfab.shared.features.credit.usecase

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor
import com.posfab.shared.features.credit.repository.CreditRepository

class CreditUseCases(private val repository: CreditRepository) {
    suspend fun debtors(): AppResult<List<Debtor>> = repository.debtors()
    suspend fun statement(customerId: Long, limit: Int): AppResult<CustomerStatement> =
        repository.statement(customerId, limit)
}
