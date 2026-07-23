package com.febricahyaa.fluxlab.integration

import com.febricahyaa.fluxlab.model.BatteryCapacityNormalizer
import com.febricahyaa.fluxlab.model.BatteryCapacityUnit
import com.febricahyaa.fluxlab.model.BatteryHealthParser
import com.febricahyaa.fluxlab.model.BatteryHealthState
import com.febricahyaa.fluxlab.model.BatteryHealthEstimator
import com.febricahyaa.fluxlab.model.StorageHealthState
import com.febricahyaa.fluxlab.model.StorageLifetimeParser
import com.febricahyaa.fluxlab.model.StorageSafety
import com.febricahyaa.fluxlab.model.StorageMetricLabels
import com.febricahyaa.fluxlab.model.StorageType
import com.febricahyaa.fluxlab.model.ThermalSensorClassifier
import com.febricahyaa.fluxlab.model.ThermalSensorGroup
import com.febricahyaa.fluxlab.model.ThermalTemperatureNormalizer
import com.febricahyaa.fluxlab.model.TemperatureUnitSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticsProvidersTest {
    @Test fun chargeCounterNormalizesMicroAmpHoursToMilliAmpHours() {
        assertEquals(3140.0, BatteryCapacityNormalizer.toMilliAmpHours(3_140_000, BatteryCapacityUnit.MICROAMP_HOURS)!!, 0.001)
    }

    @Test fun energyCapacityIsNotConvertedWithoutVoltageBasis() {
        assertNull(BatteryCapacityNormalizer.toMilliAmpHours(4_000_000, BatteryCapacityUnit.MICRO_WATT_HOURS))
    }

    @Test fun estimatedHealthRequiresValidatedDesignAndFullCapacity() {
        assertEquals(93.333, BatteryHealthEstimator.estimate(4_760.0, 5_100.0)!!, 0.01)
        assertNull(BatteryHealthEstimator.estimate(4_760.0, 0.0))
        assertNull(BatteryHealthEstimator.estimate(5_200.0, 5_000.0))
    }

    @Test fun androidHealthValuesRemainSeparateFromNumericalHealth() {
        assertEquals(BatteryHealthState.GOOD, BatteryHealthParser.parse(2))
        assertEquals(BatteryHealthState.UNKNOWN, BatteryHealthParser.parse(99))
    }

    @Test fun lifetimeDescriptorsRemainCoarseRanges() {
        val estimate = StorageLifetimeParser.parseBucket("0x02", "/sys/mock")
        assertEquals(10, estimate.rangeStartPercent)
        assertEquals(20, estimate.rangeEndPercent)
        assertEquals(StorageHealthState.HEALTHY, estimate.normalizedState)
    }

    @Test fun reservedLifetimeDescriptorsAreNotReportedHealthy() {
        val estimate = StorageLifetimeParser.parseBucket("0xFF", "/sys/mock")
        assertEquals(StorageHealthState.DESCRIPTOR_MALFORMED, estimate.normalizedState)
    }

    @Test fun extCsdParserReadsEmmcLifetimeAndPreEol() {
        val health = StorageLifetimeParser.parseEmmcExtCsd(
            "DEVICE_LIFE_TIME_EST_TYP_A=0x03\nDEVICE_LIFE_TIME_EST_TYP_B=0x04\nPRE_EOL_INFO=0x01",
            "/sys/mock/ext_csd",
        )
        assertEquals(30, health.lifetimeA.rangeEndPercent)
        assertEquals("1", health.preEndOfLife)
    }

    @Test fun storageAllocationKeepsAConfiguredSafetyMargin() {
        assertTrue(StorageSafety.canAllocate(256L * 1024L * 1024L, 128L * 1024L * 1024L))
        assertTrue(!StorageSafety.canAllocate(128L * 1024L * 1024L, 128L * 1024L * 1024L))
    }

    @Test fun storageMetricNamesDoNotClaimPhysicalUfsSpeed() {
        assertEquals("Buffered storage read", StorageMetricLabels.BUFFERED_READ)
    }

    @Test fun storageDetectionDoesNotUseMarketingNames() {
        assertEquals(StorageType.EMMC, StorageParsers.detectType("mmcblk0", "vendor model", "/sys/block/mmcblk0/device"))
        assertEquals(StorageType.UFS, StorageParsers.detectType("sda", "UFS device", "/sys/block/sda/device"))
        assertEquals(StorageType.VIRTUAL, StorageParsers.detectType("loop0", null, "/sys/devices/virtual/block/loop0"))
    }
    @Test fun cpuFrequencyUnitsAreNormalizedConservatively() {
        assertEquals(1_840_000_000L, CpuFrequencyParser.normalize("1840000", "scaling_cur_freq")!!.hz)
        assertEquals(1_840_000_000L, CpuFrequencyParser.normalize("1840", "vendor_mhz")!!.hz)
        assertNull(CpuFrequencyParser.normalize("0", "scaling_cur_freq"))
    }

    @Test fun thermalClassifierAndPlaceholderValidationAreDeterministic() {
        assertEquals(ThermalSensorGroup.CPU, ThermalSensorClassifier.classify("cpu-cluster0"))
        assertEquals(ThermalSensorGroup.SKIN, ThermalSensorClassifier.classify("skin_temp"))
        assertEquals(ThermalSensorGroup.GPU, ThermalSensorClassifier.classify("gpu-therm"))
        assertEquals(TemperatureUnitSource.MILLI_CELSIUS, ThermalTemperatureNormalizer.normalize(44_800)?.unitSource)
        assertNull(ThermalTemperatureNormalizer.normalize(0))
    }

}
