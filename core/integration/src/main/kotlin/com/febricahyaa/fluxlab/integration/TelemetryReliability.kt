package com.febricahyaa.fluxlab.integration

import com.febricahyaa.fluxlab.model.BatteryPowerConfidence
import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.ChargingState
import com.febricahyaa.fluxlab.model.ThermalAssessment
import com.febricahyaa.fluxlab.model.ThermalEligibility
import com.febricahyaa.fluxlab.model.ThermalEligibilityEvaluator
import com.febricahyaa.fluxlab.model.ThermalTemperatureNormalizer
import java.util.Locale

enum class BatteryCurrentUnit { MICROAMPERES, MILLIAMPERES, AMPERES }
enum class BatteryVoltageUnit { MICROVOLTS, MILLIVOLTS, VOLTS }

data class BatteryPowerReading(
    val currentRaw: Long?,
    val normalizedCurrentAmps: Double?,
    val currentUnitSource: String?,
    val voltageRaw: Long?,
    val normalizedVoltageVolts: Double?,
    val voltageUnitSource: String?,
    val calculatedPowerWatts: Double?,
    val chargingState: ChargingState,
    val powerConfidence: BatteryPowerConfidence,
    val powerWarnings: List<String>,
    val powerSource: String?,
)

/** Unit-validated battery power calculation. Values are never inferred from sign alone. */
object BatteryPowerNormalizer {
    private const val MAX_CURRENT_A = 50.0
    private const val MIN_VOLTAGE_V = 2.5
    private const val MAX_VOLTAGE_V = 30.0

    fun normalizeCurrent(raw: Long?, unit: BatteryCurrentUnit): Double? {
        if (raw == null || raw == Long.MIN_VALUE) return null
        val divisor = when (unit) {
            BatteryCurrentUnit.MICROAMPERES -> 1_000_000.0
            BatteryCurrentUnit.MILLIAMPERES -> 1_000.0
            BatteryCurrentUnit.AMPERES -> 1.0
        }
        return (raw.toDouble() / divisor).takeIf { it.isFinite() && kotlin.math.abs(it) <= MAX_CURRENT_A }
    }

    fun normalizeVoltage(raw: Long?, unit: BatteryVoltageUnit): Double? {
        if (raw == null || raw <= 0L || raw == Long.MIN_VALUE) return null
        val divisor = when (unit) {
            BatteryVoltageUnit.MICROVOLTS -> 1_000_000.0
            BatteryVoltageUnit.MILLIVOLTS -> 1_000.0
            BatteryVoltageUnit.VOLTS -> 1.0
        }
        return (raw.toDouble() / divisor).takeIf { it.isFinite() && it in MIN_VOLTAGE_V..MAX_VOLTAGE_V }
    }

    fun read(
        currentRaw: Long?,
        currentUnit: BatteryCurrentUnit,
        voltageRaw: Long?,
        voltageUnit: BatteryVoltageUnit,
        chargingState: ChargingState,
        currentUnitSource: String = currentUnit.name,
        voltageUnitSource: String = voltageUnit.name,
    ): BatteryPowerReading {
        val current = normalizeCurrent(currentRaw, currentUnit)
        val voltage = normalizeVoltage(voltageRaw, voltageUnit)
        val warnings = buildList {
            if (currentRaw == null || currentRaw == Long.MIN_VALUE) add("Battery current is unavailable")
            else if (current == null) add("Battery current is outside the validated range")
            if (voltageRaw == null || voltageRaw <= 0L || voltageRaw == Long.MIN_VALUE) add("Battery voltage is unavailable")
            else if (voltage == null) add("Battery voltage is outside the validated range")
        }
        val power = if (current != null && voltage != null) kotlin.math.abs(current * voltage) else null
        val confidence = when {
            power != null -> BatteryPowerConfidence.HIGH
            currentRaw == null || currentRaw == Long.MIN_VALUE -> BatteryPowerConfidence.UNAVAILABLE
            current != null || voltage != null -> BatteryPowerConfidence.LOW
            else -> BatteryPowerConfidence.UNAVAILABLE
        }
        return BatteryPowerReading(
            currentRaw = currentRaw,
            normalizedCurrentAmps = current,
            currentUnitSource = currentUnitSource,
            voltageRaw = voltageRaw,
            normalizedVoltageVolts = voltage,
            voltageUnitSource = voltageUnitSource,
            calculatedPowerWatts = power,
            chargingState = chargingState,
            powerConfidence = confidence,
            powerWarnings = warnings,
            powerSource = if (power != null) "$currentUnitSource × $voltageUnitSource" else null,
        )
    }
}

data class BatteryPowerPresentation(val text: String, val isAvailable: Boolean)

object BatteryPowerPresenter {
    fun present(
        state: ChargingState,
        powerWatts: Double?,
        languageTag: String = "en",
    ): BatteryPowerPresentation {
        if (powerWatts == null || !powerWatts.isFinite()) {
            return BatteryPowerPresentation(if (languageTag.startsWith("id", true)) "Data daya tidak tersedia" else "Power unavailable", false)
        }
        val indonesian = languageTag.startsWith("id", true)
        val number = String.format(Locale.forLanguageTag(if (indonesian) "id" else "en"), "%.2f", powerWatts)
        val stateLabel = if (indonesian) {
            when (state) {
                ChargingState.CHARGING -> "Mengisi daya"
                ChargingState.DISCHARGING -> "Menggunakan baterai"
                ChargingState.FULL -> "Baterai penuh"
                ChargingState.NOT_CHARGING -> "Tidak mengisi daya"
                ChargingState.UNKNOWN -> "Status daya tidak diketahui"
            }
        } else {
            when (state) {
                ChargingState.CHARGING -> "Charging"
                ChargingState.DISCHARGING -> "Discharging"
                ChargingState.FULL -> "Full"
                ChargingState.NOT_CHARGING -> "Not charging"
                ChargingState.UNKNOWN -> "Power state unknown"
            }
        }
        return BatteryPowerPresentation("$stateLabel • $number W", true)
    }
}

object ThermalReadiness {
    fun evaluate(status: Int?, preset: BenchmarkPreset): ThermalAssessment =
        ThermalEligibilityEvaluator.evaluate(status, preset)

    fun isAllowed(assessment: ThermalAssessment): Boolean =
        assessment.eligibility != ThermalEligibility.BLOCKED_BY_THERMAL_CONDITION
}

object ThermalSensorParser {
    fun normalize(raw: Long) = ThermalTemperatureNormalizer.normalize(raw)
}
