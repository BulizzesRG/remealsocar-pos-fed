package com.posfab.shared.network.customers

import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: Long,
    val code: String? = null,
    val name: String,
    val nickname: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val creditLimit: String? = null,
    val active: Boolean = true,
    val balance: String,
)

@Serializable
data class CreateCustomerRequestDto(
    val name: String,
    val nickname: String? = null,
    val phone: String? = null,
    val address: String? = null,
)

@Serializable
data class UpdateCustomerRequestDto(
    val name: String,
    val nickname: String?,
    val phone: String?,
    val address: String?,
    val active: Boolean,
)

@Serializable
data class UpdateCustomerAddressRequestDto(
    val address: String,
)

@Serializable
data class CustomerPaymentRequestDto(
    val businessUnitId: Long,
    val terminalId: Long,
    val amount: String,
    val notes: String? = null,
)

@Serializable
data class CustomerPaymentResponseDto(
    val id: Long,
    val customerId: Long,
    val amount: String,
    val balance: String,
)

@Serializable
data class DebtorDto(
    val id: Long,
    val code: String? = null,
    val name: String,
    val nickname: String? = null,
    val phone: String? = null,
    val active: Boolean = true,
    val balance: String,
    val owesSince: String? = null,
    val lastMovementAt: String? = null,
)

@Serializable
data class LedgerEntryDto(
    val id: Long,
    val entryType: String,
    val amount: String,
    val createdAt: String,
    val referenceType: String? = null,
    val saleId: Long? = null,
    val saleFolio: String? = null,
    val notes: String? = null,
)

@Serializable
data class CustomerLedgerDto(
    val customer: CustomerDto,
    val entries: List<LedgerEntryDto> = emptyList(),
)

@Serializable
data class OpeningBalanceRequestDto(
    val businessUnitId: Long,
    val amount: String,
    val owedSince: String? = null,
    val notes: String? = null,
)

@Serializable
data class OpeningBalanceResponseDto(
    val id: Long,
    val customerId: Long,
    val amount: String,
    val owedSince: String? = null,
    val balance: String,
)
