package com.posfab.shared.auth.session

import com.posfab.shared.auth.domain.UserSession

class InMemorySessionStorage : SessionStorage {
    private var value: UserSession? = null

    override suspend fun load(): UserSession? = value

    override suspend fun save(session: UserSession) {
        value = session
    }

    override suspend fun clear() {
        value = null
    }
}
