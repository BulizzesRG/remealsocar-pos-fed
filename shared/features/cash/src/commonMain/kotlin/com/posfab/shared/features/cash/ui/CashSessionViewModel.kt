package com.posfab.shared.features.cash.ui

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.usecase.CashUseCases
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CashSessionViewModel(
    private val session: UserSession,
    private val cashUseCases: CashUseCases,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val canSwitchTerminal = session.user.roles.any { it == UserRole.ADMIN || it == UserRole.MANAGER }
    private val defaultDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    private val _state = MutableStateFlow(
        CashSessionState(
            terminalId = session.terminal.name,
            canSwitchTerminal = canSwitchTerminal,
            reportDateInput = defaultDate,
        )
    )
    val state: StateFlow<CashSessionState> = _state.asStateFlow()

    init {
        refreshCurrentSession()
        fetchDailyReport()
    }

    fun onOpeningCashChange(value: String) {
        _state.value = _state.value.copy(openingCashInput = value)
    }

    fun onCountedCashChange(value: String) {
        _state.value = _state.value.copy(countedCashInput = value)
    }

    fun onReportDateChange(value: String) {
        _state.value = _state.value.copy(reportDateInput = value)
    }

    fun onTerminalChange(value: String) {
        val normalized = value.trim().uppercase()
        if (normalized.isBlank()) return

        if (!canSwitchTerminal && normalized != session.terminal.name) {
            _state.value = _state.value.copy(notice = "Perfil CAJERO: terminal fija ${session.terminal.name}")
            return
        }

        _state.value = _state.value.copy(terminalId = normalized)
        refreshCurrentSession()
        fetchDailyReport()
    }

    fun refreshCurrentSession() {
        val terminalId = _state.value.terminalId
        scope.launch {
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
    }

    fun openSession() {
        val snapshot = _state.value
        if (snapshot.isOpenSubmitting) return

        val openingCash = snapshot.openingCashInput.toDoubleOrNull()
        if (openingCash == null || openingCash < 0.0) {
            _state.value = snapshot.copy(errorMessage = "Monto de apertura invalido")
            return
        }

        _state.value = snapshot.copy(isOpenSubmitting = true, errorMessage = null, notice = null)
        scope.launch {
            when (val result = cashUseCases.open(snapshot.terminalId, openingCash)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isOpenSubmitting = false,
                        currentSession = result.value,
                        notice = "Sesion de caja abierta.",
                    )
                }
                is AppResult.Failure -> {
                    if (result.error is AppError.Conflict) {
                        refreshCurrentSession()
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
        val counted = snapshot.countedCashInput.toDoubleOrNull()
        if (counted == null || counted < 0.0) {
            _state.value = snapshot.copy(errorMessage = "Monto contado invalido")
            return
        }
        val session = snapshot.currentSession
        if (session == null || session.status != com.posfab.shared.features.cash.domain.CashSessionStatus.OPEN) {
            _state.value = snapshot.copy(notice = "No hay una sesion abierta para cerrar.")
            return
        }
        _state.value = snapshot.copy(
            errorMessage = null,
            closeConfirmDialog = CashCloseConfirmState(
                message = "Se registrara un cierre con efectivo contado de $counted para la terminal ${snapshot.terminalId}.",
            ),
        )
    }

    fun dismissCloseSessionDialog() {
        _state.value = _state.value.copy(closeConfirmDialog = null)
    }

    fun closeSession() {
        val snapshot = _state.value
        if (snapshot.isCloseSubmitting) return

        val counted = snapshot.countedCashInput.toDoubleOrNull()
        if (counted == null || counted < 0.0) {
            _state.value = snapshot.copy(errorMessage = "Monto contado invalido")
            return
        }

        _state.value = snapshot.copy(
            isCloseSubmitting = true,
            errorMessage = null,
            notice = null,
            closeConfirmDialog = null,
        )
        scope.launch {
            when (val result = cashUseCases.close(snapshot.terminalId, counted)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isCloseSubmitting = false,
                        currentSession = result.value,
                        notice = "Sesion cerrada. Delta: ${result.value.delta ?: 0.0}",
                    )
                    fetchDailyReport()
                }
                is AppResult.Failure -> {
                    if (result.error is AppError.Conflict) {
                        refreshCurrentSession()
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
            when (val result = cashUseCases.daily(snapshot.reportDateInput, snapshot.terminalId)) {
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
