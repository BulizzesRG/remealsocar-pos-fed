package com.posfab.shared.core.diagnostics

import com.posfab.shared.core.logging.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile
import kotlin.time.Clock

object CrashReporter {
    private const val DEFAULT_VERSION = "dev"

    private val _lastCrash = MutableStateFlow<CrashRecord?>(null)
    val lastCrash: StateFlow<CrashRecord?> = _lastCrash.asStateFlow()

    @Volatile
    private var sink: CrashSink? = null

    @Volatile
    private var version: String = DEFAULT_VERSION

    fun install(sink: CrashSink?, version: String) {
        this.sink = sink
        this.version = version
    }

    fun report(t: Throwable, screen: String = ScreenTracker.current): CrashRecord {
        val record = runCatching { CrashRecord.from(t, version, screen) }
            .getOrElse { fallbackRecord(t, screen) }
        runCatching { AppLogger.e("Error inesperado en $screen: ${record.type}: ${record.message}", t) }
        runCatching { sink?.append(record) }
        _lastCrash.value = record
        return record
    }

    private fun fallbackRecord(t: Throwable, screen: String): CrashRecord = CrashRecord(
        at = runCatching { Clock.System.now().toString() }.getOrDefault(""),
        version = version,
        screen = screen,
        type = t::class.simpleName ?: CrashRecord.UNKNOWN_TYPE,
        message = "",
        stack = emptyList(),
    )

    fun recent(limit: Int): List<CrashRecord> {
        if (limit <= 0) return emptyList()
        val fromSink = sink?.let { current -> runCatching { current.recent(limit) }.getOrNull() }
        return fromSink ?: listOfNotNull(_lastCrash.value)
    }
}
