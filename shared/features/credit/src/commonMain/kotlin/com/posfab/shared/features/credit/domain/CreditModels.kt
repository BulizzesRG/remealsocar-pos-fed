package com.posfab.shared.features.credit.domain

data class Debtor(
    val id: Long,
    val name: String,
    val phone: String?,
    val active: Boolean,
    val balance: Double,
    val owesSince: String?,
    val lastMovementAt: String?,
)

enum class LedgerEntryType { CHARGE, PAYMENT }

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
)
