package com.zyrex.screenshotsorter.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import com.zyrex.screenshotsorter.core.InstalledApps

class AndroidInstalledApps(context: Context) : InstalledApps {
    private val packages = context.packageManager
    private var labels: Map<String, String> = emptyMap()
    private var labelsLoadedAt = Long.MIN_VALUE

    override fun labelForPackage(packageId: String): String? = try {
        packages.getApplicationLabel(packages.getApplicationInfo(packageId, 0)).toString().trim().ifEmpty { null }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    @Synchronized
    override fun labelMatching(text: String): String? {
        val now = SystemClock.elapsedRealtime()
        // Apps get installed and removed; a minute-old list is fresh enough and avoids reloading per screenshot.
        if (labelsLoadedAt == Long.MIN_VALUE || now - labelsLoadedAt > REFRESH_MILLIS) {
            labels = loadLabels()
            labelsLoadedAt = now
        }
        return labels[text.trim().lowercase()]
    }

    private fun loadLabels(): Map<String, String> {
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packages.queryIntentActivities(launchable, 0).associate { info ->
            val label = info.activityInfo.applicationInfo.loadLabel(packages).toString().trim()
            label.lowercase() to label
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 60_000L
    }
}
