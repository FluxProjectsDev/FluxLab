package com.febricahyaa.fluxlab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationHierarchyTest {
    @Test
    fun topLevelRoutesKeepBottomNavigation() {
        listOf("overview", "tests", "sessions", "reports", "settings").forEach { route ->
            assertTrue(route, isTopLevelRoute(route))
        }
    }

    @Test
    fun metricAndSessionDetailsHideBottomNavigation() {
        listOf(
            "overview/cpu", "overview/gpu", "overview/memory", "overview/storage",
            "overview/thermal", "overview/battery", "overview/profile", "overview/flux",
            "overview/synthesiscore", "overview/root", "sessions/session-id", "benchmark/active",
            "about", "about/version", "about/update", "about/changelog", "about/licenses",
            "about/privacy", "about/terms", "about/credits", "about/support",
        ).forEach { route ->
            assertFalse(route, isTopLevelRoute(route))
        }
    }

    @Test
    fun nullAndUnknownRoutesAreNotTopLevel() {
        assertFalse(isTopLevelRoute(null))
        assertFalse(isTopLevelRoute("overview/unknown"))
    }
}
