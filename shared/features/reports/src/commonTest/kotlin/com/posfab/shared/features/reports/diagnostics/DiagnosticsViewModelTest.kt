package com.posfab.shared.features.reports.diagnostics

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.config.PosConfig
import com.posfab.shared.config.PosEnv
import com.posfab.shared.core.diagnostics.AppBuildInfo
import com.posfab.shared.core.diagnostics.CrashReporter
import com.posfab.shared.core.diagnostics.SupportBundle
import com.posfab.shared.core.logging.LogLevel
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.model.UserRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val session = UserSession(
        tokens = AuthTokens("access", "refresh"),
        user = AuthUser("1", "gerente", setOf(UserRole.MANAGER), setOf(Permission.VIEW_PRODUCTS)),
        terminalCode = "POS1",
    )

    private val config = PosConfig(
        env = PosEnv.DEV,
        apiBaseUrl = "http://localhost:8080",
        logLevel = LogLevel.DEBUG,
        requestTimeoutMs = 15_000L,
        enableVerboseLogs = false,
    )

    private fun viewModel() = DiagnosticsViewModel(session, config, AppBuildInfo("1.0.0", "42"), dispatcher)

    @Test
    fun support_bundle_keeps_header_and_includes_recent_errors() = runTest(dispatcher) {
        CrashReporter.install(null, "1.0.0")
        CrashReporter.report(IllegalStateException("diagnostics-bundle"), screen = "Vender")
        val vm = viewModel()
        advanceUntilIdle()

        val bundle = vm.buildSupportBundle()

        listOf(
            "version=1.0.0", "build=42", "env=dev", "api_base_url=http://localhost:8080",
            "terminal=POS1", "user=gerente", "roles=MANAGER", "last_sync=", "last_failure=", "offline=",
        ).forEach { assertTrue(bundle.contains(it), "missing $it") }
        assertTrue(bundle.contains(SupportBundle.ERRORS_SECTION))
        assertTrue(bundle.contains("type=IllegalStateException"))
        assertTrue(bundle.contains("message=diagnostics-bundle"))
        assertTrue(bundle.contains(SupportBundle.LOGS_SECTION))
        assertTrue(bundle.indexOf(SupportBundle.ERRORS_SECTION) < bundle.indexOf(SupportBundle.LOGS_SECTION))
        assertEquals(DiagnosticsStrings.COPIED, vm.state.value.notice)
        vm.clear()
    }

    @Test
    fun last_crash_at_follows_new_reports() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val record = CrashReporter.report(IllegalArgumentException("diagnostics-follow"), screen = "Caja")
        advanceUntilIdle()

        assertEquals(record.at, vm.state.value.lastCrashAt)
        vm.clear()
    }

    @Test
    fun starts_with_the_most_recent_known_crash() = runTest(dispatcher) {
        val record = CrashReporter.report(IllegalStateException("diagnostics-start"), screen = "Fiado")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(record.at, vm.state.value.lastCrashAt)
        vm.clear()
    }
}
