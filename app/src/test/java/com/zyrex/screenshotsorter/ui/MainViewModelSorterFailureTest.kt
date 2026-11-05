package com.zyrex.screenshotsorter.ui

import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.os.Process
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.core.ActivityLog
import com.zyrex.screenshotsorter.core.DirectFileMover
import com.zyrex.screenshotsorter.core.HandledKeys
import com.zyrex.screenshotsorter.core.MemoryStore
import com.zyrex.screenshotsorter.core.Outcome
import com.zyrex.screenshotsorter.core.Sorter
import com.zyrex.screenshotsorter.core.WriteFailsAfter
import com.zyrex.screenshotsorter.grantStorage
import com.zyrex.screenshotsorter.makeEverySortingPassFail
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
import org.robolectric.util.ReflectionHelpers

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
    fun countingWhenTheSorterFailsEndsInTheFailedDialogAndSaysWhyInTheRecentList() {
        val viewModel = MainViewModel(app)

        viewModel.countOld()

        assertEquals(OldSort.Failed(0), viewModel.awaitUntil("the failure") { it.oldSort is OldSort.Failed }.oldSort)
        assertTrue("the sorter was never asked", UsageQueries.count.get() >= 1)
        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(
            listOf("Sorting" to "IllegalStateException: The usage service fell over"),
            failuresInTheRecentList().map { it.fileName to it.detail },
        )
    }

    // One screenshot that cannot be handled is the sorter's own business: the others are still sorted and the dialog
    // says how many moved. Here the only screenshot is the one that cannot be handled.
    @Test
    fun sortingWhenOnlyOneScreenshotFailsEndsAsNothingMovedAndNamesTheScreenshotInTheRecentList() {
        val viewModel = MainViewModel(app)

        viewModel.confirmOld()

        assertEquals(OldSort.Done(0), viewModel.awaitUntil("the sort") { it.oldSort is OldSort.Done }.oldSort)
        assertTrue("the sorter was never asked", UsageQueries.count.get() >= 1)
        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(
            listOf("Screenshot_20260101-100000.jpg" to "IllegalStateException: The usage service fell over"),
            failuresInTheRecentList().map { it.fileName to it.detail },
        )
    }

    @Test
    fun sortingWhenTheWholePassFailsEndsInTheFailedDialogAndSaysWhyInTheRecentList() {
        makeEverySortingPassFail(app)
        val viewModel = MainViewModel(app)

        viewModel.confirmOld()

        assertEquals(OldSort.Failed(0), viewModel.awaitUntil("the failure") { it.oldSort is OldSort.Failed }.oldSort)
        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(
            listOf("Sorting" to "IllegalStateException: Storage cannot be listed"),
            failuresInTheRecentList().map { it.fileName to it.detail },
        )
    }

    @Test
    fun sortingThatFailsAfterSomeScreenshotsMovedSaysHowManyMoved() {
        val graph = (app as SorterApp).graph
        val folder = screenshotsFolder()
        screenshotAged("Screenshot_20260101-100100_com.example.one.jpg", 90_000)
        screenshotAged("Screenshot_20260101-100200_com.example.two.jpg", 80_000)
        screenshotAged("Screenshot_20260101-100300_com.example.three.jpg", 70_000)
        // The sorter's own recent list can be written twice and then fails, so the third move is followed by an
        // exception that no single screenshot's handling can absorb: it would have to be written down too.
        val sorter = Sorter(graph.finder, graph.identifier, DirectFileMover(), HandledKeys(MemoryStore()), ActivityLog(WriteFailsAfter(allowedWrites = 2)))
        ReflectionHelpers.setField(graph, "sorter", sorter)
        val viewModel = MainViewModel(app)

        viewModel.confirmOld()

        assertEquals(OldSort.Failed(3), viewModel.awaitUntil("the failure") { it.oldSort is OldSort.Failed }.oldSort)
        assertEquals("the three screenshots really moved", 3, folder.listFiles().orEmpty().count { it.isDirectory })
        assertEquals(emptyList<Throwable>(), uncaught)
    }

    @Test
    fun reopeningTheAppAfterGrantingUsageAccessSurvivesAFailingRecheckAndTriesAgainNextTime() {
        val prefs = (app as SorterApp).graph.prefs
        // The recheck is an automatic pass, so it only runs while sorting is switched on.
        prefs.enabled = true
        assertFalse(prefs.usageAccessSeen)
        // The whole pass fails. A failing screenshot on its own no longer does: the sorter absorbs it and logs it.
        makeEverySortingPassFail(app)
        val viewModel = MainViewModel(app)

        viewModel.refresh()
        viewModel.awaitUntil("usage access to show as granted") { it.setup.usage }
        awaitTheRecheck()

        assertEquals(emptyList<Throwable>(), uncaught)
        assertFalse("a recheck that failed should be tried again next time", prefs.usageAccessSeen)
        assertTrue("the setup list should still have been shown", viewModel.ui.value.setup.storage)
    }

    @Test
    fun theRecheckAfterGrantingUsageAccessLeavesScreenshotsFromBeforeSortingWasSwitchedOnAlone() {
        val prefs = (app as SorterApp).graph.prefs
        // The screenshot from the set-up is a minute old and needs the usage service; sorting was switched on 30 seconds ago.
        // The recheck is an automatic pass, so it only runs while sorting is switched on.
        prefs.enabled = true
        prefs.sortingSince = System.currentTimeMillis() - 30_000
        val viewModel = MainViewModel(app)

        viewModel.refresh()
        viewModel.awaitUntil("usage access to show as granted") { it.setup.usage }
        awaitOnMainThread("the recheck to finish") { prefs.usageAccessSeen }

        assertEquals("the screenshot should not even have been looked at", 0, UsageQueries.count.get())
        assertEquals(emptyList<Throwable>(), uncaught)
    }

    private fun failuresInTheRecentList() =
        (app as SorterApp).graph.log.entries.value.filter { it.outcome == Outcome.FAILED }

    // The recheck runs on a background thread after the setup is published. It is over once the screen has either
    // written down that it coped (a log line) or let the error loose.
    private fun awaitTheRecheck() {
        awaitOnMainThread("the recheck to fail") {
            uncaught.isNotEmpty() || ShadowLog.getLogsForTag("MainViewModel").isNotEmpty()
        }
    }
}
