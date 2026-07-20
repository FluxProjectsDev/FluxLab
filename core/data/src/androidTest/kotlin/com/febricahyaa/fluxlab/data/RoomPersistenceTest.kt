package com.febricahyaa.fluxlab.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.febricahyaa.fluxlab.model.BenchmarkEnvironment
import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.ComparisonRole
import com.febricahyaa.fluxlab.model.FluxInstallation
import com.febricahyaa.fluxlab.model.SessionStatus
import com.febricahyaa.fluxlab.model.Statistics
import com.febricahyaa.fluxlab.model.WorkloadKind
import com.febricahyaa.fluxlab.model.WorkloadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPersistenceTest {
    private lateinit var database: FluxLabDatabase
    private lateinit var repository: RoomBenchmarkSessionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FluxLabDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomBenchmarkSessionRepository(database.benchmarkDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun sessionWorkloadsRenameBaselineAndDeleteRoundTrip() = runBlocking {
        repository.save(session())
        assertEquals(1, repository.observeSessions().first().size)
        assertEquals(42.0, repository.get("session-1")!!.workloadResults.single().statistics.median, 0.0)

        repository.rename("session-1", "After update")
        repository.markBaseline("session-1")
        val updated = repository.get("session-1")!!
        assertEquals("After update", updated.label)
        assertEquals(ComparisonRole.BASELINE, updated.comparisonRole)

        repository.delete("session-1")
        assertNull(repository.get("session-1"))
    }

    private fun session(): BenchmarkSession = BenchmarkSession(
        id = "session-1",
        startedAtEpochMs = 1L,
        endedAtEpochMs = 2L,
        status = SessionStatus.COMPLETED,
        label = "Quick Test",
        environment = BenchmarkEnvironment(
            "0.1.0", 1, "Test", "Device", "fingerprint", "kernel",
            FluxInstallation(installed = true), true, "Available", false, 80,
            30.0, 31.0, 0, listOf(0.5), 60.0,
        ),
        workloadResults = listOf(
            WorkloadResult(
                WorkloadKind.CPU_INTEGER, 1, "ops/s", listOf(41.0, 42.0, 43.0),
                listOf(1L, 2L, 3L), Statistics(42.0, 41.0, 43.0, 1.0, 1.0 / 42.0), "126",
            ),
        ),
        warnings = listOf("test warning"),
        failureReason = null,
    )
}
