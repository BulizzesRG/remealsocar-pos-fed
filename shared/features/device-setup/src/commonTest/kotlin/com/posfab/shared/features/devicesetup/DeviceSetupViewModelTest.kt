package com.posfab.shared.features.devicesetup

import com.posfab.shared.core.device.DeviceStorage
import com.posfab.shared.core.model.DeviceRegistration
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.device.DeviceApi
import com.posfab.shared.network.device.DeviceRegisterRequestDto
import com.posfab.shared.network.device.DeviceRegisterResponseDto
import com.posfab.shared.network.device.DeviceTerminalInfoDto
import com.posfab.shared.network.device.DeviceVerifyRequestDto
import com.posfab.shared.network.device.DeviceVerifyResponseDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val successResponse = DeviceRegisterResponseDto(
        deviceToken = "token-abc-123",
        terminal = DeviceTerminalInfoDto(
            id = 10L,
            code = "POS1",
            terminalType = "POS",
            businessUnitId = 5L,
        ),
    )

    @Test
    fun submit_with_empty_creds_sets_error_no_api_call() = runTest(dispatcher) {
        val api = FakeDeviceApi()
        val vm = makeViewModel(api)

        vm.onTerminalSelect("POS1")
        vm.submit()
        advanceUntilIdle()

        assertEquals(DeviceSetupStrings.ERROR_EMPTY_CREDS, vm.state.value.error)
        assertFalse(vm.state.value.isConfirmingRegistration)
        assertEquals(0, api.registerCallCount)
    }

    @Test
    fun submit_with_valid_fields_sets_confirming_no_api_call() = runTest(dispatcher) {
        val api = FakeDeviceApi()
        val vm = makeViewModel(api)

        fillValidFields(vm)
        vm.submit()
        advanceUntilIdle()

        assertTrue(vm.state.value.isConfirmingRegistration)
        assertNull(vm.state.value.error)
        assertEquals(0, api.registerCallCount)
    }

    @Test
    fun edit_registration_returns_to_form_with_fields_preserved() = runTest(dispatcher) {
        val api = FakeDeviceApi()
        val vm = makeViewModel(api)

        fillValidFields(vm)
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.state.value.isConfirmingRegistration)

        vm.editRegistration()
        advanceUntilIdle()

        assertFalse(vm.state.value.isConfirmingRegistration)
        assertEquals("admin", vm.state.value.adminUsername)
        assertEquals("secret123", vm.state.value.adminPassword)
        assertEquals("POS1", vm.state.value.selectedTerminalCode)
    }

    @Test
    fun confirm_and_register_success_sets_registered_device() = runTest(dispatcher) {
        val api = FakeDeviceApi(registerResult = AppResult.Success(successResponse))
        val vm = makeViewModel(api)

        fillValidFields(vm)
        vm.submit()
        advanceUntilIdle()

        val emissions = mutableListOf<DeviceRegistration>()
        val collectJob = launch { vm.registrationComplete.collect { emissions.add(it) } }

        vm.confirmAndRegister()
        advanceUntilIdle()

        val device = vm.state.value.registeredDevice
        assertNotNull(device)
        assertEquals("token-abc-123", device.deviceToken)
        assertEquals(10L, device.terminalId)
        assertEquals("POS1", device.terminalCode)
        assertEquals("POS", device.terminalType)
        assertEquals(5L, device.businessUnitId)
        assertTrue(emissions.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun confirm_and_register_success_saves_to_storage() = runTest(dispatcher) {
        val api = FakeDeviceApi(registerResult = AppResult.Success(successResponse))
        val storage = FakeDeviceStorage()
        val vm = makeViewModel(api, storage)

        fillValidFields(vm)
        vm.submit()
        advanceUntilIdle()

        vm.confirmAndRegister()
        advanceUntilIdle()

        val saved = storage.savedRegistration
        assertNotNull(saved)
        assertEquals("token-abc-123", saved.deviceToken)
        assertEquals(10L, saved.terminalId)
        assertEquals("POS1", saved.terminalCode)
        assertEquals(5L, saved.businessUnitId)
    }

    @Test
    fun confirm_and_register_success_clears_admin_password() = runTest(dispatcher) {
        val api = FakeDeviceApi(registerResult = AppResult.Success(successResponse))
        val vm = makeViewModel(api)

        fillValidFields(vm)
        vm.submit()
        advanceUntilIdle()

        vm.confirmAndRegister()
        advanceUntilIdle()

        assertEquals("", vm.state.value.adminPassword)
    }

    @Test
    fun confirm_and_continue_after_success_emits_registration_complete() = runTest(dispatcher) {
        val api = FakeDeviceApi(registerResult = AppResult.Success(successResponse))
        val vm = makeViewModel(api)

        fillValidFields(vm)
        vm.submit()
        advanceUntilIdle()
        vm.confirmAndRegister()
        advanceUntilIdle()

        val emissions = mutableListOf<DeviceRegistration>()
        val collectJob = launch { vm.registrationComplete.collect { emissions.add(it) } }

        vm.confirmAndContinue()
        advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertEquals("token-abc-123", emissions.first().deviceToken)
        assertEquals(10L, emissions.first().terminalId)

        collectJob.cancel()
    }

    @Test
    fun confirm_and_continue_without_registration_does_not_emit() = runTest(dispatcher) {
        val api = FakeDeviceApi()
        val vm = makeViewModel(api)

        val emissions = mutableListOf<DeviceRegistration>()
        val collectJob = launch { vm.registrationComplete.collect { emissions.add(it) } }

        vm.confirmAndContinue()
        advanceUntilIdle()

        assertTrue(emissions.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun confirm_and_register_unauthorized_shows_error() = runTest(dispatcher) {
        val api = FakeDeviceApi(registerResult = AppResult.Failure(AppError.Unauthorized))
        val vm = makeViewModel(api)

        fillValidFields(vm)
        vm.submit()
        advanceUntilIdle()

        vm.confirmAndRegister()
        advanceUntilIdle()

        assertEquals(DeviceSetupErrorText.from(AppError.Unauthorized), vm.state.value.error)
        assertNull(vm.state.value.registeredDevice)
        assertFalse(vm.state.value.isRegistering)
    }

    @Test
    fun confirm_and_register_conflict_shows_terminal_already_registered() = runTest(dispatcher) {
        val api = FakeDeviceApi(registerResult = AppResult.Failure(AppError.Conflict))
        val vm = makeViewModel(api)

        fillValidFields(vm)
        vm.submit()
        advanceUntilIdle()

        vm.confirmAndRegister()
        advanceUntilIdle()

        assertEquals(DeviceSetupErrorText.from(AppError.Conflict), vm.state.value.error)
        assertNull(vm.state.value.registeredDevice)
        assertFalse(vm.state.value.isRegistering)
    }

    @Test
    fun terminal_options_are_both_cajas_and_cocina() {
        assertEquals(listOf("POS1", "POS2", "COCINA"), DeviceSetupState().terminalOptions)
    }

    @Test
    fun submit_without_terminal_asks_to_choose_an_equipo() = runTest(dispatcher) {
        val vm = makeViewModel()

        vm.onAdminUsernameChange("admin")
        vm.onAdminPasswordChange("secret123")
        vm.submit()
        advanceUntilIdle()

        assertEquals("Elige un equipo", vm.state.value.error)
        assertFalse(vm.state.value.isConfirmingRegistration)
    }

    @Test
    fun registering_cocina_stores_the_terminal_the_backend_returns() = runTest(dispatcher) {
        val cocinaResponse = DeviceRegisterResponseDto(
            deviceToken = "token-cocina",
            terminal = DeviceTerminalInfoDto(
                id = 4L,
                code = "COCINA",
                terminalType = "POS",
                businessUnitId = 2L,
            ),
        )
        val api = FakeDeviceApi(registerResult = AppResult.Success(cocinaResponse))
        val storage = FakeDeviceStorage()
        val vm = makeViewModel(api, storage)

        vm.onAdminUsernameChange("admin")
        vm.onAdminPasswordChange("secret123")
        vm.onTerminalSelect("COCINA")
        vm.submit()
        advanceUntilIdle()
        vm.confirmAndRegister()
        advanceUntilIdle()

        assertEquals("COCINA", api.lastRequest?.terminalCode)
        val saved = assertNotNull(storage.savedRegistration)
        assertEquals("token-cocina", saved.deviceToken)
        assertEquals(4L, saved.terminalId)
        assertEquals("COCINA", saved.terminalCode)
        assertEquals("POS", saved.terminalType)
        assertEquals(2L, saved.businessUnitId)
        assertEquals(saved, vm.state.value.registeredDevice)
    }

    private fun fillValidFields(vm: DeviceSetupViewModel) {
        vm.onAdminUsernameChange("admin")
        vm.onAdminPasswordChange("secret123")
        vm.onTerminalSelect("POS1")
    }

    private fun makeViewModel(
        api: DeviceApi = FakeDeviceApi(),
        storage: DeviceStorage = FakeDeviceStorage(),
    ) = DeviceSetupViewModel(
        deviceApi = api,
        deviceStorage = storage,
        dispatcher = dispatcher,
    )
}

private class FakeDeviceApi(
    private val registerResult: AppResult<DeviceRegisterResponseDto> =
        AppResult.Failure(AppError.Unknown("not configured")),
) : DeviceApi {
    var registerCallCount = 0
        private set
    var lastRequest: DeviceRegisterRequestDto? = null
        private set

    override suspend fun register(request: DeviceRegisterRequestDto): AppResult<DeviceRegisterResponseDto> {
        registerCallCount++
        lastRequest = request
        return registerResult
    }

    override suspend fun verify(request: DeviceVerifyRequestDto): AppResult<DeviceVerifyResponseDto> {
        error("verify should not be called in DeviceSetupViewModel tests")
    }
}

private class FakeDeviceStorage : DeviceStorage {
    var savedRegistration: DeviceRegistration? = null
        private set

    override suspend fun load(): DeviceRegistration? = savedRegistration

    override suspend fun save(registration: DeviceRegistration) {
        savedRegistration = registration
    }

    override suspend fun clear() {
        savedRegistration = null
    }
}
