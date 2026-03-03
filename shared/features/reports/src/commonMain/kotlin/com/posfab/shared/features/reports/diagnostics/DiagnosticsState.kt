package com.posfab.shared.features.reports.diagnostics

data class DiagnosticsState(
    val appVersion: String,
    val appBuild: String,
    val environment: String,
    val apiBaseUrl: String,
    val terminal: String,
    val username: String,
    val roles: String,
    val lastSyncAt: String? = null,
    val lastFailureAt: String? = null,
    val offline: Boolean = false,
    val supportBundleText: String = "",
    val notice: String? = null,
)
