package com.febricahyaa.fluxlab.integration

import org.junit.Assert.assertEquals
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
}
