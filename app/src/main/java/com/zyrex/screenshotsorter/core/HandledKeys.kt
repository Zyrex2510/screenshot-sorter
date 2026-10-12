package com.zyrex.screenshotsorter.core

import java.io.File

/** A small piece of saved text. On the phone it is backed by app preferences. */
interface TextStore {
    fun read(): String
    fun write(text: String)
}

/**
 * Remembers screenshots that were looked at and could not be matched to an app,
 * so they are not looked at again on every pass.
 */
class HandledKeys(private val store: TextStore, private val capacity: Int = 2000) {
    private val keys = LinkedHashSet(store.read().split('\n').filter { it.isNotEmpty() })

    @Synchronized
    fun contains(key: String): Boolean = key in keys

    @Synchronized
    fun add(key: String) {
        if (!keys.add(key)) return
        while (keys.size > capacity) keys.remove(keys.first())
        store.write(keys.joinToString("\n"))
    }

    companion object {
        /** Changes when the file is edited or replaced, so an edited screenshot gets a fresh look. */
        fun keyFor(file: File): String =
            "${file.absolutePath}|${file.lastModified()}|${file.length()}".replace('\n', ' ')
    }
}
