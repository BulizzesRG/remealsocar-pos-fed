package com.posfab.shared.features.reports.daily

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.reports.common.ReportsErrorText
import com.posfab.shared.features.reports.common.ReportsUseCases
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class DailyHistoryViewModel(
    private val session: UserSession,
    private val useCases: ReportsUseCases,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val canSwitchTerminal = session.user.roles.any { it == UserRole.MANAGER || it == UserRole.ADMIN }
    private val defaultDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(
        DailyHistoryState(
            terminalId = session.terminal.name,
            canSwitchTerminal = canSwitchTerminal,
            dateInput = defaultDate,
            isLoadingSales = true,
            isLoadingCashSummary = true,
        )
    )
    val state: kotlinx.coroutines.flow.StateFlow<DailyHistoryState> = _state

    private var refreshJob: Job? = null

    init {
        refreshNow()
    }

    fun onDateChange(value: String) {
        _state.value = _state.value.copy(dateInput = value)
        scheduleRefresh()
    }

    fun onTerminalChange(value: String) {
        val normalized = value.trim().uppercase()
        if (normalized.isBlank()) return

        if (!canSwitchTerminal && normalized != session.terminal.name) {
            _state.value = _state.value.copy(notice = "Perfil CAJERO: terminal fija ${session.terminal.name}")
            return
        }

        _state.value = _state.value.copy(terminalId = normalized)
        scheduleRefresh()
    }

    fun refreshNow() {
        refreshJob?.cancel()
        loadSales()
        loadCashSummary()
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(350)
            loadSales()
            loadCashSummary()
        }
    }

    private fun loadSales() {
        scope.launch {
            val request = _state.value
            _state.value = _state.value.copy(isLoadingSales = true, errorMessage = null)
            when (val result = useCases.dailyHistoryRepository.salesByDate(request.dateInput, request.terminalId)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isLoadingSales = false,
                        sales = result.value,
                        notice = if (result.value.isEmpty()) "Sin ventas para el filtro actual" else null,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoadingSales = false,
                        errorMessage = ReportsErrorText.from(result.error),
                    )
                }
            }
        }
    }

    private fun loadCashSummary() {
        scope.launch {
            val request = _state.value
            _state.value = _state.value.copy(isLoadingCashSummary = true, errorMessage = null)
            when (val result = useCases.dailyHistoryRepository.cashSummary(request.dateInput, request.terminalId)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isLoadingCashSummary = false,
                        cashSummary = result.value,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoadingCashSummary = false,
                        errorMessage = ReportsErrorText.from(result.error),
                    )
                }
            }
        }
    }
}
