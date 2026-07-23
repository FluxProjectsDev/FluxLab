package com.febricahyaa.fluxlab.model

/** The result of the thermal gate before a benchmark starts. */
enum class ThermalEligibility {
    READY,
    READY_WITH_WARNING,
    COOLDOWN_RECOMMENDED,
    BLOCKED_BY_THERMAL_CONDITION,
    THERMAL_STATUS_UNAVAILABLE,
}

data class ThermalAssessment(
    val eligibility: ThermalEligibility,
    val status: Int?,
    val reason: String,
    val recommendedAction: String,
    val mayBeThermallyBiased: Boolean,
    val warnings: List<String> = emptyList(),
)

/**
 * Maps Android's PowerManager thermal status values to a benchmark-specific
 * eligibility.  The values intentionally mirror the platform constants but
 * do not depend on the Android SDK, keeping this rule deterministic in unit
 * tests and safe for older API levels.
 */
object ThermalEligibilityEvaluator {
    const val THERMAL_STATUS_NONE = 0
    const val THERMAL_STATUS_LIGHT = 1
    const val THERMAL_STATUS_MODERATE = 2
    const val THERMAL_STATUS_SEVERE = 3
    const val THERMAL_STATUS_CRITICAL = 4
    const val THERMAL_STATUS_EMERGENCY = 5
    const val THERMAL_STATUS_SHUTDOWN = 6

    fun evaluate(status: Int?, preset: BenchmarkPreset): ThermalAssessment {
        if (status == null) {
            return ThermalAssessment(
                eligibility = ThermalEligibility.THERMAL_STATUS_UNAVAILABLE,
                status = null,
                reason = "Android thermal status is unavailable",
                recommendedAction = "Continue only if the device is cool and stable",
                mayBeThermallyBiased = true,
                warnings = listOf("Thermal status source is unavailable"),
            )
        }

        return when {
            status <= THERMAL_STATUS_NONE -> ThermalAssessment(
                eligibility = ThermalEligibility.READY,
                status = status,
                reason = "Android reports no thermal pressure",
                recommendedAction = "No cooldown is required",
                mayBeThermallyBiased = false,
            )
            status == THERMAL_STATUS_LIGHT -> ThermalAssessment(
                eligibility = ThermalEligibility.READY_WITH_WARNING,
                status = status,
                reason = "Android reports light thermal pressure",
                recommendedAction = "Avoid other sustained workloads",
                mayBeThermallyBiased = true,
                warnings = listOf("Light thermal pressure may affect repeatability"),
            )
            status == THERMAL_STATUS_MODERATE -> {
                val quick = preset == BenchmarkPreset.QUICK
                ThermalAssessment(
                    eligibility = if (quick) ThermalEligibility.READY_WITH_WARNING else ThermalEligibility.COOLDOWN_RECOMMENDED,
                    status = status,
                    reason = "Android reports moderate thermal pressure",
                    recommendedAction = "Let the device cool before a longer test",
                    mayBeThermallyBiased = true,
                    warnings = listOf("Moderate thermal pressure may throttle sustained workloads"),
                )
            }
            status >= THERMAL_STATUS_SEVERE -> {
                val blocked = preset != BenchmarkPreset.QUICK
                ThermalAssessment(
                    eligibility = if (blocked) ThermalEligibility.BLOCKED_BY_THERMAL_CONDITION else ThermalEligibility.READY_WITH_WARNING,
                    status = status,
                    reason = "Android reports severe or higher thermal pressure",
                    recommendedAction = "Stop heavy workloads and cool the device",
                    mayBeThermallyBiased = true,
                    warnings = listOf("Severe thermal pressure makes sustained results unreliable"),
                )
            }
            else -> ThermalAssessment(
                eligibility = ThermalEligibility.THERMAL_STATUS_UNAVAILABLE,
                status = status,
                reason = "Android returned an unknown thermal status",
                recommendedAction = "Check device temperature before testing",
                mayBeThermallyBiased = true,
                warnings = listOf("Unknown thermal status value: $status"),
            )
        }
    }
}

data class NormalizedTemperature(
    val celsius: Double,
    val unitSource: TemperatureUnitSource,
    val rawValue: Long,
)

/** Normalizes common Android thermal sensor encodings without guessing a sensor type. */
object ThermalSensorClassifier {
    fun classify(rawName: String): ThermalSensorGroup {
        val name = rawName.lowercase()
        return when {
            listOf("cpu", "soc", "cluster").any(name::contains) -> ThermalSensorGroup.CPU
            listOf("gpu", "kgsl", "adreno", "mali").any(name::contains) -> ThermalSensorGroup.GPU
            listOf("battery", "batt").any(name::contains) -> ThermalSensorGroup.BATTERY
            listOf("charger", "charge", "usb").any(name::contains) -> ThermalSensorGroup.CHARGER
            listOf("modem", "radio", "mdm").any(name::contains) -> ThermalSensorGroup.MODEM
            listOf("wifi", "wlan").any(name::contains) -> ThermalSensorGroup.WIFI
            listOf("skin", "skin_temp", "case").any(name::contains) -> ThermalSensorGroup.SKIN
            listOf("pmic", "power").any(name::contains) -> ThermalSensorGroup.PMIC
            listOf("camera", "cam").any(name::contains) -> ThermalSensorGroup.CAMERA
            listOf("display", "panel", "lcd", "oled").any(name::contains) -> ThermalSensorGroup.DISPLAY
            else -> ThermalSensorGroup.OTHER
        }
    }
}

object ThermalTemperatureNormalizer {
    private const val MIN_CELSIUS = -40.0
    private const val MAX_CELSIUS = 150.0

    fun normalize(rawValue: Long): NormalizedTemperature? {
        if (rawValue == 0L) return null
        val magnitude = kotlin.math.abs(rawValue)
        val candidates = when {
            magnitude <= 200L -> listOf(rawValue.toDouble() to TemperatureUnitSource.CELSIUS)
            magnitude <= 20_000L -> listOf(rawValue / 10.0 to TemperatureUnitSource.DECI_CELSIUS)
            else -> listOf(rawValue / 1_000.0 to TemperatureUnitSource.MILLI_CELSIUS)
        }
        val (celsius, unit) = candidates.firstOrNull { it.first in MIN_CELSIUS..MAX_CELSIUS }
            ?: return null
        return NormalizedTemperature(celsius = celsius, unitSource = unit, rawValue = rawValue)
    }
}
