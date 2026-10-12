package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderNamesTest {
    @Test
    fun keepsOrdinaryNames() {
        for (name in listOf("Telegram", "PDF Reader - Hi Read", "Телеграм", "Fun 🎉", "2GIS")) {
            assertEquals(name, FolderNames.clean(name))
        }
    }

    @Test
    fun removesCharactersFoldersCannotHave() {
        assertEquals("A B Test", FolderNames.clean("A/B: Test?"))
        assertEquals("a b c d e f g h i", FolderNames.clean("a\\b/c:d*e?f\"g<h>i|"))
        assertEquals("Line Break", FolderNames.clean("Line\nBreak"))
        assertEquals("WhatsApp", FolderNames.clean("  WhatsApp  "))
    }

    @Test
    fun neverMakesAHiddenOrEmptyFolder() {
        assertEquals("hidden", FolderNames.clean("..hidden"))
        assertEquals("Name", FolderNames.clean("Name. "))
        assertEquals("Other", FolderNames.clean("///"))
        assertEquals("Other", FolderNames.clean(""))
        assertEquals("Other", FolderNames.clean(" . "))
    }

    @Test
    fun cutsLongNamesWithoutSplittingAnEmoji() {
        assertEquals(60, FolderNames.clean("x".repeat(100)).length)
        // 59 letters then an emoji: the emoji is two chars, so a cut at 60 would split it.
        assertEquals("x".repeat(59), FolderNames.clean("x".repeat(59) + "🎉"))
    }
}

class UniqueNameTest {
    @Test
    fun keepsAFreeName() {
        assertEquals("a.jpg", UniqueName.pick("a.jpg") { false })
    }

    @Test
    fun addsTheFirstFreeNumberBeforeTheExtension() {
        val taken = setOf("a.jpg", "a (1).jpg", "a (2).jpg")
        assertEquals("a (3).jpg", UniqueName.pick("a.jpg") { it in taken })
    }

    @Test
    fun usesTheLastDotAsTheExtension() {
        val name = "Screenshot_2026-10-09-14-22-33-456_org.telegram.messenger.jpg"
        assertEquals(
            "Screenshot_2026-10-09-14-22-33-456_org.telegram.messenger (1).jpg",
            UniqueName.pick(name) { it == name },
        )
    }

    @Test
    fun handlesNamesWithoutAnExtension() {
        assertEquals("notes (1)", UniqueName.pick("notes") { it == "notes" })
    }
}
