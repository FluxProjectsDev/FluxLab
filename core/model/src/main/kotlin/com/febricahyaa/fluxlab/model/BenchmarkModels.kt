package com.febricahyaa.fluxlab.model

import kotlinx.coroutines.flow.Flow

enum class WorkloadKind(val higherIsBetter: Boolean, val unit: String) {
    CPU_INTEGER(true, "ops/s"),
    CPU_FLOATING_POINT(true, "ops/s"),
    CPU_MULTI_THREADED(true, "ops/s"),
    MEMORY_COPY(true, "MiB/s"),
    MEMORY_FILL(true, "MiB/s"),
    MEMORY_LATENCY(false, "ns/access"),
    STORAGE_WRITE(true, "MiB/s"),
    STORAGE_READ(true, "MiB/s"),
    STORAGE_FSYNC(false, "ms/fsync"),
}

data class Statistics(
    val median: Double,
    val minimum: Double,
    val maximum: Double,
    val standardDeviation: Double,
    val coefficientOfVariation: Double?,
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
