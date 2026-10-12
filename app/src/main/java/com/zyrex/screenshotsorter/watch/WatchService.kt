package com.zyrex.screenshotsorter.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zyrex.screenshotsorter.R
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.core.Sorter
import com.zyrex.screenshotsorter.platform.SetupState
import com.zyrex.screenshotsorter.ui.MainActivity
import java.util.concurrent.atomic.AtomicBoolean

/** Stays alive as a foreground service and sorts each new screenshot shortly after it appears. */
class WatchService : Service() {
    private var thread: HandlerThread? = null
    // The gallery's observer calls in on the worker thread, so this is read from there.
    @Volatile
    private var handler: Handler? = null
    private var observer: ContentObserver? = null
    private val sweepQueued = AtomicBoolean(false)
    private val sweep = Runnable { runSweep() }

    override fun onCreate() {
        super.onCreate()
        if (!goForeground()) {
            // Android refused (the app is in the background). The catch-up job covers until the app is opened.
            stopSelf()
            return
        }
        val worker = HandlerThread("screenshot-watch").apply { start() }
        val workerHandler = Handler(worker.looper)
        val mediaObserver = object : ContentObserver(workerHandler) {
            override fun onChange(selfChange: Boolean) = queueSweep(Sorter.MIN_AGE_MILLIS + SETTLE_MILLIS)
        }
        // Everything the observer needs is in place before it can report a change. Registering comes before the
        // first sweep, so a screenshot taken in between either shows up in that sweep or triggers another.
        thread = worker
        handler = workerHandler
        observer = mediaObserver
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        queueSweep(0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Opening the app asks for the watcher again. If it is already running, that is the cue for a catch-up pass.
        queueSweep(0)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observer?.let(contentResolver::unregisterContentObserver)
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        super.onDestroy()
    }

    // One queued sweep is enough: it handles everything old enough and re-queues itself for the rest.
    private fun queueSweep(delayMillis: Long) {
        // With no handler there is nothing to post to, and marking a sweep as queued would block every later one.
        val target = handler ?: return
        if (sweepQueued.compareAndSet(false, true)) target.postDelayed(sweep, delayMillis)
    }

    private fun runSweep() {
        // Cleared first, so a change that arrives while this pass runs still queues the next one.
        sweepQueued.set(false)
        try {
            if (!SetupState.hasStorage(this)) return
            val result = (application as SorterApp).graph.sortNewScreenshots()
            if (result.tooNew > 0) queueSweep(Sorter.MIN_AGE_MILLIS)
        } catch (e: Exception) {
            // The pass has no catch of its own, and an exception here would end the watcher's thread and crash the app
            // in the background. What was left waits for the next change or the next catch-up job.
            Log.w(TAG, "A sweep failed", e)
        }
    }

    private fun goForeground(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sort)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        private const val TAG = "WatchService"
        private const val CHANNEL_ID = "sorting"
        private const val NOTIFICATION_ID = 1
        private const val SETTLE_MILLIS = 500L

        /** Starts watching. False when Android refuses to start a service from the background. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}
