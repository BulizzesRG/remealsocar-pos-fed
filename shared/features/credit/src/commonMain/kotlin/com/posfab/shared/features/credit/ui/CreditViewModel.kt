package com.posfab.shared.features.credit.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.usecase.CreditUseCases
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreditViewModel(
    private val creditUseCases: CreditUseCases,
    private val now: () -> Instant = { Clock.System.now() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(CreditState())
    val state: StateFlow<CreditState> = _state.asStateFlow()

    private var debtorsJob: Job? = null
    private var statementJob: Job? = null

    fun load() {
        debtorsJob?.cancel()
        _state.update {
            it.copy(isLoading = true, errorMessage = null, nowEpochMillis = now().toEpochMilliseconds())
        }
        debtorsJob = scope.launch {
            when (val result = creditUseCases.debtors()) {
                is AppResult.Success -> _state.update { it.copy(isLoading = false, debtors = result.value) }
                is AppResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = CreditErrorText.from(result.error))
                }
            }
        }
    }

    fun refresh() {
        val selectedId = _state.value.selectedId
        load()
        if (selectedId != null) select(selectedId)
    }

    fun onFilterChange(value: String) {
        _state.update { it.copy(filter = value) }
    }

    fun select(customerId: Long) {
        statementJob?.cancel()
        _state.update {
            it.copy(
                selectedId = customerId,
                selected = it.selected?.takeIf { statement -> statement.id == customerId },
                isStatementLoading = true,
                errorMessage = null,
            )
        }
        statementJob = scope.launch {
            val result = creditUseCases.statement(customerId, LEDGER_LIMIT)
            if (_state.value.selectedId != customerId) return@launch
            when (result) {
                is AppResult.Success -> _state.update { it.copy(isStatementLoading = false, selected = result.value) }
                is AppResult.Failure -> _state.update {
                    it.copy(isStatementLoading = false, errorMessage = CreditErrorText.from(result.error))
                }
            }
        }
    }

    fun back() {
        statementJob?.cancel()
        _state.update {
            it.copy(selectedId = null, selected = null, isStatementLoading = false, errorMessage = null)
        }
    }

    companion object {
        const val LEDGER_LIMIT = 100
    }
}
