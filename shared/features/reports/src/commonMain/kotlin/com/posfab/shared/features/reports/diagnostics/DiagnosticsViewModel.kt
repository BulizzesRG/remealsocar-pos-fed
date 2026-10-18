package com.posfab.shared.features.reports.diagnostics

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.diagnostics.AppBuildInfo
import com.posfab.shared.core.diagnostics.CrashReporter
import com.posfab.shared.core.diagnostics.NetworkHealthTracker
import com.posfab.shared.core.diagnostics.SupportBundle
import com.posfab.shared.core.logging.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
            terminal = session.terminalCode,
            username = session.user.username,
            roles = session.user.roles.joinToString(",") { it.name },
        )
    )
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init {
        scope.launch {
            NetworkHealthTracker.state.collect { network ->
                _state.update {
                    it.copy(
                        lastSyncAt = network.lastSuccessfulRequestAt,
                        lastFailureAt = network.lastFailureAt,
                        offline = network.isOffline,
                    )
                }
            }
        }
        scope.launch {
            val previous = CrashReporter.recent(1).lastOrNull()?.at
            if (previous != null) _state.update { if (it.lastCrashAt == null) it.copy(lastCrashAt = previous) else it }
            CrashReporter.lastCrash.collect { record ->
                if (record != null) _state.update { it.copy(lastCrashAt = record.at) }
            }
        }
    }

    fun buildSupportBundle(): String {
        val snapshot = _state.value
        val bundle = SupportBundle.build(
            header = listOf(
                "version" to snapshot.appVersion,
                "build" to snapshot.appBuild,
                "env" to snapshot.environment,
                "api_base_url" to snapshot.apiBaseUrl,
                "terminal" to snapshot.terminal,
                "user" to snapshot.username,
                "roles" to snapshot.roles,
                "last_sync" to (snapshot.lastSyncAt ?: "-"),
                "last_failure" to (snapshot.lastFailureAt ?: "-"),
                "offline" to snapshot.offline.toString(),
            ),
            logs = AppLogger.recentSupportEntries(limit = SUPPORT_LOG_LIMIT),
            crashes = CrashReporter.recent(SUPPORT_CRASH_LIMIT),
        )
        _state.update { it.copy(notice = DiagnosticsStrings.COPIED) }
        return bundle
    }

    private companion object {
        const val SUPPORT_LOG_LIMIT = 200
        const val SUPPORT_CRASH_LIMIT = 5
    }
}
