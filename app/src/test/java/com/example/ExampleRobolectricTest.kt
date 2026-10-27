package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndNavigatesBetweenAllFiveStudioTabs() {
        composeTestRule.onNodeWithTag("nav_tab_record").assertExists()
        composeTestRule.onNodeWithTag("nav_tab_editor").performClick()
        composeTestRule.onNodeWithTag("nav_tab_subtitles").performClick()
        composeTestRule.onNodeWithTag("nav_tab_video").performClick()
        composeTestRule.onNodeWithTag("nav_tab_hub").performClick()
    }
}
