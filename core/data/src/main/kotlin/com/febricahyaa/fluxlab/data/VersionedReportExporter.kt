package com.febricahyaa.fluxlab.data

import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.ReportExporter
import com.febricahyaa.fluxlab.model.SessionComparison
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VersionedReportExporter : ReportExporter {
    override suspend fun exportSessionJson(session: BenchmarkSession, destination: OutputStream) = write(destination) {
        ReportSerializer.sessionJson(session)
    }

    override suspend fun exportSessionCsv(session: BenchmarkSession, destination: OutputStream) = write(destination) {
        ReportSerializer.sessionCsv(session)
    }

    override suspend fun exportComparisonJson(comparison: SessionComparison, destination: OutputStream) = write(destination) {
        ReportSerializer.comparisonJson(comparison)
    }

    override suspend fun exportComparisonCsv(comparison: SessionComparison, destination: OutputStream) = write(destination) {
        ReportSerializer.comparisonCsv(comparison)
    }

    private suspend fun write(destination: OutputStream, content: () -> String) = withContext(Dispatchers.IO) {
        destination.bufferedWriter(Charsets.UTF_8).use { it.write(content()) }
    }
}

object ReportSerializer {
    const val SCHEMA_VERSION = 1

    fun sessionJson(session: BenchmarkSession): String = buildString {
        append("{\n  \"schema_version\": ").append(SCHEMA_VERSION)
        append(",\n  \"report_type\": \"session\"")
        append(",\n  \"application_version\": ").json(session.environment.appVersion)
        append(",\n  \"session\": {")
        append("\n    \"id\": ").json(session.id)
        append(",\n    \"label\": ").json(session.label)
        append(",\n    \"status\": ").json(session.status.name)
        append(",\n    \"started_at_epoch_ms\": ").append(session.startedAtEpochMs)
        append(",\n    \"ended_at_epoch_ms\": ").append(session.endedAtEpochMs ?: "null")
        append("\n  },\n  \"device\": {")
        append("\n    \"manufacturer\": ").json(session.environment.deviceManufacturer)
        append(",\n    \"model\": ").json(session.environment.deviceModel)
        append(",\n    \"android_fingerprint\": ").json(session.environment.androidFingerprint)
        append(",\n    \"kernel_version\": ").json(session.environment.kernelVersion)
        append("\n  },\n  \"flux\": {")
        append("\n    \"installed\": ").append(session.environment.flux.installed)
        append(",\n    \"version_name\": ").nullable(session.environment.flux.versionName)
        append(",\n    \"active_profile\": ").nullable(session.environment.flux.activeProfile)
        append(",\n    \"runtime_available\": ").append(session.environment.flux.runtimeAvailable)
        append("\n  },\n  \"methodology\": {")
        append("\n    \"id\": ").json(session.methodology.methodologyId)
        append(",\n    \"methodology_version\": ").append(session.methodology.methodologyVersion)
        append(",\n    \"workload_version\": ").append(session.methodology.workloadVersion)
        append(",\n    \"statistics_version\": ").append(session.methodology.statisticsVersion)
        append(",\n    \"telemetry_schema_version\": ").append(session.methodology.telemetrySchemaVersion)
        append(",\n    \"preset_definition_version\": ").append(session.methodology.presetDefinitionVersion)
        append(",\n    \"storage_methodology_version\": ").append(session.methodology.storageMethodologyVersion)
        append(",\n    \"metric_definition_version\": ").append(session.methodology.metricDefinitionVersion)
        append("\n  },\n  \"environment\": {")
        append("\n    \"root_state\": ").json(session.environment.rootState)
        append(",\n    \"synthesis_core_available\": ").append(session.environment.synthesisAvailable)
        append(",\n    \"charging\": ").append(session.environment.charging ?: "null")
        append(",\n    \"battery_level_percent\": ").append(session.environment.batteryLevel ?: "null")
        append(",\n    \"initial_battery_temperature_c\": ").append(session.environment.initialBatteryTemperatureC ?: "null")
        append(",\n    \"peak_battery_temperature_c\": ").append(session.environment.peakBatteryTemperatureC ?: "null")
        append(",\n    \"android_thermal_status\": ").append(session.environment.androidThermalStatus ?: "null")
        append(",\n    \"thermal_headroom_samples\": [").append(session.environment.thermalHeadroomSamples.joinToString(",")).append(']')
        append(",\n    \"refresh_rate_hz\": ").append(session.environment.refreshRateHz ?: "null")
        append("\n  },\n  \"workloads\": [")
        session.workloadResults.forEachIndexed { index, result ->
            if (index > 0) append(',')
            append("\n    {\"kind\": ").json(result.kind.name)
            append(", \"version\": ").append(result.workloadVersion)
            append(", \"unit\": ").json(result.unit)
            append(", \"repetitions\": [").append(result.repetitions.joinToString(",")).append(']')
            append(", \"durations_ns\": [").append(result.durationsNs.joinToString(",")).append(']')
            append(", \"median\": ").append(result.statistics.median)
            append(", \"minimum\": ").append(result.statistics.minimum)
            append(", \"maximum\": ").append(result.statistics.maximum)
            append(", \"standard_deviation\": ").append(result.statistics.standardDeviation)
            append(", \"coefficient_of_variation\": ").append(result.statistics.coefficientOfVariation ?: "null")
            append(", \"checksum\": ").json(result.validationChecksum).append('}')
        }
        append("\n  ],\n  \"preset_configuration\": {")
        append("\n    \"preset\": ").json(session.environment.presetConfiguration.preset.name)
        append(",\n    \"warm_up_count\": ").append(session.environment.presetConfiguration.warmUpCount)
        append(",\n    \"measured_repetition_count\": ").append(session.environment.presetConfiguration.measuredRepetitionCount)
        append(",\n    \"workload_scale\": ").append(session.environment.presetConfiguration.workloadScale)
        append(",\n    \"inter_test_cooldown_ms\": ").append(session.environment.presetConfiguration.interTestCooldownMs)
        append(",\n    \"maximum_duration_ms\": ").append(session.environment.presetConfiguration.maximumDurationMs)
        append(",\n    \"storage_allocation_limit_bytes\": ").append(session.environment.presetConfiguration.storageAllocationLimitBytes)
        append("\n  },\n  \"warnings\": ").stringArray(session.warnings)
        append(",\n  \"failure_reason\": ").nullable(session.failureReason)
        append("\n}\n")
    }

