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
)

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
)

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
)

interface ReportExporter {
    suspend fun exportSessionJson(session: BenchmarkSession, destination: java.io.OutputStream)
    suspend fun exportSessionCsv(session: BenchmarkSession, destination: java.io.OutputStream)
    suspend fun exportComparisonJson(comparison: SessionComparison, destination: java.io.OutputStream)
    suspend fun exportComparisonCsv(comparison: SessionComparison, destination: java.io.OutputStream)
}
