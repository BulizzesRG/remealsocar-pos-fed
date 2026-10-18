package com.posfab.shared.core.diagnostics

import com.posfab.shared.core.logging.sanitizeForSupport
import kotlin.time.Clock
import kotlin.time.Instant

data class CrashRecord(
    val at: String,
    val version: String,
    val screen: String,
    val type: String,
    val message: String,
    val stack: List<String>,
) {
    companion object {
        const val SEPARATOR = "----"
        const val MAX_STACK_LINES = 15
        const val MAX_MESSAGE_CHARS = 500
        const val MAX_STACK_LINE_CHARS = 300
        const val KEY_AT = "at"
        const val KEY_VERSION = "version"
        const val KEY_SCREEN = "screen"
        const val KEY_TYPE = "type"
        const val KEY_MESSAGE = "message"
        const val UNKNOWN_TYPE = "Throwable"
    }
}

fun CrashRecord.format(): String = buildString {
    appendLine("${CrashRecord.KEY_AT}=${singleLine(at)}")
    appendLine("${CrashRecord.KEY_VERSION}=${singleLine(version)}")
    appendLine("${CrashRecord.KEY_SCREEN}=${singleLine(screen)}")
    appendLine("${CrashRecord.KEY_TYPE}=${singleLine(type)}")
    appendLine("${CrashRecord.KEY_MESSAGE}=${singleLine(message)}")
    stack.filter { it.trim() != CrashRecord.SEPARATOR }.forEach { appendLine(it) }
    appendLine(CrashRecord.SEPARATOR)
}

fun CrashRecord.Companion.from(
    throwable: Throwable,
    version: String,
    screen: String,
    now: Instant = Clock.System.now(),
): CrashRecord = CrashRecord(
    at = now.toString(),
    version = version,
    screen = screen,
    type = throwable::class.simpleName ?: CrashRecord.UNKNOWN_TYPE,
    message = sanitizeForSupport(throwable.message.orEmpty()).take(CrashRecord.MAX_MESSAGE_CHARS),
    stack = throwable.stackTraceToString()
        .lines()
        .filter { it.isNotBlank() }
        .take(CrashRecord.MAX_STACK_LINES)
        .map { sanitizeForSupport(it).take(CrashRecord.MAX_STACK_LINE_CHARS) },
)

private fun singleLine(text: String): String = text.replace('\r', ' ').replace('\n', ' ')
