package com.febricahyaa.fluxlab.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTopologyResolverTest {
    @Test
    fun resolvesDeviceMapperToPhysicalSlave() {
        val result = StorageTopologyResolver.resolve(
            "dm-0",
            mapOf(
                "dm-0" to BlockTopologyNode("dm-0", slaves = listOf("sda")),
                "sda" to BlockTopologyNode("sda", subsystem = "ufs", devicePath = "/sys/devices/platform/ufshcd0"),
            ),
        )
        assertEquals("sda", result.physicalDevice)
        assertEquals(listOf("dm-0", "sda"), result.chain)
    }

    @Test
    fun preventsCyclesAndKeepsDiagnostics() {
        val result = StorageTopologyResolver.resolve(
            "dm-0",
            mapOf(
                "dm-0" to BlockTopologyNode("dm-0", slaves = listOf("dm-1")),
                "dm-1" to BlockTopologyNode("dm-1", slaves = listOf("dm-0")),
            ),
        )
        assertEquals(null, result.physicalDevice)
        assertTrue(result.diagnostics.any { it.contains("Cycle prevented") })
    }

    @Test
    fun resolvesPartitionParentAlongsideDeviceMapperSlave() {
        val result = StorageTopologyResolver.resolve(
            "dm-0",
            mapOf(
                "dm-0" to BlockTopologyNode("dm-0", slaves = listOf("sda1")),
                "sda1" to BlockTopologyNode("sda1", parents = listOf("sda")),
                "sda" to BlockTopologyNode("sda"),
            ),
        )
        assertEquals("sda", result.physicalDevice)
        assertTrue(result.diagnostics.any { it.contains("parents=sda") })
    }
}
