package com.febricahyaa.fluxlab

import android.app.Application
import android.app.LocaleManager
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.febricahyaa.fluxlab.benchmark.QuickTestEngine
import com.febricahyaa.fluxlab.benchmark.ReadinessContext
import com.febricahyaa.fluxlab.benchmark.ReadinessGuard
import com.febricahyaa.fluxlab.data.ComparisonEngine
import com.febricahyaa.fluxlab.model.BenchmarkEnvironment
import com.febricahyaa.fluxlab.model.BenchmarkPresetConfig
import com.febricahyaa.fluxlab.model.BenchmarkProgress
import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.DeviceTelemetrySnapshot
import com.febricahyaa.fluxlab.model.FluxInstallation
import com.febricahyaa.fluxlab.model.ReadinessResult
import com.febricahyaa.fluxlab.model.RootState
import com.febricahyaa.fluxlab.model.SessionComparison
import com.febricahyaa.fluxlab.model.SessionStatus
import com.febricahyaa.fluxlab.model.SnapshotFreshness
import com.febricahyaa.fluxlab.model.SynthesisReadResult
import com.febricahyaa.fluxlab.model.MonitoringState
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DashboardState(
    val root: RootState = RootState.Unknown,
    val flux: FluxInstallation = FluxInstallation(),
    val synthesis: SynthesisReadResult = SynthesisReadResult.Unavailable("Not read yet"),
    val telemetry: DeviceTelemetrySnapshot? = null,
    val frameTelemetry: FrameTelemetrySummary = FrameTelemetrySummary(),
    val cpuHistory: List<Pair<Long, Double?>> = emptyList(),
    val gpuHistory: List<Pair<Long, Double?>> = emptyList(),
    val memoryHistory: List<Pair<Long, Double?>> = emptyList(),
    val thermalHistory: List<Pair<Long, Double?>> = emptyList(),
    val batteryHistory: List<Pair<Long, Double?>> = emptyList(),
    val telemetryHistory: List<DeviceTelemetrySnapshot> = emptyList(),
    val monitoringState: MonitoringState = MonitoringState.INACTIVE,
    val errorCode: String? = null,
)

