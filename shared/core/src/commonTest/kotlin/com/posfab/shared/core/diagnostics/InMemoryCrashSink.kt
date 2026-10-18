package com.posfab.shared.core.diagnostics

class InMemoryCrashSink : CrashSink {
    val records = mutableListOf<CrashRecord>()

    override fun append(record: CrashRecord) {
        records += record
    }

    override fun recent(limit: Int): List<CrashRecord> = records.takeLast(limit)
}

class FailingCrashSink : CrashSink {
    override fun append(record: CrashRecord) {
        throw IllegalStateException("disk full")
    }

    override fun recent(limit: Int): List<CrashRecord> = throw IllegalStateException("disk full")
}
