package com.posfab.shared.features.cash.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSessionStatus
import com.posfab.shared.features.cash.usecase.CashUseCases
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CashSessionViewModel(
    private val terminalId: Long,
    private val businessUnitId: Long,
    private val cashUseCases: CashUseCases,
    val onNavigateToPos: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val defaultDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    private val _state = MutableStateFlow(
        CashSessionState(
            terminalId = terminalId,
            reportDateInput = defaultDate,
        )
    )
    val state: StateFlow<CashSessionState> = _state.asStateFlow()

    init {
        refreshCurrentSession()
        fetchDailyReport()
    }

    fun onOpeningAmountChange(value: String) {
        _state.value = _state.value.copy(openingAmountInput = value)
    }

    fun onClosingAmountChange(value: String) {
        _state.value = _state.value.copy(closingAmountInput = value)
    }

    fun onReportDateChange(value: String) {
        _state.value = _state.value.copy(reportDateInput = value)
    }

    fun refreshCurrentSession() {
        scope.launch { loadCurrentSession() }
    }

    /** Loads the current session and waits for it, so callers can set a notice afterwards without being overwritten. */
    private suspend fun loadCurrentSession() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        when (val result = cashUseCases.current(terminalId)) {
            is AppResult.Success -> {
                val existingNotice = _state.value.notice
                _state.value = _state.value.copy(
                    isLoading = false,
                    currentSession = result.value,
                    notice = if (result.value == null) "No hay sesion abierta" else existingNotice,
                )
            }
            is AppResult.Failure -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = CashErrorText.from(result.error),
                )
            }
        }
    }

    fun openSession() {
        val snapshot = _state.value
        if (snapshot.isOpenSubmitting) return

        val openingAmount = snapshot.openingAmountInput.toDoubleOrNull()
        if (openingAmount == null || openingAmount < 0.0) {
            _state.value = snapshot.copy(errorMessage = "Monto de apertura invalido")
            return
        }

        _state.value = snapshot.copy(isOpenSubmitting = true, errorMessage = null, notice = null)
        scope.launch {
            when (val result = cashUseCases.open(terminalId, businessUnitId, openingAmount)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isOpenSubmitting = false,
                        currentSession = result.value,
                        notice = "Sesion de caja abierta.",
                    )
                }
                is AppResult.Failure -> {
                    if (result.error is AppError.Conflict) {
                        loadCurrentSession()
                        _state.value = _state.value.copy(
                            isOpenSubmitting = false,
                            notice = "La caja ya estaba abierta. Estado actualizado.",
                            errorMessage = null,
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isOpenSubmitting = false,
                            errorMessage = CashErrorText.from(result.error),
                        )
                    }
                }
            }
        }
    }

    fun requestCloseSession() {
        val snapshot = _state.value
        val closingAmount = snapshot.closingAmountInput.toDoubleOrNull()
        if (closingAmount == null || closingAmount < 0.0) {
            _state.value = snapshot.copy(errorMessage = "Monto de cierre invalido")
            return
        }
        val session = snapshot.currentSession
        if (session == null || session.status != CashSessionStatus.OPEN) {
            _state.value = snapshot.copy(notice = "No hay una sesion abierta para cerrar.")
            return
        }
        _state.value = snapshot.copy(
            errorMessage = null,
            closeConfirmDialog = CashCloseConfirmState(
                message = "Se registrara un cierre con efectivo de $closingAmount para la terminal ${snapshot.terminalId}.",
            ),
        )
    }

    fun dismissCloseSessionDialog() {
        _state.value = _state.value.copy(closeConfirmDialog = null)
    }

    fun closeSession() {
        val snapshot = _state.value
        if (snapshot.isCloseSubmitting) return

        val closingAmount = snapshot.closingAmountInput.toDoubleOrNull()
        if (closingAmount == null || closingAmount < 0.0) {
            _state.value = snapshot.copy(errorMessage = "Monto de cierre invalido")
            return
        }

        _state.value = snapshot.copy(
            isCloseSubmitting = true,
            errorMessage = null,
            notice = null,
            closeConfirmDialog = null,
        )
        scope.launch {
            when (val result = cashUseCases.close(terminalId, closingAmount)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isCloseSubmitting = false,
                        currentSession = result.value,
                        notice = "Sesion cerrada.",
                    )
                    fetchDailyReport()
                }
                is AppResult.Failure -> {
                    if (result.error is AppError.Conflict) {
                        loadCurrentSession()
                        _state.value = _state.value.copy(
                            isCloseSubmitting = false,
                            notice = "La sesion ya estaba cerrada o cambio de estado. Se recargo.",
                            errorMessage = null,
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isCloseSubmitting = false,
                            errorMessage = CashErrorText.from(result.error),
                        )
                    }
                }
            }
        }
    }

    fun fetchDailyReport() {
        val snapshot = _state.value
        scope.launch {
            _state.value = snapshot.copy(isReportLoading = true, errorMessage = null)
            when (val result = cashUseCases.daily(snapshot.reportDateInput, terminalId)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isReportLoading = false,
                        dailyReport = result.value,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isReportLoading = false,
                        errorMessage = CashErrorText.from(result.error),
                    )
                }
            }
        }
    }
}
