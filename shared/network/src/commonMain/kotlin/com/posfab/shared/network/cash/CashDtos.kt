package com.posfab.shared.network.cash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenCashSessionRequestDto(
    @SerialName("terminal_id") val terminalId: String,
    @SerialName("opening_cash") val openingCash: Double,
)

@Serializable
data class CloseCashSessionRequestDto(
    @SerialName("terminal_id") val terminalId: String,
    @SerialName("counted_cash") val countedCash: Double,
)

@Serializable
data class CashSessionDto(
    val id: String,
    val status: String,
    @SerialName("terminal_id") val terminalId: String,
    @SerialName("opened_at") val openedAt: String? = null,
    @SerialName("opened_by") val openedBy: String? = null,
    @SerialName("opening_cash") val openingCash: Double = 0.0,
    @SerialName("movement_in") val movementIn: Double = 0.0,
    @SerialName("movement_out") val movementOut: Double = 0.0,
    @SerialName("expected_close") val expectedClose: Double = 0.0,
    @SerialName("counted_close") val countedClose: Double? = null,
    @SerialName("delta") val delta: Double? = null,
)

@Serializable
data class DailyCashReportDto(
    val date: String,
    @SerialName("terminal_id") val terminalId: String,
    @SerialName("opening_cash") val openingCash: Double = 0.0,
    @SerialName("movement_in") val movementIn: Double = 0.0,
    @SerialName("movement_out") val movementOut: Double = 0.0,
    @SerialName("sales_cash") val salesCash: Double = 0.0,
    @SerialName("expected_close") val expectedClose: Double = 0.0,
    @SerialName("counted_close") val countedClose: Double? = null,
    @SerialName("delta") val delta: Double? = null,
)
