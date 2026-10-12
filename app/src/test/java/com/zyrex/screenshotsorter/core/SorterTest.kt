package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SorterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val clock = 1_000_000_000_000L
    private val telegramShot = "Screenshot_2026-10-09-14-22-33-456_org.telegram.messenger.jpg"
    private val plainShot = "Screenshot_20261009-101205.png"
    private val history = FakeHistory()
    private lateinit var shots: File
    private lateinit var log: ActivityLog
    private lateinit var sorter: Sorter

    @Before
    fun setUp() {
        shots = temp.newFolder("DCIM", "Screenshots")
        log = ActivityLog(MemoryStore())
        sorter = sorterFor(temp.root)
    }

    private fun sorterFor(root: File) = Sorter(
        ScreenshotFinder(root),
        AppIdentifier(FakeInstalledApps(mapOf("org.telegram.messenger" to "Telegram")), history),
        DirectFileMover(),
        HandledKeys(MemoryStore()),
        log,
    ) { clock }

    private fun shot(name: String, ageMillis: Long = 60_000, text: String = "new"): File =
        File(shots, name).apply {
            writeText(text)
            setLastModified(clock - ageMillis)
        }

    @Test
    fun movesAScreenshotIntoTheAppFolder() {
        val file = shot(telegramShot)

        assertEquals(SweepResult(moved = 1, tooNew = 0), sorter.sortPending())

        assertFalse(file.exists())
        assertTrue(File(shots, "Telegram/$telegramShot").isFile)
        assertEquals(listOf(LogEntry(clock, telegramShot, Outcome.MOVED, "Telegram")), log.entries.value)
    }

    @Test
    fun leavesAFreshScreenshotForTheNextPass() {
        val file = shot(telegramShot, ageMillis = 3_000)

        assertEquals(SweepResult(moved = 0, tooNew = 1), sorter.sortPending())

        assertTrue(file.exists())
        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun anUnknownScreenshotStaysAndIsNotCheckedAgain() {
        val file = shot(plainShot)

        sorter.sortPending()
        sorter.sortPending()

        assertTrue(file.exists())
        assertEquals(1, history.calls)
        assertEquals(listOf(LogEntry(clock, plainShot, Outcome.UNKNOWN_APP, "")), log.entries.value)
    }

    @Test
    fun recheckAllLooksAtGivenUpScreenshotsAgain() {
        shot(plainShot)
        sorter.sortPending()
        history.packageName = "org.telegram.messenger"

        assertEquals(SweepResult(moved = 1, tooNew = 0), sorter.sortPending(recheckAll = true))
        assertTrue(File(shots, "Telegram/$plainShot").isFile)
    }

    @Test
    fun oldUnknownScreenshotsAreNotListedInRecent() {
        shot(plainShot, ageMillis = 2 * 60 * 60_000L)

        sorter.sortPending()

        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun aFailedMoveIsLoggedAndTriedAgain() {
        val file = shot(telegramShot)
        val inTheWay = File(shots, "Telegram").apply { writeText("not a folder") }

        assertEquals(SweepResult(moved = 0, tooNew = 0), sorter.sortPending())
        assertTrue(file.exists())
        assertEquals(Outcome.FAILED, log.entries.value.first().outcome)

        inTheWay.delete()
        assertEquals(SweepResult(moved = 1, tooNew = 0), sorter.sortPending())
    }

    @Test
    fun neverOverwritesAnExistingFile() {
        File(shots, "Telegram").mkdirs()
        File(shots, "Telegram/$telegramShot").writeText("old")
        shot(telegramShot, text = "new")

        sorter.sortPending()

        assertEquals("old", File(shots, "Telegram/$telegramShot").readText())
        assertEquals("new", File(shots, "Telegram/${telegramShot.removeSuffix(".jpg")} (1).jpg").readText())
    }

    @Test
    fun aZeroMinimumAgeSortsFreshOnesToo() {
        shot(telegramShot, ageMillis = 0)

        assertEquals(SweepResult(moved = 1, tooNew = 0), sorter.sortPending(minAgeMillis = 0))
    }

    @Test
    fun countsOnlyWhatItCanIdentify() {
        shot(telegramShot)
        shot(plainShot)

        assertEquals(1, sorter.countSortable())
    }

    @Test
    fun missingStorageIsNotAnError() {
        val nowhere = sorterFor(File(temp.root, "nope"))

        assertEquals(SweepResult(moved = 0, tooNew = 0), nowhere.sortPending())
        assertEquals(0, nowhere.countSortable())
    }
}
