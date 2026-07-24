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
import com.febricahyaa.fluxlab.model.TelemetrySourceStatus
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val monitoringStatus: TelemetrySourceStatus = TelemetrySourceStatus(),
    val errorCode: String? = null,
)

class AppViewModel(application: Application, private val container: AppContainer) : AndroidViewModel(application) {
    private val mutableDashboard = MutableStateFlow(DashboardState())
    val dashboard: StateFlow<DashboardState> = mutableDashboard.asStateFlow()
    val settings = container.settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val sessions = container.repository.observeSessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val aboutLegalRepository = AboutLegalRepository(application.resources, UnconfiguredUpdateProvider())
    private val mutableAboutLegal = MutableStateFlow(AboutLegalState())
    val aboutLegal: StateFlow<AboutLegalState> = mutableAboutLegal.asStateFlow()

    private val engine = QuickTestEngine(
        repository = container.repository,
        storage = container.storageSuite,
        telemetryProvider = { container.telemetryRepository.state.value.latest },
        monitoring = ::setBenchmarkMonitoring,
    )
    val benchmarkProgress: StateFlow<BenchmarkProgress> = engine.progress
    private var benchmarkJob: Job? = null
    private var updateCheckJob: Job? = null
    private val mutableComparison = MutableStateFlow<SessionComparison?>(null)
    val comparison: StateFlow<SessionComparison?> = mutableComparison.asStateFlow()
    private var monitoringEnabled = true

