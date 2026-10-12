package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScreenshotFinderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun file(path: String, modified: Long = 1_000_000_000_000L): File =
        File(temp.root, path).apply {
            parentFile!!.mkdirs()
            writeText("x")
            setLastModified(modified)
        }

    @Test
    fun findsImagesDirectlyInScreenshotFoldersOldestFirst() {
        file("DCIM/Screenshots/b.jpg", modified = 1_000_000_002_000L)
        file("Pictures/screenshots/a.png", modified = 1_000_000_001_000L)
        file("Pictures/Screenshot/c.webp", modified = 1_000_000_003_000L)

        assertEquals(listOf("a.png", "b.jpg", "c.webp"), ScreenshotFinder(temp.root).unsorted().map { it.name })
    }

    @Test
    fun ignoresEverythingElse() {
        file("DCIM/Screenshots/Telegram/sorted.jpg")
        file("DCIM/Camera/photo.jpg")
        file("DCIM/Screenshots/.pending-123-new.png")
        file("DCIM/Screenshots/notes.txt")
        file("Download/Screenshots/elsewhere.jpg")
        File(temp.root, "DCIM/Screenshots/folder.jpg").mkdirs()

        assertTrue(ScreenshotFinder(temp.root).unsorted().isEmpty())
    }

    @Test
    fun aMissingOrUnreadableRootGivesNothing() {
        val finder = ScreenshotFinder(File(temp.root, "nope"))
        assertTrue(finder.folders().isEmpty())
        assertTrue(finder.unsorted().isEmpty())
        assertTrue(finder.recentNames(30).isEmpty())
    }

    @Test
    fun recentNamesIncludeSortedOnesNewestFirst() {
        file("DCIM/Screenshots/old.jpg", modified = 1_000_000_001_000L)
        file("DCIM/Screenshots/Telegram/new.jpg", modified = 1_000_000_003_000L)
        file("DCIM/Screenshots/Telegram/mid.jpg", modified = 1_000_000_002_000L)
        file("DCIM/Screenshots/Telegram/deep/too-deep.jpg", modified = 1_000_000_004_000L)

        val finder = ScreenshotFinder(temp.root)
        assertEquals(listOf("new.jpg", "mid.jpg", "old.jpg"), finder.recentNames(30))
        assertEquals(listOf("new.jpg", "mid.jpg"), finder.recentNames(2))
    }
}
