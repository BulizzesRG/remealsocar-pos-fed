package com.posfab.shared.features.orders.ui

import com.posfab.shared.core.input.PhoneInput
import com.posfab.shared.features.orders.domain.CancelReason
import com.posfab.shared.features.orders.domain.Order
import com.posfab.shared.features.orders.domain.OrderCustomer
import com.posfab.shared.features.orders.domain.OrderLine
import com.posfab.shared.features.orders.domain.OrderProduct
import com.posfab.shared.features.orders.domain.OrderStage
import com.posfab.shared.features.orders.domain.roundMoney

sealed interface OrdersView {
    data object List : OrdersView
    data class Editing(val orderId: Long) : OrdersView
}

enum class OrderFocus { CUSTOMER, NEW_CUSTOMER_NAME, ADDRESS, PRODUCT }

data class NewCustomerForm(
    val name: String = "",
    val phone: String = "",
) {
    val hasValidName: Boolean
        get() {
            val compact = name.filterNot { it.isWhitespace() }
            return compact.isNotEmpty() && !compact.all { it.isDigit() }
        }

    val isComplete: Boolean get() = hasValidName && PhoneInput.isComplete(phone)
}

sealed interface OrderPrompt {
    val product: OrderProduct
    val input: String

    data class Weight(override val product: OrderProduct, override val input: String = "") : OrderPrompt
    data class OpenAmount(override val product: OrderProduct, override val input: String = "") : OrderPrompt

    val parsed: Double? get() = input.toDoubleOrNull()?.takeIf { it > 0.0 }

    val previewTotal: Double?
        get() = when (this) {
            is Weight -> parsed?.let { kilos -> product.shownPrice?.let { roundMoney(it * kilos) } }
            is OpenAmount -> parsed
        }
}

data class CancelDialog(
    val orderId: Long,
    val reason: CancelReason? = null,
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val needsNotes: Boolean get() = reason == CancelReason.OTRO

    val canConfirm: Boolean
        get() = !isSubmitting && reason != null && (!needsNotes || notes.isNotBlank())
}

data class OrderForm(
    val orderId: Long,
    val version: Int,
    val isNew: Boolean,
    val stage: OrderStage?,
    val hadCustomer: Boolean = false,
    val hadPaysWith: Boolean = false,
    val customerQuery: String = "",
    val suggestions: List<OrderCustomer> = emptyList(),
    val isSearchingCustomers: Boolean = false,
    val searchedQuery: String? = null,
    val customerSearchError: String? = null,
    val isSavingCustomer: Boolean = false,
    val customerError: String? = null,
    val selected: OrderCustomer? = null,
    val newCustomer: NewCustomerForm? = null,
    val address: String = "",
    val notes: String = "",
    val paysWithInput: String = "",
    val lines: List<OrderLine> = emptyList(),
    val total: Double = 0.0,
    val pendingMutations: Int = 0,
    val productQuery: String = "",
    val productResults: List<OrderProduct> = emptyList(),
    val isSearchingProducts: Boolean = false,
    val prompt: OrderPrompt? = null,
    val isSaving: Boolean = false,
    val isMarkingSent: Boolean = false,
    val isDirty: Boolean = false,
    val confirmLeave: Boolean = false,
    val errorMessage: String? = null,
    val focus: OrderFocus = OrderFocus.CUSTOMER,
    val focusRequest: Int = 0,
) {
    val paysWith: Double? get() = paysWithInput.toDoubleOrNull()?.takeIf { roundMoney(it) > 0.0 }

    val paysWithToSend: Double? get() = paysWith ?: if (hadPaysWith) 0.0 else null

    val change: Double? get() = paysWith?.let { roundMoney(it - total) }

    val hasCustomer: Boolean get() = selected != null

    val isBusy: Boolean get() = isSaving || isMarkingSent || isSavingCustomer

    val canSaveCustomer: Boolean get() = newCustomer?.name?.isNotBlank() == true && !isBusy && prompt == null

    val isEmpty: Boolean get() = selected == null && newCustomer == null && lines.isEmpty()

    val isEmptyOnServer: Boolean get() = !hadCustomer && lines.isEmpty()

    val showsNoCustomerMatch: Boolean
        get() = selected == null && newCustomer == null && !isSearchingCustomers && customerSearchError == null &&
            suggestions.isEmpty() && customerQuery.trim().length >= 2 && searchedQuery == customerQuery.trim()

    val hasUnsavedChanges: Boolean get() = isDirty || (isNew && !isEmpty)

    val canSave: Boolean
        get() = hasCustomer && address.isNotBlank() && lines.isNotEmpty() && pendingMutations == 0 &&
            !isBusy && prompt == null

    val canMarkSent: Boolean
        get() = !isNew && stage == OrderStage.ANOTADO && !isDirty && pendingMutations == 0 && !isBusy

    val canCancel: Boolean get() = pendingMutations == 0 && !isBusy

    val saveHint: String?
        get() = when {
            isBusy || canSave -> null
            pendingMutations > 0 -> OrdersStrings.WAIT_FOR_LINES
            !hasCustomer -> OrdersStrings.MISSING_CUSTOMER
            address.isBlank() -> OrdersStrings.MISSING_ADDRESS
            lines.isEmpty() -> OrdersStrings.MISSING_LINES
            else -> null
        }

    val title: String
        get() = selected?.displayName?.let(OrdersStrings::orderOf)
            ?: newCustomer?.name?.trim()?.takeIf { it.isNotEmpty() }?.let(OrdersStrings::orderOf)
            ?: OrdersStrings.NEW_ORDER_TITLE
}

data class OrdersState(
    val view: OrdersView = OrdersView.List,
    val open: List<Order> = emptyList(),
    val paidToday: List<Order> = emptyList(),
    val cancelledToday: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val showPaid: Boolean = false,
    val showCancelled: Boolean = false,
    val isCreating: Boolean = false,
    val openingId: Long? = null,
    val busyOrderId: Long? = null,
    val pendingCreateKey: String? = null,
    val editing: OrderForm? = null,
    val cancelDialog: CancelDialog? = null,
    val kitchenProducts: List<OrderProduct> = emptyList(),
    val loadGeneration: Int = 0,
    val errorMessage: String? = null,
    val notice: String? = null,
) {
    val isEditing: Boolean get() = view is OrdersView.Editing && editing != null

    val editorLocked: Boolean
        get() = editing?.let { it.isBusy || it.prompt != null } != false || cancelDialog != null
}
