package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.BenchmarkEnvironment
import com.febricahyaa.fluxlab.model.BenchmarkPresetConfig
import com.febricahyaa.fluxlab.model.BenchmarkProgress
import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.BenchmarkSessionRepository
import com.febricahyaa.fluxlab.model.BenchmarkWorkload
import com.febricahyaa.fluxlab.model.ComparisonRole
import com.febricahyaa.fluxlab.model.DeviceTelemetrySnapshot
import com.febricahyaa.fluxlab.model.SessionStatus
import com.febricahyaa.fluxlab.model.WorkloadKind
import com.febricahyaa.fluxlab.model.WorkloadResult
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

class QuickTestEngine(
    private val repository: BenchmarkSessionRepository,
    private val storage: StorageBenchmarkSuite,
    private val telemetryProvider: suspend () -> DeviceTelemetrySnapshot?,
    private val monitoring: (Boolean) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val running = AtomicBoolean(false)
    private var activeWorkload: BenchmarkWorkload? = null
    private val mutableProgress = MutableStateFlow(BenchmarkProgress(null, SessionStatus.PREPARING, null, 0, 1, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.PREFLIGHT))
    val progress: StateFlow<BenchmarkProgress> = mutableProgress.asStateFlow()
    val isRunning: Boolean get() = running.get()

    suspend fun run(initialEnvironment: BenchmarkEnvironment, includeStorage: Boolean): BenchmarkSession {
        check(running.compareAndSet(false, true)) { "A benchmark is already running" }
        val config = initialEnvironment.presetConfiguration
        return try {
            withTimeout(config.maximumDurationMs) { execute(initialEnvironment, includeStorage, config) }
        } catch (timeout: TimeoutCancellationException) {
            activeWorkload?.cancel()
            storage.cleanupInterruptedFiles()
            val partial = repository.get(mutableProgress.value.sessionId.orEmpty()) ?: throw timeout
            partial.copy(
                endedAtEpochMs = clock(), status = SessionStatus.FAILED,
                failureReason = "Preset maximum duration exceeded",
            ).also { repository.save(it) }
        } finally {
            activeWorkload = null
            running.set(false)
            monitoring(false)
        }
    }

    private suspend fun execute(
        initialEnvironment: BenchmarkEnvironment,
        includeStorage: Boolean,
        config: BenchmarkPresetConfig,
    ): BenchmarkSession {
        val id = UUID.randomUUID().toString()
        val started = clock()
        val nativeKinds = listOf(
            WorkloadKind.CPU_INTEGER, WorkloadKind.CPU_FLOATING_POINT, WorkloadKind.CPU_MULTI_THREADED,
            WorkloadKind.MEMORY_COPY, WorkloadKind.MEMORY_FILL, WorkloadKind.MEMORY_LATENCY,
        )
        val total = nativeKinds.size + if (includeStorage) 3 else 0
        val results = mutableListOf<WorkloadResult>()
        val warnings = initialEnvironment.flux.warnings.toMutableList()
        val headrooms = initialEnvironment.thermalHeadroomSamples.toMutableList()
        var peakBattery = initialEnvironment.initialBatteryTemperatureC
        var session = BenchmarkSession(
            id, started, null, SessionStatus.PREPARING, presetLabel(config), initialEnvironment,
            emptyList(), warnings, null, ComparisonRole.NONE,
        )
        repository.save(session)
        monitoring(true)
        storage.cleanupInterruptedFiles()
        try {
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.PREPARING, null, 0, total, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.COUNTDOWN, totalWorkloads = total, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
            delay(500)
            session = update(session, SessionStatus.WARMING_UP, results, warnings, null, headrooms, peakBattery)
            nativeKinds.forEachIndexed { index, kind ->
                mutableProgress.value = BenchmarkProgress(id, SessionStatus.WARMING_UP, kind, index, total, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.WARMUP, workloadIndex = index, totalWorkloads = total, totalRepetitions = config.warmUpCount, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
                activeWorkload = NativeBenchmarkWorkload(kind, config)
                session = update(session, SessionStatus.RUNNING, results, warnings, null, headrooms, peakBattery)
                mutableProgress.value = BenchmarkProgress(id, SessionStatus.RUNNING, kind, index, total, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.RUNNING, workloadIndex = index, totalWorkloads = total, currentRepetition = 0, totalRepetitions = config.measuredRepetitionCount, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
                results += requireNotNull(activeWorkload).run()
                telemetryProvider()?.let { telemetry ->
                    telemetry.thermal.headroom?.let(headrooms::add)
                    telemetry.battery.temperatureCelsius?.let { peakBattery = maxOf(peakBattery ?: it, it) }
                }
                session = update(session, SessionStatus.RUNNING, results, warnings, null, headrooms, peakBattery)
                delay(config.interTestCooldownMs)
            }
            activeWorkload = null
            if (includeStorage) {
                mutableProgress.value = BenchmarkProgress(id, SessionStatus.RUNNING, WorkloadKind.STORAGE_WRITE, nativeKinds.size, total, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.RUNNING, workloadIndex = nativeKinds.size, totalWorkloads = total, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
                storage.run(config).forEach { result ->
                    results += result
                    session = update(session, SessionStatus.RUNNING, results, warnings, null, headrooms, peakBattery)
                    mutableProgress.value = BenchmarkProgress(id, SessionStatus.RUNNING, result.kind, results.size.coerceAtMost(total), total, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.RUNNING, workloadIndex = results.size.coerceAtMost(total), totalWorkloads = total, currentRepetition = config.measuredRepetitionCount, totalRepetitions = config.measuredRepetitionCount, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
                }
            }
            session = update(session, SessionStatus.COOLING_DOWN, results, warnings, null, headrooms, peakBattery)
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.COOLING_DOWN, null, total, total, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.INTER_WORKLOAD_COOLDOWN, workloadIndex = total, totalWorkloads = total, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
            delay(config.interTestCooldownMs)
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.COOLING_DOWN, null, total, total, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.FINALIZING, workloadIndex = total, totalWorkloads = total, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
            session = update(session.copy(endedAtEpochMs = clock()), SessionStatus.COMPLETED, results, warnings, null, headrooms, peakBattery)
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.COMPLETED, null, total, total, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.COMPLETED, workloadIndex = total, totalWorkloads = total, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
            return session
        } catch (cancelled: CancellationException) {
            if (cancelled is TimeoutCancellationException) throw cancelled
            storage.cleanupInterruptedFiles()
            session = update(
                session.copy(endedAtEpochMs = clock()), SessionStatus.CANCELLED, results, warnings,
                "Cancelled by user", headrooms, peakBattery,
            )
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.CANCELLED, null, results.size, total, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.CANCELLED, workloadIndex = results.size, totalWorkloads = total, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
            return session
        } catch (error: Throwable) {
            storage.cleanupInterruptedFiles()
            session = update(
                session.copy(endedAtEpochMs = clock()), SessionStatus.FAILED, results, warnings,
                error.message ?: error.javaClass.simpleName, headrooms, peakBattery,
            )
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.FAILED, null, results.size, total, session.failureReason, stage = com.febricahyaa.fluxlab.model.BenchmarkStage.FAILED, workloadIndex = results.size, totalWorkloads = total, visualMode = initialEnvironment.visualMode, preset = config.preset, elapsedMs = clock() - started)
            return session
        }
    }

    fun cancel() {
        if (running.get()) mutableProgress.value = mutableProgress.value.copy(stage = com.febricahyaa.fluxlab.model.BenchmarkStage.CANCELLING, status = SessionStatus.RUNNING, message = "Cancelling")
        activeWorkload?.cancel()
    }

    private suspend fun update(
        previous: BenchmarkSession,
        status: SessionStatus,
        results: List<WorkloadResult>,
        warnings: List<String>,
        failure: String?,
        headrooms: List<Double>,
        peakBattery: Double?,
    ): BenchmarkSession = previous.copy(
        status = status,
        workloadResults = results.toList(),
        warnings = warnings.distinct(),
        failureReason = failure,
        environment = previous.environment.copy(
            thermalHeadroomSamples = headrooms.toList(), peakBatteryTemperatureC = peakBattery,
        ),
    ).also { repository.save(it) }

    private fun presetLabel(config: BenchmarkPresetConfig): String = when (config.preset) {
        com.febricahyaa.fluxlab.model.BenchmarkPreset.QUICK -> "Quick Test"
        com.febricahyaa.fluxlab.model.BenchmarkPreset.STANDARD -> "Standard Test"
        com.febricahyaa.fluxlab.model.BenchmarkPreset.EXTENDED -> "Extended Test"
    }
}
