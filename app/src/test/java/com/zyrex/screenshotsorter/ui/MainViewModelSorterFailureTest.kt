package com.zyrex.screenshotsorter.ui

import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.os.Process
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.grantStorage
import com.zyrex.screenshotsorter.startAsAFreshInstall
import com.zyrex.screenshotsorter.watch.FallingOverUsageStatsManager
import com.zyrex.screenshotsorter.watch.UsageQueries
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * The sorting pass can hit an error nobody planned for (here the phone's usage service falls over, which the
 * pass has no catch for). On the screen's background work that error would end the app, so it must end in an
 * ordinary state instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], shadows = [FallingOverUsageStatsManager::class])
class MainViewModelSorterFailureTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val uncaught = mutableListOf<Throwable>()
    private val handlerBefore = Thread.currentThread().uncaughtExceptionHandler

    @Before
    fun startWithStorageAndUsageAccessAndWatchForCrashes() {
        startAsAFreshInstall(app)
        grantStorage(app)
        val appOps = shadowOf(app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
        UsageQueries.count.set(0)
        // A coroutine that fails with nobody to catch it reports to this thread's handler; on a phone that ends the app.
        Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> uncaught += error }
        // No app in the name, so the sorting pass has to ask the usage service, and that fails every time.
        screenshotAged("Screenshot_20260101-100000.jpg", 60_000)
    }

    @After
    fun restoreTheHandler() {
        Thread.currentThread().uncaughtExceptionHandler = handlerBefore
    }

    @Test
    fun countingWhenTheSorterFailsEndsAsNothingToSort() {
        val viewModel = MainViewModel(app)

        viewModel.countOld()

        assertEquals(OldSort.Confirm(0), viewModel.awaitUntil("the count") { it.oldSort is OldSort.Confirm }.oldSort)
        assertTrue("the sorter was never asked", UsageQueries.count.get() >= 1)
        assertEquals(emptyList<Throwable>(), uncaught)
    }

    @Test
    fun sortingWhenTheSorterFailsEndsAsNothingMoved() {
        val viewModel = MainViewModel(app)

        viewModel.confirmOld()

        assertEquals(OldSort.Done(0), viewModel.awaitUntil("the sort") { it.oldSort is OldSort.Done }.oldSort)
        assertTrue("the sorter was never asked", UsageQueries.count.get() >= 1)
        assertEquals(emptyList<Throwable>(), uncaught)
    }

    @Test
    fun reopeningTheAppAfterGrantingUsageAccessSurvivesAFailingRecheckAndTriesAgainNextTime() {
        val prefs = (app as SorterApp).graph.prefs
        assertFalse(prefs.usageAccessSeen)
        val viewModel = MainViewModel(app)

        viewModel.refresh()
        viewModel.awaitUntil("usage access to show as granted") { it.setup.usage }
        awaitTheRecheck()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertFalse("a recheck that failed should be tried again next time", prefs.usageAccessSeen)
        assertTrue("the setup list should still have been shown", viewModel.ui.value.setup.storage)
    }

    // The recheck runs on a background thread after the setup is published. It is over once the screen has either
    // written down that it coped (a log line) or let the error loose.
    private fun awaitTheRecheck() {
        awaitOnMainThread("the recheck to fail") {
            uncaught.isNotEmpty() || ShadowLog.getLogsForTag("MainViewModel").isNotEmpty()
        }
    }
}