class AppViewModel(application: Application, private val container: AppContainer) : AndroidViewModel(application) {
    private val mutableDashboard = MutableStateFlow(DashboardState())
    val dashboard: StateFlow<DashboardState> = mutableDashboard.asStateFlow()
    val settings = container.settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val sessions = container.repository.observeSessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val engine = QuickTestEngine(
        repository = container.repository,
        storage = container.storageSuite,
        telemetryProvider = { mutableDashboard.value.telemetry },
        monitoring = { active -> MonitoringService.setRunning(getApplication(), active) },
    )
    val benchmarkProgress: StateFlow<BenchmarkProgress> = engine.progress
    private var benchmarkJob: Job? = null
    private val mutableComparison = MutableStateFlow<SessionComparison?>(null)
    val comparison: StateFlow<SessionComparison?> = mutableComparison.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collectLatest { current ->
                mutableDashboard.value = mutableDashboard.value.copy(monitoringState = MonitoringState.STARTING)
                container.telemetrySource.stream(current.samplingIntervalMs)
                    .catch { error ->
                        mutableDashboard.value = mutableDashboard.value.copy(
                            monitoringState = MonitoringState.TEMPORARILY_UNAVAILABLE,
                            errorCode = "telemetry:" + error.javaClass.simpleName,
                        )
                    }
                    .collect { snapshot ->
                        val timestamp = snapshot.elapsedRealtimeMs
                        val history = (mutableDashboard.value.cpuHistory +
                            (timestamp to snapshot.cpu.totalUsagePercent)).takeLast(120)
                        val gpuHistory = (mutableDashboard.value.gpuHistory +
                            (timestamp to snapshot.gpu.utilizationPercent)).takeLast(120)
                        val memoryPercent = snapshot.memory.usedKb?.toDouble()?.let { used ->
                            snapshot.memory.totalKb?.takeIf { it > 0L }?.let { total -> used / total * 100.0 }
                        }
                        val memoryHistory = (mutableDashboard.value.memoryHistory + (timestamp to memoryPercent)).takeLast(120)
                        val thermalHistory = (mutableDashboard.value.thermalHistory +
                            (timestamp to snapshot.thermal.hottestZone?.temperatureCelsius)).takeLast(120)
                        val batteryHistory = (mutableDashboard.value.batteryHistory +
                            (timestamp to snapshot.battery.levelPercent?.toDouble())).takeLast(120)
                        val telemetryHistory = (mutableDashboard.value.telemetryHistory + snapshot).takeLast(120)
                        val monitoringState = if (snapshot.cpu.totalUsagePercent == null) {
                            MonitoringState.COLLECTING_INITIAL_SAMPLES
                        } else {
                            MonitoringState.ACTIVE
                        }
                        mutableDashboard.value = mutableDashboard.value.copy(
                            telemetry = snapshot,
                            frameTelemetry = container.frameTelemetry.snapshot(),
                            cpuHistory = history,
                            gpuHistory = gpuHistory,
                            memoryHistory = memoryHistory,
                            thermalHistory = thermalHistory,
                            batteryHistory = batteryHistory,
                            telemetryHistory = telemetryHistory,
                            monitoringState = monitoringState,
                        )
                    }
            }
        }
        viewModelScope.launch {
            mutableDashboard.value = mutableDashboard.value.copy(root = RootState.Checking)
            val root = container.rootGateway.checkAvailability()
            mutableDashboard.value = mutableDashboard.value.copy(root = root)
            while (isActive) {
                refreshRuntime()
                delay(5_000)
            }
        }
    }

    fun readiness(): ReadinessResult {
        val synthesisFreshness = (dashboard.value.synthesis as? SynthesisReadResult.Success)?.snapshot?.freshness
            ?: SnapshotFreshness.UNAVAILABLE
        return ReadinessGuard.evaluate(
            ReadinessContext(
                telemetry = dashboard.value.telemetry,
                availableStorageBytes = getApplication<Application>().cacheDir.usableSpace,
                benchmarkRunning = engine.isRunning,
                monitoringConflict = false,
                fluxFreshness = synthesisFreshness,
                preset = settings.value.preset,
            ),
        )
    }

    fun startQuickTest() {
        if (benchmarkJob?.isActive == true || readiness() is ReadinessResult.Blocked) return
        benchmarkJob = viewModelScope.launch {
            val telemetry = dashboard.value.telemetry ?: return@launch
            val environment = BenchmarkEnvironment(
                appVersion = appVersion(),
                benchmarkSchemaVersion = 1,
                deviceManufacturer = telemetry.system.manufacturer,
                deviceModel = telemetry.system.model,
                androidFingerprint = telemetry.system.buildFingerprint,
                kernelVersion = telemetry.system.kernelVersion,
                flux = dashboard.value.flux,
                synthesisAvailable = dashboard.value.synthesis is SynthesisReadResult.Success,
                rootState = rootName(dashboard.value.root),
                charging = telemetry.battery.charging,
                batteryLevel = telemetry.battery.levelPercent,
                initialBatteryTemperatureC = telemetry.battery.temperatureCelsius,
                peakBatteryTemperatureC = telemetry.battery.temperatureCelsius,
                androidThermalStatus = telemetry.thermal.androidStatus,
                thermalHeadroomSamples = listOfNotNull(telemetry.thermal.headroom),
                refreshRateHz = telemetry.system.refreshRateHz,
                presetConfiguration = BenchmarkPresetConfig.forPreset(settings.value.preset),
                visualMode = settings.value.visualMode,
            )
            engine.run(environment, settings.value.includeStorage)
        }
    }

    fun setLiveMonitoring(enabled: Boolean) {
        mutableDashboard.value = mutableDashboard.value.copy(monitoringState = if (enabled) MonitoringState.STARTING else MonitoringState.PAUSED)
    }

    fun cancelQuickTest() {
        engine.cancel()
        benchmarkJob?.cancel()
    }

    fun selectReportSession(id: String?) = viewModelScope.launch { container.settingsStore.setSelectedReportSessionId(id) }
    fun acceptStorageNotice() = viewModelScope.launch { container.settingsStore.acceptStorageNotice() }
    fun renameSession(id: String, label: String) = viewModelScope.launch { container.repository.rename(id, label) }
    fun markBaseline(id: String) = viewModelScope.launch { container.repository.markBaseline(id) }
    fun deleteSession(id: String) = viewModelScope.launch { container.repository.delete(id) }
    fun resetHistory() = viewModelScope.launch { container.repository.deleteAll() }

    fun compare(candidateId: String) {
        viewModelScope.launch {
            val baseline = sessions.value.firstOrNull { it.comparisonRole.name == "BASELINE" }
            val candidate = container.repository.get(candidateId)
            mutableComparison.value = if (baseline != null && candidate != null && baseline.id != candidate.id) {
                runCatching { ComparisonEngine.compare(baseline, candidate) }.getOrNull()
            } else null
        }
    }

    fun exportJson(sessionId: String, uri: Uri) = viewModelScope.launch {
        export(sessionId, uri, json = true)
    }

    fun exportCsv(sessionId: String, uri: Uri) = viewModelScope.launch {
        export(sessionId, uri, json = false)
    }

    fun exportComparisonJson(uri: Uri) = viewModelScope.launch {
        val value = comparison.value ?: return@launch
        getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { output ->
            container.reportExporter.exportComparisonJson(value, output)
        }
    }

    fun exportComparisonCsv(uri: Uri) = viewModelScope.launch {
        val value = comparison.value ?: return@launch
        getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { output ->
            container.reportExporter.exportComparisonCsv(value, output)
        }
    }

    fun setTheme(value: ThemeSetting) = viewModelScope.launch { container.settingsStore.setTheme(value) }
    fun setSamplingInterval(value: Long) = viewModelScope.launch { container.settingsStore.setInterval(value) }
    fun setPreset(value: com.febricahyaa.fluxlab.model.BenchmarkPreset) = viewModelScope.launch { container.settingsStore.setPreset(value) }
    fun setVisualMode(value: com.febricahyaa.fluxlab.model.BenchmarkVisualMode) = viewModelScope.launch { container.settingsStore.setVisualMode(value) }
    fun setIncludeStorage(value: Boolean) = viewModelScope.launch { container.settingsStore.setStorage(value) }
    fun setAdvanced(value: Boolean) = viewModelScope.launch { container.settingsStore.setAdvanced(value) }
    fun setUnits(value: UnitSetting) = viewModelScope.launch { container.settingsStore.setUnits(value) }
    fun setLanguage(value: LanguageSetting) = viewModelScope.launch {
        container.settingsStore.setLanguage(value)
        applyLanguage(value)
    }

    private suspend fun refreshRuntime() {
        val flux = runCatching { container.fluxReader.readInstallation() }.getOrElse {
            FluxInstallation(warnings = listOf("Flux read failed: ${it.javaClass.simpleName}"))
        }
        val synthesis = runCatching { container.synthesisReader.read() }.getOrElse {
            SynthesisReadResult.Unavailable("SynthesisCore read failed: ${it.javaClass.simpleName}")
        }
        mutableDashboard.value = mutableDashboard.value.copy(
            root = container.rootGateway.state.value,
            flux = flux,
            synthesis = synthesis,
        )
    }

    private suspend fun export(sessionId: String, uri: Uri, json: Boolean) {
        val session = container.repository.get(sessionId) ?: return
        getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { output ->
            if (json) container.reportExporter.exportSessionJson(session, output)
            else container.reportExporter.exportSessionCsv(session, output)
        }
    }

    private fun appVersion(): String = runCatching {
        val info = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
        info.versionName.orEmpty()
    }.getOrDefault("unknown")

    private fun rootName(state: RootState): String = when (state) {
        RootState.Unknown -> "unknown"
        RootState.Checking -> "checking"
        RootState.Available -> "available"
        RootState.Denied -> "denied"
        RootState.Unavailable -> "unavailable"
        is RootState.Error -> "error"
    }

    @Suppress("DEPRECATION")
    private fun applyLanguage(value: LanguageSetting) {
        val tags = when (value) {
            LanguageSetting.SYSTEM -> ""
            LanguageSetting.ENGLISH -> "en"
            LanguageSetting.INDONESIAN -> "id"
        }
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
        } else {
            val locale = if (tags.isBlank()) Locale.getDefault() else Locale.forLanguageTag(tags)
            val configuration = app.resources.configuration
            configuration.setLocale(locale)
            app.resources.updateConfiguration(configuration, app.resources.displayMetrics)
        }
    }

    class Factory(private val application: FluxLabApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(application, application.container) as T
    }
}
