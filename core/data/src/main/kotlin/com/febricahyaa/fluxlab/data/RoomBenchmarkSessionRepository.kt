package com.febricahyaa.fluxlab.data

import com.febricahyaa.fluxlab.model.BenchmarkEnvironment
import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.BenchmarkSessionRepository
import com.febricahyaa.fluxlab.model.ComparisonRole
import com.febricahyaa.fluxlab.model.FluxInstallation
import com.febricahyaa.fluxlab.model.SessionStatus
import com.febricahyaa.fluxlab.model.Statistics
import com.febricahyaa.fluxlab.model.WorkloadKind
import com.febricahyaa.fluxlab.model.WorkloadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBenchmarkSessionRepository(private val dao: BenchmarkDao) : BenchmarkSessionRepository {
    override fun observeSessions(): Flow<List<BenchmarkSession>> = dao.observeAll().map { sessions -> sessions.map(::toModel) }
    override suspend fun get(id: String): BenchmarkSession? = dao.get(id)?.let(::toModel)

    override suspend fun save(session: BenchmarkSession) {
        dao.save(session.toEntity(), session.workloadResults.map { it.toEntity(session.id) })
    }

    override suspend fun rename(id: String, label: String) = dao.rename(id, label.trim().take(80).ifEmpty { "Quick Test" })
    override suspend fun markBaseline(id: String) = dao.markBaseline(id)
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()

    private fun BenchmarkSession.toEntity(): BenchmarkSessionEntity = BenchmarkSessionEntity(
        id, startedAtEpochMs, endedAtEpochMs, status.name, label,
        environment.appVersion, environment.benchmarkSchemaVersion,
        environment.deviceManufacturer, environment.deviceModel, environment.androidFingerprint,
        environment.kernelVersion, environment.flux.installed, environment.flux.enabled,
        environment.flux.runtimeAvailable, environment.flux.daemonAlive,
        environment.flux.versionName, environment.flux.versionCode, environment.flux.activeProfile,
        environment.flux.kernelType, environment.flux.configDirectory, environment.synthesisAvailable,
        environment.rootState, environment.charging, environment.batteryLevel,
        environment.initialBatteryTemperatureC, environment.peakBatteryTemperatureC,
        environment.androidThermalStatus, environment.thermalHeadroomSamples.joinToString(","),
        environment.refreshRateHz, warnings.joinToString("\n"), failureReason, comparisonRole.name,
    )

    private fun WorkloadResult.toEntity(sessionId: String): WorkloadResultEntity = WorkloadResultEntity(
        sessionId, kind.name, workloadVersion, unit, repetitions.joinToString(","), durationsNs.joinToString(","),
        statistics.median, statistics.minimum, statistics.maximum, statistics.standardDeviation,
        statistics.coefficientOfVariation, validationChecksum, threadCount, affinityForced,
        warnings.joinToString("\n"),
    )

    private fun toModel(value: SessionWithWorkloads): BenchmarkSession {
        val entity = value.session
        val flux = FluxInstallation(
            installed = entity.fluxInstalled,
            enabled = entity.fluxEnabled,
            runtimeAvailable = entity.fluxRuntimeAvailable,
            daemonAlive = entity.fluxDaemonAlive,
            versionName = entity.fluxVersionName,
            versionCode = entity.fluxVersionCode,
            activeProfile = entity.fluxActiveProfile,
            kernelType = entity.fluxKernelType,
            configDirectory = entity.fluxConfigDirectory,
            synthesisCoreAvailable = entity.synthesisAvailable,
        )
        val environment = BenchmarkEnvironment(
            entity.appVersion, entity.benchmarkSchemaVersion, entity.deviceManufacturer, entity.deviceModel,
            entity.androidFingerprint, entity.kernelVersion, flux, entity.synthesisAvailable, entity.rootState,
            entity.charging, entity.batteryLevel, entity.initialBatteryTemperatureC,
            entity.peakBatteryTemperatureC, entity.androidThermalStatus,
            doubles(entity.thermalHeadroomSamples), entity.refreshRateHz,
        )
        return BenchmarkSession(
            entity.id, entity.startedAtEpochMs, entity.endedAtEpochMs, SessionStatus.valueOf(entity.status),
            entity.label, environment, value.workloads.sortedBy { it.kind }.map { workload ->
                WorkloadResult(
                    WorkloadKind.valueOf(workload.kind), workload.workloadVersion, workload.unit,
                    doubles(workload.repetitions), longs(workload.durationsNs),
                    Statistics(
                        workload.median, workload.minimum, workload.maximum, workload.standardDeviation,
                        workload.coefficientOfVariation,
                    ),
                    workload.validationChecksum, workload.threadCount, workload.affinityForced,
                    lines(workload.warnings),
                )
            },
            lines(entity.warnings), entity.failureReason, ComparisonRole.valueOf(entity.comparisonRole),
        )
    }

    private fun doubles(value: String): List<Double> = value.split(',').mapNotNull(String::toDoubleOrNull)
    private fun longs(value: String): List<Long> = value.split(',').mapNotNull(String::toLongOrNull)
    private fun lines(value: String): List<String> = value.lines().filter(String::isNotBlank)
}
