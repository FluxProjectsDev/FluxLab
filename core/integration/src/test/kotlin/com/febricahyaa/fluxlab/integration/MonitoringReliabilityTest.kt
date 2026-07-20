package com.febricahyaa.fluxlab.integration

import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.BenchmarkPresetConfig
import com.febricahyaa.fluxlab.model.BenchmarkStage
import com.febricahyaa.fluxlab.model.SampleRingBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringReliabilityTest {
    @Test fun ringBufferRetainsOnlyTheVisibleWindow() {
        val buffer = SampleRingBuffer<Int>(3)
        (1..5).forEach(buffer::add)
        assertEquals(listOf(3, 4, 5), buffer.snapshot())
        assertEquals(3, buffer.size)
    }

    @Test fun presetsRemainDistinctAndBounded() {
        val quick = BenchmarkPresetConfig.forPreset(BenchmarkPreset.QUICK)
        val standard = BenchmarkPresetConfig.forPreset(BenchmarkPreset.STANDARD)
        val extended = BenchmarkPresetConfig.forPreset(BenchmarkPreset.EXTENDED)
        assertTrue(quick.storageAllocationLimitBytes < standard.storageAllocationLimitBytes)
        assertTrue(standard.measuredRepetitionCount < extended.measuredRepetitionCount)
    }

    @Test fun benchmarkStagesIncludeCancellationAndFailure() {
        assertTrue(BenchmarkStage.entries.contains(BenchmarkStage.CANCELLING))
        assertTrue(BenchmarkStage.entries.contains(BenchmarkStage.FAILED))
    }
}
