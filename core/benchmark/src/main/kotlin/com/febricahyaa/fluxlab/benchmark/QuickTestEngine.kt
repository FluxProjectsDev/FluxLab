package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.BenchmarkEnvironment
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QuickTestEngine(
    private val repository: BenchmarkSessionRepository,
    private val storage: StorageBenchmarkSuite,
    private val telemetryProvider: suspend () -> DeviceTelemetrySnapshot?,
    private val monitoring: (Boolean) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val running = AtomicBoolean(false)
    private var activeWorkload: BenchmarkWorkload? = null
    private val mutableProgress = MutableStateFlow(
        BenchmarkProgress(null, SessionStatus.PREPARING, null, 0, 1),
    )
    val progress: StateFlow<BenchmarkProgress> = mutableProgress.asStateFlow()
    val isRunning: Boolean get() = running.get()

    suspend fun run(initialEnvironment: BenchmarkEnvironment, includeStorage: Boolean): BenchmarkSession {
        check(running.compareAndSet(false, true)) { "A benchmark is already running" }
        val id = UUID.randomUUID().toString()
        val started = clock()
        val nativeKinds = listOf(
            WorkloadKind.CPU_INTEGER,
            WorkloadKind.CPU_FLOATING_POINT,
            WorkloadKind.CPU_MULTI_THREADED,
            WorkloadKind.MEMORY_COPY,
            WorkloadKind.MEMORY_FILL,
            WorkloadKind.MEMORY_LATENCY,
        )
        val total = nativeKinds.size + if (includeStorage) 3 else 0
        val results = mutableListOf<WorkloadResult>()
        val warnings = initialEnvironment.flux.warnings.toMutableList()
        val headrooms = initialEnvironment.thermalHeadroomSamples.toMutableList()
        var peakBattery = initialEnvironment.initialBatteryTemperatureC
        var session = BenchmarkSession(
            id, started, null, SessionStatus.PREPARING, "Quick Test", initialEnvironment,
            emptyList(), warnings, null, ComparisonRole.NONE,
        )
        repository.save(session)
        monitoring(true)
        storage.cleanupInterruptedFiles()
        try {
            session = update(session, SessionStatus.WARMING_UP, results, warnings, null, headrooms, peakBattery)
            nativeKinds.forEachIndexed { index, kind ->
                mutableProgress.value = BenchmarkProgress(id, SessionStatus.WARMING_UP, kind, index, total)
                val workload = NativeBenchmarkWorkload(kind)
                activeWorkload = workload
                session = update(session, SessionStatus.RUNNING, results, warnings, null, headrooms, peakBattery)
                mutableProgress.value = BenchmarkProgress(id, SessionStatus.RUNNING, kind, index, total)
                results += workload.run()
                val telemetry = telemetryProvider()
                telemetry?.thermal?.headroom?.let(headrooms::add)
                telemetry?.battery?.temperatureCelsius?.let { sample ->
                    peakBattery = maxOf(peakBattery ?: sample, sample)
                }
                session = update(session, SessionStatus.RUNNING, results, warnings, null, headrooms, peakBattery)
            }
            activeWorkload = null
            if (includeStorage) {
                mutableProgress.value = BenchmarkProgress(
                    id, SessionStatus.RUNNING, WorkloadKind.STORAGE_WRITE, nativeKinds.size, total,
                )
                storage.run().forEach { result ->
                    results += result
                    session = update(session, SessionStatus.RUNNING, results, warnings, null, headrooms, peakBattery)
                    mutableProgress.value = BenchmarkProgress(
                        id, SessionStatus.RUNNING, result.kind, results.size.coerceAtMost(total), total,
                    )
                }
            }
            session = update(session, SessionStatus.COOLING_DOWN, results, warnings, null, headrooms, peakBattery)
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.COOLING_DOWN, null, total, total)
            delay(350)
            session = update(
                session.copy(endedAtEpochMs = clock()), SessionStatus.COMPLETED,
                results, warnings, null, headrooms, peakBattery,
            )
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.COMPLETED, null, total, total)
            return session
        } catch (cancelled: CancellationException) {
            storage.cleanupInterruptedFiles()
            session = update(
                session.copy(endedAtEpochMs = clock()), SessionStatus.CANCELLED,
                results, warnings, "Cancelled by user", headrooms, peakBattery,
            )
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.CANCELLED, null, results.size, total)
            return session
        } catch (error: Throwable) {
            storage.cleanupInterruptedFiles()
            session = update(
                session.copy(endedAtEpochMs = clock()), SessionStatus.FAILED,
                results, warnings, error.message ?: error.javaClass.simpleName, headrooms, peakBattery,
            )
            mutableProgress.value = BenchmarkProgress(id, SessionStatus.FAILED, null, results.size, total, session.failureReason)
            return session
        } finally {
            activeWorkload = null
            running.set(false)
            monitoring(false)
        }
    }

    fun cancel() = activeWorkload?.cancel()

    private suspend fun update(
        previous: BenchmarkSession,
        status: SessionStatus,
        results: List<WorkloadResult>,
        warnings: List<String>,
        failure: String?,
        headrooms: List<Double>,
        peakBattery: Double?,
    ): BenchmarkSession {
        val updated = previous.copy(
            status = status,
            workloadResults = results.toList(),
            warnings = warnings.distinct(),
            failureReason = failure,
            environment = previous.environment.copy(
                thermalHeadroomSamples = headrooms.toList(),
                peakBatteryTemperatureC = peakBattery,
            ),
        )
        repository.save(updated)
        return updated
    }
}
