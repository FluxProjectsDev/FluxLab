package com.febricahyaa.fluxlab.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareCapabilityProvidersTest {
    @Test
    fun `cpu ranges count unique cores and reject malformed ranges`() {
        assertEquals(8, AndroidCpuIdentityProvider.parseCpuRange("0-3,4-7"))
        assertEquals(3, AndroidCpuIdentityProvider.parseCpuRange("0,1,1,2"))
        assertNull(AndroidCpuIdentityProvider.parseCpuRange("0-x"))
    }

    @Test
    fun `frequency normalization validates units by magnitude`() {
        assertEquals(600_000_000L, GpuParsers.normalizeFrequency("600000")!!.hz)
        assertEquals(800_000_000L, GpuParsers.normalizeFrequency("800000000")!!.hz)
        assertNull(GpuParsers.normalizeFrequency("42"))
    }

    @Test
    fun `kgsl busy requires exactly valid busy and total counters`() {
        assertEquals(50.0, GpuParsers.parseKgslBusy("50 100")!!, 0.001)
        assertNull(GpuParsers.parseKgslBusy("50"))
        assertNull(GpuParsers.parseKgslBusy("101 100"))
        assertTrue(GpuParsers.parseKgslBusy("0 1") == 0.0)
    }
    @Test
    fun kgslBusyDeltaRejectsResetAndZeroTotal() {
        val first = GpuParsers.parseKgslBusyCounters("50 100")
        val second = GpuParsers.parseKgslBusyCounters("80 160")
        assertEquals(50.0, GpuParsers.deltaUtilization(first, second)!!, 0.001)
        assertNull(GpuParsers.deltaUtilization(second, first))
        assertNull(GpuParsers.parseKgslBusyCounters("0 0"))
    }

}
