package com.posfab.shared.auth.session

import com.posfab.shared.auth.domain.UserSession

interface SessionStorage {
    suspend fun load(): UserSession?
    suspend fun save(session: UserSession)
    suspend fun clear()
}
