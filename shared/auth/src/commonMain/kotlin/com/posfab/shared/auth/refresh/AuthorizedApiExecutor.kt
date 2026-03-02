package com.posfab.shared.auth.refresh

import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthorizedApiExecutor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) {
    private val refreshMutex = Mutex()

    suspend fun <T> execute(block: suspend (accessToken: String) -> AppResult<T>): AppResult<T> {
        val session = sessionManager.current() ?: return AppResult.Failure(AppError.Unauthorized)
        val failedToken = session.tokens.accessToken

        val firstResult = block(failedToken)
        if (firstResult !is AppResult.Failure || firstResult.error !is AppError.Unauthorized) {
            return firstResult
        }

        val refreshed = refreshIfNeeded(failedToken)
        if (refreshed is AppResult.Failure) return refreshed

        val freshToken = sessionManager.current()?.tokens?.accessToken ?: return AppResult.Failure(AppError.Unauthorized)
        return block(freshToken)
    }

    private suspend fun refreshIfNeeded(failedToken: String): AppResult<Unit> = refreshMutex.withLock {
        val currentToken = sessionManager.current()?.tokens?.accessToken ?: return@withLock AppResult.Failure(AppError.Unauthorized)
        if (currentToken != failedToken) {
            return@withLock AppResult.Success(Unit)
        }

        return@withLock when (val refreshed = authRepository.refresh()) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> refreshed
        }
    }
}
