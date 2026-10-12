package com.zyrex.screenshotsorter.core

class FakeInstalledApps(private val byPackage: Map<String, String>) : InstalledApps {
    override fun labelForPackage(packageId: String): String? = byPackage[packageId]

    override fun labelMatching(text: String): String? =
        byPackage.values.firstOrNull { it.equals(text.trim(), ignoreCase = true) }
}

class FakeHistory(var packageName: String? = null) : ForegroundAppFinder {
    var calls = 0

    override fun packageAt(timeMillis: Long): String? {
        calls++
        return packageName
    }
}

class MemoryStore(var text: String = "") : TextStore {
    override fun read(): String = text

    override fun write(text: String) {
        this.text = text
    }
}
