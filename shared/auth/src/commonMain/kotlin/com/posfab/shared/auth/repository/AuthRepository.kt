package com.posfab.shared.auth.repository

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.auth.AuthApi
import com.posfab.shared.network.auth.AuthResponseDto
import com.posfab.shared.network.auth.LoginRequestDto
import com.posfab.shared.network.auth.LogoutRequestDto
import com.posfab.shared.network.auth.RefreshRequestDto

interface AuthRepository {
    suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession>
    suspend fun refresh(refreshTokenOverride: String? = null): AppResult<UserSession>
    suspend fun restoreSession(): AppResult<UserSession?>
    suspend fun logout(): AppResult<Unit>
}

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
) : AuthRepository {
    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> {
        val response = authApi.login(
            LoginRequestDto(
                username = username,
                password = password,
                terminalCode = terminalCode,
            )
        )

        return when (response) {
            is AppResult.Success -> {
                val session = response.value.toSession()
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
                val updated = current?.copy(
                    tokens = AuthTokens(
                        accessToken = response.value.accessToken,
                        refreshToken = response.value.refreshToken,
                    )
                ) ?: return AppResult.Failure(AppError.Unauthorized)
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

    private fun AuthResponseDto.toSession(): UserSession {
        return UserSession(
            tokens = AuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
            ),
            user = AuthUser(
                id = user.id.toString(),
                username = user.fullName,
                roles = setOfNotNull(UserRole.fromRaw(user.role)),
            ),
            terminalCode = terminal.code,
            terminalType = if (terminal.code.equals("ADMIN", ignoreCase = true)) "ADMIN" else "POS",
        )
    }
}
