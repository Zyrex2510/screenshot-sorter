package com.zyrex.screenshotsorter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdentifierTest {
    private val installed = FakeInstalledApps(
        mapOf(
            "org.telegram.messenger" to "Telegram",
            "pdf.reader.pdfviewer.pdfeditor" to "PDF Reader - Hi Read",
            "com.odd" to "A/B: Odd",
        ),
    )
    private val history = FakeHistory()
    private val identifier = AppIdentifier(installed, history)

    @Test
    fun xiaomiNameOfAnInstalledAppGivesItsDisplayName() {
        val name = "Screenshot_2026-10-09-10-12-05-243_pdf.reader.pdfviewer.pdfeditor.jpg"
        assertEquals("PDF Reader - Hi Read", identifier.folderFor(name, 0))
        assertEquals(0, history.calls)
    }

    @Test
    fun xiaomiNameOfAnUninstalledAppFallsBackToTheAppId() {
        assertEquals("com.gone.app", identifier.folderFor("Screenshot_2026-10-09-10-12-05-243_com.gone.app.jpg", 0))
    }

    @Test
    fun samsungNameMatchesAnInstalledAppIgnoringCase() {
        assertEquals("Telegram", identifier.folderFor("Screenshot_20261009_101205_telegram.jpg", 0))
    }

    @Test
    fun tailThatIsNotAnAppFallsBackToHistory() {
        history.packageName = "org.telegram.messenger"
        for (name in listOf("Screenshot_20261009_101205_1.jpg", "Screenshot_20261009-101205_display-1.png")) {
            assertEquals(name, "Telegram", identifier.folderFor(name, 0))
        }
    }

    @Test
    fun plainNameUsesHistory() {
        history.packageName = "org.telegram.messenger"
        assertEquals("Telegram", identifier.folderFor("Screenshot_20261009-101205.png", 0))
        history.packageName = "com.not.visible"
        assertEquals("com.not.visible", identifier.folderFor("Screenshot_20261009-101205.png", 0))
    }

    @Test
    fun nothingKnownGivesNull() {
        assertNull(identifier.folderFor("Screenshot_20261009-101205.png", 0))
        assertNull(identifier.folderFor("Screenshot_20261009_101205_1.jpg", 0))
    }

    @Test
    fun folderNamesAreCleaned() {
        assertEquals("A B Odd", identifier.folderFor("Screenshot_2026-10-09-10-12-05-243_com.odd.jpg", 0))
    }

    @Test
    fun nameIdentifiesOnlyWhenTheFilenameIsEnough() {
        assertTrue(identifier.nameIdentifies("Screenshot_2026-10-09-10-12-05-243_org.telegram.messenger.jpg"))
        assertTrue(identifier.nameIdentifies("Screenshot_20261009_101205_Telegram.jpg"))
        assertFalse(identifier.nameIdentifies("Screenshot_20261009-101205.png"))
        assertFalse(identifier.nameIdentifies("Screenshot_20261009_101205_1.jpg"))
    }
}
