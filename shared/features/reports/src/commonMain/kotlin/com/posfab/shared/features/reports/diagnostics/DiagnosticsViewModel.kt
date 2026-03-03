package com.posfab.shared.features.reports.diagnostics

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.diagnostics.AppBuildInfo
import com.posfab.shared.core.diagnostics.NetworkHealthTracker
import com.posfab.shared.core.logging.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    private val session: UserSession,
    private val config: PosConfig,
    private val buildInfo: AppBuildInfo,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(
        DiagnosticsState(
            appVersion = buildInfo.version,
            appBuild = buildInfo.build,
            environment = config.env.name.lowercase(),
            apiBaseUrl = config.apiBaseUrl,
            terminal = session.terminal.name,
            username = session.user.username,
            roles = session.user.roles.joinToString(",") { it.name },
        )
    )
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init {
        scope.launch {
            NetworkHealthTracker.state.collect { network ->
                _state.value = _state.value.copy(
                    lastSyncAt = network.lastSuccessfulRequestAt,
                    lastFailureAt = network.lastFailureAt,
                    offline = network.isOffline,
                )
            }
        }
    }

    fun buildSupportBundle() {
        val snapshot = _state.value
        val logs = AppLogger.recentSupportEntries(limit = 200).joinToString("\n")
        val bundle = buildString {
            appendLine("version=${snapshot.appVersion}")
            appendLine("build=${snapshot.appBuild}")
            appendLine("env=${snapshot.environment}")
            appendLine("api_base_url=${snapshot.apiBaseUrl}")
            appendLine("terminal=${snapshot.terminal}")
            appendLine("user=${snapshot.username}")
            appendLine("roles=${snapshot.roles}")
            appendLine("last_sync=${snapshot.lastSyncAt ?: "-"}")
            appendLine("last_failure=${snapshot.lastFailureAt ?: "-"}")
            appendLine("offline=${snapshot.offline}")
            appendLine("--- recent_logs ---")
            append(logs)
        }
        _state.value = snapshot.copy(
            supportBundleText = bundle,
            notice = "Support bundle preparado (${bundle.length} chars)",
        )
    }
}
