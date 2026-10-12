package com.zyrex.screenshotsorter.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import com.zyrex.screenshotsorter.startAsAFreshInstall
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class MainScreenSmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    // Robolectric grants every permission by default; a freshly installed app has none. The phone must be set
    // up before the activity starts, so the activity is launched by hand.
    private fun onFirstLaunch(check: () -> Unit) {
        startAsAFreshInstall(RuntimeEnvironment.getApplication())
        ActivityScenario.launch(MainActivity::class.java).use { check() }
    }

    // The list only builds the rows near the top of a small screen, so bring a row into view before asking about it.
    private fun showInList(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun firstLaunchShowsTheSwitchOffAndTheSetupList() = onFirstLaunch {
        compose.onNodeWithText("Sorting is off").assertIsDisplayed()
        compose.onNodeWithText("Allow files access below first.").assertIsDisplayed()
        showInList("Files access")
        compose.onNodeWithText("Files access").assertIsDisplayed()
        showInList("Run in the background")
        compose.onNodeWithText("Run in the background").assertIsDisplayed()
    }

    @Test
    fun sortingOldScreenshotsWaitsForFilesAccess() = onFirstLaunch {
        showInList("Sort old screenshots")
        compose.onNodeWithText("Sort old screenshots").assertIsNotEnabled()
    }
}
