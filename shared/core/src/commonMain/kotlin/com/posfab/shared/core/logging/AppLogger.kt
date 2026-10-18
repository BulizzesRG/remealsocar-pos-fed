package com.posfab.shared.core.logging

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

enum class LogLevel { DEBUG, INFO, WARN, ERROR, NONE }

object AppLogger {
    private const val MAX_BUFFER_SIZE = 500
    private val supportLogBuffer = mutableListOf<String>()

    fun init(isDebug: Boolean) {
        if (isDebug) Napier.base(DebugAntilog())
    }

    fun d(message: String) {
        Napier.d(message)
        addSupportEntry("DEBUG", message)
    }

    fun i(message: String) {
        Napier.i(message)
        addSupportEntry("INFO", message)
    }

    fun w(message: String) {
        Napier.w(message)
        addSupportEntry("WARN", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Napier.e(throwable = throwable, message = message)
        addSupportEntry("ERROR", message)
    }

    fun recentSupportEntries(limit: Int = 200): List<String> = synchronized(supportLogBuffer) {
        supportLogBuffer.takeLast(limit)
    }

    private fun addSupportEntry(level: String, message: String) {
        val sanitized = sanitizeForSupport(message)
        synchronized(supportLogBuffer) {
            supportLogBuffer += "[$level] $sanitized"
            if (supportLogBuffer.size > MAX_BUFFER_SIZE) {
                supportLogBuffer.removeAt(0)
            }
        }
    }
}

private val BEARER_PATTERN = Regex("Bearer\\s+[A-Za-z0-9._-]+")
private val SECRET_VALUE_PATTERN =
    Regex("(?i)(\"?(password|refresh_?token|access_?token|token|secret)\"?\\s*[:=]\\s*\"?)[^\"\\s,}&]+")

fun sanitizeForSupport(text: String): String = text
    .replace(BEARER_PATTERN, "Bearer <redacted>")
    .replace(SECRET_VALUE_PATTERN, "\$1<redacted>")