    fun sessionCsv(session: BenchmarkSession): String = buildString {
        append("report_schema_version,session_id,session_status,workload_kind,workload_version,unit,repetition_index,value,duration_ns,median,minimum,maximum,standard_deviation,coefficient_of_variation\n")
        session.workloadResults.forEach { result ->
            result.repetitions.forEachIndexed { index, value ->
                append(SCHEMA_VERSION).append(',').csv(session.id).append(',').csv(session.status.name).append(',')
                    .csv(result.kind.name).append(',').append(result.workloadVersion).append(',').csv(result.unit).append(',')
                    .append(index).append(',').append(value).append(',').append(result.durationsNs.getOrNull(index) ?: "")
                    .append(',').append(result.statistics.median).append(',').append(result.statistics.minimum)
                    .append(',').append(result.statistics.maximum).append(',').append(result.statistics.standardDeviation)
                    .append(',').append(result.statistics.coefficientOfVariation ?: "").append('\n')
            }
        }
    }

    fun comparisonJson(comparison: SessionComparison): String = buildString {
        append("{\n  \"schema_version\": ").append(SCHEMA_VERSION)
        append(",\n  \"report_type\": \"comparison\"")
        append(",\n  \"baseline_session_id\": ").json(comparison.baselineId)
        append(",\n  \"compatibility\": {")
        append("\n    \"state\": ").json(comparison.compatibility.state.name)
        append(",\n    \"warnings\": ").stringArray(comparison.compatibility.warnings)
        append("\n  }")
        append(",\n  \"candidate_session_id\": ").json(comparison.candidateId)
        append(",\n  \"workloads\": [")
        comparison.workloads.forEachIndexed { index, value ->
            if (index > 0) append(',')
            append("\n    {\"kind\": ").json(value.kind.name)
            append(", \"compatible\": ").append(value.compatible)
            append(", \"absolute_delta\": ").append(value.absoluteDelta ?: "null")
            append(", \"percentage_delta\": ").append(value.percentageDelta ?: "null")
            append(", \"improved\": ").append(value.improved ?: "null")
            append(", \"confidence\": ").json(value.confidence.name)
            append(", \"warnings\": ").stringArray(value.warnings).append('}')
        }
        append("\n  ],\n  \"environment_warnings\": ").stringArray(comparison.environmentWarnings)
        append("\n}\n")
    }

    fun comparisonCsv(comparison: SessionComparison): String = buildString {
        append("report_schema_version,baseline_session_id,candidate_session_id,compatibility_state,compatibility_warnings,workload_kind,compatible,absolute_delta,percentage_delta,improved,confidence,warnings\n")
        comparison.workloads.forEach { value ->
            append(SCHEMA_VERSION).append(',').csv(comparison.baselineId).append(',').csv(comparison.candidateId).append(',')
                .csv(comparison.compatibility.state.name).append(',').csv(comparison.compatibility.warnings.joinToString("; ")).append(',')
                .csv(value.kind.name).append(',').append(value.compatible).append(',')
                .append(value.absoluteDelta ?: "").append(',').append(value.percentageDelta ?: "").append(',')
                .append(value.improved ?: "").append(',').csv(value.confidence.name).append(',')
                .csv(value.warnings.joinToString("; ")).append('\n')
        }
    }

    private fun StringBuilder.json(value: String): StringBuilder = append('"').append(
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"),
    ).append('"')

    private fun StringBuilder.nullable(value: String?): StringBuilder = if (value == null) append("null") else json(value)
    private fun StringBuilder.stringArray(values: List<String>): StringBuilder = append('[').apply {
        values.forEachIndexed { index, value -> if (index > 0) append(','); json(value) }
        append(']')
    }
    private fun StringBuilder.csv(value: String): StringBuilder = append('"').append(value.replace("\"", "\"\"")).append('"')
}
