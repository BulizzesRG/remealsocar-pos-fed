package com.posfab.shared.auth.repository

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.auth.AuthApi
import com.posfab.shared.network.auth.AuthResponseDto
import com.posfab.shared.network.auth.LoginRequestDto
import com.posfab.shared.network.auth.LogoutRequestDto
import com.posfab.shared.network.auth.RefreshRequestDto

interface AuthRepository {
    suspend fun login(username: String, password: String, terminal: TerminalCode): AppResult<UserSession>
    suspend fun refresh(refreshTokenOverride: String? = null): AppResult<UserSession>
    suspend fun restoreSession(): AppResult<UserSession?>
    suspend fun logout(): AppResult<Unit>
}

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
) : AuthRepository {
    override suspend fun login(username: String, password: String, terminal: TerminalCode): AppResult<UserSession> {
        val response = authApi.login(
            LoginRequestDto(
                username = username,
                password = password,
                terminalCode = terminal.name,
            )
        )

        return when (response) {
            is AppResult.Success -> {
                val session = response.value.toSession(terminal)
                sessionManager.set(session)
                AppResult.Success(session)
            }
            is AppResult.Failure -> response
        }
    }

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> {
        val current = sessionManager.current()
        val refreshToken = refreshTokenOverride ?: current?.tokens?.refreshToken
            ?: return AppResult.Failure(AppError.Unauthorized)

        return when (val response = authApi.refresh(RefreshRequestDto(refreshToken))) {
            is AppResult.Success -> {
                val terminal = current?.terminal ?: TerminalCode.POS1
                val updated = response.value.toSession(terminal)
                sessionManager.set(updated)
                AppResult.Success(updated)
            }
            is AppResult.Failure -> {
                if (response.error is AppError.Unauthorized) {
                    sessionManager.clear()
                }
                response
            }
        }
    }

    override suspend fun restoreSession(): AppResult<UserSession?> {
        val restored = sessionManager.restore() ?: return AppResult.Success(null)
        return when (val refreshed = refresh(restored.tokens.refreshToken)) {
            is AppResult.Success -> AppResult.Success(refreshed.value)
            is AppResult.Failure -> {
                if (refreshed.error is AppError.Unauthorized) {
                    AppResult.Success(null)
                } else {
                    AppResult.Failure(refreshed.error)
                }
            }
        }
    }

    override suspend fun logout(): AppResult<Unit> {
        val current = sessionManager.current()
        val refreshToken = current?.tokens?.refreshToken

        if (refreshToken != null) {
            authApi.logout(
                request = LogoutRequestDto(refreshToken),
                accessToken = current.tokens.accessToken,
            )
        }

        sessionManager.clear()
        return AppResult.Success(Unit)
    }

    private fun AuthResponseDto.toSession(terminal: TerminalCode): UserSession {
        val roles = user.roles.mapNotNull(UserRole::fromRaw).toSet()
        return UserSession(
            tokens = AuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
            ),
            user = AuthUser(
                id = user.id,
                username = user.username,
                roles = roles,
            ),
            terminal = terminal,
        )
    }
}
