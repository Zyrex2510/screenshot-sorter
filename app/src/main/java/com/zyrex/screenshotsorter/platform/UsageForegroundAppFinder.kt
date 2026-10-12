package com.zyrex.screenshotsorter.platform

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.zyrex.screenshotsorter.core.ForegroundAppFinder
import com.zyrex.screenshotsorter.core.ForegroundEvent
import com.zyrex.screenshotsorter.core.ForegroundPicker

class UsageForegroundAppFinder(private val context: Context) : ForegroundAppFinder {
    override fun packageAt(timeMillis: Long): String? {
        // Android keeps usage events for only a few days, so do not bother asking about older moments.
        if (System.currentTimeMillis() - timeMillis > RETENTION_MILLIS) return null
        if (!SetupState.hasUsageAccess(context)) return null

        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cursor = try {
            usage.queryEvents(timeMillis - LOOKBACK_MILLIS, timeMillis + 1)
        } catch (e: SecurityException) {
            // Usage access was switched off after the check above. The sorting pass has no catch of its own.
            null
        } ?: return null
        val events = ArrayList<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (cursor.hasNextEvent()) {
            cursor.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                events += ForegroundEvent(event.packageName, event.timeStamp)
            }
        }
        return ForegroundPicker.pick(events, timeMillis) {
            ForegroundPicker.isScreenshotTool(it, context.packageName)
        }
    }

    private companion object {
        const val RETENTION_MILLIS = 14 * 24 * 60 * 60_000L
        // The app on screen may have been opened long before the screenshot.
        const val LOOKBACK_MILLIS = 24 * 60 * 60_000L
    }
}
