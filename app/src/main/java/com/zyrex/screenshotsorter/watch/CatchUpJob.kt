package com.zyrex.screenshotsorter.watch

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.platform.SetupState

/** Runs about every 15 minutes: sorts what the watcher missed and brings the watcher back if the phone killed it. */
class CatchUpJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val graph = (application as SorterApp).graph
        if (!graph.prefs.enabled || !SetupState.hasStorage(this)) return false
        Thread {
            runCatching { graph.sorter.sortPending() }
            WatchService.start(this)
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 1
        private const val PERIOD_MILLIS = 15 * 60_000L

        fun schedule(context: Context) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CatchUpJob::class.java))
                .setPeriodic(PERIOD_MILLIS)
                .setPersisted(true)
                .build()
            scheduler(context).schedule(job)
        }

        fun cancel(context: Context) {
            scheduler(context).cancel(JOB_ID)
        }

        private fun scheduler(context: Context) =
            context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    }
}
