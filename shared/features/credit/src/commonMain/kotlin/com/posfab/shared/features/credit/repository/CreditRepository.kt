package com.posfab.shared.features.credit.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor

interface CreditRepository {
    suspend fun debtors(): AppResult<List<Debtor>>
    suspend fun statement(customerId: Long, limit: Int): AppResult<CustomerStatement>
}
