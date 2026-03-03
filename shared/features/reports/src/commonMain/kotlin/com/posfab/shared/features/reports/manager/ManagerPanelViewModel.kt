package com.posfab.shared.features.reports.manager

import com.posfab.shared.core.BaseViewModel
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

class ManagerPanelViewModel(
    private val useCases: ReportsUseCases,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(
        ManagerPanelState(
            dateFrom = today,
            dateTo = today,
            isLoadingDashboard = true,
            isLoadingIntegrity = false,
        )
    )
    val state: kotlinx.coroutines.flow.StateFlow<ManagerPanelState> = _state

    private var dashboardDebounceJob: Job? = null

    init {
        refreshDashboard()
    }

    fun onDateFromChange(value: String) {
        _state.value = _state.value.copy(dateFrom = value)
        debounceDashboardRefresh()
    }

    fun onDateToChange(value: String) {
        _state.value = _state.value.copy(dateTo = value)
        debounceDashboardRefresh()
    }

    fun refreshDashboard() {
        val snapshot = _state.value
        scope.launch {
            _state.value = snapshot.copy(isLoadingDashboard = true, errorMessage = null)

            val totals = useCases.managerRepository.dailyTotals(snapshot.dateFrom, snapshot.dateTo)
            val debtors = useCases.managerRepository.topDebtors(snapshot.dateFrom, snapshot.dateTo)
            val waste = useCases.managerRepository.wasteTotals(snapshot.dateFrom, snapshot.dateTo)

            val failure = listOf(totals, debtors, waste).filterIsInstance<AppResult.Failure>().firstOrNull()
            if (failure != null) {
                _state.value = _state.value.copy(
                    isLoadingDashboard = false,
                    errorMessage = ReportsErrorText.from(failure.error),
                )
                return@launch
            }

            _state.value = _state.value.copy(
                isLoadingDashboard = false,
                dailyTotals = (totals as AppResult.Success).value,
                debtors = (debtors as AppResult.Success).value,
                waste = (waste as AppResult.Success).value,
                notice = null,
            )
        }
    }

    fun refreshIntegrity() {
        val snapshot = _state.value
        scope.launch {
            _state.value = snapshot.copy(isLoadingIntegrity = true, errorMessage = null)
            when (val result = useCases.managerRepository.integrityCheck()) {
                is AppResult.Success -> {
                    val nowStamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
                    _state.value = _state.value.copy(
                        isLoadingIntegrity = false,
                        integrity = result.value,
                        integrityLastCheckedAt = nowStamp,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoadingIntegrity = false,
                        errorMessage = ReportsErrorText.from(result.error),
                    )
                }
            }
        }
    }

    private fun debounceDashboardRefresh() {
        dashboardDebounceJob?.cancel()
        dashboardDebounceJob = scope.launch {
            delay(400)
            refreshDashboard()
        }
    }
}
