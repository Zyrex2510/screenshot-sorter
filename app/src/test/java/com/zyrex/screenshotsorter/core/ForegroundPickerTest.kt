package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPickerTest {
    private val events = listOf(
        ForegroundEvent("c", 300),
        ForegroundEvent("a", 100),
        ForegroundEvent("b", 200),
    )

    @Test
    fun picksTheLatestAppNotAfterTheMoment() {
        assertEquals("b", ForegroundPicker.pick(events, 250) { false })
        assertEquals("c", ForegroundPicker.pick(events, 300) { false })
        assertEquals("c", ForegroundPicker.pick(events, 9_999) { false })
    }

    @Test
    fun nothingBeforeTheFirstEvent() {
        assertNull(ForegroundPicker.pick(events, 50) { false })
        assertNull(ForegroundPicker.pick(emptyList(), 50) { false })
    }

    @Test
    fun skipsIgnoredApps() {
        assertEquals("b", ForegroundPicker.pick(events, 350) { it == "c" })
    }

    @Test
    fun knowsScreenshotToolsAndItself() {
        val own = "com.zyrex.screenshotsorter"
        for (tool in listOf(own, "com.android.systemui", "com.miui.screenshot", "com.oplus.screenshot", "com.samsung.android.app.smartcapture")) {
            assertTrue(tool, ForegroundPicker.isScreenshotTool(tool, own))
        }
        for (app in listOf("org.telegram.messenger", "com.miui.home", "com.duolingo")) {
            assertFalse(app, ForegroundPicker.isScreenshotTool(app, own))
        }
    }
}
