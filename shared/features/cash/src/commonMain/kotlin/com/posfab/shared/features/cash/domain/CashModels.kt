package com.posfab.shared.features.cash.domain

enum class CashSessionStatus { OPEN, CLOSED, NONE }

data class CashSession(
    val id: Long,
    val status: CashSessionStatus,
    val terminalId: Long,
    val openingAmount: Double,
    val closingAmount: Double?,
    val openedAt: String,
    val closedAt: String?,
)

data class DailyCashReport(
    val date: String,
    val terminalId: Long,
    val totalIn: Double,
    val totalOut: Double,
    val movements: Int,
)
