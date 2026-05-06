package com.posfab.shared.network.cash

import kotlinx.serialization.Serializable

@Serializable
data class OpenCashSessionRequestDto(
    val terminalId: Long,
    val businessUnitId: Long,
    val openingAmount: String,
    val userId: Long? = null,
)

@Serializable
data class CloseCashSessionRequestDto(
    val terminalId: Long,
    val closingAmount: String,
    val userId: Long? = null,
)

@Serializable
data class CashSessionResponseDto(
    val id: Long,
    val terminalId: Long,
    val businessUnitId: Long,
    val status: String,
    val openingAmount: String,
    val closingAmount: String? = null,
    val openedAt: String,
    val closedAt: String? = null,
)

@Serializable
data class CashIdResponseDto(
    val id: Long,
)

@Serializable
data class DailyCashReportResponseDto(
    val date: String,
    val terminalId: Long,
    val totalIn: String,
    val totalOut: String,
    val movements: Int,
)
