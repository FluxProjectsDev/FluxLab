package com.febricahyaa.fluxlab.integration

import com.febricahyaa.fluxlab.model.BatteryTelemetry
import com.febricahyaa.fluxlab.model.CpuTelemetry
import com.febricahyaa.fluxlab.model.DeviceTelemetrySnapshot
import com.febricahyaa.fluxlab.model.DeviceTelemetrySource
import com.febricahyaa.fluxlab.model.GpuTelemetry
import com.febricahyaa.fluxlab.model.MemoryTelemetry
import com.febricahyaa.fluxlab.model.MonitoringState
import com.febricahyaa.fluxlab.model.SystemTelemetry
import com.febricahyaa.fluxlab.model.ThermalTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTelemetryRepositoryTest {
    @Test
    fun firstSampleCollectsAndSecondSampleActivatesWithoutResettingSource() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val source = FakeTelemetrySource()
        val repository = AndroidTelemetryRepository(source, scope, { testScheduler.currentTime }, dispatcher)

        repository.start(250L)
        runCurrent()
        assertEquals(MonitoringState.COLLECTING_INITIAL_SAMPLES, repository.state.value.status.state)
        assertEquals(1, source.sampleCount)

        advanceTimeBy(250L)
        runCurrent()
        assertEquals(MonitoringState.ACTIVE, repository.state.value.status.state)
        assertEquals(2, source.sampleCount)
        assertEquals(2, repository.state.value.history.size)

        repository.start(250L)
        runCurrent()
        assertEquals(2, source.sampleCount)
        assertEquals(0, source.resetCount)

        repository.stop()
        assertEquals(MonitoringState.PAUSED, repository.state.value.status.state)
        assertEquals(1, source.resetCount)
        scope.cancel()
    }

    @Test
    fun historyRemainsBounded() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val repository = AndroidTelemetryRepository(FakeTelemetrySource(), scope, { testScheduler.currentTime }, dispatcher)

        repository.start(250L)
        advanceTimeBy(250L * 150L)
        runCurrent()

        assertTrue(repository.state.value.history.size <= 120)
        assertTrue(repository.state.value.successfulSampleCount >= 100)
        scope.cancel()
    }

    private class FakeTelemetrySource : DeviceTelemetrySource {
        var sampleCount = 0
        var resetCount = 0

        override suspend fun sample(): DeviceTelemetrySnapshot {
            sampleCount += 1
            return snapshot(sampleCount.toLong(), if (sampleCount == 1) null else 20.0 + sampleCount)
        }

        override fun stream(intervalMs: Long): Flow<DeviceTelemetrySnapshot> = emptyFlow()

        override fun reset() {
            resetCount += 1
        }
    }

    private companion object {
        fun snapshot(timestamp: Long, usage: Double?): DeviceTelemetrySnapshot = DeviceTelemetrySnapshot(
            elapsedRealtimeMs = timestamp,
            cpu = CpuTelemetry(usage, emptyList(), "arm64-v8a", 8),
            memory = MemoryTelemetry(8_000_000L, 4_000_000L, 4_000_000L, 500_000L, null, null, null, null, null, null),
            thermal = ThermalTelemetry(null, null, emptyList(), null, null, null),
            battery = BatteryTelemetry(80, false, null, 30.0, null, null, null, null),
            gpu = GpuTelemetry(null, null, null, null, null, null),
            system = SystemTelemetry("Test", "Device", "test", 35, "test/fingerprint", "test", null, timestamp, 60.0),
        )
    }
}
