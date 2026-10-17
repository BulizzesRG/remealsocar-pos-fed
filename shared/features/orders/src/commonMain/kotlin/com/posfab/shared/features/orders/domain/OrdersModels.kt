package com.posfab.shared.features.orders.domain

import com.posfab.shared.core.result.AppError
import kotlin.math.round
import kotlin.time.Instant

enum class OrderStatus {
    OPEN,
    PAID,
    CANCELLED,
    ;

    companion object {
        fun fromRaw(raw: String?): OrderStatus? = when (raw?.trim()?.uppercase()) {
            "OPEN" -> OPEN
            "CHECKED_OUT" -> PAID
            "CANCELLED" -> CANCELLED
            else -> null
        }
    }
}

enum class OrderStage {
    ANOTADO,
    ENVIADO,
    ;

    companion object {
        fun fromRaw(raw: String?): OrderStage? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

enum class CancelReason {
    NO_CONTESTO,
    DIRECCION,
    SE_ARREPINTIO,
    SIN_DINERO,
    OTRO,
    ;

    companion object {
        fun fromRaw(raw: String?): CancelReason? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

enum class OrderPriceType {
    PIECE,
    WEIGHT,
    OPEN,
    OTHER,
    ;

    val isSellable: Boolean get() = this != OTHER

    companion object {
        fun fromRaw(raw: String?): OrderPriceType {
            val normalized = raw?.trim()?.uppercase() ?: return PIECE
            return entries.firstOrNull { it.name == normalized && it != OTHER } ?: OTHER
        }
    }
}

enum class OrderKitchenKind {
    FONDA,
    TORTERIA,
    ;

    companion object {
        fun fromRaw(raw: String?): OrderKitchenKind? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

data class OrderCustomer(
    val id: Long,
    val name: String,
    val nickname: String? = null,
    val phone: String? = null,
    val address: String? = null,
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() }?.let { "$name ($it)" } ?: name
}

data class OrderLine(
    val id: Long,
    val productId: Long,
    val productName: String,
    val qty: Double,
    val unitCode: String,
    val priceType: OrderPriceType,
    val unitPrice: Double,
    val lineTotal: Double,
)

data class Order(
    val id: Long,
    val version: Int,
    val status: OrderStatus,
    val stage: OrderStage?,
    val customer: OrderCustomer?,
    val phone: String?,
    val address: String?,
    val notes: String?,
    val paysWith: Double?,
    val change: Double?,
    val cancelReason: CancelReason?,
    val cancelNotes: String?,
    val createdAt: Instant,
    val lines: List<OrderLine>,
    val total: Double,
    val lineCount: Int,
    val folio: String? = null,
) {
    val capturedPaysWith: Double? get() = paysWith?.takeIf { roundMoney(it) > 0.0 }

    val shownChange: Double? get() = change.takeIf { capturedPaysWith != null }

    val cart: OrderCart get() = OrderCart(id, version, status == OrderStatus.OPEN, lines, total)
}

data class OrderCart(
    val orderId: Long,
    val version: Int,
    val isOpen: Boolean,
    val lines: List<OrderLine>,
    val total: Double,
)

data class OrderProduct(
    val id: Long,
    val name: String,
    val unitId: Long,
    val priceType: OrderPriceType,
    val listPrice: Double?,
    val kitchenKind: OrderKitchenKind? = null,
    val cashPrice: Double? = null,
) {
    val shownPrice: Double? get() = cashPrice ?: listPrice
}

data class OrderFields(
    val customerId: Long?,
    val address: String?,
    val notes: String?,
    val paysWith: Double?,
)

sealed interface OrderLineInput {
    data class Qty(val qty: Double) : OrderLineInput
    data class OpenPrice(val amount: Double) : OrderLineInput
}

sealed interface OrderMutationResult {
    data class Success(val cart: OrderCart) : OrderMutationResult
    data class ConflictRefetched(val cart: OrderCart) : OrderMutationResult
    data class Closed(val cart: OrderCart) : OrderMutationResult
    data class Failure(val error: AppError) : OrderMutationResult
}

fun roundMoney(value: Double): Double = round(value * 100.0) / 100.0
