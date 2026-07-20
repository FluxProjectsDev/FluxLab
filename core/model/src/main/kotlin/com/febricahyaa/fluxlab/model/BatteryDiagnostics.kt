package com.febricahyaa.fluxlab.model

/** Platform health is an enum; it is deliberately separate from estimated SoH. */
enum class BatteryHealthState { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, UNSPECIFIED_FAILURE, COLD, UNKNOWN }

enum class BatteryCapacityUnit { MICROAMP_HOURS, MILLIAMP_HOURS, MICRO_WATT_HOURS, MILLI_WATT_HOURS, UNKNOWN }

data class BatteryCapacityReading(
    val rawValue: Long? = null,
    val rawUnit: BatteryCapacityUnit = BatteryCapacityUnit.UNKNOWN,
    val normalizedMilliAmpHours: Double? = null,
    val source: String? = null,
    val confidence: BatteryPowerConfidence = BatteryPowerConfidence.UNAVAILABLE,
    val warning: String? = null,
)

data class BatteryCapacityDiagnostics(
    val currentCharge: BatteryCapacityReading = BatteryCapacityReading(),
    val designCapacity: BatteryCapacityReading = BatteryCapacityReading(),
    val fullChargeCapacity: BatteryCapacityReading = BatteryCapacityReading(),
    val estimatedSoHPercent: Double? = null,
    val sohWarning: String? = null,
    val capacitySource: String? = null,
    val capacityConfidence: BatteryPowerConfidence = BatteryPowerConfidence.UNAVAILABLE,
)

data class BatteryDiagnostics(
    val status: String? = null,
    val health: BatteryHealthState = BatteryHealthState.UNKNOWN,
    val technology: String? = null,
    val present: Boolean? = null,
    val cycleCount: Int? = null,
    val cycleCountSource: String? = null,
    val cycleCountWarning: String? = null,
    val chargeTimeRemainingMs: Long? = null,
    val dischargeTimeRemainingMs: Long? = null,
    val maximumChargingCurrentMicroamps: Long? = null,
    val maximumChargingVoltageMicrovolts: Long? = null,
    val capacity: BatteryCapacityDiagnostics = BatteryCapacityDiagnostics(),
    val warnings: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
)

object BatteryCapacityNormalizer {
    private const val MAX_MAH = 2_000_000.0

    fun toMilliAmpHours(raw: Long?, unit: BatteryCapacityUnit): Double? {
        if (raw == null || raw <= 0L || raw == Long.MIN_VALUE) return null
        val factor = when (unit) {
            BatteryCapacityUnit.MICROAMP_HOURS -> 1e-3
            BatteryCapacityUnit.MILLIAMP_HOURS -> 1.0
            BatteryCapacityUnit.MICRO_WATT_HOURS,
            BatteryCapacityUnit.MILLI_WATT_HOURS,
            BatteryCapacityUnit.UNKNOWN -> return null
        }
        return (raw.toDouble() * factor).takeIf { it.isFinite() && it in 1.0..MAX_MAH }
    }

    fun reading(raw: Long?, unit: BatteryCapacityUnit, source: String?): BatteryCapacityReading {
        val normalized = toMilliAmpHours(raw, unit)
        return BatteryCapacityReading(
            rawValue = raw,
            rawUnit = unit,
            normalizedMilliAmpHours = normalized,
            source = source,
            confidence = when {
                normalized == null && raw == null -> BatteryPowerConfidence.UNAVAILABLE
                normalized == null -> BatteryPowerConfidence.LOW
                else -> BatteryPowerConfidence.HIGH
            },
            warning = if (raw != null && normalized == null) "Capacity value or unit is outside the validated range" else null,
        )
    }
}

object BatteryHealthParser {
    // Android BatteryManager.BATTERY_HEALTH_* values, kept here to avoid an
    // Android dependency in the model module.
    fun parse(raw: Int?): BatteryHealthState = when (raw) {
        2 -> BatteryHealthState.GOOD
        3 -> BatteryHealthState.OVERHEAT
        4 -> BatteryHealthState.DEAD
        5 -> BatteryHealthState.OVER_VOLTAGE
        6 -> BatteryHealthState.UNSPECIFIED_FAILURE
        7 -> BatteryHealthState.COLD
        else -> BatteryHealthState.UNKNOWN
    }
}

object BatteryHealthEstimator {
    fun estimate(fullChargeMilliAmpHours: Double?, designMilliAmpHours: Double?): Double? {
        if (fullChargeMilliAmpHours == null || designMilliAmpHours == null) return null
        if (!fullChargeMilliAmpHours.isFinite() || !designMilliAmpHours.isFinite() || designMilliAmpHours <= 0.0) return null
        val estimate = fullChargeMilliAmpHours / designMilliAmpHours * 100.0
        return estimate.takeIf { it.isFinite() && it in 1.0..150.0 }
    }
}

interface BatteryDiagnosticsProvider {
    suspend fun read(): BatteryDiagnostics
}
