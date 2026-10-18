package com.posfab.app

import com.posfab.shared.core.diagnostics.CrashRecord
import com.posfab.shared.core.diagnostics.CrashSink
import com.posfab.shared.core.diagnostics.format
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class FileCrashSink(
    private val file: Path,
    private val maxBytes: Long = MAX_BYTES,
) : CrashSink {
    private val lock = Any()

    override fun append(record: CrashRecord) {
        synchronized(lock) {
            runCatching {
                file.parent?.let { Files.createDirectories(it) }
                Files.writeString(
                    file,
                    record.format(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                )
                if (Files.size(file) > maxBytes) trim()
            }
        }
    }

    override fun recent(limit: Int): List<CrashRecord> {
        if (limit <= 0) return emptyList()
        return synchronized(lock) {
            runCatching {
                if (!Files.exists(file)) emptyList() else parse(file.toFile().readText()).takeLast(limit)
            }.getOrDefault(emptyList())
        }
    }

    private fun trim() {
        val text = file.toFile().readText()
        val tail = text.substring(text.length / 2)
        val marker = "\n${CrashRecord.SEPARATOR}\n"
        val cut = tail.indexOf(marker)
        val afterCut = if (cut < 0) "" else tail.substring(cut + marker.length)
        file.toFile().writeText(afterCut.ifEmpty { lastRecord(text, marker) })
    }

    private fun lastRecord(text: String, marker: String): String {
        val searchFrom = text.length - marker.length - 1
        val previous = if (searchFrom < 0) -1 else text.lastIndexOf(marker, searchFrom)
        return if (previous < 0) text else text.substring(previous + marker.length)
    }

    private fun parse(text: String): List<CrashRecord> {
        val records = mutableListOf<CrashRecord>()
        val block = mutableListOf<String>()
        text.lineSequence().forEach { line ->
            if (line.trim() == CrashRecord.SEPARATOR) {
                parseBlock(block)?.let { records += it }
                block.clear()
            } else {
                block += line
            }
        }
        return records
    }

    private fun parseBlock(lines: List<String>): CrashRecord? {
        val fields = mutableMapOf<String, String>()
        val stack = mutableListOf<String>()
        lines.forEach { line ->
            val key = line.substringBefore('=', missingDelimiterValue = "")
            if (key in FIELD_KEYS && key !in fields) {
                fields[key] = line.substringAfter('=')
            } else if (line.isNotBlank()) {
                stack += line
            }
        }
        val at = fields[CrashRecord.KEY_AT] ?: return null
        val type = fields[CrashRecord.KEY_TYPE] ?: return null
        return CrashRecord(
            at = at,
            version = fields[CrashRecord.KEY_VERSION].orEmpty(),
            screen = fields[CrashRecord.KEY_SCREEN].orEmpty(),
            type = type,
            message = fields[CrashRecord.KEY_MESSAGE].orEmpty(),
            stack = stack,
        )
    }

    companion object {
        const val MAX_BYTES: Long = 256L * 1024L
        private val FIELD_KEYS = setOf(
            CrashRecord.KEY_AT,
            CrashRecord.KEY_VERSION,
            CrashRecord.KEY_SCREEN,
            CrashRecord.KEY_TYPE,
            CrashRecord.KEY_MESSAGE,
        )
    }
}
