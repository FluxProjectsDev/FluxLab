package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.VariabilityClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetReliabilityTest {
    @Test fun `presets expose bounded configurations and extended warning`() {
        assertEquals(3, BenchmarkPresetCatalog.all.size)
        assertTrue(BenchmarkPresetCatalog.description(BenchmarkPreset.STANDARD).config.measuredRepetitionCount > 1)
        assertTrue(BenchmarkPresetCatalog.description(BenchmarkPreset.EXTENDED).warning!!.contains("heat"))
        assertTrue(BenchmarkPresetCatalog.all.all { it.config.storageAllocationLimitBytes > 0 })
    }

    @Test fun `zero variance is stable while single sample is unreliable`() {
        val stable = BenchmarkStatistics.calculate(listOf(10.0, 10.0, 10.0), latencyMetric = true)
        assertEquals(VariabilityClassification.STABLE, stable.variability)
        assertEquals(10.0, stable.p95!!, 0.001)
        val single = BenchmarkStatistics.calculate(listOf(42.0), latencyMetric = true)
        assertEquals(VariabilityClassification.UNRELIABLE, single.variability)
        assertEquals(42.0, single.p95!!, 0.001)
    }
}
