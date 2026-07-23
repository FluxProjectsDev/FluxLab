package com.febricahyaa.fluxlab.integration

import android.os.SystemClock
import com.febricahyaa.fluxlab.model.DeviceTelemetryRepository
import com.febricahyaa.fluxlab.model.DeviceTelemetrySource
import com.febricahyaa.fluxlab.model.MonitoringState
import com.febricahyaa.fluxlab.model.TelemetryRepositoryState
import com.febricahyaa.fluxlab.model.TelemetrySourceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One sampler for Overview, detail screens, and benchmark-required telemetry. */
class AndroidTelemetryRepository(
    private val source: DeviceTelemetrySource,
    private val scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val samplerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeviceTelemetryRepository {
    private val mutableState = MutableStateFlow(TelemetryRepositoryState())
    override val state: StateFlow<TelemetryRepositoryState> = mutableState.asStateFlow()

    private var samplingJob: Job? = null
    private var activeIntervalMs: Long? = null

    @Synchronized
    override fun start(intervalMs: Long) {
        val bounded = intervalMs.coerceIn(250L, 10_000L)
        if (samplingJob?.isActive == true && activeIntervalMs == bounded) return
        samplingJob?.cancel()
        activeIntervalMs = bounded
        mutableState.value = mutableState.value.copy(
            status = mutableState.value.status.copy(
                state = MonitoringState.STARTING,
                reason = null,
                consecutiveFailureCount = 0,
            ),
        )
        samplingJob = scope.launch(samplerDispatcher) {
            while (isActive) {
                val startedAt = clock()
                try {
                    val snapshot = source.sample()
                    val previous = mutableState.value
                    val sourceState = when {
                        snapshot.cpu.totalUsagePercent != null -> MonitoringState.ACTIVE
                        previous.successfulSampleCount == 0L -> MonitoringState.COLLECTING_INITIAL_SAMPLES
                        else -> MonitoringState.TEMPORARILY_UNAVAILABLE
                    }
                    mutableState.value = TelemetryRepositoryState(
                        latest = snapshot,
                        history = (previous.history + snapshot).takeLast(MAX_HISTORY),
                        status = TelemetrySourceStatus(
                            state = sourceState,
                            source = "DeviceTelemetrySource",
                            lastSuccessfulSampleElapsedMs = snapshot.elapsedRealtimeMs,
                            consecutiveFailureCount = 0,
                            warning = if (sourceState == MonitoringState.TEMPORARILY_UNAVAILABLE) {
                                "CPU counters have not produced a valid interval"
                            } else null,
                        ),
                        successfulSampleCount = previous.successfulSampleCount + 1L,
                    )
                } catch (error: Throwable) {
                    if (!isActive) break
                    val previous = mutableState.value
                    val failures = previous.status.consecutiveFailureCount + 1
                    mutableState.value = previous.copy(
                        status = previous.status.copy(
                            state = if (previous.latest == null) MonitoringState.FAILED else MonitoringState.TEMPORARILY_UNAVAILABLE,
                            reason = error.javaClass.simpleName,
                            consecutiveFailureCount = failures,
                            warning = "Telemetry read failed; retrying",
                        ),
                    )
                }
                val interval = activeIntervalMs ?: bounded
                val elapsed = clock() - startedAt
                delay((interval - elapsed).coerceAtLeast(0L))
            }
        }
    }

    @Synchronized
    override fun stop() {
        samplingJob?.cancel()
        samplingJob = null
        activeIntervalMs = null
        source.reset()
        mutableState.value = mutableState.value.copy(
            status = mutableState.value.status.copy(
                state = MonitoringState.PAUSED,
                reason = "Monitoring is paused",
                warning = null,
                consecutiveFailureCount = 0,
            ),
        )
    }

    @Synchronized
    override fun reset() {
        samplingJob?.cancel()
        samplingJob = null
        activeIntervalMs = null
        source.reset()
        mutableState.value = TelemetryRepositoryState()
    }

    private companion object { const val MAX_HISTORY = 120 }
}
