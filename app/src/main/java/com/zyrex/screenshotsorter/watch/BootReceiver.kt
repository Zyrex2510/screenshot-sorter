package com.zyrex.screenshotsorter.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.platform.SetupState

/** Brings sorting back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as SorterApp).graph
        if (!graph.prefs.enabled) return
        CatchUpJob.schedule(context)
        if (SetupState.hasStorage(context)) WatchService.start(context)
    }
}
