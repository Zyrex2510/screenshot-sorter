package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogTest {
    private fun moved(name: String, time: Long = 1) = LogEntry(time, name, Outcome.MOVED, "Telegram")

    @Test
    fun codecRoundTrips() {
        val entries = listOf(
            moved("a.jpg", 3),
            LogEntry(2, "b.png", Outcome.UNKNOWN_APP, ""),
            LogEntry(1, "c.jpg", Outcome.FAILED, "Could not move the file"),
        )
        assertEquals(entries, LogCodec.decode(LogCodec.encode(entries)))
    }

    @Test
    fun codecKeepsOddCharactersOnOneLine() {
        val entry = LogEntry(1, "a\tb\nc.jpg", Outcome.MOVED, "X\tY")
        assertEquals(listOf(LogEntry(1, "a b c.jpg", Outcome.MOVED, "X Y")), LogCodec.decode(LogCodec.encode(listOf(entry))))
    }

    @Test
    fun codecSkipsDamagedLines() {
        val text = "garbage\n5\tok.jpg\tMOVED\tTelegram\nx\tbad-time.jpg\tMOVED\tT\n6\tbad-outcome.jpg\tNOPE\tT"
        assertEquals(listOf(moved("ok.jpg", 5)), LogCodec.decode(text))
        assertEquals(emptyList<LogEntry>(), LogCodec.decode(""))
    }

    @Test
    fun newestFirstAndCapped() {
        val log = ActivityLog(MemoryStore(), capacity = 2)
        log.record(moved("a.jpg"))
        log.record(moved("b.jpg"))
        log.record(moved("c.jpg"))
        assertEquals(listOf("c.jpg", "b.jpg"), log.entries.value.map { it.fileName })
    }

    @Test
    fun aRepeatedResultIsListedOnce() {
        val log = ActivityLog(MemoryStore())
        val failed = LogEntry(1, "a.jpg", Outcome.FAILED, "Could not move the file")
        log.record(failed)
        log.record(failed.copy(timeMillis = 2))
        log.record(moved("a.jpg", 3))
        assertEquals(listOf(Outcome.MOVED, Outcome.FAILED), log.entries.value.map { it.outcome })
    }

    @Test
    fun survivesARestart() {
        val store = MemoryStore()
        ActivityLog(store).record(moved("a.jpg"))
        assertEquals(listOf(moved("a.jpg")), ActivityLog(store).entries.value)
    }
}
