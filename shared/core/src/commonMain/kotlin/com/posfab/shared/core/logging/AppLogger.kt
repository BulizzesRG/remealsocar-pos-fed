package com.posfab.shared.core.logging

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

enum class LogLevel { DEBUG, INFO, WARN, ERROR, NONE }

object AppLogger {
    fun init(isDebug: Boolean) {
        if (isDebug) Napier.base(DebugAntilog())
    }

    fun d(message: String) = Napier.d(message)
    fun i(message: String) = Napier.i(message)
    fun w(message: String) = Napier.w(message)
    fun e(message: String, throwable: Throwable? = null) = Napier.e(throwable = throwable, message = message)
}
