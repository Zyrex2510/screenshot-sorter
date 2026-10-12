package com.zyrex.screenshotsorter.core

object UsageNeed {
    private val brandsThatNameTheApp = setOf("xiaomi", "redmi", "poco", "samsung", "huawei", "honor")

    /**
     * Whether this phone needs the Usage access permission. It does not when its screenshot filenames
     * already say which app they came from. With no screenshots to look at yet, go by the brand.
     */
    fun isNeeded(recentNames: List<String>, manufacturer: String, nameIdentifies: (String) -> Boolean): Boolean {
        if (recentNames.isEmpty()) return manufacturer.trim().lowercase() !in brandsThatNameTheApp
        return recentNames.count(nameIdentifies) * 2 < recentNames.size
    }
}
