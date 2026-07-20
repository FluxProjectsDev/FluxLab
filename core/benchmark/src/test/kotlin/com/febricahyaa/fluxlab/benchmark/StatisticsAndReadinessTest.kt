package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.ReadinessResult
import com.febricahyaa.fluxlab.model.SnapshotFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsAndReadinessTest {
    @Test
    fun `statistics calculate median sample deviation and coefficient of variation`() {
        val result = BenchmarkStatistics.calculate(listOf(1.0, 2.0, 3.0))

        assertEquals(2.0, result.median, 0.0001)
        assertEquals(1.0, result.minimum, 0.0001)
        assertEquals(3.0, result.maximum, 0.0001)
        assertEquals(1.0, result.standardDeviation, 0.0001)
        assertEquals(0.5, result.coefficientOfVariation!!, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `statistics reject non finite measurements`() {
        BenchmarkStatistics.calculate(listOf(1.0, Double.NaN))
    }

    @Test
    fun `readiness is ready when bounded storage and environment are acceptable`() {
        val result = ReadinessGuard.evaluate(
            ReadinessContext(null, 256L * 1024 * 1024, false, false, SnapshotFreshness.FRESH),
        )

        assertTrue(result is ReadinessResult.Ready)
    }

    @Test
    fun `readiness reports warnings without blocking stale Flux state`() {
        val result = ReadinessGuard.evaluate(
            ReadinessContext(null, 256L * 1024 * 1024, false, true, SnapshotFreshness.STALE),
        )

        assertTrue(result is ReadinessResult.ReadyWithWarnings)
        assertEquals(2, result.reasons.size)
    }

    @Test
    fun `readiness blocks duplicate launches and critically low storage`() {
        val result = ReadinessGuard.evaluate(
            ReadinessContext(null, 8L * 1024 * 1024, true, false, SnapshotFreshness.UNKNOWN),
        )

        assertTrue(result is ReadinessResult.Blocked)
        assertTrue(result.reasons.any { it.contains("already running") })
        assertTrue(result.reasons.any { it.contains("storage") })
    }
}