    init {
        mutableAboutLegal.value = mutableAboutLegal.value.copy(version = AppVersionInfoReader.read(application))
        viewModelScope.launch {
            settings.map { it.language }.distinctUntilChanged().collect { language ->
                loadAboutLegalContent(language)
            }
        }
        viewModelScope.launch {
            settings.map { it.samplingIntervalMs }.distinctUntilChanged().collect { interval ->
                if (monitoringEnabled) container.telemetryRepository.start(interval)
            }
        }
        viewModelScope.launch {
            settings.map { it.autoStartMonitoring }.distinctUntilChanged().collect { enabled ->
                monitoringEnabled = enabled
                if (enabled) container.telemetryRepository.start(settings.value.samplingIntervalMs)
                else if (settings.value.preserveLastSample) container.telemetryRepository.stop()
                else container.telemetryRepository.reset()
            }
        }
        viewModelScope.launch {
            container.telemetryRepository.state.collect { repositoryState ->
                val history = repositoryState.history
                val latest = repositoryState.latest
                if (latest == null && repositoryState.status.state == MonitoringState.INACTIVE) return@collect
                val cpuHistory = history.map { it.elapsedRealtimeMs to it.cpu.totalUsagePercent }
                val gpuHistory = history.map { it.elapsedRealtimeMs to it.gpu.utilizationPercent }
                val memoryHistory = history.map { snapshot ->
                    val memory = snapshot.memory
                    val used = memory.usedKb?.toDouble()
                    val total = memory.totalKb?.takeIf { it > 0L }
                    snapshot.elapsedRealtimeMs to used?.let { value -> total?.let { value / it * 100.0 } }
                }
                val thermalHistory = history.map { it.elapsedRealtimeMs to it.thermal.hottestZone?.temperatureCelsius }
                val batteryHistory = history.map { it.elapsedRealtimeMs to it.battery.levelPercent?.toDouble() }
                mutableDashboard.value = mutableDashboard.value.copy(
                    telemetry = latest,
                    frameTelemetry = container.frameTelemetry.snapshot(),
                    cpuHistory = cpuHistory.takeLast(120),
                    gpuHistory = gpuHistory.takeLast(120),
                    memoryHistory = memoryHistory.takeLast(120),
                    thermalHistory = thermalHistory.takeLast(120),
                    batteryHistory = batteryHistory.takeLast(120),
                    telemetryHistory = history,
                    monitoringState = repositoryState.status.state,
                    monitoringStatus = repositoryState.status,
                    errorCode = repositoryState.status.reason,
                )
            }
        }
        if (settings.value.autoStartMonitoring) container.telemetryRepository.start(settings.value.samplingIntervalMs)
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

    override fun onCleared() {
        container.telemetryRepository.stop()
        super.onCleared()
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
        // Capture all run-affecting settings before launching the coroutine. A later
        // chip selection must not mutate the configuration already shown on the run screen.
        val selectedPreset = settings.value.preset
        val selectedVisualMode = settings.value.visualMode
        val selectedIncludeStorage = settings.value.includeStorage
        val selectedConfiguration = BenchmarkPresetConfig.forPreset(selectedPreset)
        val readinessWarnings = (readiness() as? ReadinessResult.ReadyWithWarnings)?.reasons.orEmpty()
        val methodologyWarnings = buildList {
            addAll(readinessWarnings)
            if (settings.value.screenRecordingDeclared) add("Screen recording declared by operator")
        }.distinct()
        benchmarkJob = viewModelScope.launch {
            val telemetry = dashboard.value.telemetry ?: return@launch
            val environment = BenchmarkEnvironment(
                appVersion = appVersion(),
                benchmarkSchemaVersion = 1,
                deviceManufacturer = telemetry.system.manufacturer,
                deviceModel = telemetry.system.model,
                androidFingerprint = telemetry.system.buildFingerprint,
                kernelVersion = telemetry.system.kernelVersion,
                flux = dashboard.value.flux.copy(warnings = (dashboard.value.flux.warnings + methodologyWarnings).distinct()),
                synthesisAvailable = dashboard.value.synthesis is SynthesisReadResult.Success,
                rootState = rootName(dashboard.value.root),
                charging = telemetry.battery.charging,
                batteryLevel = telemetry.battery.levelPercent,
                initialBatteryTemperatureC = telemetry.battery.temperatureCelsius,
                peakBatteryTemperatureC = telemetry.battery.temperatureCelsius,
                androidThermalStatus = telemetry.thermal.androidStatus,
                thermalHeadroomSamples = listOfNotNull(telemetry.thermal.headroom),
                refreshRateHz = telemetry.system.refreshRateHz,
                presetConfiguration = selectedConfiguration,
                visualMode = selectedVisualMode,
            )
            engine.run(environment, selectedIncludeStorage)
        }
    }

    fun setLiveMonitoring(enabled: Boolean) {
        if (!enabled && engine.isRunning) return
        monitoringEnabled = enabled
        if (enabled) {
            container.telemetryRepository.start(settings.value.samplingIntervalMs)
        } else if (settings.value.preserveLastSample) {
            container.telemetryRepository.stop()
        } else {
            container.telemetryRepository.reset()
        }
    }

    fun cancelQuickTest() {
        engine.cancel()
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
    fun setColorStyle(value: ColorStyle) = viewModelScope.launch { container.settingsStore.setColorStyle(value) }
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
    fun setAutoStartMonitoring(value: Boolean) = viewModelScope.launch { container.settingsStore.setAutoStartMonitoring(value) }
    fun setPreserveLastSample(value: Boolean) = viewModelScope.launch { container.settingsStore.setPreserveLastSample(value) }
    fun setStopMonitoringWhenIdle(value: Boolean) = viewModelScope.launch { container.settingsStore.setStopMonitoringWhenIdle(value) }
    fun setScreenRecordingDeclared(value: Boolean) = viewModelScope.launch { container.settingsStore.setScreenRecordingDeclared(value) }
    fun setConfirmReadinessWarnings(value: Boolean) = viewModelScope.launch { container.settingsStore.setConfirmReadinessWarnings(value) }

    fun reloadAboutLegal() {
        viewModelScope.launch { loadAboutLegalContent(settings.value.language) }
    }

    fun checkForUpdates() {
        if (updateCheckJob?.isActive == true) return
        val current = aboutLegal.value.version ?: AppVersionInfoReader.read(getApplication<Application>())
        mutableAboutLegal.value = mutableAboutLegal.value.copy(update = UpdateCheckState.Checking)
        updateCheckJob = viewModelScope.launch {
            val result = runCatching { aboutLegalRepository.checkForUpdates(current) }
                .getOrElse { UpdateCheckState.Failed(it.message ?: it.javaClass.simpleName) }
            mutableAboutLegal.value = mutableAboutLegal.value.copy(update = result)
        }
    }

    private fun setBenchmarkMonitoring(active: Boolean) {
        MonitoringService.setRunning(getApplication(), active)
        if (active) {
            monitoringEnabled = true
            container.telemetryRepository.start(settings.value.samplingIntervalMs)
        } else if (!settings.value.autoStartMonitoring || settings.value.stopMonitoringWhenIdle) {
            monitoringEnabled = false
            if (settings.value.preserveLastSample) container.telemetryRepository.stop()
            else container.telemetryRepository.reset()
        } else {
            monitoringEnabled = true
            container.telemetryRepository.start(settings.value.samplingIntervalMs)
        }
    }

    private suspend fun loadAboutLegalContent(language: LanguageSetting) {
        mutableAboutLegal.value = mutableAboutLegal.value.copy(contentStatus = ContentLoadStatus.LOADING, contentError = null)
        val result = aboutLegalRepository.load(language)
        mutableAboutLegal.value = result.fold(
            onSuccess = { documents ->
                mutableAboutLegal.value.copy(
                    contentStatus = ContentLoadStatus.READY,
                    documents = documents,
                    contentError = null,
                )
            },
            onFailure = { error ->
                mutableAboutLegal.value.copy(
                    contentStatus = ContentLoadStatus.ERROR,
                    contentError = error.message ?: error.javaClass.simpleName,
                )
            },
        )
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
