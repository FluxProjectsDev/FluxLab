package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.WorkloadKind
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageMethodologyTest {
    @Test
    fun `allocation preserves safety margin`() {
        val safe = StorageSafety.evaluate(96L * 1_048_576, 64L * 1_048_576, 32L * 1_048_576)
        val unsafe = StorageSafety.evaluate(95L * 1_048_576, 64L * 1_048_576, 32L * 1_048_576)
        assertTrue(safe.allowed)
        assertFalse(unsafe.allowed)
        assertTrue(unsafe.reason!!.contains("safety margin"))
    }

    @Test
    fun `invalid allocation is rejected`() {
        assertFalse(StorageSafety.evaluate(-1, 1).allowed)
        assertFalse(StorageSafety.evaluate(10, 0).allowed)
    }

    @Test
    fun `storage labels are honest and localized`() {
        assertEquals("Buffered app-file read", StorageMetricLabels.english(WorkloadKind.STORAGE_READ))
        assertEquals("Pembacaan file aplikasi berbasis buffer", StorageMetricLabels.indonesian(WorkloadKind.STORAGE_READ))
        assertTrue(StorageMetricLabels.methodologyNote(false).contains("not guaranteed physical UFS"))
    }

    @Test
    fun `checksum detects corruption`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val checksum = StorageIntegrity.checksum(bytes)
        assertTrue(StorageIntegrity.isValid(bytes, checksum))
        bytes[0] = 9
        assertFalse(StorageIntegrity.isValid(bytes, checksum))
    }

    @Test
    fun `cleanup removes only recognized benchmark files`() {
        val directory = Files.createTempDirectory("fluxlab-storage").toFile()
        try {
            directory.resolve("quick-test-old.bin").writeBytes(byteArrayOf(1))
            directory.resolve("fluxlab-benchmark-stale.bin").writeBytes(byteArrayOf(1))
            directory.resolve("user-file.bin").writeBytes(byteArrayOf(1))
            StorageBenchmarkSuite(directory).cleanupInterruptedFiles()
            assertFalse(directory.resolve("quick-test-old.bin").exists())
            assertFalse(directory.resolve("fluxlab-benchmark-stale.bin").exists())
            assertTrue(directory.resolve("user-file.bin").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
