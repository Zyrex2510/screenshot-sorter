package com.zyrex.screenshotsorter.platform

import android.content.Context
import com.zyrex.screenshotsorter.core.TextStore

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The big on/off switch. */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /**
     * When sorting was last switched on, in milliseconds since 1970. Automatic sorting leaves screenshots that
     * last changed before this alone; they wait for "Sort old screenshots". 0 means no limit.
     */
    var sortingSince: Long
        get() = prefs.getLong("sortingSince", 0)
        set(value) = prefs.edit().putLong("sortingSince", value).apply()

    /** Whether usage access was already granted the last time the screen looked. */
    var usageAccessSeen: Boolean
        get() = prefs.getBoolean("usageAccessSeen", false)
        set(value) = prefs.edit().putBoolean("usageAccessSeen", value).apply()

    fun textStore(key: String): TextStore = object : TextStore {
        override fun read(): String = prefs.getString(key, "") ?: ""

        override fun write(text: String) {
            prefs.edit().putString(key, text).apply()
        }
    }
}
