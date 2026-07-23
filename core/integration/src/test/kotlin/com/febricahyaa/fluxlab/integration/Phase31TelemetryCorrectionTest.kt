package com.febricahyaa.fluxlab.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase31TelemetryCorrectionTest {
    @Test
    fun guestCountersAreNotAddedToProcStatTotal() {
        val previous = ProcStatParser.parse("cpu 100 0 100 700 0 0 0 0 100 50")["cpu"]
        val current = ProcStatParser.parse("cpu 150 0 150 800 0 0 0 0 120 60")["cpu"]

        assertEquals(50.0, ProcStatParser.usage(previous, current)!!, 0.0001)
    }

    @Test
    fun psiRejectsNegativeAndOutOfRangeAverages() {
        val values = PsiParser.parse(
            listOf(
                "some avg10=-1 avg60=0.5 avg300=0.1 total=1",
                "full avg10=101 avg60=0.2 avg300=0.1 total=1",
            ).joinToString(System.lineSeparator()),
        )

        assertNull(values.someAvg10)
        assertNull(values.fullAvg10)
        assertEquals(0.5, values.someAvg60!!, 0.0001)
    }

    @Test
    fun topologyDiagnosticsRetainHolderEvidence() {
        val result = StorageTopologyResolver.resolve(
            "dm-0",
            mapOf("dm-0" to BlockTopologyNode("dm-0", holders = listOf("crypt-userdata"))),
        )

        assertTrue(result.diagnostics.any { it.contains("holders=crypt-userdata") })
    }
}
