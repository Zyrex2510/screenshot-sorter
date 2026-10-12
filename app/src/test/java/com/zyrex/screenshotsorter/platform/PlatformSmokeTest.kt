package com.zyrex.screenshotsorter.platform

import android.app.Application
import android.os.Build
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.startAsAFreshInstall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class PlatformSmokeTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun freshInstall() = startAsAFreshInstall(app)

    @Test
    fun readsEveryPermissionWithoutCrashing() {
        assertFalse(SetupState.hasStorage(app))
        assertFalse(SetupState.hasUsageAccess(app))
        SetupState.ignoresBatteryLimits(app)
        SetupState.notificationsAllowed(app)
    }

    @Test
    fun buildsEverySettingsIntent() {
        assertTrue(SetupState.usageAccessIntents(app).isNotEmpty())
        assertTrue(SetupState.batteryIntents(app).isNotEmpty())
        assertTrue(SetupState.notificationSettingsIntents(app).isNotEmpty())
        SetupState.autostartIntents(app)
        SetupState.appDetailsIntent(app)
        if (Build.VERSION.SDK_INT >= 30) assertTrue(SetupState.allFilesAccessIntents(app).isNotEmpty())
    }

    @Test
    fun resolvesInstalledApps() {
        val apps = AndroidInstalledApps(app)
        assertEquals("Screenshot Sorter", apps.labelForPackage(app.packageName))
        assertNull(apps.labelForPackage("no.such.app"))
        assertNull(apps.labelMatching("No Such App"))
    }

    @Test
    fun historyIsSilentWithoutUsageAccess() {
        assertNull(UsageForegroundAppFinder(app).packageAt(System.currentTimeMillis()))
    }

    @Test
    fun indexUpdateDoesNotCrash() {
        MediaIndex(app).onMoved(
            File("/storage/emulated/0/DCIM/Screenshots/a.jpg"),
            File("/storage/emulated/0/DCIM/Screenshots/Telegram/a.jpg"),
        )
    }

    @Test
    fun prefsRoundTrip() {
        val prefs = Prefs(app)
        assertFalse(prefs.enabled)
        prefs.enabled = true
        assertTrue(Prefs(app).enabled)
        prefs.textStore("log").write("hello")
        assertEquals("hello", Prefs(app).textStore("log").read())
        assertEquals("", Prefs(app).textStore("other").read())
    }

    @Test
    fun theGraphBuildsAndSortsNothingWithoutStorage() {
        val graph = (app as SorterApp).graph
        assertEquals(0, graph.sorter.sortPending().moved)
    }
}
