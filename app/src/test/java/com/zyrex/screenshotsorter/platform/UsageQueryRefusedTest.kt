package com.zyrex.screenshotsorter.platform

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** A phone that checked usage access as on, then refused the query itself, as when the switch is turned off in between. */
@Implements(UsageStatsManager::class)
class RefusingUsageStatsManager {
    @Implementation
    fun queryEvents(beginTime: Long, endTime: Long): UsageEvents = throw SecurityException("Usage access was turned off")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], shadows = [RefusingUsageStatsManager::class])
class UsageQueryRefusedTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun grantUsageAccess() {
        val appOps = shadowOf(app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
    }

    @Test
    fun historyIsSilentWhenTheSystemRefusesTheQuery() {
        assertNull(UsageForegroundAppFinder(app).packageAt(System.currentTimeMillis()))
    }
}
