package com.febricahyaa.fluxlab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportSelectionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun reportsExplainWhyExportIsDisabledWithoutSelection() {
        composeRule.onNodeWithText("Reports").performClick()
        composeRule.onNodeWithText("No session selected").assertIsDisplayed()
        composeRule.onNodeWithText("Select a completed benchmark session before exporting a report.").assertIsDisplayed()
    }
}
