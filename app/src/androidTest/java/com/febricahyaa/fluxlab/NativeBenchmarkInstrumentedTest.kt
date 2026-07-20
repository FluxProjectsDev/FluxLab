package com.febricahyaa.fluxlab

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.febricahyaa.fluxlab.benchmark.NativeBenchmarkWorkload
import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.BenchmarkPresetConfig
import com.febricahyaa.fluxlab.model.WorkloadKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeBenchmarkInstrumentedTest {
    @Test
    fun nativeIntegerWorkloadProducesValidatedNonConstantMeasurements() = runBlocking {
        val result = NativeBenchmarkWorkload(
            WorkloadKind.CPU_INTEGER,
            BenchmarkPresetConfig.forPreset(BenchmarkPreset.QUICK).copy(measuredRepetitionCount = 3),
        ).run()

        assertEquals(3, result.repetitions.size)
        assertTrue(result.repetitions.all { it > 0.0 && it.isFinite() })
        assertTrue(result.durationsNs.all { it > 0L })
        assertTrue(result.validationChecksum.toDouble() != 0.0)
    }
}
