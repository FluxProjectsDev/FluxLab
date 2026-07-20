package com.febricahyaa.fluxlab.data

import com.febricahyaa.fluxlab.model.BenchmarkEnvironment
import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.BenchmarkPresetConfig
import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.ComparisonCompatibilityAnalyzer
import com.febricahyaa.fluxlab.model.ComparisonCompatibilityState
import com.febricahyaa.fluxlab.model.ComparisonRole
import com.febricahyaa.fluxlab.model.FluxInstallation
import com.febricahyaa.fluxlab.model.MethodologyMetadata
import com.febricahyaa.fluxlab.model.SessionStatus
import com.febricahyaa.fluxlab.model.localizedStatusKey
import com.febricahyaa.fluxlab.model.LocalizedStatusKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MethodologyCompatibilityTest {
    @Test
    fun `metadata round trips and unknown fields are ignored`() {
        val metadata = MethodologyMetadata(methodologyId = "fluxlab.v2", methodologyVersion = 2, storageMethodologyVersion = 3)
        val decoded = MethodologyMetadata.decode(metadata.encode() + ";future=preserved-externally")
        assertEquals(metadata, decoded)
    }

    @Test
    fun `legacy blank metadata uses safe defaults`() {
        assertEquals(MethodologyMetadata.DEFAULT, MethodologyMetadata.decode(""))
        assertEquals(MethodologyMetadata.DEFAULT, MethodologyMetadata.decode(null))
    }

    @Test
    fun `methodology mismatch is incompatible while condition mismatch warns`() {
        val baseline = session()
        val candidate = session(methodology = MethodologyMetadata(storageMethodologyVersion = 2))
        assertEquals(ComparisonCompatibilityState.INCOMPATIBLE_METHODOLOGY, ComparisonCompatibilityAnalyzer.analyze(baseline, candidate).state)
        val warningCandidate = session(environment = baseline.environment.copy(batteryLevel = 50))
        val result = ComparisonCompatibilityAnalyzer.analyze(baseline, warningCandidate)
        assertEquals(ComparisonCompatibilityState.COMPATIBLE_WITH_WARNINGS, result.state)
        assertTrue(result.warnings.any { it.contains("battery", ignoreCase = true) })
    }

    @Test
    fun `status mapper never leaks internal values`() {
        assertEquals(LocalizedStatusKey.UP_TO_DATE, localizedStatusKey("fresh"))
        assertEquals(LocalizedStatusKey.UPDATE_REQUIRED, localizedStatusKey("stale"))
        assertEquals(LocalizedStatusKey.PARTIALLY_AVAILABLE, localizedStatusKey("partial"))
        assertEquals(LocalizedStatusKey.INVALID_DATA_FORMAT, localizedStatusKey("malformed"))
        assertEquals(LocalizedStatusKey.UNAVAILABLE, localizedStatusKey("unavailable"))
        assertEquals(LocalizedStatusKey.UNKNOWN_STATUS, localizedStatusKey("future-value"))
    }

    private fun session(
        environment: BenchmarkEnvironment = environment(),
        methodology: MethodologyMetadata = MethodologyMetadata(),
    ) = BenchmarkSession(
        id = "id-${methodology.storageMethodologyVersion}-${environment.batteryLevel}",
        startedAtEpochMs = 1L, endedAtEpochMs = 2L, status = SessionStatus.COMPLETED, label = "Standard Test",
        environment = environment, workloadResults = emptyList(), warnings = emptyList(), failureReason = null,
        comparisonRole = ComparisonRole.NONE, methodology = methodology,
    )

    private fun environment() = BenchmarkEnvironment(
        appVersion = "1", benchmarkSchemaVersion = 1, deviceManufacturer = "Test", deviceModel = "Device",
        androidFingerprint = "fingerprint", kernelVersion = "kernel", flux = FluxInstallation(),
        synthesisAvailable = true, rootState = "unavailable", charging = false, batteryLevel = 80,
        initialBatteryTemperatureC = 30.0, peakBatteryTemperatureC = 31.0, androidThermalStatus = 0,
        thermalHeadroomSamples = emptyList(), refreshRateHz = 120.0,
        presetConfiguration = BenchmarkPresetConfig.forPreset(BenchmarkPreset.STANDARD),
    )
}
