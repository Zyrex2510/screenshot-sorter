package com.zyrex.screenshotsorter.core

data class ForegroundEvent(val packageName: String, val timeMillis: Long)

object ForegroundPicker {
    private val toolWords = listOf("screenshot", "screencapture", "smartcapture")

    /** The app that was on screen at [atMillis]: the latest one brought forward at or before it. */
    fun pick(events: List<ForegroundEvent>, atMillis: Long, ignored: (String) -> Boolean): String? =
        events.asSequence()
            .filter { it.timeMillis <= atMillis && !ignored(it.packageName) }
            .maxByOrNull { it.timeMillis }
            ?.packageName

    /** Screenshot tools pop up over the real app, so they must not be mistaken for it. */
    fun isScreenshotTool(packageName: String, ownPackage: String): Boolean {
        val name = packageName.lowercase()
        return packageName == ownPackage || name == "com.android.systemui" || toolWords.any { it in name }
    }
}
