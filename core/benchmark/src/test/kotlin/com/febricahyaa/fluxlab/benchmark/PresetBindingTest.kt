package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.BenchmarkPresetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PresetBindingTest {
    @Test
    fun eachPresetOwnsItsMeasuredRepetitionsAndStorageAllocation() {
        assertEquals(5, BenchmarkPresetConfig.forPreset(BenchmarkPreset.QUICK).measuredRepetitionCount)
        assertEquals(32L * 1_048_576L, BenchmarkPresetConfig.forPreset(BenchmarkPreset.QUICK).storageAllocationLimitBytes)
        assertEquals(9, BenchmarkPresetConfig.forPreset(BenchmarkPreset.STANDARD).measuredRepetitionCount)
        assertEquals(192L * 1_048_576L, BenchmarkPresetConfig.forPreset(BenchmarkPreset.STANDARD).storageAllocationLimitBytes)
        assertEquals(15, BenchmarkPresetConfig.forPreset(BenchmarkPreset.EXTENDED).measuredRepetitionCount)
        assertEquals(384L * 1_048_576L, BenchmarkPresetConfig.forPreset(BenchmarkPreset.EXTENDED).storageAllocationLimitBytes)
    }

    @Test
    fun activeRunUsesAnImmutablePresetSnapshot() {
        val frozen = BenchmarkPresetConfig.forPreset(BenchmarkPreset.QUICK)
        val laterSelection = BenchmarkPresetConfig.forPreset(BenchmarkPreset.EXTENDED)

        assertEquals(BenchmarkPreset.QUICK, frozen.preset)
        assertEquals(32L * 1_048_576L, frozen.storageAllocationLimitBytes)
        assertNotEquals(frozen.preset, laterSelection.preset)
        assertNotEquals(frozen.storageAllocationLimitBytes, laterSelection.storageAllocationLimitBytes)
    }
}
