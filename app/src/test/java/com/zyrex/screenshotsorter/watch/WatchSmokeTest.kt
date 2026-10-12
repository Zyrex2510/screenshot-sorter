package com.zyrex.screenshotsorter.watch

import android.app.Application
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.startAsAFreshInstall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class WatchSmokeTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val jobs: JobScheduler get() = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    // Storage is not granted: Robolectric starts with every permission, a freshly installed app has none.
    @Before
    fun startWithoutStorage() = startAsAFreshInstall(app)

    @Test
    fun theServiceGoesForegroundAndShutsDownCleanly() {
        val controller = Robolectric.buildService(WatchService::class.java).create()
        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        controller.destroy()
    }

    @Test
    fun startAsksAndroidForTheService() {
        assertTrue(WatchService.start(app))
        assertEquals(WatchService::class.java.name, shadowOf(app).nextStartedService.component?.className)
    }

    @Test
    fun theCatchUpJobIsScheduledOnceAndCanBeCancelled() {
        CatchUpJob.schedule(app)
        CatchUpJob.schedule(app)
        assertEquals(1, jobs.allPendingJobs.size)
        CatchUpJob.cancel(app)
        assertEquals(0, jobs.allPendingJobs.size)
    }

    @Test
    fun schedulingAgainKeepsTheJobThatIsAlreadyPending() {
        CatchUpJob.schedule(app)
        val first = jobs.allPendingJobs.single()
        // Opening the app schedules every time; replacing the job would restart its 15 minute period.
        CatchUpJob.schedule(app)
        assertSame(first, jobs.allPendingJobs.single())
    }

    @Test
    fun theCatchUpJobCanBeScheduledAgainAfterItWasCancelled() {
        CatchUpJob.schedule(app)
        CatchUpJob.cancel(app)
        CatchUpJob.schedule(app)
        assertEquals(1, jobs.allPendingJobs.size)
    }

    @Test
    fun bootDoesNothingWhileSortingIsOff() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(app).nextStartedService)
        assertEquals(0, jobs.allPendingJobs.size)
    }

    @Test
    fun bootSchedulesCatchUpWhenSortingIsOn() {
        (app as SorterApp).graph.prefs.enabled = true
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, jobs.allPendingJobs.size)
        // Storage is not granted in this test, so the watcher itself must not start.
        assertNull(shadowOf(app).nextStartedService)
    }
}
