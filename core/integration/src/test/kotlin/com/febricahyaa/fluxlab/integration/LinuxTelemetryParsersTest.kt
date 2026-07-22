package com.febricahyaa.fluxlab.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class LinuxTelemetryParsersTest {
    @Test
    fun `proc stat usage is calculated from two cumulative samples`() {
        val previous = ProcStatParser.parse("cpu 100 0 100 700 0 0 0 0\n")["cpu"]
        val current = ProcStatParser.parse("cpu 150 0 150 800 0 0 0 0\n")["cpu"]

        assertEquals(50.0, ProcStatParser.usage(previous, current)!!, 0.0001)
        assertNull(ProcStatParser.usage(current, previous))
    }

    @Test
    fun `meminfo parser keeps kernel values in kibibytes`() {
        val values = MemInfoParser.parse(
            "MemTotal:       8000000 kB\nMemAvailable:   2500000 kB\nCached:          500000 kB\nSwapTotal:      1000000 kB\n",
        )

        assertEquals(8_000_000L, values["MemTotal"])
        assertEquals(2_500_000L, values["MemAvailable"])
        assertEquals(500_000L, values["Cached"])
    }

    @Test
    fun `PSI parser reads some and full avg10 values independently`() {
        val values = PsiParser.parse(
            "some avg10=1.25 avg60=0.50 avg300=0.10 total=123\nfull avg10=0.07 avg60=0.01 avg300=0.00 total=4\n",
        )

        assertEquals(1.25, values.someAvg10!!, 0.0001)
        assertEquals(0.07, values.fullAvg10!!, 0.0001)
    }

    @Test
    fun `battery power estimate handles OEM current sign and rejects invalid input`() {
        assertEquals(8.0, BatteryPowerEstimator.watts(-2_000_000L, 4_000L)!!, 0.0001)
        assertEquals(8.0, BatteryPowerEstimator.watts(2_000_000L, 4_000L)!!, 0.0001)
        assertNull(BatteryPowerEstimator.watts(0L, 4_000L))
        assertNull(BatteryPowerEstimator.watts(1_000L, null))
    }
    @Test
    fun firstCpuSampleReportsCollectingInitialSamples() {
        val current = ProcStatParser.parse("cpu 10 0 5 20\ncpu0 5 0 2 10\n")
        assertEquals(CpuSampleState.COLLECTING_INITIAL_SAMPLES, ProcStatParser.sampleState(emptyMap(), current))
        assertNull(ProcStatParser.usage(null, current["cpu"]))
    }

    @Test
    fun aggregateAndPerCoreUtilizationUseIndependentDeltas() {
        val previous = ProcStatParser.parse("cpu 100 0 100 700\ncpu0 50 0 50 300\ncpu1 50 0 50 400\n")
        val current = ProcStatParser.parse("cpu 150 0 150 800\ncpu0 70 0 70 360\ncpu1 80 0 80 440\n")
        assertEquals(50.0, ProcStatParser.usage(previous["cpu"], current["cpu"])!!, 0.0001)
        assertEquals(40.0, ProcStatParser.usage(previous["cpu0"], current["cpu0"])!!, 0.0001)
        assertEquals(60.0, ProcStatParser.snapshotUsage(previous, current)["cpu1"]!!, 0.0001)
    }

    @Test
    fun invalidDeltasAndMalformedCountersAreRejected() {
        val previous = ProcStatParser.parse("cpu 10 0 5 20\n")
        val same = ProcStatParser.parse("cpu 10 0 5 20\n")
        val reset = ProcStatParser.parse("cpu 9 0 5 20\n")
        assertNull(ProcStatParser.usage(previous["cpu"], same["cpu"]))
        assertNull(ProcStatParser.usage(previous["cpu"], reset["cpu"]))
        assertTrue(ProcStatParser.parse("cpu 10 nope 5 20").isEmpty())
    }

    @Test
    fun missingOptionalCpuFieldsRemainValid() {
        val previous = ProcStatParser.parse("cpu 100 0 100 700 0 0 0 0 0 0\n")
        val current = ProcStatParser.parse("cpu 150 0 150 800\n")
        assertEquals(50.0, ProcStatParser.usage(previous["cpu"], current["cpu"])!!, 0.0001)
    }

    @Test
    fun disappearingCoreIsReportedAsUnavailable() {
        val previous = ProcStatParser.parse("cpu 100 0 100 700\ncpu0 50 0 50 300\ncpu1 50 0 50 400\n")
        val current = ProcStatParser.parse("cpu 150 0 150 800\ncpu0 70 0 70 360\n")
        assertEquals(CpuSampleState.TEMPORARILY_UNAVAILABLE, ProcStatParser.sampleState(previous, current))
        assertNull(ProcStatParser.snapshotUsage(previous, current)["cpu1"])
    }

    @Test
    fun memoryBreakdownAndZramParsingAreBoundedAndConsistent() {
        val values = MemInfoParser.parse("MemTotal: 8000000 kB\nMemFree: 1000000 kB\nBuffers: 500000 kB\nCached: 1000000 kB\nSReclaimable: 200000 kB\nShmem: 100000 kB\n")
        assertEquals(1_100_000L, MemInfoParser.cachedKb(values))
        assertEquals(5_400_000L, MemInfoParser.usedKb(values))
        val zram = ZramParser.parse(mapOf("disksize" to 2_000L, "orig_data_size" to 1_000L, "compr_data_size" to 500L))
        assertEquals(2.0, zram.compressionRatio!!, 0.0001)
    }

    @Test
    fun psiParserSupportsMissingFullAndMalformedValues() {
        val someOnly = PsiParser.parse("some avg10=1.0 avg60=0.5 avg300=0.2 total=10\n")
        assertEquals(1.0, someOnly.someAvg10!!, 0.0001)
        assertNull(someOnly.fullAvg10)
        assertNull(PsiParser.parse("full avg10=nope").fullAvg10)
    }

}
