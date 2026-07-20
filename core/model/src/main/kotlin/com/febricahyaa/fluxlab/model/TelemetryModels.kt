package com.febricahyaa.fluxlab.model

import kotlinx.coroutines.flow.Flow

enum class CapabilityState { AVAILABLE, PARTIAL, UNAVAILABLE, PERMISSION_DENIED, MALFORMED, UNSUPPORTED }
enum class IdentityConfidence { HIGH, MEDIUM, LOW, UNAVAILABLE }

data class CpuIdentity(
    val manufacturer: String? = null,
    val model: String? = null,
    val hardware: String? = null,
    val board: String? = null,
    val coreCount: Int,
    val supportedAbis: List<String>,
    val identitySource: String? = null,
    val confidence: IdentityConfidence = IdentityConfidence.UNAVAILABLE,
    val capabilityState: CapabilityState = CapabilityState.UNAVAILABLE,
    val rawSources: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

interface CpuIdentityProvider {
    suspend fun identify(): CpuIdentity
}

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
    val identity: CpuIdentity = CpuIdentity(coreCount = coreCount, supportedAbis = listOf(architecture)),
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

enum class TemperatureUnitSource { CELSIUS, DECI_CELSIUS, MILLI_CELSIUS, UNKNOWN }

data class ThermalZone(
    val name: String,
    val temperatureCelsius: Double?,
    val sourcePath: String,
    val rawValue: Long? = null,
    val unitSource: TemperatureUnitSource = TemperatureUnitSource.UNKNOWN,
)

data class ThermalTelemetry(
    val androidStatus: Int?,
    val headroom: Double?,
    val zones: List<ThermalZone>,
    val batteryTemperatureCelsius: Double?,
    val primaryTemperatureCelsius: Double?,
    val primarySource: String?,
)

enum class BatteryPowerConfidence { HIGH, MEDIUM, LOW, UNAVAILABLE }
enum class ChargingState { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }

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
    val currentRaw: Long? = currentMicroamps,
    val currentUnitSource: String? = currentMicroamps?.let { "BatteryManager microamperes" },
    val voltageRaw: Long? = voltageMillivolts,
    val voltageUnitSource: String? = voltageMillivolts?.let { "ACTION_BATTERY_CHANGED millivolts" },
    val calculatedPowerWatts: Double? = estimatedPowerWatts,
    val chargingState: ChargingState = ChargingState.UNKNOWN,
    val powerConfidence: BatteryPowerConfidence = BatteryPowerConfidence.UNAVAILABLE,
    val powerWarnings: List<String> = emptyList(),
)

enum class GpuCapabilityState {
    IDENTIFIED_TELEMETRY_AVAILABLE,
    IDENTIFIED_FREQUENCY_INACCESSIBLE,
    IDENTIFIED_UTILIZATION_INACCESSIBLE,
    ROOT_REQUIRED,
    PERMISSION_DENIED,
    DRIVER_PATH_UNAVAILABLE,
    GPU_NOT_IDENTIFIED,
    UNSUPPORTED_DEVICE_TOPOLOGY,
}

data class GpuTelemetry(
    val vendor: String?,
    val model: String?,
    val currentFrequencyHz: Long?,
    val minimumFrequencyHz: Long?,
    val maximumFrequencyHz: Long?,
    val frequencySource: String?,
    val driver: String? = null,
    val utilizationPercent: Double? = null,
    val identitySource: String? = null,
    val utilizationSource: String? = null,
    val capabilityState: GpuCapabilityState = GpuCapabilityState.GPU_NOT_IDENTIFIED,
    val warnings: List<String> = emptyList(),
)

interface GpuCapabilityProvider {
    suspend fun sample(): GpuTelemetry
}

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
