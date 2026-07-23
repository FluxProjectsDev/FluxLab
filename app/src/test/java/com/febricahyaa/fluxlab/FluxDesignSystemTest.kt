package com.febricahyaa.fluxlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FluxDesignSystemTest {
    @Test
    fun metricIdentitiesRemainDistinctInBothAppearances() {
        assertNotEquals(FluxMetricColors.dark.cpu, FluxMetricColors.dark.gpu)
        assertNotEquals(FluxMetricColors.dark.gpu, FluxMetricColors.dark.memory)
        assertNotEquals(FluxMetricColors.light.cpu, FluxMetricColors.light.gpu)
        assertNotEquals(FluxMetricColors.light.storage, FluxMetricColors.light.battery)
    }

    @Test
    fun settingsDefaultToSystemAppearanceAndFluxColorStyle() {
        val settings = AppSettings()
        assertEquals(ThemeSetting.SYSTEM, settings.theme)
        assertEquals(ColorStyle.FLUX, settings.colorStyle)
    }
}
