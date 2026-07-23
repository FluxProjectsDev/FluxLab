package com.febricahyaa.fluxlab.integration

import com.febricahyaa.fluxlab.model.BatteryPowerConfidence
import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.ChargingState
import com.febricahyaa.fluxlab.model.TemperatureUnitSource
import com.febricahyaa.fluxlab.model.ThermalEligibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryReliabilityTest {
    @Test
    fun `thermal mapping allows quick with warning but blocks sustained presets`() {
        val quick = ThermalReadiness.evaluate(2, BenchmarkPreset.QUICK)
        val standard = ThermalReadiness.evaluate(2, BenchmarkPreset.STANDARD)
        val extended = ThermalReadiness.evaluate(3, BenchmarkPreset.EXTENDED)

        assertEquals(ThermalEligibility.READY_WITH_WARNING, quick.eligibility)
        assertEquals(ThermalEligibility.COOLDOWN_RECOMMENDED, standard.eligibility)
        assertEquals(ThermalEligibility.BLOCKED_BY_THERMAL_CONDITION, extended.eligibility)
        assertFalse(ThermalReadiness.isAllowed(extended))
    }

    @Test
    fun `thermal status unavailable is explicit and not a silent ready`() {
        val result = ThermalReadiness.evaluate(null, BenchmarkPreset.STANDARD)

        assertEquals(ThermalEligibility.THERMAL_STATUS_UNAVAILABLE, result.eligibility)
        assertTrue(result.mayBeThermallyBiased)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `temperature parser preserves unit and rejects implausible values`() {
        assertEquals(TemperatureUnitSource.CELSIUS, ThermalSensorParser.normalize(45)?.unitSource)
        assertEquals(45.0, ThermalSensorParser.normalize(450)?.celsius ?: Double.NaN, 0.0001)
        assertEquals(TemperatureUnitSource.MILLI_CELSIUS, ThermalSensorParser.normalize(45_000)?.unitSource)
        assertNull(ThermalSensorParser.normalize(2_000))
        assertNull(ThermalSensorParser.normalize(250_000))
    }

    @Test
    fun `battery power validates microamp and millivolt units`() {
        val reading = BatteryPowerNormalizer.read(
            currentRaw = -2_040_000L,
            currentUnit = BatteryCurrentUnit.MICROAMPERES,
            voltageRaw = 4_100L,
            voltageUnit = BatteryVoltageUnit.MILLIVOLTS,
            chargingState = ChargingState.DISCHARGING,
        )

        assertEquals(-2.04, reading.normalizedCurrentAmps!!, 0.0001)
        assertEquals(4.1, reading.normalizedVoltageVolts!!, 0.0001)
        assertEquals(8.364, reading.calculatedPowerWatts!!, 0.0001)
        assertEquals(BatteryPowerConfidence.HIGH, reading.powerConfidence)
    }

    @Test
    fun `invalid battery inputs do not produce power`() {
        val invalidCurrent = BatteryPowerNormalizer.read(
            currentRaw = Long.MIN_VALUE,
            currentUnit = BatteryCurrentUnit.MICROAMPERES,
            voltageRaw = 4_100L,
            voltageUnit = BatteryVoltageUnit.MILLIVOLTS,
            chargingState = ChargingState.UNKNOWN,
        )
        val invalidVoltage = BatteryPowerNormalizer.read(
            currentRaw = 2_000_000L,
            currentUnit = BatteryCurrentUnit.MICROAMPERES,
            voltageRaw = 0L,
            voltageUnit = BatteryVoltageUnit.MILLIVOLTS,
            chargingState = ChargingState.CHARGING,
        )

        assertNull(invalidCurrent.calculatedPowerWatts)
        assertEquals(BatteryPowerConfidence.UNAVAILABLE, invalidCurrent.powerConfidence)
        assertNull(invalidVoltage.calculatedPowerWatts)
        assertEquals(BatteryPowerConfidence.LOW, invalidVoltage.powerConfidence)
    }

    @Test
    fun `power presentation uses semantic charging state`() {
        assertEquals("Discharging • 2.04 W", BatteryPowerPresenter.present(ChargingState.DISCHARGING, 2.04).text)
        assertEquals("Charging • 8.31 W", BatteryPowerPresenter.present(ChargingState.CHARGING, 8.31).text)
        assertEquals("Menggunakan baterai • 2,04 W", BatteryPowerPresenter.present(ChargingState.DISCHARGING, 2.04, "id").text)
        assertEquals("Mengisi daya • 8,31 W", BatteryPowerPresenter.present(ChargingState.CHARGING, 8.31, "id").text)
        assertFalse(BatteryPowerPresenter.present(ChargingState.UNKNOWN, null).isAvailable)
    }

    @Test
    fun `current and voltage source metadata remain available`() {
        val reading = BatteryPowerNormalizer.read(
            currentRaw = 1_000L,
            currentUnit = BatteryCurrentUnit.MILLIAMPERES,
            voltageRaw = 4_200_000L,
            voltageUnit = BatteryVoltageUnit.MICROVOLTS,
            chargingState = ChargingState.CHARGING,
            currentUnitSource = "vendor current milliamperes",
            voltageUnitSource = "vendor voltage microvolts",
        )
        assertNotNull(reading.currentUnitSource)
        assertNotNull(reading.voltageUnitSource)
        assertTrue(reading.powerSource!!.contains("vendor"))
    }
}
