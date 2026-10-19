package com.posfab.shared.network.shopping

import kotlinx.serialization.Serializable

@Serializable
data class ShoppingItemDto(
    val id: Long,
    val text: String,
    val productId: Long? = null,
    val productName: String? = null,
    val qty: String? = null,
    val status: String,
    val carriedFrom: Long? = null,
    val createdBy: String? = null,
    val createdAt: String,
)

@Serializable
data class ShoppingListDto(
    val id: Long,
    val status: String,
    val createdAt: String,
    val createdBy: String? = null,
    val closedAt: String? = null,
    val closedBy: String? = null,
    val doneAt: String? = null,
    val doneBy: String? = null,
    val itemCount: Int = 0,
    val boughtCount: Int = 0,
    val notFoundCount: Int = 0,
    val items: List<ShoppingItemDto> = emptyList(),
)

@Serializable
data class ShoppingListSummaryDto(
    val id: Long,
    val status: String,
    val createdAt: String,
    val createdBy: String? = null,
    val closedAt: String? = null,
    val closedBy: String? = null,
    val doneAt: String? = null,
    val doneBy: String? = null,
    val itemCount: Int = 0,
    val boughtCount: Int = 0,
    val notFoundCount: Int = 0,
)

@Serializable
data class ShoppingCurrentDto(
    val open: ShoppingListDto? = null,
    val toShop: ShoppingListDto? = null,
)

@Serializable
data class ShoppingHistoryDto(
    val items: List<ShoppingListSummaryDto> = emptyList(),
)

@Serializable
data class ShoppingItemRequestDto(
    val text: String,
    val productId: Long? = null,
    val qty: String? = null,
)

@Serializable
data class ShoppingItemStatusRequestDto(
    val status: String,
)

@Serializable
data class ShoppingListDoneRequestDto(
    val carryOver: List<Long>,
)
