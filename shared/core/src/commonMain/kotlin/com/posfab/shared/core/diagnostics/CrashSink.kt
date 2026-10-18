package com.posfab.shared.core.diagnostics

interface CrashSink {
    fun append(record: CrashRecord)

    fun recent(limit: Int): List<CrashRecord>
}
