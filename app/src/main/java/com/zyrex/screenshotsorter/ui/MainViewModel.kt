package com.zyrex.screenshotsorter.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zyrex.screenshotsorter.SorterApp
import com.zyrex.screenshotsorter.core.AutostartScreens
import com.zyrex.screenshotsorter.core.LogEntry
import com.zyrex.screenshotsorter.core.Sorter
import com.zyrex.screenshotsorter.core.SweepResult
import com.zyrex.screenshotsorter.core.UsageNeed
import com.zyrex.screenshotsorter.platform.SetupState
import com.zyrex.screenshotsorter.watch.CatchUpJob
import com.zyrex.screenshotsorter.watch.WatchService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupUi(
    val storage: Boolean = false,
    val usage: Boolean = false,
    val usageNeeded: Boolean = true,
    val battery: Boolean = false,
    val notifications: Boolean = true,
    val asksNotifications: Boolean = false,
    val brandHint: String? = null,
)

sealed interface OldSort {
    data object Idle : OldSort
    data object Counting : OldSort
    data class Confirm(val count: Int) : OldSort
    data object Working : OldSort
    data class Done(val moved: Int) : OldSort
}

data class MainUi(
    val setup: SetupUi = SetupUi(),
    val enabled: Boolean = false,
    val recent: List<LogEntry> = emptyList(),
    val oldSort: OldSort = OldSort.Idle,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as SorterApp).graph
    private val setup = MutableStateFlow(SetupUi())
    private val enabled = MutableStateFlow(graph.prefs.enabled)
    private val oldSort = MutableStateFlow<OldSort>(OldSort.Idle)
    private var knownNames: List<String> = emptyList()

    val ui: StateFlow<MainUi> =
        combine(setup, enabled, graph.log.entries, oldSort) { s, e, recent, old -> MainUi(s, e, recent, old) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, MainUi())

    /** Re-reads every permission. Called each time the screen comes back, because they change in system settings. */
    fun refresh() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { readSetup(context) }
            setup.value = state
            if (enabled.value && state.storage) {
                // Opening the app also revives a watcher the phone may have killed.
                WatchService.start(context)
                CatchUpJob.schedule(context)
            }
            // Automatic sorting never runs while the switch is off. What the recheck would have covered then predates
            // the next switch-on, so it belongs to the "Sort old screenshots" button, and the flag below is updated anyway.
            if (state.usage && !graph.prefs.usageAccessSeen && enabled.value && state.storage) {
                // Usage access was just granted: screenshots given up on earlier can now be matched.
                // If the recheck fails, leave the flag alone so it is tried again the next time the screen opens.
                // It is an automatic pass, so it goes through the graph and leaves older screenshots to the button.
                askSorter<SweepResult?>(null) { graph.sortNewScreenshots(recheckAll = true) } ?: return@launch
            }
            graph.prefs.usageAccessSeen = state.usage
        }
    }

    fun setEnabled(on: Boolean) {
        if (on && !setup.value.storage) return
        val context = getApplication<Application>()
        // Written before the switch itself, so no pass can see sorting on with the old time. From now on, automatic
        // sorting leaves everything older than this to the "Sort old screenshots" button.
        if (on) graph.prefs.sortingSince = System.currentTimeMillis()
        graph.prefs.enabled = on
        enabled.value = on
        if (on) {
            WatchService.start(context)
            CatchUpJob.schedule(context)
        } else {
            WatchService.stop(context)
            CatchUpJob.cancel(context)
        }
    }

    fun countOld() {
        oldSort.value = OldSort.Counting
        viewModelScope.launch {
            oldSort.value = OldSort.Confirm(askSorter(0) { countSortable() })
        }
    }

    fun confirmOld() {
        oldSort.value = OldSort.Working
        viewModelScope.launch {
            // Old screenshots keep the ten second rule too: one that changed a moment ago may still be open in an editor.
            oldSort.value = OldSort.Done(askSorter(0) { sortPending(recheckAll = true).moved })
        }
    }

    fun dismissOld() {
        oldSort.value = OldSort.Idle
    }

    /**
     * Runs [work] on the sorter off the main thread. An error in the sorting pass must never end the app from here,
     * so it is logged and [whenItFails] is used instead; the watcher and the catch-up job have their own guards.
     */
    private suspend fun <T> askSorter(whenItFails: T, work: Sorter.() -> T): T =
        try {
            withContext(Dispatchers.IO) { graph.sorter.work() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The sorting pass failed", e)
            whenItFails
        }

    private fun readSetup(context: Context): SetupUi {
        val storage = SetupState.hasStorage(context)
        // Listing every screenshot is slow on a full gallery, so keep the first answer that has names in it.
        if (storage && knownNames.isEmpty()) knownNames = graph.finder.recentNames(NAME_SAMPLE)
        return SetupUi(
            storage = storage,
            usage = SetupState.hasUsageAccess(context),
            usageNeeded = UsageNeed.isNeeded(knownNames, Build.MANUFACTURER, graph.identifier::nameIdentifies),
            battery = SetupState.ignoresBatteryLimits(context),
            notifications = SetupState.notificationsAllowed(context),
            asksNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            brandHint = AutostartScreens.hint(Build.MANUFACTURER),
        )
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val NAME_SAMPLE = 30
    }
}
