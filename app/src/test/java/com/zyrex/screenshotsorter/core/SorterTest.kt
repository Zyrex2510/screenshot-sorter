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
    fun aScreenshotFromBeforeTheLimitIsLeftWhereItIsAndNotRememberedAsLookedAt() {
        val old = shot(telegramShot, ageMillis = 60_000)
        val unknown = shot(plainShot, ageMillis = 50_000)
        val limit = clock - 30_000

        assertEquals(SweepResult(moved = 0, tooNew = 0), sorter.sortPending(notBeforeMillis = limit))

        assertTrue(old.exists())
        assertTrue(unknown.exists())
        assertTrue("a skipped screenshot is not logged", log.entries.value.isEmpty())
        assertEquals("a skipped screenshot is not looked at", 0, history.calls)
        // Without the limit the same screenshots are still waiting: nothing was given up on.
        history.packageName = "org.telegram.messenger"
        assertEquals(SweepResult(moved = 2, tooNew = 0), sorter.sortPending())
        assertTrue(File(shots, "Telegram/$telegramShot").isFile)
        assertTrue(File(shots, "Telegram/$plainShot").isFile)
    }

    @Test
    fun aScreenshotFromBeforeTheLimitIsNotCountedAsTooNewEither() {
        val fresh = shot(telegramShot, ageMillis = 3_000)

        assertEquals(SweepResult(moved = 0, tooNew = 0), sorter.sortPending(notBeforeMillis = clock))

        assertTrue(fresh.exists())
    }

    @Test
    fun aScreenshotFromTheLimitOnwardsIsMovedAsBefore() {
        val atTheLimit = shot(telegramShot, ageMillis = 60_000)
        val afterTheLimit = shot("Screenshot_2026-10-09-14-23-00-000_org.telegram.messenger.jpg", ageMillis = 20_000)
        val tooNew = shot("Screenshot_2026-10-09-14-24-00-000_org.telegram.messenger.jpg", ageMillis = 3_000)

        val result = sorter.sortPending(notBeforeMillis = clock - 60_000)

        assertEquals(SweepResult(moved = 2, tooNew = 1), result)
        assertFalse(atTheLimit.exists())
        assertFalse(afterTheLimit.exists())
        assertTrue("the ten second rule still applies", tooNew.exists())
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
