package com.zyrex.screenshotsorter

import android.app.Application
import android.os.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class AppGraphTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val graph get() = (app as SorterApp).graph

    @Before
    fun startOnAFreshInstall() = startAsAFreshInstall(app)

    @Test
    fun anAutomaticPassThatWaitedForAnotherOneUsesTheLimitFromWhenItGotItsTurn() {
        // A bulk sort holds the sorter. A screenshot a minute old is after the limit that was in force when the
        // automatic pass was queued, and before the limit that is in force by the time it gets its turn.
        val waiting = waitingScreenshot("Screenshot_20260101-100000_com.example.chat.jpg", ageMillis = 60_000)
        graph.prefs.sortingSince = System.currentTimeMillis() - 120_000
        val bulkSortIsRunning = CountDownLatch(1)
        val bulkSortMayEnd = CountDownLatch(1)
        val bulkSort = Thread {
            synchronized(graph.sorter) {
                bulkSortIsRunning.countDown()
                bulkSortMayEnd.await(10, TimeUnit.SECONDS)
            }
        }
        val automaticPass = AtomicReference<Throwable>()
        val automatic = Thread {
            try {
                graph.sortNewScreenshots()
            } catch (e: Throwable) {
                automaticPass.set(e)
            }
        }
        bulkSort.start()
        assertTrue(bulkSortIsRunning.await(10, TimeUnit.SECONDS))
        automatic.start()
        val giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (automatic.state != Thread.State.BLOCKED) {
            if (System.nanoTime() > giveUpAt) fail("The automatic pass never got as far as waiting for the sorter")
            Thread.sleep(5)
        }

        // The person switched sorting off and on again while the automatic pass waited.
        graph.prefs.sortingSince = System.currentTimeMillis() - 30_000
        bulkSortMayEnd.countDown()
        automatic.join(10_000)
        bulkSort.join(10_000)

        assertEquals(null, automaticPass.get())
        assertTrue("the screenshot is older than the new limit, so it waits for the button", waiting.exists())
    }

    @Suppress("DEPRECATION")
    private fun waitingScreenshot(name: String, ageMillis: Long): File {
        val folder = File(Environment.getExternalStorageDirectory(), "DCIM/Screenshots").also { it.mkdirs() }
        return File(folder, name).apply {
            writeText("pixels")
            setLastModified(System.currentTimeMillis() - ageMillis)
        }
    }
}
