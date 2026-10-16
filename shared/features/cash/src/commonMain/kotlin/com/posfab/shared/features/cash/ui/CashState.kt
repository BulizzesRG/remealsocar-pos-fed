package com.posfab.shared.features.cash.ui

import com.posfab.shared.features.cash.domain.CashCloseResult
import com.posfab.shared.features.cash.domain.CashSessionSummary
import com.posfab.shared.features.cash.domain.DrawerDifference
import com.posfab.shared.features.cash.domain.PayoutReason

sealed interface CashPhase {
    data object Loading : CashPhase

    data class Closed(val previousClosingAmount: Double?) : CashPhase

    data class Open(val session: CashSessionSummary) : CashPhase

    data class Unavailable(val message: String) : CashPhase

    data class DayClosed(val result: CashCloseResult) : CashPhase
}

data class PayoutForm(
    val amount: String = "",
    val amountRejected: Boolean = false,
    val reason: PayoutReason = PayoutReason.SURTIDO,
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val pendingKey: String? = null,
    val errorMessage: String? = null,
) {
    val isLocked: Boolean get() = isSubmitting || pendingKey != null
    val parsedAmount: Double? get() = amount.toDoubleOrNull()?.takeIf { it > 0.0 }
    val needsNotes: Boolean get() = reason == PayoutReason.OTRO
    val isBlank: Boolean get() = amount.isBlank() && notes.isBlank()
    val canConfirm: Boolean
        get() = !isSubmitting && parsedAmount != null && (!needsNotes || notes.isNotBlank())
}

data class CloseForm(
    val counted: String = "",
    val countedRejected: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val parsedCounted: Double? get() = counted.toDoubleOrNull()?.takeIf { it >= 0.0 }
}

data class CashState(
    val phase: CashPhase = CashPhase.Loading,
    val canManage: Boolean = false,
    val openingInput: String = "",
    val openingRejected: Boolean = false,
    val isOpening: Boolean = false,
    val isRefreshing: Boolean = false,
    val loadGeneration: Long = 0L,
    val payout: PayoutForm? = null,
    val closing: CloseForm? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
) {
    val openSession: CashSessionSummary? get() = (phase as? CashPhase.Open)?.session

    val canOpenDay: Boolean
        get() = phase is CashPhase.Closed && !isOpening && openingInput.toDoubleOrNull()?.let { it >= 0.0 } == true

    val closeDifference: DrawerDifference?
        get() {
            val session = openSession ?: return null
            val counted = closing?.parsedCounted ?: return null
            return DrawerDifference.of(expected = session.expectedInDrawer, counted = counted)
        }

    val canConfirmClose: Boolean
        get() {
            val form = closing ?: return false
            val session = openSession ?: return false
            return !form.isSubmitting && form.parsedCounted != null && session.openAccounts == 0
        }

    val hasOverlay: Boolean get() = payout != null || closing != null
}
