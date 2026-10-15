package com.posfab.shared.features.login

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.usecase.LoginUseCase
import com.posfab.shared.auth.usecase.LogoutUseCase
import com.posfab.shared.core.model.DeviceRegistration
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val deviceRegistration = DeviceRegistration(
        deviceToken = "test-token",
        terminalId = 1L,
        terminalCode = "POS1",
        terminalType = "POS",
        businessUnitId = 2L,
    )

    private val testSession = UserSession(
        tokens = AuthTokens(accessToken = "access", refreshToken = "refresh"),
        user = AuthUser(id = "1", username = "admin", roles = setOf(UserRole.ADMIN), permissions = Permission.entries.toSet()),
        terminalCode = "POS1",
        terminalType = "POS",
    )

    @Test
    fun init_sets_terminalCode_from_deviceRegistration() = runTest(dispatcher) {
        val vm = makeViewModel(AppResult.Success(testSession))
        assertEquals("POS1", vm.state.value.terminalCode)
    }

    @Test
    fun submit_with_empty_fields_sets_error_to_ERROR_EMPTY_CREDS() = runTest(dispatcher) {
        val vm = makeViewModel(AppResult.Success(testSession))

        vm.submit()
        advanceUntilIdle()

        assertEquals(LoginStrings.ERROR_EMPTY_CREDS, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun submit_success_emits_loggedIn_with_UserSession() = runTest(dispatcher) {
        val vm = makeViewModel(AppResult.Success(testSession))

        var emittedSession: UserSession? = null
        val collectJob = launch {
            emittedSession = vm.loggedIn.first()
        }

        vm.onUsernameChange("admin")
        vm.onPasswordChange("pass123")
        vm.submit()
        advanceUntilIdle()

        assertEquals(testSession, emittedSession)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
        collectJob.cancel()
    }

    @Test
    fun submit_with_unauthorized_error_shows_correct_message() = runTest(dispatcher) {
        val vm = makeViewModel(AppResult.Failure(AppError.Unauthorized))

        vm.onUsernameChange("admin")
        vm.onPasswordChange("wrong")
        vm.submit()
        advanceUntilIdle()

        assertEquals(LoginErrorText.from(AppError.Unauthorized), vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun submit_with_network_error_shows_correct_message() = runTest(dispatcher) {
        val networkError = AppError.Network("Connection refused")
        val vm = makeViewModel(AppResult.Failure(networkError))

        vm.onUsernameChange("admin")
        vm.onPasswordChange("pass123")
        vm.submit()
        advanceUntilIdle()

        assertEquals(LoginErrorText.from(networkError), vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun submit_without_caja_access_logs_out_and_does_not_emit() = runTest(dispatcher) {
        val stockerSession = testSession.copy(
            user = AuthUser(
                id = "9",
                username = "empleado",
                roles = setOf(UserRole.STOCKER),
                permissions = setOf(Permission.VIEW_PRODUCTS, Permission.COUNT_STOCK),
            ),
        )
        val repository = FakeAuthRepository(AppResult.Success(stockerSession))
        val vm = makeViewModel(repository)

        var emitted = false
        val collectJob = launch {
            vm.loggedIn.first()
            emitted = true
        }

        vm.onUsernameChange("empleado")
        vm.onPasswordChange("empleado123")
        vm.submit()
        advanceUntilIdle()

        assertEquals(LoginStrings.ERROR_NO_CAJA_ACCESS, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
        assertFalse(emitted)
        assertEquals(1, repository.logoutCalls)
        collectJob.cancel()
    }

    @Test
    fun submit_with_caja_access_does_not_log_out() = runTest(dispatcher) {
        val repository = FakeAuthRepository(AppResult.Success(testSession))
        val vm = makeViewModel(repository)

        vm.onUsernameChange("admin")
        vm.onPasswordChange("pass123")
        vm.submit()
        advanceUntilIdle()

        assertEquals(0, repository.logoutCalls)
        assertTrue(vm.state.value.error == null)
    }

    private fun makeViewModel(loginResult: AppResult<UserSession>) =
        makeViewModel(FakeAuthRepository(loginResult))

    private fun makeViewModel(repository: FakeAuthRepository) = LoginViewModel(
        loginUseCase = LoginUseCase(repository),
        logoutUseCase = LogoutUseCase(repository),
        deviceRegistration = deviceRegistration,
        dispatcher = dispatcher,
    )
}

private class FakeAuthRepository(
    private val loginResult: AppResult<UserSession>,
) : AuthRepository {
    var logoutCalls = 0
        private set

    override suspend fun login(
        username: String,
        password: String,
        terminalCode: String,
    ): AppResult<UserSession> = loginResult

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("not implemented"))

    override suspend fun restoreSession(): AppResult<UserSession?> =
        AppResult.Failure(AppError.Unknown("not implemented"))

    override suspend fun logout(): AppResult<Unit> {
        logoutCalls++
        return AppResult.Success(Unit)
    }
}
