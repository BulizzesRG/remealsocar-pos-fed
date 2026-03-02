package com.posfab.app

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.session.SessionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DesktopSessionStorage(
    private val sessionFile: Path,
) : SessionStorage {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override suspend fun load(): UserSession? = withContext(Dispatchers.IO) {
        if (!sessionFile.exists()) return@withContext null
        runCatching { json.decodeFromString<UserSession>(sessionFile.readText()) }.getOrNull()
    }

    override suspend fun save(session: UserSession) {
        withContext(Dispatchers.IO) {
            sessionFile.parent?.createDirectories()
            sessionFile.writeText(json.encodeToString(UserSession.serializer(), session))
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            if (sessionFile.exists()) {
                Files.deleteIfExists(sessionFile)
            }
        }
    }
}
