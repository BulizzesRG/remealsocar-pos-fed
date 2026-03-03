package com.posfab.shared.features.cash.domain

enum class CashSessionStatus { OPEN, CLOSED, NONE }

data class CashSession(
    val id: String,
    val status: CashSessionStatus,
    val terminalId: String,
    val openedAt: String?,
    val openedBy: String?,
    val openingCash: Double,
    val movementIn: Double,
    val movementOut: Double,
    val expectedClose: Double,
    val countedClose: Double?,
    val delta: Double?,
)

data class DailyCashReport(
    val date: String,
    val terminalId: String,
    val openingCash: Double,
    val movementIn: Double,
    val movementOut: Double,
    val salesCash: Double,
    val expectedClose: Double,
    val countedClose: Double?,
    val delta: Double?,
)
