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

    /**
     * The pass every automatic caller must use: it leaves screenshots from before sorting was switched on alone.
     *
     * The limit is read once the sorter is free, not before: a pass that waited behind a long bulk sort would
     * otherwise run with the limit from when it was queued, even if sorting was switched off and on again since.
     * The sorter's own methods lock the same monitor, so holding it here costs nothing.
     */
    fun sortNewScreenshots(recheckAll: Boolean = false): SweepResult = synchronized(sorter) {
        sorter.sortPending(recheckAll = recheckAll, notBeforeMillis = prefs.sortingSince)
    }
}
