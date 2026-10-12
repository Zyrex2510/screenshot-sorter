package com.zyrex.screenshotsorter.core

import java.io.File

/** Finds screenshots by plain file listing, which works the same on every Android version. */
class ScreenshotFinder(private val storageRoot: File) {

    /** Screenshot folders that exist: `DCIM` or `Pictures`, then `Screenshots` or `Screenshot`, any letter case. */
    fun folders(): List<File> =
        children(storageRoot)
            .filter { it.isDirectory && it.name.lowercase() in PARENTS }
            .flatMap { parent -> children(parent).filter { it.isDirectory && it.name.lowercase() in NAMES } }

    /** Images directly inside the screenshot folders, oldest first. Sorted ones are in subfolders and are left out. */
    fun unsorted(): List<File> = folders().flatMap(::imagesIn).sortedBy { it.lastModified() }

    /** Names of the newest screenshots, sorted or not. Used only to learn how this phone names them. */
    fun recentNames(limit: Int): List<String> =
        folders()
            .flatMap { folder -> imagesIn(folder) + children(folder).filter { it.isDirectory }.flatMap(::imagesIn) }
            .sortedByDescending { it.lastModified() }
            .take(limit)
            .map { it.name }

    private fun imagesIn(folder: File): List<File> =
        children(folder).filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in EXTENSIONS }

    // listFiles() returns null when the folder is missing or the app may not read it.
    private fun children(folder: File): List<File> = folder.listFiles()?.toList() ?: emptyList()

    private companion object {
        val PARENTS = setOf("dcim", "pictures")
        val NAMES = setOf("screenshots", "screenshot")
        val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
