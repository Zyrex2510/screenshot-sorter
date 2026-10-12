package com.zyrex.screenshotsorter.watch

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import com.zyrex.screenshotsorter.grantStorage
import com.zyrex.screenshotsorter.startAsAFreshInstall
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** How often the sorting pass asked the usage service, which is the last thing it does before a screenshot is filed. */
object UsageQueries {
    val count = AtomicInteger()
}

/** The phone's usage service falls over in the middle of a sorting pass, with an error the pass has no catch for. */
@Implements(UsageStatsManager::class)
class FallingOverUsageStatsManager {
    @Implementation
    fun queryEvents(beginTime: Long, endTime: Long): UsageEvents {
        UsageQueries.count.incrementAndGet()
        throw IllegalStateException("The usage service fell over")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], shadows = [FallingOverUsageStatsManager::class])
class WatchSweepTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun startWithStorageAndUsageAccess() {
        startAsAFreshInstall(app)
        grantStorage(app)
        val appOps = shadowOf(app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
        UsageQueries.count.set(0)
    }

    @Test
    fun theWatcherFilesAWaitingScreenshotWhenItStarts() {
        val screenshot = waitingScreenshot("Screenshot_20260101-100000_com.example.chat.jpg")
        val service = Robolectric.buildService(WatchService::class.java).create()
        try {
            awaitUntil("the screenshot to be filed") { !screenshot.exists() }
            val filed = screenshotsFolder().listFiles().orEmpty().filter { it.isDirectory }.flatMap { it.listFiles().orEmpty().toList() }
            assertEquals(listOf(screenshot.name), filed.map { it.name })
        } finally {
            service.destroy()
        }
    }

    @Test
    fun aSweepThatFailsDoesNotStopTheWatcherFromSweepingAgain() {
        // No app in the name, so the sorting pass has to ask the usage service, and that fails every time.
        waitingScreenshot("Screenshot_20260101-100000.jpg")
        val service = Robolectric.buildService(WatchService::class.java).create()
        try {
            awaitUntil("the first sweep") { UsageQueries.count.get() == 1 }
            // A new screenshot appears. The watcher waits out the ten second rule, which the test skips by moving the clock.
            app.contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
            awaitUntil("a second sweep after the change") {
                ShadowSystemClock.advanceBy(Duration.ofSeconds(11))
                UsageQueries.count.get() >= 2
            }
        } finally {
            service.destroy()
        }
    }

    @Suppress("DEPRECATION")
    private fun screenshotsFolder(): File = File(Environment.getExternalStorageDirectory(), "DCIM/Screenshots").also { it.mkdirs() }

    // Old enough to be moved: a screenshot is left alone for its first ten seconds.
    private fun waitingScreenshot(name: String): File = File(screenshotsFolder(), name).apply {
        writeText("pixels")
        setLastModified(System.currentTimeMillis() - 60_000)
    }

    // The watcher works on its own thread, so the test waits for what it does instead of assuming it is done.
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition()) {
            if (System.nanoTime() > giveUpAt) fail("Gave up waiting for $what")
            Thread.sleep(25)
        }
    }
}
