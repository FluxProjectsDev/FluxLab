package com.febricahyaa.fluxlab.model

import kotlinx.coroutines.flow.Flow

enum class MetricDirection { HIGHER_IS_BETTER, LOWER_IS_BETTER, UNKNOWN }

enum class WorkloadKind(val direction: MetricDirection, val unit: String) {
    CPU_INTEGER(MetricDirection.HIGHER_IS_BETTER, "ops/s"),
    CPU_FLOATING_POINT(MetricDirection.HIGHER_IS_BETTER, "ops/s"),
    CPU_MULTI_THREADED(MetricDirection.HIGHER_IS_BETTER, "ops/s"),
    MEMORY_COPY(MetricDirection.HIGHER_IS_BETTER, "MiB/s"),
    MEMORY_FILL(MetricDirection.HIGHER_IS_BETTER, "MiB/s"),
    MEMORY_LATENCY(MetricDirection.LOWER_IS_BETTER, "ns/access"),
    STORAGE_WRITE(MetricDirection.HIGHER_IS_BETTER, "MiB/s"),
    STORAGE_READ(MetricDirection.HIGHER_IS_BETTER, "MiB/s"),
    STORAGE_FSYNC(MetricDirection.LOWER_IS_BETTER, "ms/fsync");

    val higherIsBetter: Boolean get() = direction == MetricDirection.HIGHER_IS_BETTER
}

enum class BenchmarkPreset { QUICK, STANDARD, EXTENDED }

data class BenchmarkPresetConfig(
    val preset: BenchmarkPreset,
    val warmUpCount: Int,
    val measuredRepetitionCount: Int,
    val workloadScale: Int,
    val interTestCooldownMs: Long,
    val maximumDurationMs: Long,
    val cancellationBehavior: String,
    val storageAllocationLimitBytes: Long,
) {
    companion object {
        private const val MIB = 1_048_576L

        fun forPreset(preset: BenchmarkPreset): BenchmarkPresetConfig = when (preset) {
            BenchmarkPreset.QUICK -> BenchmarkPresetConfig(
                preset, warmUpCount = 1, measuredRepetitionCount = 5, workloadScale = 1,
                interTestCooldownMs = 250, maximumDurationMs = 45_000,
                cancellationBehavior = "cooperative_cleanup", storageAllocationLimitBytes = 32 * MIB,
            )
            BenchmarkPreset.STANDARD -> BenchmarkPresetConfig(
                preset, warmUpCount = 2, measuredRepetitionCount = 9, workloadScale = 2,
                interTestCooldownMs = 1_000, maximumDurationMs = 300_000,
                cancellationBehavior = "cooperative_cleanup", storageAllocationLimitBytes = 192 * MIB,
            )
            BenchmarkPreset.EXTENDED -> BenchmarkPresetConfig(
                preset, warmUpCount = 3, measuredRepetitionCount = 15, workloadScale = 4,
                interTestCooldownMs = 2_000, maximumDurationMs = 900_000,
                cancellationBehavior = "cooperative_cleanup", storageAllocationLimitBytes = 384 * MIB,
            )
        }
    }
}

enum class VariabilityClassification { STABLE, ACCEPTABLE, NOISY, UNRELIABLE }

data class Statistics(
    val median: Double,
    val minimum: Double,
    val maximum: Double,
    val standardDeviation: Double,
    val coefficientOfVariation: Double?,
    val sampleCount: Int = 0,
    val arithmeticMean: Double = median,
    val p95: Double? = null,
    val variability: VariabilityClassification = VariabilityClassification.UNRELIABLE,
)

data class WorkloadResult(
    val kind: WorkloadKind,
    val workloadVersion: Int,
    val unit: String,
    val repetitions: List<Double>,
    val durationsNs: List<Long>,
    val statistics: Statistics,
    val validationChecksum: String,
    val threadCount: Int = 1,
    val affinityForced: Boolean = false,
    val warnings: List<String> = emptyList(),
    val excludedSampleIndices: List<Int> = emptyList(),
) 

enum class SessionStatus { PREPARING, WARMING_UP, RUNNING, COOLING_DOWN, COMPLETED, CANCELLED, FAILED }
enum class ComparisonRole { NONE, BASELINE, CANDIDATE }

data class BenchmarkEnvironment(
    val appVersion: String,
    val benchmarkSchemaVersion: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidFingerprint: String,
    val kernelVersion: String,
    val flux: FluxInstallation,
    val synthesisAvailable: Boolean,
    val rootState: String,
    val charging: Boolean?,
    val batteryLevel: Int?,
    val initialBatteryTemperatureC: Double?,
    val peakBatteryTemperatureC: Double?,
    val androidThermalStatus: Int?,
    val thermalHeadroomSamples: List<Double>,
    val refreshRateHz: Double?,
    val presetConfiguration: BenchmarkPresetConfig = BenchmarkPresetConfig.forPreset(BenchmarkPreset.QUICK),
    val visualMode: BenchmarkVisualMode = BenchmarkVisualMode.REDUCED,
)

