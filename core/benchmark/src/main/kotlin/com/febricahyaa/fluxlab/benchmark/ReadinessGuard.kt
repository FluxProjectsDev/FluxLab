package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.DeviceTelemetrySnapshot
import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.ThermalEligibility
import com.febricahyaa.fluxlab.model.ThermalEligibilityEvaluator
import com.febricahyaa.fluxlab.model.ReadinessResult
import com.febricahyaa.fluxlab.model.SnapshotFreshness

data class ReadinessContext(
    val telemetry: DeviceTelemetrySnapshot?,
    val availableStorageBytes: Long,
    val benchmarkRunning: Boolean,
    val monitoringConflict: Boolean,
    val fluxFreshness: SnapshotFreshness,
    val preset: BenchmarkPreset = BenchmarkPreset.QUICK,
)

object ReadinessGuard {
    private const val MINIMUM_STORAGE = 64L * 1024 * 1024

    fun evaluate(context: ReadinessContext): ReadinessResult {
        val blocked = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (context.benchmarkRunning) blocked += "Another benchmark is already running"
        if (context.availableStorageBytes < MINIMUM_STORAGE) blocked += "Critically low private storage"
        // A missing snapshot means the telemetry pipeline has not produced a
        // sample yet; do not turn that transient state into an extra readiness
        // warning. Once a snapshot exists, a missing Android status is an
        // explicit thermal-status-unavailable condition.
        context.telemetry?.let { telemetry ->
            val thermalAssessment = ThermalEligibilityEvaluator.evaluate(telemetry.thermal.androidStatus, context.preset)
            when (thermalAssessment.eligibility) {
                ThermalEligibility.BLOCKED_BY_THERMAL_CONDITION -> blocked += thermalAssessment.reason
                ThermalEligibility.THERMAL_STATUS_UNAVAILABLE -> warnings += thermalAssessment.reason
                ThermalEligibility.READY_WITH_WARNING, ThermalEligibility.COOLDOWN_RECOMMENDED -> warnings += thermalAssessment.reason
                ThermalEligibility.READY -> Unit
            }
        }
        if (context.monitoringConflict) warnings += "Another live monitoring session is active"
        val battery = context.telemetry?.battery
        val batteryLevel = battery?.levelPercent
        if (batteryLevel != null && batteryLevel < 15) warnings += "Battery is below 15%"
        if (battery?.charging == true) warnings += "Charging may affect thermal and power results"
        if (context.telemetry?.cpu?.totalUsagePercent?.let { it > 80.0 } == true) warnings += "High background CPU activity detected"
        if (context.fluxFreshness == SnapshotFreshness.STALE) warnings += "Flux telemetry is stale"
        if (context.fluxFreshness == SnapshotFreshness.UNAVAILABLE) warnings += "Flux telemetry is unavailable"
        return when {
            blocked.isNotEmpty() -> ReadinessResult.Blocked(blocked + warnings)
            warnings.isNotEmpty() -> ReadinessResult.ReadyWithWarnings(warnings)
            else -> ReadinessResult.Ready()
        }
    }
}
