package com.febricahyaa.fluxlab

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.febricahyaa.fluxlab.benchmark.StorageBenchmarkSuite
import java.io.File
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageCancellationInstrumentedTest {
    @Test
    fun cancellationRemovesTemporaryBenchmarkFiles() = runBlocking {
        val cache = ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir
        val suite = StorageBenchmarkSuite(cache)
        suite.cleanupInterruptedFiles()
        val job = launch { suite.run() }
        delay(20)
        job.cancelAndJoin()
        suite.cleanupInterruptedFiles()

        assertFalse(cache.listFiles().orEmpty().any { it.isFluxLabBenchmarkFile() })
    }

    private fun File.isFluxLabBenchmarkFile(): Boolean = name.startsWith("quick-test-") && name.endsWith(".bin")
}