data class BenchmarkSession(
    val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val status: SessionStatus,
    val label: String,
    val environment: BenchmarkEnvironment,
    val workloadResults: List<WorkloadResult>,
    val warnings: List<String>,
    val failureReason: String?,
    val comparisonRole: ComparisonRole = ComparisonRole.NONE,
    val methodology: MethodologyMetadata = MethodologyMetadata(),
)

/** Independent contracts that make two measurements comparable. */
data class MethodologyMetadata(
    val methodologyId: String = DEFAULT_METHODOLOGY_ID,
    val methodologyVersion: Int = 1,
    val workloadVersion: Int = 1,
    val statisticsVersion: Int = 1,
    val telemetrySchemaVersion: Int = 1,
    val presetDefinitionVersion: Int = 1,
    val storageMethodologyVersion: Int = 1,
    val metricDefinitionVersion: Int = 1,
) {
    companion object {
        const val DEFAULT_METHODOLOGY_ID = "fluxlab.measurement"
        val DEFAULT = MethodologyMetadata()

        fun decode(raw: String?): MethodologyMetadata {
            if (raw.isNullOrBlank()) return DEFAULT
            val values = raw.split(';').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
            }.toMap()
            return MethodologyMetadata(
                methodologyId = values["id"]?.takeIf(String::isNotBlank) ?: DEFAULT.methodologyId,
                methodologyVersion = values["methodology"]?.toIntOrNull() ?: DEFAULT.methodologyVersion,
                workloadVersion = values["workload"]?.toIntOrNull() ?: DEFAULT.workloadVersion,
                statisticsVersion = values["statistics"]?.toIntOrNull() ?: DEFAULT.statisticsVersion,
                telemetrySchemaVersion = values["telemetry"]?.toIntOrNull() ?: DEFAULT.telemetrySchemaVersion,
                presetDefinitionVersion = values["preset"]?.toIntOrNull() ?: DEFAULT.presetDefinitionVersion,
                storageMethodologyVersion = values["storage"]?.toIntOrNull() ?: DEFAULT.storageMethodologyVersion,
                metricDefinitionVersion = values["metrics"]?.toIntOrNull() ?: DEFAULT.metricDefinitionVersion,
            )
        }
    }

    fun encode(): String = buildString {
        append("id=").append(methodologyId.replace(";", "_")).append(';')
        append("methodology=").append(methodologyVersion).append(';')
        append("workload=").append(workloadVersion).append(';')
        append("statistics=").append(statisticsVersion).append(';')
        append("telemetry=").append(telemetrySchemaVersion).append(';')
        append("preset=").append(presetDefinitionVersion).append(';')
        append("storage=").append(storageMethodologyVersion).append(';')
        append("metrics=").append(metricDefinitionVersion)
    }
}

enum class ComparisonCompatibilityState {
    COMPATIBLE,
    COMPATIBLE_WITH_WARNINGS,
    INCOMPATIBLE_METHODOLOGY,
}

data class ComparisonCompatibility(
    val state: ComparisonCompatibilityState,
    val warnings: List<String> = emptyList(),
) {
    val isMethodologyCompatible: Boolean
        get() = state != ComparisonCompatibilityState.INCOMPATIBLE_METHODOLOGY
}

