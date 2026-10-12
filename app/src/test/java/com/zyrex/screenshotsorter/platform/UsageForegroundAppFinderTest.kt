package com.zyrex.screenshotsorter.platform

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Runs the real usage-history query of each Android version, with usage access switched on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class UsageForegroundAppFinderTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun grantUsageAccess() {
        val appOps = shadowOf(app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
    }

    // MOVE_TO_FOREGROUND and this overload are the ones that exist on Android 6; the newer names start at Android 10.
    @Suppress("DEPRECATION")
    @Test
    fun namesTheAppThatWasOnScreenWhenTheScreenshotWasTaken() {
        val now = System.currentTimeMillis()
        val usage = shadowOf(app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
        usage.addEvent("org.telegram.messenger", now - 60_000, UsageEvents.Event.MOVE_TO_FOREGROUND)
        usage.addEvent("com.android.chrome", now - 30_000, UsageEvents.Event.MOVE_TO_FOREGROUND)
        usage.addEvent("com.android.systemui", now - 1_000, UsageEvents.Event.MOVE_TO_FOREGROUND)
        usage.addEvent("com.whatsapp", now + 10_000, UsageEvents.Event.MOVE_TO_FOREGROUND)

        assertEquals("com.android.chrome", UsageForegroundAppFinder(app).packageAt(now))
    }
}
