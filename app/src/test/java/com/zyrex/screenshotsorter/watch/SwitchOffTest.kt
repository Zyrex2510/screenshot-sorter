package com.zyrex.screenshotsorter.watch

import android.app.AppOpsManager
import android.app.Application
import android.app.job.JobParameters
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Process
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.grantStorage
import com.zyrex.screenshotsorter.startAsAFreshInstall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
import org.robolectric.util.ReflectionHelpers
import java.io.File

/**
 * While a screenshot is being looked at, the person switches sorting off, and then the phone's usage service falls
 * over. Only the screenshot below needs the usage service, so it is where the tests below choose their moment.
 */
@Implements(UsageStatsManager::class)
class SwitchingOffUsageStatsManager {
    @Implementation
    fun queryEvents(beginTime: Long, endTime: Long): UsageEvents {
        (RuntimeEnvironment.getApplication() as SorterApp).graph.prefs.enabled = false
        throw IllegalStateException("The usage service fell over")
    }
}

/** Automatic sorting never runs while the switch is off, however late the switch is turned off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], shadows = [SwitchingOffUsageStatsManager::class])
class SwitchOffTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val graph get() = (app as SorterApp).graph

    @Before
    fun startWithStorageAndUsageAccessAndSortingOn() {
        startAsAFreshInstall(app)
        grantStorage(app)
        val appOps = shadowOf(app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
        graph.prefs.sortingSince = System.currentTimeMillis() - 120_000
        graph.prefs.enabled = true
    }

    @Test
    fun aRunningWatcherWhoseSwitchWasTurnedOffDoesNotSortAndStopsItself() {
        val service = Robolectric.buildService(WatchService::class.java).create()
        try {
            waitUntilTheWatcherHasDoneItsFirstSweep(service.get())
            // The switch writes this before it asks Android to stop the service, and the service may be mid-way through
            // a pass or have one queued when the stop arrives.
            graph.prefs.enabled = false
            val screenshot = waitingScreenshot("Screenshot_20260101-100000_com.example.chat.jpg")

            service.startCommand(0, 1)
            waitUntilTheWatcherHasDoneItsFirstSweep(service.get())

            assertTrue("sorting is off, so the screenshot stays where it is", screenshot.exists())
            assertTrue("a watcher that finds sorting off has no reason to stay", shadowOf(service.get()).isStoppedBySelf)
        } finally {
            service.destroy()
        }
    }

    @Test
    fun aCatchUpJobWhoseSwitchWasTurnedOffAfterItStartedNeitherSortsNorStartsTheWatcher() {
        val screenshot = waitingScreenshot("Screenshot_20260101-100000_com.example.chat.jpg")
        val job = Robolectric.buildService(CatchUpJob::class.java).create().get()
        graph.prefs.enabled = false

        // onStartJob checks the switch on the main thread and then hands over to a thread that runs this. It is called
        // directly here because the moment between the two cannot be hit on purpose with a real thread.
        job.catchUp(graph, jobParameters())

        assertTrue("sorting is off, so the screenshot stays where it is", screenshot.exists())
        assertNull("sorting is off, so the watcher must not come back", shadowOf(app).nextStartedService)
        assertTrue("the job still reports itself finished", shadowOf(job).isJobFinished)
    }

    @Test
    fun aCatchUpJobWhoseSwitchWasTurnedOffDuringTheSweepDoesNotBringTheWatcherBack() {
        // No app in the name, so the sweep asks the usage service. That is where the switch is turned off.
        waitingScreenshot("Screenshot_20260101-100000.jpg")
        val job = Robolectric.buildService(CatchUpJob::class.java).create().get()

        job.catchUp(graph, jobParameters())

        assertFalse("the switch should have been turned off during the sweep", graph.prefs.enabled)
        assertNull("sorting is off, so the watcher must not come back", shadowOf(app).nextStartedService)
        assertTrue("the job still reports itself finished", shadowOf(job).isJobFinished)
    }

    @Suppress("DEPRECATION")
    private fun screenshotsFolder(): File = File(Environment.getExternalStorageDirectory(), "DCIM/Screenshots").also { it.mkdirs() }

    // Old enough to be moved and taken after sorting was switched on.
    private fun waitingScreenshot(name: String): File = File(screenshotsFolder(), name).apply {
        writeText("pixels")
        setLastModified(System.currentTimeMillis() - 60_000)
    }

    // Android hides the constructor from apps; Robolectric's phone has an empty one. Nothing reads the contents here.
    private fun jobParameters(): JobParameters = ReflectionHelpers.callConstructor(JobParameters::class.java)
}
