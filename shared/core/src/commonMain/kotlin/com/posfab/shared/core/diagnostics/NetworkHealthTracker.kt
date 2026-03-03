package com.posfab.shared.core.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

data class NetworkHealthState(
    val isOffline: Boolean = false,
    val lastSuccessfulRequestAt: String? = null,
    val lastFailureAt: String? = null,
    val lastFailureCategory: String? = null,
)

object NetworkHealthTracker {
    private val _state = MutableStateFlow(NetworkHealthState())
    val state: StateFlow<NetworkHealthState> = _state.asStateFlow()

    fun markSuccess() {
        _state.value = _state.value.copy(
            isOffline = false,
            lastSuccessfulRequestAt = Clock.System.now().toString(),
        )
    }

    fun markFailure(category: String) {
        _state.value = _state.value.copy(
            isOffline = true,
            lastFailureAt = Clock.System.now().toString(),
            lastFailureCategory = category,
        )
    }
}
