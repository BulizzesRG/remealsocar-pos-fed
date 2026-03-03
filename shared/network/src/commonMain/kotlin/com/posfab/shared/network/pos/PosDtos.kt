package com.posfab.shared.network.pos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: String,
    val sku: String? = null,
    val barcode: String? = null,
    val name: String,
    @SerialName("defaultUnit") val defaultUnit: String = "EA",
    @SerialName("unitPrice") val unitPrice: Double,
    @SerialName("lotTracked") val lotTracked: Boolean = false,
)

@Serializable
data class DraftDto(
    val id: String,
    val type: String,
    val status: String,
    @SerialName("terminalCode") val terminalCode: String,
    val version: Long,
    val lines: List<DraftLineDto>,
    val totals: DraftTotalsDto,
)

@Serializable
data class DraftLineDto(
    val id: String,
    @SerialName("productId") val productId: String,
    @SerialName("productName") val productName: String,
    val barcode: String? = null,
    val qty: Double,
    val unit: String,
    @SerialName("unitPrice") val unitPrice: Double,
    @SerialName("lineTotal") val lineTotal: Double,
    @SerialName("lotId") val lotId: String? = null,
    @SerialName("lotTracked") val lotTracked: Boolean = false,
)

@Serializable
data class DraftTotalsDto(
    val subtotal: Double,
    val tax: Double,
    val total: Double,
)

@Serializable
data class DraftIssueDto(
    @SerialName("lineId") val lineId: String? = null,
    val code: String,
    val message: String,
)

@Serializable
data class ValidateDraftResponseDto(
    val issues: List<DraftIssueDto> = emptyList(),
)

@Serializable
data class AddLineRequestDto(
    @SerialName("productId") val productId: String,
    val qty: Double,
    val unit: String,
    @SerialName("unitPrice") val unitPrice: Double,
    @SerialName("lotId") val lotId: String? = null,
    @SerialName("expectedVersion") val expectedVersion: Long,
)

@Serializable
data class UpdateLineRequestDto(
    val qty: Double,
    val unit: String,
    @SerialName("unitPrice") val unitPrice: Double,
    @SerialName("lotId") val lotId: String? = null,
    @SerialName("expectedVersion") val expectedVersion: Long,
)

@Serializable
data class CheckoutRequestDto(
    val mode: String,
    @SerialName("expectedVersion") val expectedVersion: Long,
)

@Serializable
data class CheckoutResponseDto(
    @SerialName("saleId") val saleId: String,
    val folio: String,
    val total: Double,
    val mode: String,
)
