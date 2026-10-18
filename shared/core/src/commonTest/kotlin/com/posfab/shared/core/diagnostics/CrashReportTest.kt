package com.posfab.shared.core.diagnostics

import com.posfab.shared.core.logging.sanitizeForSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CrashReportTest {
    private val now = Instant.parse("2026-10-17T17:05:00Z")

    private fun deepThrowable(depth: Int, message: String): Throwable =
        if (depth == 0) IllegalStateException(message) else deepThrowable(depth - 1, message)

    @Test
    fun from_takes_type_message_screen_version_and_time() {
        val record = CrashRecord.from(IllegalStateException("boom"), "1.2.3", "Vender", now)

        assertEquals("2026-10-17T17:05:00Z", record.at)
        assertEquals("1.2.3", record.version)
        assertEquals("Vender", record.screen)
        assertEquals("IllegalStateException", record.type)
        assertEquals("boom", record.message)
    }

    @Test
    fun stack_is_cut_to_fifteen_lines() {
        val record = CrashRecord.from(deepThrowable(40, "deep"), "dev", "Caja", now)

        assertEquals(CrashRecord.MAX_STACK_LINES, record.stack.size)
        assertTrue(record.stack.first().contains("IllegalStateException"))
    }

    @Test
    fun bearer_tokens_are_redacted_from_message_and_stack() {
        val record = CrashRecord.from(IllegalStateException("Bearer abc.def-123 failed"), "dev", "Vender", now)

        assertEquals("Bearer <redacted> failed", record.message)
        assertFalse(record.stack.any { it.contains("abc.def-123") })
        assertFalse(record.format().contains("abc.def-123"))
    }

    @Test
    fun format_writes_one_line_per_field_then_stack_then_separator() {
        val record = CrashRecord(
            at = "2026-10-17T17:05:00Z",
            version = "dev",
            screen = "Vender",
            type = "IllegalStateException",
            message = "line one\nline two",
            stack = listOf("java.lang.IllegalStateException: line one", "\tat A.b(A.kt:1)"),
        )

        val lines = record.format().lines()

        assertEquals("at=2026-10-17T17:05:00Z", lines[0])
        assertEquals("version=dev", lines[1])
        assertEquals("screen=Vender", lines[2])
        assertEquals("type=IllegalStateException", lines[3])
        assertEquals("message=line one line two", lines[4])
        assertEquals("java.lang.IllegalStateException: line one", lines[5])
        assertEquals("\tat A.b(A.kt:1)", lines[6])
        assertEquals(CrashRecord.SEPARATOR, lines[7])
        assertEquals("", lines[8])
    }

    @Test
    fun long_message_and_stack_lines_are_capped() {
        val record = CrashRecord.from(IllegalStateException("x".repeat(2_000)), "dev", "Vender", now)

        assertEquals(CrashRecord.MAX_MESSAGE_CHARS, record.message.length)
        assertTrue(record.stack.all { it.length <= CrashRecord.MAX_STACK_LINE_CHARS })
    }

    @Test
    fun json_and_query_secrets_are_redacted() {
        val json = sanitizeForSupport("""{"password":"x","refreshToken":"y","name":"Lupe"}""")
        val query = sanitizeForSupport("login?user=ana&access_token=abc123&secret=s3")
        val plain = sanitizeForSupport("password=hunter2 token: zzz")

        assertEquals("""{"password":"<redacted>","refreshToken":"<redacted>","name":"Lupe"}""", json)
        assertEquals("login?user=ana&access_token=<redacted>&secret=<redacted>", query)
        assertEquals("password=<redacted> token: <redacted>", plain)
    }
}
