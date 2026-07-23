package com.febricahyaa.fluxlab

import android.app.Application
import com.febricahyaa.fluxlab.benchmark.StorageBenchmarkSuite
import com.febricahyaa.fluxlab.data.FluxLabDatabase
import com.febricahyaa.fluxlab.data.RoomBenchmarkSessionRepository
import com.febricahyaa.fluxlab.data.VersionedReportExporter
import com.febricahyaa.fluxlab.integration.AndroidDeviceTelemetrySource
import com.febricahyaa.fluxlab.integration.AndroidTelemetryRepository
import com.febricahyaa.fluxlab.integration.FluxRuntimeAdapter
import com.febricahyaa.fluxlab.integration.SuRootGateway
import com.febricahyaa.fluxlab.integration.SynthesisCoreAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FluxLabApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = FluxLabDatabase.create(this)
        val root = SuRootGateway()
        val telemetrySource = AndroidDeviceTelemetrySource(this, root)
        val telemetryRepository = AndroidTelemetryRepository(
            source = telemetrySource,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        container = AppContainer(
            repository = RoomBenchmarkSessionRepository(database.benchmarkDao()),
            rootGateway = root,
            fluxReader = FluxRuntimeAdapter(root),
            synthesisReader = SynthesisCoreAdapter(root),
            telemetrySource = telemetrySource,
            telemetryRepository = telemetryRepository,
            frameTelemetry = FrameTelemetry(),
            reportExporter = VersionedReportExporter(),
            settingsStore = SettingsStore(this),
            storageSuite = StorageBenchmarkSuite(cacheDir),
        )
        container.storageSuite.cleanupInterruptedFiles()
    }
}

data class AppContainer(
    val repository: com.febricahyaa.fluxlab.model.BenchmarkSessionRepository,
    val rootGateway: com.febricahyaa.fluxlab.model.RootGateway,
    val fluxReader: com.febricahyaa.fluxlab.model.FluxRuntimeReader,
    val synthesisReader: com.febricahyaa.fluxlab.model.SynthesisCoreReader,
    val telemetrySource: com.febricahyaa.fluxlab.model.DeviceTelemetrySource,
    val telemetryRepository: com.febricahyaa.fluxlab.model.DeviceTelemetryRepository,
    val frameTelemetry: FrameTelemetry,
    val reportExporter: com.febricahyaa.fluxlab.model.ReportExporter,
    val settingsStore: SettingsStore,
    val storageSuite: StorageBenchmarkSuite,
)
