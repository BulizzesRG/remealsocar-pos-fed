package com.posfab.shared.features.shopping.domain

import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

enum class ShoppingListStatus {
    OPEN,
    TO_SHOP,
    DONE,
    ;

    companion object {
        fun fromRaw(value: String?): ShoppingListStatus? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

enum class ShoppingItemStatus {
    PENDING,
    BOUGHT,
    NOT_FOUND,
    ;

    companion object {
        fun fromRaw(value: String?): ShoppingItemStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: PENDING
    }
}

data class ShoppingItem(
    val id: Long,
    val text: String,
    val productId: Long? = null,
    val productName: String? = null,
    val qty: Double? = null,
    val status: ShoppingItemStatus = ShoppingItemStatus.PENDING,
    val carriedFrom: Long? = null,
    val createdBy: String = "",
    val createdAt: Instant,
) {
    val isLinked: Boolean get() = productId != null
    val isCarried: Boolean get() = carriedFrom != null
}

data class ShoppingList(
    val id: Long,
    val status: ShoppingListStatus,
    val createdAt: Instant,
    val createdBy: String = "",
    val closedAt: Instant? = null,
    val doneAt: Instant? = null,
    val items: List<ShoppingItem> = emptyList(),
) {
    val itemCount: Int get() = items.size
    val boughtCount: Int get() = items.count { it.status == ShoppingItemStatus.BOUGHT }
    val notFoundCount: Int get() = items.count { it.status == ShoppingItemStatus.NOT_FOUND }
    val notBought: List<ShoppingItem> get() = items.filter { it.status != ShoppingItemStatus.BOUGHT }

    fun withItem(item: ShoppingItem): ShoppingList =
        copy(items = items.map { if (it.id == item.id) item else it })

    fun withoutItem(id: Long): ShoppingList = copy(items = items.filterNot { it.id == id })

    fun contains(id: Long): Boolean = items.any { it.id == id }
}

data class ShoppingListSummary(
    val id: Long,
    val createdAt: Instant,
    val doneAt: Instant? = null,
    val itemCount: Int = 0,
    val boughtCount: Int = 0,
    val notFoundCount: Int = 0,
)

data class ShoppingCurrent(
    val open: ShoppingList? = null,
    val toShop: ShoppingList? = null,
)

data class ShoppingProduct(
    val id: Long,
    val name: String,
    val barcode: String? = null,
)

fun plainQty(qty: Double): String {
    val scaled = round(qty * THOUSANDTHS).toLong()
    val sign = if (scaled < 0) "-" else ""
    val absolute = abs(scaled)
    val whole = absolute / THOUSANDTHS.toLong()
    val fraction = (absolute % THOUSANDTHS.toLong()).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

private const val THOUSANDTHS = 1000.0
