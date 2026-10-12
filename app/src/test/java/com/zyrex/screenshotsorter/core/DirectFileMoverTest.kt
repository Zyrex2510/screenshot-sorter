package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DirectFileMoverTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun source(name: String = "shot.jpg", text: String = "new"): File =
        File(temp.root, name).apply { writeText(text) }

    @Test
    fun movesIntoANewFolder() {
        val source = source()
        val target = File(temp.root, "Telegram")

        val result = DirectFileMover().move(source, target)

        assertEquals(MoveResult.Moved(File(target, "shot.jpg")), result)
        assertFalse(source.exists())
        assertEquals("new", File(target, "shot.jpg").readText())
    }

    @Test
    fun addsANumberInsteadOfOverwriting() {
        val target = File(temp.root, "Telegram").apply { mkdirs() }
        File(target, "shot.jpg").writeText("old")

        val result = DirectFileMover().move(source(), target)

        assertEquals(MoveResult.Moved(File(target, "shot (1).jpg")), result)
        assertEquals("old", File(target, "shot.jpg").readText())
        assertEquals("new", File(target, "shot (1).jpg").readText())
    }

    @Test
    fun aMissingSourceIsAFailure() {
        val result = DirectFileMover().move(File(temp.root, "gone.jpg"), File(temp.root, "Telegram"))

        assertTrue(result is MoveResult.Failed)
        assertFalse(File(temp.root, "Telegram").exists())
    }

    @Test
    fun aFileInTheWayIsAFailure() {
        val source = source()
        val blocked = File(temp.root, "Telegram").apply { writeText("not a folder") }

        val result = DirectFileMover().move(source, blocked)

        assertTrue(result is MoveResult.Failed)
        assertTrue(source.exists())
        assertEquals("not a folder", blocked.readText())
    }

    @Test
    fun tellsTheIndexAboutTheMove() {
        val seen = mutableListOf<Pair<File, File>>()
        val source = source()
        val target = File(temp.root, "Telegram")

        DirectFileMover { from, to -> seen += from to to }.move(source, target)

        assertEquals(listOf(source to File(target, "shot.jpg")), seen)
    }

    @Test
    fun anIndexErrorDoesNotUndoTheMove() {
        val target = File(temp.root, "Telegram")

        val result = DirectFileMover { _, _ -> error("index is down") }.move(source(), target)

        assertEquals(MoveResult.Moved(File(target, "shot.jpg")), result)
    }
}
