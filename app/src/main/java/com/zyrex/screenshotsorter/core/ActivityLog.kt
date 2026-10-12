package com.zyrex.screenshotsorter.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Outcome { MOVED, UNKNOWN_APP, FAILED }

/** [detail] is the folder for MOVED, the reason for FAILED, and empty for UNKNOWN_APP. */
data class LogEntry(val timeMillis: Long, val fileName: String, val outcome: Outcome, val detail: String)

/** One entry per line, fields separated by tabs. */
object LogCodec {
    fun encode(entries: List<LogEntry>): String = entries.joinToString("\n") { entry ->
        listOf(entry.timeMillis.toString(), oneLine(entry.fileName), entry.outcome.name, oneLine(entry.detail))
            .joinToString("\t")
    }

    fun decode(text: String): List<LogEntry> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 4) return@mapNotNull null
        val time = parts[0].toLongOrNull() ?: return@mapNotNull null
        val outcome = Outcome.entries.firstOrNull { it.name == parts[2] } ?: return@mapNotNull null
        LogEntry(time, parts[1], outcome, parts[3])
    }.toList()

    private fun oneLine(text: String): String = text.replace('\t', ' ').replace('\n', ' ')
}

/** The recent list shown on the screen, newest first. */
class ActivityLog(private val store: TextStore, private val capacity: Int = 20) {
    private val state = MutableStateFlow(LogCodec.decode(store.read()).take(capacity))
    val entries: StateFlow<List<LogEntry>> = state

    @Synchronized
    fun record(entry: LogEntry) {
        val current = state.value
        // A move that keeps failing is retried on every pass; list it once.
        if (current.any { it.fileName == entry.fileName && it.outcome == entry.outcome && it.detail == entry.detail }) return
        val next = (listOf(entry) + current).take(capacity)
        state.value = next
        store.write(LogCodec.encode(next))
    }
}
