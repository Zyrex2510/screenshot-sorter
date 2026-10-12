package com.zyrex.screenshotsorter.ui

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Looper
import android.os.Process
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.grantStorage
import com.zyrex.screenshotsorter.startAsAFreshInstall
import com.zyrex.screenshotsorter.watch.WatchService
import com.zyrex.screenshotsorter.watch.waitUntilTheWatcherHasDoneItsFirstSweep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.io.File
import java.util.concurrent.TimeUnit

// The view model's work happens on a background thread and comes back on the main thread, so the tests
// let the main thread run until the state they expect shows up.
internal fun awaitOnMainThread(what: String, condition: () -> Boolean) {
    val giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (!condition()) {
        if (System.nanoTime() > giveUpAt) fail("Gave up waiting for $what")
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(10)
    }
}

internal fun MainViewModel.awaitUntil(what: String, condition: (MainUi) -> Boolean): MainUi {
    awaitOnMainThread("$what, the screen was left at $ui") { condition(ui.value) }
    return ui.value
}

@Suppress("DEPRECATION")
internal fun screenshotsFolder(): File = File(Environment.getExternalStorageDirectory(), "DCIM/Screenshots").also { it.mkdirs() }

/** A screenshot that last changed [ageMillis] ago. Nothing is moved before it is ten seconds old. */
internal fun screenshotAged(name: String, ageMillis: Long): File = File(screenshotsFolder(), name).apply {
    writeText("pixels")
    setLastModified(System.currentTimeMillis() - ageMillis)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class MainViewModelTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs get() = (app as SorterApp).graph.prefs

    @Before
    fun startOnAFreshInstall() = startAsAFreshInstall(app)

    @Test
    fun theSwitchCannotBeTurnedOnBeforeFilesAccessIsGranted() {
        val viewModel = MainViewModel(app)

        viewModel.setEnabled(true)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(viewModel.ui.value.enabled)
        assertFalse(prefs.enabled)
        assertEquals("a switch that did not turn on starts no clock", 0L, prefs.sortingSince)
        assertNull("nothing should have been started", shadowOf(app).nextStartedService)
    }

    @Test
    fun switchingOnRecordsWhenSortingStartedAndSwitchingOffLeavesItAlone() {
        grantStorage(app)
        val viewModel = MainViewModel(app)
        viewModel.refresh()
        viewModel.awaitUntil("files access to show as granted") { it.setup.storage }
        assertEquals(0L, prefs.sortingSince)

        val before = System.currentTimeMillis()
        viewModel.setEnabled(true)
        val first = prefs.sortingSince
        assertTrue("switching on should record the time", first >= before)

        viewModel.setEnabled(false)
        assertEquals("switching off must not change it", first, prefs.sortingSince)

        // Pretend a long time went by: the next switch-on starts a new period, so what was taken meanwhile waits for the button.
        prefs.sortingSince = 1L
        viewModel.setEnabled(true)
        assertTrue("switching on again should record the new time", prefs.sortingSince >= before)
    }

    @Test
    fun screenshotsFromBeforeTheSwitchWaitForTheButtonWhichStillSortsThem() {
        grantStorage(app)
        val old = screenshotAged("Screenshot_20260101-100000_com.example.chat.jpg", 60_000)
        val viewModel = MainViewModel(app)
        viewModel.refresh()
        viewModel.awaitUntil("files access to show as granted") { it.setup.storage }

        viewModel.setEnabled(true)
        val service = Robolectric.buildService(WatchService::class.java).create()
        try {
            waitUntilTheWatcherHasDoneItsFirstSweep(service.get())
        } finally {
            service.destroy()
        }
        assertTrue("the watcher only files screenshots taken after sorting was switched on", old.exists())

        viewModel.countOld()
        assertEquals(OldSort.Confirm(1), viewModel.awaitUntil("the count") { it.oldSort is OldSort.Confirm }.oldSort)
        viewModel.confirmOld()
        assertEquals(OldSort.Done(1), viewModel.awaitUntil("the sort") { it.oldSort is OldSort.Done }.oldSort)
        assertFalse("the button should have filed the old screenshot", old.exists())
    }

    @Test
    fun theSwitchStartsAndStopsTheWatcherAndIsRemembered() {
        grantStorage(app)
        val viewModel = MainViewModel(app)
        viewModel.refresh()
        viewModel.awaitUntil("files access to show as granted") { it.setup.storage }

        viewModel.setEnabled(true)
        viewModel.awaitUntil("the switch to show as on") { it.enabled }
        assertTrue(prefs.enabled)
        assertNotNull("the watcher should have been started", shadowOf(app).nextStartedService)

        viewModel.setEnabled(false)
        viewModel.awaitUntil("the switch to show as off") { !it.enabled }
        assertFalse(prefs.enabled)
        assertNotNull("the watcher should have been stopped", shadowOf(app).nextStoppedService)
    }

    @Test
    fun countingOldScreenshotsOffersTheOnesThatCanBeMatchedToAnApp() {
        grantStorage(app)
        screenshotAged("Screenshot_20260101-100000_com.example.chat.jpg", 60_000)
        screenshotAged("Screenshot_20260101-100100_com.example.mail.jpg", 60_000)
        // Usage access is not granted, so a name without an app in it cannot be matched.
        screenshotAged("Screenshot_20260101-100200.jpg", 60_000)
        val viewModel = MainViewModel(app)

        viewModel.countOld()

        assertEquals(OldSort.Confirm(2), viewModel.awaitUntil("the count") { it.oldSort is OldSort.Confirm }.oldSort)
    }

    @Test
    fun sortingOldScreenshotsMovesTheOldOnesButStillLeavesAnythingTenSecondsYoungAlone() {
        grantStorage(app)
        val old = screenshotAged("Screenshot_20260101-100000_com.example.chat.jpg", 60_000)
        val justTaken = screenshotAged("Screenshot_20260101-100100_com.example.mail.jpg", 0)
        val viewModel = MainViewModel(app)

        viewModel.confirmOld()

        assertEquals(OldSort.Done(1), viewModel.awaitUntil("the sort") { it.oldSort is OldSort.Done }.oldSort)
        assertFalse("the old screenshot should have moved into a folder", old.exists())
        assertTrue("a screenshot younger than ten seconds must stay where it is", justTaken.exists())
    }

    // Usage access is on, and the usage history says Telegram was on screen before the screenshot below was taken.
    // The screenshot has no app in its name, so only that history can say where it belongs.
    @Suppress("DEPRECATION")
    private fun grantUsageAccessWithTelegramOnScreen(): File {
        val appOps = shadowOf(app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
        val usage = shadowOf(app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
        usage.addEvent("org.telegram.messenger", System.currentTimeMillis() - 120_000, UsageEvents.Event.MOVE_TO_FOREGROUND)
        return screenshotAged("Screenshot_20260101-100200.jpg", 60_000)
    }

    @Test
    fun grantingUsageAccessWhileTheSwitchIsOffSortsNothingButIsStillNoted() {
        grantStorage(app)
        val waiting = grantUsageAccessWithTelegramOnScreen()
        assertFalse(prefs.enabled)
        assertFalse(prefs.usageAccessSeen)
        val viewModel = MainViewModel(app)

        viewModel.refresh()
        viewModel.awaitUntil("usage access to show as granted") { it.setup.usage }
        awaitOnMainThread("usage access to be noted") { prefs.usageAccessSeen }

        assertTrue("sorting is off, so the screenshot waits for the button", waiting.exists())
    }

    // Guards the test above: with the switch on, the same set-up does file the screenshot, so that test cannot pass by accident.
    @Test
    fun grantingUsageAccessWhileTheSwitchIsOnFilesWhatWasTakenSinceItWasSwitchedOn() {
        grantStorage(app)
        val waiting = grantUsageAccessWithTelegramOnScreen()
        prefs.enabled = true
        prefs.sortingSince = System.currentTimeMillis() - 300_000
        val viewModel = MainViewModel(app)

        viewModel.refresh()
        viewModel.awaitUntil("usage access to show as granted") { it.setup.usage }
        awaitOnMainThread("usage access to be noted") { prefs.usageAccessSeen }

        assertFalse("sorting is on, so the recheck should have filed it", waiting.exists())
    }

    @Test
    fun theDialogsGoAwayWhenDismissed() {
        val viewModel = MainViewModel(app)
        viewModel.countOld()
        viewModel.awaitUntil("the count") { it.oldSort is OldSort.Confirm }

        viewModel.dismissOld()

        viewModel.awaitUntil("the dialog to go away") { it.oldSort == OldSort.Idle }
    }
}
