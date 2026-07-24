package com.febricahyaa.fluxlab

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutLegalTest {
    @Test
    fun markdownLoaderPreservesHeadingsParagraphsAndBullets() {
        val blocks = parseMarkdown("# Heading\n\nParagraph line one\nline two\n\n- First item")

        assertEquals(ContentBlock(ContentBlockKind.HEADING, "Heading", 1), blocks[0])
        assertEquals(ContentBlock(ContentBlockKind.PARAGRAPH, "Paragraph line one line two"), blocks[1])
        assertEquals(ContentBlock(ContentBlockKind.BULLET, "First item"), blocks[2])
    }

    @Test
    fun changelogHasLocalizedMaintainedCurrentBaseline() {
        val entry = ChangelogCatalog.entries.single()

        assertEquals("0.1.0", entry.versionName)
        assertTrue(entry.categories[ChangelogCategory.NEW].orEmpty().isNotEmpty())
        assertTrue(entry.indonesianCategories[ChangelogCategory.NEW].orEmpty().isNotEmpty())
    }

    @Test
    fun unconfiguredUpdateProviderNeverFabricatesReleaseData() = runBlocking {
        val current = AppVersionInfo(
            appName = "FluxLab",
            versionName = "0.1.0",
            versionCode = 1L,
            buildType = "debug",
            packageName = "com.febricahyaa.fluxlab",
            installTimeEpochMs = null,
            updateTimeEpochMs = null,
            supportedAbis = emptyList(),
            minSdk = 28,
            targetSdk = 36,
        )

        assertEquals(UpdateCheckState.SourceNotConfigured, UnconfiguredUpdateProvider().check(current))
    }
}
