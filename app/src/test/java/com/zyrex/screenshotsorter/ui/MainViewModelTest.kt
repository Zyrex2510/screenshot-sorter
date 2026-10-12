package com.zyrex.screenshotsorter.ui

import android.app.Application
import android.os.Environment
import android.os.Looper
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.grantStorage
import com.zyrex.screenshotsorter.startAsAFreshInstall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
        assertNull("nothing should have been started", shadowOf(app).nextStartedService)
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

    @Test
    fun theDialogsGoAwayWhenDismissed() {
        val viewModel = MainViewModel(app)
        viewModel.countOld()
        viewModel.awaitUntil("the count") { it.oldSort is OldSort.Confirm }

        viewModel.dismissOld()

        viewModel.awaitUntil("the dialog to go away") { it.oldSort == OldSort.Idle }
    }
}
