package com.posfab.shared.features.cash.ui

import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.DailyCashReport

data class CashCloseConfirmState(
    val title: String = "Cerrar sesion de caja",
    val message: String,
    val confirmLabel: String = "Cerrar sesion",
    val cancelLabel: String = "Cancelar",
)

data class CashSessionState(
    val terminalId: Long,
    val isLoading: Boolean = true,
    val isOpenSubmitting: Boolean = false,
    val isCloseSubmitting: Boolean = false,
    val isReportLoading: Boolean = false,
    val openingAmountInput: String = "",
    val closingAmountInput: String = "",
    val reportDateInput: String,
    val currentSession: CashSession? = null,
    val dailyReport: DailyCashReport? = null,
    val closeConfirmDialog: CashCloseConfirmState? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
)
