package com.zyrex.screenshotsorter.core

interface InstalledApps {
    /** Display name of the installed app with this ID, or null when it is not installed. */
    fun labelForPackage(packageId: String): String?

    /** The display name of an installed app that equals [text] ignoring case, or null. */
    fun labelMatching(text: String): String?
}

interface ForegroundAppFinder {
    /** ID of the app that was on screen at that moment, or null when the phone cannot say. */
    fun packageAt(timeMillis: Long): String?
}

class AppIdentifier(
    private val installed: InstalledApps,
    private val foreground: ForegroundAppFinder,
) {
    /** Folder name for this screenshot, or null when the app cannot be worked out. */
    fun folderFor(fileName: String, createdAtMillis: Long): String? {
        fromName(fileName)?.let { return FolderNames.clean(it) }
        val packageId = foreground.packageAt(createdAtMillis) ?: return null
        return FolderNames.clean(installed.labelForPackage(packageId) ?: packageId)
    }

    /** True when the filename alone names the app, so usage history is not needed for it. */
    fun nameIdentifies(fileName: String): Boolean = fromName(fileName) != null

    private fun fromName(fileName: String): String? {
        val tail = ScreenshotNameParser.tail(fileName) ?: return null
        if (ScreenshotNameParser.looksLikePackageId(tail)) {
            // An ID of an app that was uninstalled still makes a usable folder name.
            return installed.labelForPackage(tail) ?: installed.labelMatching(tail) ?: tail
        }
        return installed.labelMatching(tail)
    }
}
