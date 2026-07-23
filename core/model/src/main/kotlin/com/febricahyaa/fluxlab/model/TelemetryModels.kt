package com.febricahyaa.fluxlab.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
    val currentFrequencyHz: Long? = currentFrequencyKhz?.times(1_000L),
    val minimumFrequencyHz: Long? = minimumFrequencyKhz?.times(1_000L),
    val maximumFrequencyHz: Long? = maximumFrequencyKhz?.times(1_000L),
    val frequencySource: String? = null,
    val cluster: String? = null,
)

enum class CpuTelemetryState {
    COLLECTING_INITIAL_SAMPLES,
    ACTIVE,
    TEMPORARILY_UNAVAILABLE,
}

data class CpuTelemetry(
    val totalUsagePercent: Double?,
    val cores: List<CpuCoreTelemetry>,
    val architecture: String,
    val coreCount: Int,
    val identity: CpuIdentity = CpuIdentity(coreCount = coreCount, supportedAbis = listOf(architecture)),
    val aggregateFrequencyHz: Long? = null,
    val frequencySource: String? = null,
    val frequencyConfidence: IdentityConfidence = IdentityConfidence.UNAVAILABLE,
    val sampleState: CpuTelemetryState = if (totalUsagePercent == null) {
        CpuTelemetryState.COLLECTING_INITIAL_SAMPLES
    } else CpuTelemetryState.ACTIVE,
    val aggregateFrequencyMethod: String? = null,
)

enum class MemoryPressure { NORMAL, ELEVATED, HIGH, UNAVAILABLE }

object MemoryPressureClassifier {
    fun classify(someAvg10: Double?, fullAvg10: Double?): MemoryPressure {
        val pressure = listOfNotNull(someAvg10, fullAvg10).filter { it.isFinite() && it >= 0.0 }.maxOrNull()
            ?: return MemoryPressure.UNAVAILABLE
        return when {
            pressure >= 10.0 -> MemoryPressure.HIGH
            pressure >= 1.0 -> MemoryPressure.ELEVATED
            else -> MemoryPressure.NORMAL
        }
    }
}

data class SwapDeviceTelemetry(
    val name: String,
    val type: String?,
    val totalKb: Long,
    val usedKb: Long,
    val priority: Int?,
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
    val buffersKb: Long? = null,
    val shmemKb: Long? = null,
    val psiSomeAvg60: Double? = null,
    val psiSomeAvg300: Double? = null,
    val psiFullAvg60: Double? = null,
    val psiFullAvg300: Double? = null,
    val zramMemoryUsedBytes: Long? = null,
    val zramOriginalDataBytes: Long? = null,
    val zramCompressedDataBytes: Long? = null,
    val pressure: MemoryPressure = MemoryPressure.UNAVAILABLE,
    val swapDevices: List<SwapDeviceTelemetry> = emptyList(),
    val zramDeviceCount: Int = 0,
    val zramCompressionRatio: Double? = null,
    val warnings: List<String> = emptyList(),
)

enum class TemperatureUnitSource { CELSIUS, DECI_CELSIUS, MILLI_CELSIUS, UNKNOWN }

data class ThermalZone(
    val name: String,
    val temperatureCelsius: Double?,
    val sourcePath: String,
    val rawValue: Long? = null,
    val unitSource: TemperatureUnitSource = TemperatureUnitSource.UNKNOWN,
    val group: ThermalSensorGroup = ThermalSensorGroup.OTHER,
)

enum class ThermalSensorGroup { CPU, GPU, BATTERY, CHARGER, MODEM, WIFI, SKIN, PMIC, CAMERA, DISPLAY, OTHER }

data class ThermalTelemetry(
    val androidStatus: Int?,
    val headroom: Double?,
    val zones: List<ThermalZone>,
    val batteryTemperatureCelsius: Double?,
    val primaryTemperatureCelsius: Double?,
    val primarySource: String?,
    val eligibility: ThermalEligibility = ThermalEligibility.THERMAL_STATUS_UNAVAILABLE,
    val warnings: List<String> = emptyList(),
    val hottestZone: ThermalZone? = null,
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
    val normalizedCurrentAmps: Double? = currentMicroamps?.div(1_000_000.0),
    val normalizedVoltageVolts: Double? = voltageMillivolts?.div(1_000.0),
    val powerSource: String? = null,
    val averageCurrentMicroamps: Long? = null,
    val diagnostics: BatteryDiagnostics = BatteryDiagnostics(),
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
    val driverPath: String? = null,
    val utilizationPercent: Double? = null,
    val identitySource: String? = null,
    val utilizationSource: String? = null,
    val utilizationAvailabilityReason: String? = null,
    val capabilityState: GpuCapabilityState = GpuCapabilityState.GPU_NOT_IDENTIFIED,
    val warnings: List<String> = emptyList(),
)

interface GpuCapabilityProvider {
    suspend fun sample(): GpuTelemetry
    fun reset() {}
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
    val storage: StorageTelemetry = StorageTelemetry(),
)

data class TelemetryRepositoryState(
    val latest: DeviceTelemetrySnapshot? = null,
    val history: List<DeviceTelemetrySnapshot> = emptyList(),
    val status: TelemetrySourceStatus = TelemetrySourceStatus(),
    val successfulSampleCount: Long = 0L,
)

interface DeviceTelemetryRepository {
    val state: StateFlow<TelemetryRepositoryState>
    fun start(intervalMs: Long)
    fun stop()
    fun reset()
}

interface DeviceTelemetrySource {
    suspend fun sample(): DeviceTelemetrySnapshot
    fun stream(intervalMs: Long): Flow<DeviceTelemetrySnapshot>
    fun reset() {}
}
