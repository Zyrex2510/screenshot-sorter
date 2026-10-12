package com.zyrex.screenshotsorter.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.platform.SetupState

/** Brings sorting back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The manifest exports this receiver so the system can reach it, which lets any app send it an intent as well.
        // Only the actions the manifest lists may bring the watcher back.
        if (intent.action !in ACTIONS) return
        val graph = (context.applicationContext as SorterApp).graph
        if (!graph.prefs.enabled) return
        CatchUpJob.schedule(context)
        if (SetupState.hasStorage(context)) WatchService.start(context)
    }

    private companion object {
        // Xiaomi, HTC and some others send this instead of BOOT_COMPLETED after a fast boot.
        const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, QUICKBOOT_POWERON, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
