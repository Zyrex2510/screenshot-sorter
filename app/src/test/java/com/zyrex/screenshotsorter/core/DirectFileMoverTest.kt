package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

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

    @Test
    fun movesRunOneAtATime() {
        val firstIsInside = CountDownLatch(1)
        val letFirstFinish = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val isFirstCall = AtomicBoolean(true)
        val target = File(temp.root, "Telegram")
        val a = source("a.jpg")
        val b = source("b.jpg")
        // The callback runs inside move(). Only the first move is held there, so it stays in progress
        // while the second one is free to run to the end if nothing stops it.
        val mover = DirectFileMover { _, _ ->
            if (isFirstCall.compareAndSet(true, false)) {
                firstIsInside.countDown()
                letFirstFinish.await()
            }
        }

        val first = thread { mover.move(a, target) }
        assertTrue(firstIsInside.await(5, TimeUnit.SECONDS))
        val second = thread {
            mover.move(b, target)
            secondFinished.countDown()
        }

        assertFalse("a second move ran while the first was still in progress", secondFinished.await(300, TimeUnit.MILLISECONDS))
        letFirstFinish.countDown()
        assertTrue(secondFinished.await(5, TimeUnit.SECONDS))
        first.join()
        second.join()
        assertTrue(File(target, "a.jpg").isFile)
        assertTrue(File(target, "b.jpg").isFile)
    }
}
