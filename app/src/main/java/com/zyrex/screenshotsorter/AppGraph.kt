package com.zyrex.screenshotsorter

import android.content.Context
import android.os.Environment
import com.zyrex.screenshotsorter.core.ActivityLog
import com.zyrex.screenshotsorter.core.AppIdentifier
import com.zyrex.screenshotsorter.core.DirectFileMover
import com.zyrex.screenshotsorter.core.HandledKeys
import com.zyrex.screenshotsorter.core.ScreenshotFinder
import com.zyrex.screenshotsorter.core.Sorter
import com.zyrex.screenshotsorter.core.SweepResult
import com.zyrex.screenshotsorter.platform.AndroidInstalledApps
import com.zyrex.screenshotsorter.platform.MediaIndex
import com.zyrex.screenshotsorter.platform.Prefs
import com.zyrex.screenshotsorter.platform.UsageForegroundAppFinder

/** The app's long-lived objects, built once per process and shared by the screen, the service and the job. */
class AppGraph(context: Context) {
    val prefs = Prefs(context)

    @Suppress("DEPRECATION")
    val finder = ScreenshotFinder(Environment.getExternalStorageDirectory())
    val identifier = AppIdentifier(AndroidInstalledApps(context), UsageForegroundAppFinder(context))
    val log = ActivityLog(prefs.textStore("log"))
    val sorter = Sorter(
        finder,
        identifier,
        DirectFileMover(MediaIndex(context)::onMoved),
        HandledKeys(prefs.textStore("handled")),
        log,
    )

    /** The pass every automatic caller must use: it leaves screenshots from before sorting was switched on alone. */
    fun sortNewScreenshots(recheckAll: Boolean = false): SweepResult =
        sorter.sortPending(recheckAll = recheckAll, notBeforeMillis = prefs.sortingSince)
}
