package com.zyrex.screenshotsorter.ui

import com.github.takahirom.roborazzi.captureRoboImage
import com.zyrex.screenshotsorter.core.AutostartScreens
import com.zyrex.screenshotsorter.core.LogEntry
import com.zyrex.screenshotsorter.core.Outcome
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainScreenShots {
    private val now = System.currentTimeMillis()
    private val working = MainUi(
        setup = SetupUi(
            storage = true,
            usage = false,
            usageNeeded = false,
            battery = true,
            notifications = false,
            asksNotifications = true,
            brandHint = AutostartScreens.hint("Xiaomi"),
        ),
        enabled = true,
        recent = listOf(
            LogEntry(now - 60_000, "Screenshot_2026-10-10-14-22-33-456_org.telegram.messenger.jpg", Outcome.MOVED, "Telegram"),
            LogEntry(now - 9 * 60_000, "Screenshot_2026-10-10-14-13-02-120_com.duolingo.jpg", Outcome.MOVED, "Duolingo"),
            LogEntry(now - 40 * 60_000, "Screenshot_2026-10-10-13-41-55-803_pdf.reader.pdfviewer.pdfeditor.jpg", Outcome.MOVED, "PDF Reader - Hi Read"),
            LogEntry(now - 3 * 3_600_000L, "Screenshot_20261010-110501.png", Outcome.UNKNOWN_APP, ""),
            LogEntry(now - 5 * 3_600_000L, "Screenshot_2026-10-10-09-00-00-001_com.google.android.youtube.jpg", Outcome.FAILED, "Could not move the file"),
        ),
        oldSort = OldSort.Idle,
    )

    @Test
    fun firstLaunch() = shoot("first-launch.png", MainUi())

    @Test
    fun workingLight() = shoot("working-light.png", working)

    @Test
    @Config(qualifiers = "+night")
    fun workingDark() = shoot("working-dark.png", working)

    private fun shoot(name: String, ui: MainUi) {
        captureRoboImage(File(System.getProperty("shots.dir"), name).path) {
            SorterTheme { MainScreen(ui, MainActions()) }
        }
    }
}
