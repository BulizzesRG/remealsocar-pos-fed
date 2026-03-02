package com.posfab.shared.auth

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.session.InMemorySessionStorage
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthorizedApiExecutorTest {
    @Test
    fun retries_with_rotated_token_after_single_refresh() = runTest {
        val storage = InMemorySessionStorage()
        val manager = SessionManager(storage)
        manager.set(
            UserSession(
                tokens = AuthTokens("old-access", "refresh-1"),
                user = AuthUser("u1", "cashier", setOf(UserRole.CASHIER)),
                terminal = TerminalCode.POS1,
            )
        )

        val repo = FakeAuthRepository(manager)
        val executor = AuthorizedApiExecutor(repo, manager)
        var callCount = 0

        val result = executor.execute { token ->
            callCount += 1
            when (callCount) {
                1 -> {
                    assertEquals("old-access", token)
                    AppResult.Failure(AppError.Unauthorized)
                }
                else -> {
                    assertEquals("new-access", token)
                    AppResult.Success("ok")
                }
            }
        }

        assertEquals(2, callCount)
        assertIs<AppResult.Success<String>>(result)
    }

    private class FakeAuthRepository(
        private val manager: SessionManager,
    ) : AuthRepository {
        override suspend fun login(username: String, password: String, terminal: TerminalCode): AppResult<UserSession> {
            return AppResult.Failure(AppError.Unknown("Not needed"))
        }

        override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> {
            val current = manager.current() ?: return AppResult.Failure(AppError.Unauthorized)
            val rotated = current.copy(tokens = AuthTokens("new-access", "new-refresh"))
            manager.set(rotated)
            return AppResult.Success(rotated)
        }

        override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(manager.current())

        override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
    }
}
