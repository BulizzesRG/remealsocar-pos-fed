package com.posfab.app

import com.posfab.shared.core.diagnostics.CrashRecord
import com.posfab.shared.core.diagnostics.format
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileCrashSinkTest {
    private val dir: Path = Files.createTempDirectory("crash-sink-test")
    private val file: Path = dir.resolve("nested").resolve("errores.log")

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private fun record(index: Int, stackLines: Int = 3): CrashRecord = CrashRecord(
        at = "2026-10-17T17:0$index:00Z",
        version = "dev",
        screen = "Vender",
        type = "IllegalStateException",
        message = "error $index",
        stack = List(stackLines) { "\tat com.posfab.Frame$it.run(Frame.kt:$it)" },
    )

    @Test
    fun append_creates_directory_and_file() {
        FileCrashSink(file).append(record(1))

        assertTrue(file.exists())
        assertTrue(file.readText().contains("message=error 1"))
    }

    @Test
    fun recent_returns_the_last_records_in_order() {
        val sink = FileCrashSink(file)
        (1..3).forEach { sink.append(record(it)) }

        val recent = sink.recent(2)

        assertEquals(listOf(record(2), record(3)), recent)
    }

    @Test
    fun trimming_keeps_file_under_limit_and_starting_at_a_complete_record() {
        val sink = FileCrashSink(file)
        repeat(600) { sink.append(record(it % 10, stackLines = 15)) }

        assertTrue(Files.size(file) <= FileCrashSink.MAX_BYTES)
        assertTrue(file.readText().startsWith("at="))
        val recent = sink.recent(1000)
        assertTrue(recent.isNotEmpty())
        assertTrue(recent.all { it.stack.size == 15 })
        assertEquals(record(9, stackLines = 15), recent.last())
    }

    @Test
    fun corrupt_file_returns_empty_list_without_throwing() {
        Files.createDirectories(file.parent)
        file.writeText("garbage\n----\nmore garbage without keys\n----\n")

        assertEquals(emptyList(), FileCrashSink(file).recent(5))

        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x2D))
        assertEquals(emptyList(), FileCrashSink(file).recent(5))
    }

    @Test
    fun missing_file_returns_empty_list() {
        assertEquals(emptyList(), FileCrashSink(file).recent(5))
    }

    @Test
    fun record_larger_than_half_the_limit_never_leaves_an_empty_file() {
        val sink = FileCrashSink(file, maxBytes = 1_000)
        val big = { index: Int -> record(index, stackLines = 15) }
        assertTrue(big(1).format().length > 500)

        sink.append(big(1))
        sink.append(big(2))

        assertTrue(file.readText().startsWith("at="))
        assertEquals(listOf(big(2)), sink.recent(5))

        sink.append(big(3))

        assertEquals(listOf(big(3)), sink.recent(5))
    }
}
