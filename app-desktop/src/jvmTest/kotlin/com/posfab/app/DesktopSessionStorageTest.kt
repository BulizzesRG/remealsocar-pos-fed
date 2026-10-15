package com.posfab.app

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.model.UserRole
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSessionStorageTest {
    private val dir: Path = Files.createTempDirectory("session-storage-test")
    private val file: Path = dir.resolve("session.json")

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun old_format_session_without_permissions_loads_as_null() = runBlocking {
        file.writeText(
            """{"tokens":{"accessToken":"a","refreshToken":"r"},"user":{"id":"1","username":"x","roles":["ADMIN"]},"terminalCode":"POS1"}"""
        )

        assertNull(DesktopSessionStorage(file).load())
    }

    @Test
    fun missing_file_loads_as_null() = runBlocking {
        assertNull(DesktopSessionStorage(file).load())
    }

    @Test
    fun saved_session_loads_back_equal() = runBlocking {
        val storage = DesktopSessionStorage(file)
        val session = UserSession(
            tokens = AuthTokens("access", "refresh"),
            user = AuthUser(
                id = "7",
                username = "cajero",
                roles = setOf(UserRole.CASHIER),
                permissions = setOf(Permission.VIEW_PRODUCTS, Permission.SELL, Permission.OPEN_CASH),
            ),
            terminalCode = "POS1",
            terminalType = "POS",
        )

        storage.save(session)

        assertEquals(session, storage.load())
    }

    @Test
    fun clear_removes_the_saved_session() = runBlocking {
        val storage = DesktopSessionStorage(file)
        storage.save(
            UserSession(
                tokens = AuthTokens("a", "r"),
                user = AuthUser("1", "x", setOf(UserRole.ADMIN), Permission.entries.toSet()),
                terminalCode = "POS1",
            )
        )

        storage.clear()

        assertNull(storage.load())
    }
}
