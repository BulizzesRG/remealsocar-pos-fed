package com.posfab.shared.features.credit.domain

data class Debtor(
    val id: Long,
    val name: String,
    val phone: String?,
    val active: Boolean,
    val balance: Double,
    val owesSince: String?,
    val lastMovementAt: String?,
    val code: String? = null,
    val nickname: String? = null,
) {
    val displayName: String get() = customerDisplayName(name, nickname)
}

enum class LedgerEntryType { CHARGE, PAYMENT, OPENING_BALANCE }

data class LedgerEntry(
    val id: Long,
    val type: LedgerEntryType,
    val amount: Double,
    val createdAt: String,
    val saleId: Long?,
    val saleFolio: String?,
    val notes: String?,
)

data class CustomerStatement(
    val id: Long,
    val name: String,
    val phone: String?,
    val active: Boolean,
    val balance: Double,
    val entries: List<LedgerEntry>,
    val code: String? = null,
    val nickname: String? = null,
    val address: String? = null,
) {
    val displayName: String get() = customerDisplayName(name, nickname)
}

data class CustomerMatch(
    val id: Long,
    val displayName: String,
    val phone: String?,
    val balance: Double,
    val active: Boolean = true,
    val code: String? = null,
)

data class CustomerEdit(
    val name: String,
    val nickname: String?,
    val phone: String?,
    val address: String?,
    val active: Boolean,
)

data class LoadedCustomer(
    val customerId: Long,
    val displayName: String,
    val amount: Double,
    val phone: String? = null,
    val owedSince: String? = null,
)

fun customerDisplayName(name: String, nickname: String?): String {
    val alias = nickname?.trim().orEmpty()
    return if (alias.isEmpty()) name else "$name ($alias)"
}
