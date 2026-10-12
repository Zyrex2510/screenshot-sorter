package com.zyrex.screenshotsorter.watch

import android.app.AppOpsManager
import android.app.Application
import android.app.job.JobParameters
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.grantStorage
import com.zyrex.screenshotsorter.startAsAFreshInstall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration
import java.util.concurrent.CountDownLatch
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
        // Sorting was switched on two minutes ago, so the waiting screenshots below (a minute old unless said otherwise)
        // were taken after that and missed while the app was not running.
        (app as SorterApp).graph.prefs.sortingSince = System.currentTimeMillis() - 120_000
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
    fun theWatcherLeavesAScreenshotFromBeforeSortingWasSwitchedOnForTheButton() {
        val before = waitingScreenshot("Screenshot_20260101-100000_com.example.chat.jpg", ageMillis = 300_000)
        val after = waitingScreenshot("Screenshot_20260101-100100_com.example.mail.jpg")
        val service = Robolectric.buildService(WatchService::class.java).create()
        try {
            awaitUntil("the newer screenshot to be filed") { !after.exists() }
            waitUntilTheWatcherHasDoneItsFirstSweep(service.get())
            assertTrue("a screenshot from before sorting was switched on waits for the button", before.exists())
        } finally {
            service.destroy()
        }
    }

    @Test
    fun theCatchUpSweepLeavesAScreenshotFromBeforeSortingWasSwitchedOnForTheButton() {
        val before = waitingScreenshot("Screenshot_20260101-100000_com.example.chat.jpg", ageMillis = 300_000)
        val after = waitingScreenshot("Screenshot_20260101-100100_com.example.mail.jpg")
        val job = startedCatchUpJob()

        assertTrue(job.onStartJob(jobParameters()))
        awaitUntil("the job to report itself finished") { shadowOf(job).isJobFinished }

        assertFalse("a screenshot taken after sorting was switched on is filed", after.exists())
        assertTrue("a screenshot from before sorting was switched on waits for the button", before.exists())
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

    @Test
    fun aCatchUpSweepThatFailsIsLoggedAndTheWatcherIsStillBroughtBack() {
        // No app in the name, so the sorting pass asks the usage service, and that fails.
        waitingScreenshot("Screenshot_20260101-100000.jpg")
        val job = startedCatchUpJob()

        assertTrue(job.onStartJob(jobParameters()))
        awaitUntil("the job to report itself finished") { shadowOf(job).isJobFinished }

        val failure = ShadowLog.getLogsForTag("CatchUpJob").single { it.type == Log.WARN }
        assertTrue(failure.throwable is IllegalStateException)
        assertNotNull("the watcher should be started even though the sweep failed", shadowOf(app).nextStartedService)
    }

    @Test
    fun theCatchUpJobStillFinishesWhenStartingTheWatcherFails() {
        val job = startedCatchUpJob()
        ReflectionHelpers.setField(ContextWrapper::class.java, job, "mBase", StartRefusingContext(job.baseContext))

        assertTrue(job.onStartJob(jobParameters()))
        awaitUntil("the job to report itself finished") { shadowOf(job).isJobFinished }

        val failure = ShadowLog.getLogsForTag("CatchUpJob").single { it.type == Log.WARN }
        assertTrue(failure.throwable is RuntimeException)
    }

    @Test
    fun startingTheServiceAgainSweepsWhatAppearedWhileItWasRunning() {
        val service = Robolectric.buildService(WatchService::class.java).create()
        try {
            waitUntilTheWatcherHasDoneItsFirstSweep(service.get())
            // No media change is reported, as when the app is opened and asks for the watcher again.
            val screenshot = waitingScreenshot("Screenshot_20260101-100000_com.example.chat.jpg")
            service.startCommand(0, 1)
            awaitUntil("the screenshot to be filed") { !screenshot.exists() }
        } finally {
            service.destroy()
        }
    }

    @Test
    fun aChangeReportedBeforeTheWatcherIsSetUpDoesNotStopItsFirstSweep() {
        val screenshot = waitingScreenshot("Screenshot_20260101-100000_com.example.chat.jpg")
        val service = Robolectric.buildService(WatchService::class.java)
        // The gallery reports a change to a service that has no thread to sweep on yet. The report is lost,
        // and the service must not remember it as a sweep that is already queued.
        ReflectionHelpers.callInstanceMethod<Unit>(
            service.get(),
            "queueSweep",
            ClassParameter.from(java.lang.Long.TYPE, 10_500L),
        )
        service.create()
        try {
            awaitUntil("the screenshot to be filed") { !screenshot.exists() }
        } finally {
            service.destroy()
        }
    }

    @Suppress("DEPRECATION")
    private fun screenshotsFolder(): File = File(Environment.getExternalStorageDirectory(), "DCIM/Screenshots").also { it.mkdirs() }

    // Old enough to be moved: a screenshot is left alone for its first ten seconds.
    private fun waitingScreenshot(name: String, ageMillis: Long = 60_000): File = File(screenshotsFolder(), name).apply {
        writeText("pixels")
        setLastModified(System.currentTimeMillis() - ageMillis)
    }

    private fun startedCatchUpJob(): CatchUpJob {
        (app as SorterApp).graph.prefs.enabled = true
        return Robolectric.buildService(CatchUpJob::class.java).create().get()
    }

    // Android hides the constructor from apps; Robolectric's phone has an empty one. Nothing reads the contents here.
    private fun jobParameters(): JobParameters = ReflectionHelpers.callConstructor(JobParameters::class.java)

    // The watcher works on its own thread, so the test waits for what it does instead of assuming it is done.
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition()) {
            if (System.nanoTime() > giveUpAt) fail("Gave up waiting for $what")
            Thread.sleep(25)
        }
    }
}

/** Whatever is queued on the watcher's thread before this runs has finished by the time it returns. */
internal fun waitUntilTheWatcherHasDoneItsFirstSweep(service: WatchService) {
    val handler = ReflectionHelpers.getField<Handler>(service, "handler")
    val done = CountDownLatch(1)
    handler.post { done.countDown() }
    assertTrue("the watcher never got to its first sweep", done.await(10, TimeUnit.SECONDS))
}

/** A phone that falls over while the app asks it to start the watcher, with an error nobody planned for. */
private class StartRefusingContext(base: Context) : ContextWrapper(base) {
    override fun startService(service: Intent?): ComponentName? = throw RuntimeException("The phone fell over")

    override fun startForegroundService(service: Intent?): ComponentName? = throw RuntimeException("The phone fell over")
}
