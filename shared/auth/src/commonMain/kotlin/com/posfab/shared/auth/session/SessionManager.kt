package com.posfab.shared.auth.session

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(
    private val sessionStorage: SessionStorage,
) {
    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    suspend fun restore(): UserSession? {
        val restored = sessionStorage.load()
        _session.value = restored
        return restored
    }

    suspend fun set(session: UserSession) {
        sessionStorage.save(session)
        _session.value = session
    }

    suspend fun updateTokens(tokens: AuthTokens) {
        val current = _session.value ?: return
        set(current.copy(tokens = tokens))
    }

    suspend fun clear() {
        sessionStorage.clear()
        _session.value = null
    }

    fun current(): UserSession? = _session.value
}
