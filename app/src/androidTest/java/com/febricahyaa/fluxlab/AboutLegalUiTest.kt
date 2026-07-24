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
class AboutLegalUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aboutHubShowsIdentityDesignerAndInformationRows() {
        openAbout()

        composeRule.onNodeWithText("About & Legal").assertIsDisplayed()
        composeRule.onNodeWithText("Measure every change. Verify every gain.").assertIsDisplayed()
        composeRule.onNodeWithText("FebriCahyaa").assertIsDisplayed()
        composeRule.onNodeWithText("Version Information").assertIsDisplayed()
        composeRule.onNodeWithText("Open Source Licenses").assertIsDisplayed()
    }

    @Test
    fun versionInformationShowsRuntimeIdentity() {
        openAbout()
        composeRule.onNodeWithText("Version Information").performClick()

        composeRule.onNodeWithText("Package name").assertIsDisplayed()
        composeRule.onNodeWithText("Build type").assertIsDisplayed()
        composeRule.onNodeWithText("Show advanced details").assertIsDisplayed()
    }

    @Test
    fun changelogEntryExpandsStructuredReleaseNotes() {
        openAbout()
        composeRule.onNodeWithText("Changelog").performClick()
        composeRule.onNodeWithText("0.1.0").performClick()

        composeRule.onNodeWithText("New").assertIsDisplayed()
        composeRule.onNodeWithText("Improved").assertIsDisplayed()
        composeRule.onNodeWithText("Known limitations").assertIsDisplayed()
    }

    @Test
    fun updateScreenReportsUnconfiguredSourceHonestly() {
        openAbout()
        composeRule.onNodeWithText("Update Information").performClick()

        composeRule.onNodeWithText("Update source not configured").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun legalScreenRendersMaintainedContent() {
        openAbout()
        composeRule.onNodeWithText("Privacy Policy").performClick()

        composeRule.onNodeWithText("Privacy Policy").assertIsDisplayed()
        composeRule.onNodeWithText("Data kept on the device").assertIsDisplayed()
    }

    @Test
    fun creditsAndSupportDestinationsAreReachable() {
        openAbout()
        composeRule.onNodeWithText("FebriCahyaa").performClick()
        composeRule.onNodeWithText("No external designer profile is configured.").assertIsDisplayed()
        composeRule.onNodeWithText("Support Development").performClick()
        composeRule.onNodeWithText("Open project page").assertIsDisplayed()
    }

    private fun openAbout() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("About & Legal").performClick()
    }
}
