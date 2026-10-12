package com.zyrex.screenshotsorter.core

object FolderNames {
    const val MAX_LENGTH = 60
    const val FALLBACK = "Other"

    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val spaces = Regex("""\s+""")

    /** Turns an app's display name into a folder name that every phone accepts and no gallery hides. */
    fun clean(label: String): String {
        val tidy = label.replace(illegal, " ").replace(spaces, " ").trim().trimStart('.').trim()
        var short = tidy.take(MAX_LENGTH)
        if (short.isNotEmpty() && short.last().isHighSurrogate()) short = short.dropLast(1)
        return short.trimEnd('.', ' ').ifEmpty { FALLBACK }
    }
}

object UniqueName {
    /** [fileName] if it is free, otherwise "name (1).ext", "name (2).ext", ... until [taken] says no. */
    fun pick(fileName: String, taken: (String) -> Boolean): String {
        if (!taken(fileName)) return fileName
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val extension = if (dot > 0) fileName.substring(dot) else ""
        var number = 1
        while (taken("$stem ($number)$extension")) number++
        return "$stem ($number)$extension"
    }
}
