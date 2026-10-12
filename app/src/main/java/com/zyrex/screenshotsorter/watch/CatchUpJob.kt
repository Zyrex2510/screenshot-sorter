package com.zyrex.screenshotsorter.watch

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.platform.SetupState

/** Runs about every 15 minutes: sorts what the watcher missed and brings the watcher back if the phone killed it. */
class CatchUpJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val graph = (application as SorterApp).graph
        if (!graph.prefs.enabled || !SetupState.hasStorage(this)) return false
        Thread {
            try {
                attempt("The catch-up sweep") { graph.sortNewScreenshots() }
                // The watcher comes back even when the sweep failed.
                attempt("Restarting the watcher") { WatchService.start(this) }
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    // Nobody can attach a debugger to the phone, so a failure leaves a log line instead of vanishing.
    // Only Exceptions are caught: an Error such as running out of memory should not be swallowed.
    private fun attempt(what: String, step: () -> Unit) {
        try {
            step()
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
        }
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val TAG = "CatchUpJob"
        private const val JOB_ID = 1
        private const val PERIOD_MILLIS = 15 * 60_000L

        /** Does nothing when the job is already scheduled, because scheduling again would restart its 15 minute period. */
        fun schedule(context: Context) {
            val scheduler = scheduler(context)
            // getPendingJob(id) needs Android 7; the list works on Android 6.
            if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CatchUpJob::class.java))
                .setPeriodic(PERIOD_MILLIS)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }

        fun cancel(context: Context) {
            scheduler(context).cancel(JOB_ID)
        }

        private fun scheduler(context: Context) =
            context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    }
}
