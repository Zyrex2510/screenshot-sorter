package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotNameParserTest {
    @Test
    fun tails() {
        val cases = mapOf(
            // Xiaomi: the friend's real filename.
            "Screenshot_2026-10-09-10-12-05-243_pdf.reader.pdfviewer.pdfeditor.jpg" to "pdf.reader.pdfviewer.pdfeditor",
            "Screenshot_2026-10-09-14-22-33-456_org.telegram.messenger.jpg" to "org.telegram.messenger",
            // Samsung and Huawei.
            "Screenshot_20261009_101205_Telegram.jpg" to "Telegram",
            "Screenshot_20261009_101205_Samsung Internet.jpg" to "Samsung Internet",
            "Screenshot_20261009-101205_Chrome.jpg" to "Chrome",
            "Screenshot_20261009_101205_2GIS.jpg" to "2GIS",
            "Screenshot_20261009_101205_com.whatsapp.PNG" to "com.whatsapp",
            // Endings that are not apps still come back as text; the caller decides.
            "Screenshot_20261009_101205_1.jpg" to "1",
            "Screenshot_20261009-101205_display-1.png" to "display-1",
            "Screenshot_2026-10-09-10-12-05-24_e39d2c7de19156b0683cd93e8735f348.jpg" to "e39d2c7de19156b0683cd93e8735f348",
            // No ending.
            "Screenshot_20261009-101205.png" to null,
            "Screenshot_20261009_101205.jpg" to null,
            "Screenshot_20261009-101205~2.png" to null,
            // Not a screenshot name.
            "IMG_20261009_101205.jpg" to null,
            "holiday.jpg" to null,
            "" to null,
        )
        for ((name, expected) in cases) {
            assertEquals(name, expected, ScreenshotNameParser.tail(name))
        }
    }

    @Test
    fun packageIds() {
        for (id in listOf("org.telegram.messenger", "com.whatsapp", "pdf.reader.pdfviewer.pdfeditor", "Booking.com", "a.b_c.d1")) {
            assertTrue(id, ScreenshotNameParser.looksLikePackageId(id))
        }
        for (text in listOf("Telegram", "Samsung Internet", "1", "1.2", "display-1", "com.foo.", ".com.foo", "com..foo", "")) {
            assertFalse(text, ScreenshotNameParser.looksLikePackageId(text))
        }
    }
}
