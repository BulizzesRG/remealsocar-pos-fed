package com.posfab.shared.auth.usecase

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.core.result.AppResult

class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(username: String, password: String, terminalCode: TerminalCode): AppResult<UserSession> {
        return authRepository.login(username, password, terminalCode)
    }
}

class RestoreSessionUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): AppResult<UserSession?> = authRepository.restoreSession()
}

class LogoutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): AppResult<Unit> = authRepository.logout()
}