object ComparisonCompatibilityAnalyzer {
    fun analyze(baseline: BenchmarkSession, candidate: BenchmarkSession): ComparisonCompatibility {
        val base = baseline.methodology
        val current = candidate.methodology
        val incompatible = buildList {
            if (base.methodologyId != current.methodologyId || base.methodologyVersion != current.methodologyVersion) add("Measurement methodology differs")
            if (base.workloadVersion != current.workloadVersion) add("Workload methodology differs")
            if (base.statisticsVersion != current.statisticsVersion) add("Statistics methodology differs")
            if (base.metricDefinitionVersion != current.metricDefinitionVersion) add("Metric definitions differ")
            if (base.storageMethodologyVersion != current.storageMethodologyVersion) add("Storage methodology differs")
        }
        if (incompatible.isNotEmpty()) return ComparisonCompatibility(ComparisonCompatibilityState.INCOMPATIBLE_METHODOLOGY, incompatible)

        val warnings = buildList {
            val baseEnvironment = baseline.environment
            val candidateEnvironment = candidate.environment
            if (baseEnvironment.batteryLevel != null && candidateEnvironment.batteryLevel != null && kotlin.math.abs(baseEnvironment.batteryLevel - candidateEnvironment.batteryLevel) >= 10) add("Starting battery level differs")
            if (baseEnvironment.initialBatteryTemperatureC != null && candidateEnvironment.initialBatteryTemperatureC != null && kotlin.math.abs(baseEnvironment.initialBatteryTemperatureC - candidateEnvironment.initialBatteryTemperatureC) >= 3.0) add("Starting battery temperature differs")
            if (baseEnvironment.charging != candidateEnvironment.charging) add("Charging state differs")
            if (baseEnvironment.androidThermalStatus != candidateEnvironment.androidThermalStatus) add("Starting thermal status differs")
            if (baseEnvironment.refreshRateHz != null && candidateEnvironment.refreshRateHz != null && kotlin.math.abs(baseEnvironment.refreshRateHz - candidateEnvironment.refreshRateHz) >= 1.0) add("Display refresh rate differs")
            if (baseEnvironment.flux.installed != candidateEnvironment.flux.installed || baseEnvironment.flux.enabled != candidateEnvironment.flux.enabled || baseEnvironment.flux.activeProfile != candidateEnvironment.flux.activeProfile) add("Flux state or profile differs")
            if (baseEnvironment.synthesisAvailable != candidateEnvironment.synthesisAvailable) add("SynthesisCore availability differs")
            if (baseEnvironment.deviceManufacturer != candidateEnvironment.deviceManufacturer || baseEnvironment.deviceModel != candidateEnvironment.deviceModel || baseEnvironment.androidFingerprint != candidateEnvironment.androidFingerprint) add("Device or Android build differs")
            if (baseEnvironment.presetConfiguration.preset != candidateEnvironment.presetConfiguration.preset) add("Benchmark preset differs")
        }.distinct()
        return ComparisonCompatibility(if (warnings.isEmpty()) ComparisonCompatibilityState.COMPATIBLE else ComparisonCompatibilityState.COMPATIBLE_WITH_WARNINGS, warnings)
    }
}

sealed interface ReadinessResult {
    val reasons: List<String>
    data class Ready(override val reasons: List<String> = emptyList()) : ReadinessResult
    data class ReadyWithWarnings(override val reasons: List<String>) : ReadinessResult
    data class Blocked(override val reasons: List<String>) : ReadinessResult
}

data class BenchmarkProgress(
    val sessionId: String?,
    val status: SessionStatus,
    val workload: WorkloadKind?,
    val completedSteps: Int,
    val totalSteps: Int,
    val message: String? = null,
    val stage: BenchmarkStage = BenchmarkStage.IDLE,
    val workloadIndex: Int = 0,
    val totalWorkloads: Int = totalSteps,
    val currentRepetition: Int = 0,
    val totalRepetitions: Int = 0,
    val completedWorkUnits: Long = 0L,
    val totalWorkUnits: Long? = null,
    val elapsedMs: Long = 0L,
    val estimatedRemainingMs: Long? = null,
    val visualMode: BenchmarkVisualMode = BenchmarkVisualMode.REDUCED,
    val warnings: List<String> = emptyList(),
    val preset: BenchmarkPreset? = null,
    val activeThreadCount: Int? = null,
)

enum class BenchmarkStage {
    IDLE, PREFLIGHT, AWAITING_CONFIRMATION, COUNTDOWN, WARMUP, RUNNING,
    INTER_WORKLOAD_COOLDOWN, FINALIZING, COMPLETED, CANCELLING, CANCELLED, FAILED,
}

enum class BenchmarkVisualMode { FULL, REDUCED }


interface BenchmarkWorkload {
    val kind: WorkloadKind
    val version: Int
    suspend fun run(): WorkloadResult
    fun cancel()
}

interface BenchmarkSessionRepository {
    fun observeSessions(): Flow<List<BenchmarkSession>>
    suspend fun get(id: String): BenchmarkSession?
    suspend fun save(session: BenchmarkSession)
    suspend fun rename(id: String, label: String)
    suspend fun markBaseline(id: String)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}

enum class Confidence { INCONCLUSIVE, POSSIBLE_CHANGE, LIKELY_CHANGE }

data class WorkloadComparison(
    val kind: WorkloadKind,
    val compatible: Boolean,
    val absoluteDelta: Double?,
    val percentageDelta: Double?,
    val improved: Boolean?,
    val confidence: Confidence,
    val warnings: List<String>,
)

data class SessionComparison(
    val baselineId: String,
    val candidateId: String,
    val workloads: List<WorkloadComparison>,
    val environmentWarnings: List<String>,
    val compatibility: ComparisonCompatibility = ComparisonCompatibility(ComparisonCompatibilityState.COMPATIBLE),
)

interface ReportExporter {
    suspend fun exportSessionJson(session: BenchmarkSession, destination: java.io.OutputStream)
    suspend fun exportSessionCsv(session: BenchmarkSession, destination: java.io.OutputStream)
    suspend fun exportComparisonJson(comparison: SessionComparison, destination: java.io.OutputStream)
    suspend fun exportComparisonCsv(comparison: SessionComparison, destination: java.io.OutputStream)
}
