package com.febricahyaa.fluxlab.model

import kotlinx.coroutines.flow.Flow

data class CpuCoreTelemetry(
    val index: Int,
    val usagePercent: Double?,
    val currentFrequencyKhz: Long?,
    val minimumFrequencyKhz: Long?,
    val maximumFrequencyKhz: Long?,
    val online: Boolean,
    val governor: String?,
)

data class CpuTelemetry(
    val totalUsagePercent: Double?,
    val cores: List<CpuCoreTelemetry>,
    val architecture: String,
    val coreCount: Int,
)

data class MemoryTelemetry(
    val totalKb: Long?,
    val availableKb: Long?,
    val usedKb: Long?,
    val cachedKb: Long?,
    val swapTotalKb: Long?,
    val swapUsedKb: Long?,
    val zramBytes: Long?,
    val psiSomeAvg10: Double?,
    val psiFullAvg10: Double?,
    val majorPageFaults: Long?,
)

data class ThermalZone(
    val name: String,
    val temperatureCelsius: Double?,
    val sourcePath: String,
)

data class ThermalTelemetry(
    val androidStatus: Int?,
    val headroom: Double?,
    val zones: List<ThermalZone>,
    val batteryTemperatureCelsius: Double?,
    val primaryTemperatureCelsius: Double?,
    val primarySource: String?,
)

data class BatteryTelemetry(
    val levelPercent: Int?,
    val charging: Boolean?,
    val plugType: String?,
    val temperatureCelsius: Double?,
    val currentMicroamps: Long?,
    val voltageMillivolts: Long?,
    val chargeCounterMicroampHours: Long?,
    val estimatedPowerWatts: Double?,
    val powerIsEstimated: Boolean = false,
)

data class GpuTelemetry(
    val vendor: String?,
    val model: String?,
    val currentFrequencyHz: Long?,
    val minimumFrequencyHz: Long?,
    val maximumFrequencyHz: Long?,
    val frequencySource: String?,
)

data class SystemTelemetry(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val buildFingerprint: String,
    val kernelVersion: String,
    val isGki: Boolean?,
    val uptimeMs: Long,
    val refreshRateHz: Double?,
)

data class DeviceTelemetrySnapshot(
    val elapsedRealtimeMs: Long,
    val cpu: CpuTelemetry,
    val memory: MemoryTelemetry,
    val thermal: ThermalTelemetry,
    val battery: BatteryTelemetry,
    val gpu: GpuTelemetry,
    val system: SystemTelemetry,
)

interface DeviceTelemetrySource {
    suspend fun sample(): DeviceTelemetrySnapshot
    fun stream(intervalMs: Long): Flow<DeviceTelemetrySnapshot>
}
