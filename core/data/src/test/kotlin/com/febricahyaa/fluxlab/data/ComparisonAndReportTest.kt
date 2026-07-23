package com.febricahyaa.fluxlab.data

import com.febricahyaa.fluxlab.model.BenchmarkEnvironment
import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.ComparisonRole
import com.febricahyaa.fluxlab.model.Confidence
import com.febricahyaa.fluxlab.model.FluxInstallation
import com.febricahyaa.fluxlab.model.SessionStatus
import com.febricahyaa.fluxlab.model.Statistics
import com.febricahyaa.fluxlab.model.WorkloadKind
import com.febricahyaa.fluxlab.model.WorkloadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonAndReportTest {
    @Test
    fun `comparison applies higher and lower is better direction rules`() {
        val baseline = session(
            result(WorkloadKind.CPU_INTEGER, 100.0),
            result(WorkloadKind.MEMORY_LATENCY, 100.0),
        )
        val candidate = session(
            result(WorkloadKind.CPU_INTEGER, 115.0),
            result(WorkloadKind.MEMORY_LATENCY, 85.0),
            id = "candidate",
        )

        val comparison = ComparisonEngine.compare(baseline, candidate)

        assertEquals(true, comparison.workloads.first { it.kind == WorkloadKind.CPU_INTEGER }.improved)
        assertEquals(true, comparison.workloads.first { it.kind == WorkloadKind.MEMORY_LATENCY }.improved)
        assertEquals(Confidence.LIKELY_CHANGE, comparison.workloads.first().confidence)
    }

    @Test
    fun `comparison refuses incompatible workload versions`() {
        val baseline = session(result(WorkloadKind.CPU_INTEGER, 100.0, version = 1))
        val candidate = session(result(WorkloadKind.CPU_INTEGER, 120.0, version = 2), id = "candidate")

        val value = ComparisonEngine.compare(baseline, candidate).workloads.single()

        assertFalse(value.compatible)
        assertEquals(Confidence.INCONCLUSIVE, value.confidence)
        assertTrue(value.warnings.single().contains("incompatible"))
    }

    @Test
    fun `versioned JSON report escapes strings and contains raw statistics`() {
        val json = ReportSerializer.sessionJson(
            session(result(WorkloadKind.CPU_INTEGER, 100.0)).copy(label = "A \"quoted\" label"),
        )

        assertTrue(json.contains("\"schema_version\": 1"))
        assertTrue(json.contains("A \\\"quoted\\\" label"))
        assertTrue(json.contains("\"repetitions\": [99.0,100.0,101.0]"))
        assertTrue(json.contains("\"coefficient_of_variation\""))
    }

    @Test
    fun `CSV report exposes stable columns units and one row per repetition`() {
        val lines = ReportSerializer.sessionCsv(session(result(WorkloadKind.STORAGE_FSYNC, 4.0))).trim().lines()

        assertEquals(4, lines.size)
        assertTrue(lines.first().startsWith("report_schema_version,session_id,session_status"))
        assertTrue(lines[1].contains("\"ms/fsync\""))
        assertTrue(lines[1].contains("STORAGE_FSYNC"))
    }

    private fun result(kind: WorkloadKind, median: Double, version: Int = 1): WorkloadResult {
        val repetitions = listOf(median - 1.0, median, median + 1.0)
        return WorkloadResult(
            kind = kind,
            workloadVersion = version,
            unit = kind.unit,
            repetitions = repetitions,
            durationsNs = listOf(1L, 2L, 3L),
            statistics = Statistics(median, median - 1.0, median + 1.0, 1.0, 0.01),
            validationChecksum = "123",
        )
    }

    private fun session(vararg results: WorkloadResult, id: String = "baseline"): BenchmarkSession = BenchmarkSession(
        id = id,
        startedAtEpochMs = 1_000L,
        endedAtEpochMs = 2_000L,
        status = SessionStatus.COMPLETED,
        label = "Quick Test",
        environment = BenchmarkEnvironment(
            appVersion = "0.1.0",
            benchmarkSchemaVersion = 1,
            deviceManufacturer = "Test",
            deviceModel = "Device",
            androidFingerprint = "fingerprint",
            kernelVersion = "6.1-test",
            flux = FluxInstallation(installed = true, activeProfile = "balanced"),
            synthesisAvailable = true,
            rootState = "Available",
            charging = false,
            batteryLevel = 80,
            initialBatteryTemperatureC = 30.0,
            peakBatteryTemperatureC = 31.0,
            androidThermalStatus = 0,
            thermalHeadroomSamples = listOf(0.5),
            refreshRateHz = 120.0,
        ),
        workloadResults = results.toList(),
        warnings = emptyList(),
        failureReason = null,
        comparisonRole = ComparisonRole.NONE,
    )
}
