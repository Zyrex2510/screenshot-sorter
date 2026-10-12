package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandledKeysTest {
    @Test
    fun remembersWhatWasAdded() {
        val keys = HandledKeys(MemoryStore())
        assertFalse(keys.contains("a"))
        keys.add("a")
        assertTrue(keys.contains("a"))
    }

    @Test
    fun survivesARestart() {
        val store = MemoryStore()
        HandledKeys(store).apply {
            add("a")
            add("b")
        }
        val reopened = HandledKeys(store)
        assertTrue(reopened.contains("a"))
        assertTrue(reopened.contains("b"))
    }

    @Test
    fun forgetsTheOldestWhenFull() {
        val keys = HandledKeys(MemoryStore(), capacity = 2)
        keys.add("a")
        keys.add("b")
        keys.add("c")
        assertFalse(keys.contains("a"))
        assertTrue(keys.contains("b"))
        assertTrue(keys.contains("c"))
    }
}
