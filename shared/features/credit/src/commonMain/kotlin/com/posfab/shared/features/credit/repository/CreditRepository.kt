package com.posfab.shared.features.credit.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.domain.CustomerEdit
import com.posfab.shared.features.credit.domain.CustomerMatch
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor

interface CreditRepository {
    suspend fun debtors(): AppResult<List<Debtor>>
    suspend fun statement(customerId: Long, limit: Int): AppResult<CustomerStatement>
    suspend fun searchCustomers(
        query: String,
        limit: Int,
        includeInactive: Boolean = false,
    ): AppResult<List<CustomerMatch>>
    suspend fun updateCustomer(customerId: Long, edit: CustomerEdit): AppResult<Unit>
    suspend fun createCustomer(name: String, nickname: String?, phone: String?): AppResult<CustomerMatch>
    suspend fun loadOpeningBalance(
        customerId: Long,
        amount: Double,
        owedSince: String?,
        idempotencyKey: String,
    ): AppResult<Double>
}
