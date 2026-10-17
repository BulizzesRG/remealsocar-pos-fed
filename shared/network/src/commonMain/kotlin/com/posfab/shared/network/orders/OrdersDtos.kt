package com.posfab.shared.network.orders

import com.posfab.shared.network.pos.DraftLineDto
import com.posfab.shared.network.pos.DraftTotalsDto
import kotlinx.serialization.Serializable

@Serializable
data class OrderDto(
    val id: Long,
    val version: Int,
    val status: String,
    val stage: String? = null,
    val customerId: Long? = null,
    val customerName: String? = null,
    val customerNickname: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val paysWith: String? = null,
    val change: String? = null,
    val cancelReason: String? = null,
    val cancelNotes: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val checkoutFolio: String? = null,
    val terminalId: Long,
    val lines: List<DraftLineDto>,
    val totals: DraftTotalsDto,
)

@Serializable
data class OrderListItemDto(
    val id: Long,
    val version: Int,
    val status: String,
    val stage: String? = null,
    val customerId: Long? = null,
    val customerName: String? = null,
    val customerNickname: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val paysWith: String? = null,
    val change: String? = null,
    val cancelReason: String? = null,
    val cancelNotes: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val checkoutFolio: String? = null,
    val terminalId: Long,
    val lineCount: Int,
    val totals: DraftTotalsDto,
)

@Serializable
data class OrderListResponseDto(
    val items: List<OrderListItemDto> = emptyList(),
)

@Serializable
data class OrderCreateRequestDto(
    val customerId: Long? = null,
    val customerName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val paysWith: String? = null,
)

@Serializable
data class OrderUpdateRequestDto(
    val expectedVersion: Int,
    val customerId: Long? = null,
    val customerName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val paysWith: String? = null,
)

@Serializable
data class OrderStageRequestDto(
    val stage: String,
    val expectedVersion: Int,
)

@Serializable
data class OrderCancelRequestDto(
    val reason: String,
    val notes: String? = null,
    val expectedVersion: Int,
)
