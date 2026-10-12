package com.zyrex.screenshotsorter.platform

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.zyrex.screenshotsorter.SorterApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class PlatformSmokeTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    private companion object {
        // The app-op behind All files access; the SDK only exposes its name to system apps.
        const val MANAGE_EXTERNAL_STORAGE_OP = "android:manage_external_storage"
    }

    // Robolectric grants every manifest permission and allows every app-op by default; a freshly installed app has none.
    @Before
    fun startAsAFreshInstall() {
        shadowOf(app).denyPermissions(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        )
        val appOps = shadowOf(app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_DEFAULT)
        if (Build.VERSION.SDK_INT >= 30) {
            appOps.setMode(
                MANAGE_EXTERNAL_STORAGE_OP,
                app.applicationInfo.uid,
                app.packageName,
                AppOpsManager.MODE_DEFAULT,
            )
            // The simulated phone has no shared storage unless one is added, and the system's All files access check reads it.
            ShadowEnvironment.addExternalDir("shared-storage")
        }
    }

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
