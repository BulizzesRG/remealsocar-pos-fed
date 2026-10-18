package com.posfab.shared.core.diagnostics

object SupportBundle {
    const val ERRORS_SECTION = "--- recent_errors ---"
    const val LOGS_SECTION = "--- recent_logs ---"

    fun build(
        header: List<Pair<String, String>>,
        logs: List<String>,
        crashes: List<CrashRecord>,
    ): String = buildString {
        header.forEach { (key, value) -> appendLine("$key=$value") }
        appendLine(ERRORS_SECTION)
        crashes.forEach { append(it.format()) }
        appendLine(LOGS_SECTION)
        append(logs.joinToString("\n"))
    }
}
