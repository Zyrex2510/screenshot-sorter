package com.zyrex.screenshotsorter.core

import java.io.File

data class SweepResult(val moved: Int, val tooNew: Int)

/** One pass over the waiting screenshots. Shared by the watcher, the catch-up job and "Sort old screenshots". */
class Sorter(
    private val finder: ScreenshotFinder,
    private val identifier: AppIdentifier,
    private val mover: FileMover,
    private val handled: HandledKeys,
    private val log: ActivityLog,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Files changed less than [minAgeMillis] ago are left for the next pass, so the system's edit/share
     * popup keeps working. [recheckAll] also retries the ones that could not be matched earlier.
     *
     * Files last changed before [notBeforeMillis] are skipped as if they were not there: not moved, not counted
     * as too new, not remembered as looked at and not logged, so a later pass without the limit still finds them.
     */
    @Synchronized
    fun sortPending(recheckAll: Boolean = false, minAgeMillis: Long = MIN_AGE_MILLIS, notBeforeMillis: Long = 0): SweepResult {
        var moved = 0
        var tooNew = 0
        for (file in finder.unsorted()) {
            if (file.lastModified() < notBeforeMillis) continue
            val age = now() - file.lastModified()
            if (age < minAgeMillis) {
                tooNew++
                continue
            }
            val key = HandledKeys.keyFor(file)
            if (!recheckAll && handled.contains(key)) continue

            val folder = identifier.folderFor(file.name, file.lastModified())
            if (folder == null) {
                handled.add(key)
                // An old backlog would flood the recent list; only say so for ones just taken.
                if (age < RECENT_MILLIS) log.record(LogEntry(now(), file.name, Outcome.UNKNOWN_APP, ""))
                continue
            }
            when (val result = mover.move(file, File(file.parentFile, folder))) {
                is MoveResult.Moved -> {
                    moved++
                    log.record(LogEntry(now(), file.name, Outcome.MOVED, folder))
                }
                is MoveResult.Failed -> log.record(LogEntry(now(), file.name, Outcome.FAILED, result.reason))
            }
        }
        return SweepResult(moved, tooNew)
    }

    /** How many waiting screenshots can be matched to an app right now. */
    fun countSortable(): Int =
        finder.unsorted().count { identifier.folderFor(it.name, it.lastModified()) != null }

    companion object {
        const val MIN_AGE_MILLIS = 10_000L
        private const val RECENT_MILLIS = 10 * 60_000L
    }
}
