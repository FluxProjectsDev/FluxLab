package com.febricahyaa.fluxlab

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.GpuCapabilityState
import com.febricahyaa.fluxlab.model.localizedStatusKey
import com.febricahyaa.fluxlab.model.LocalizedStatusKey
import com.febricahyaa.fluxlab.model.ReadinessResult
import com.febricahyaa.fluxlab.model.RootState
import com.febricahyaa.fluxlab.model.SessionStatus
import com.febricahyaa.fluxlab.model.SynthesisReadResult
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Page(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item { content() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun OverviewScreen(model: AppViewModel) {
    val state by model.dashboard.collectAsStateWithLifecycle()
    val sessions by model.sessions.collectAsStateWithLifecycle()
    Page(stringResource(R.string.app_name), stringResource(R.string.tagline)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(stringResource(R.string.root_status), rootText(state.root), state.root is RootState.Available)
                StatusChip(
                    stringResource(R.string.flux_status),
                    stringResource(if (state.flux.installed) R.string.installed else R.string.not_installed),
                    state.flux.installed,
                )
                val synthesis = state.synthesis as? SynthesisReadResult.Success
                StatusChip(
                    stringResource(R.string.synthesis_status),
                    if (synthesis == null) stringResource(R.string.unavailable) else statusText(synthesis.snapshot.freshness.name),
                    synthesis != null,
                )
            }
            state.flux.activeProfile?.let { MetricCard(stringResource(R.string.active_profile), it, state.flux.versionName) }
            val telemetry = state.telemetry
            if (telemetry == null) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.checking))
                    }
                }
            } else {
                MetricCard(
                    stringResource(R.string.cpu),
                    listOfNotNull(telemetry.cpu.identity.manufacturer, telemetry.cpu.identity.model)
                        .joinToString("\n").ifBlank { stringResource(R.string.unknown) },
                    "${telemetry.cpu.coreCount} ${stringResource(R.string.cores)} • ${telemetry.cpu.identity.supportedAbis.joinToString()}",
                )
                CpuHistoryChart(state.cpuHistory)
                MetricCard(
                    stringResource(R.string.memory),
                    memoryValue(telemetry.memory.usedKb, telemetry.memory.totalKb),
                    telemetry.memory.psiSomeAvg10?.let { "PSI avg10 ${format(it, "%")}" },
                )
                MetricCard(
                    stringResource(R.string.thermal),
                    telemetry.thermal.primaryTemperatureCelsius?.let { format(it, "°C") } ?: stringResource(R.string.not_supported),
                    telemetry.thermal.primarySource,
                )
                MetricCard(
                    stringResource(R.string.battery),
                    telemetry.battery.levelPercent?.let { "$it%" } ?: stringResource(R.string.unknown),
                    telemetry.battery.estimatedPowerWatts?.let { "≈ ${format(it, "W")}" },
                )
                MetricCard(
                    stringResource(R.string.gpu),
                    listOfNotNull(telemetry.gpu.vendor, telemetry.gpu.model).joinToString(" ")
                        .ifBlank { gpuCapabilityText(telemetry.gpu.capabilityState) },
                    telemetry.gpu.currentFrequencyHz?.let { format(it / 1_000_000.0, "MHz") }
                        ?: gpuCapabilityText(telemetry.gpu.capabilityState),
                )
            }
            sessions.firstOrNull()?.let { session ->
                MetricCard(stringResource(R.string.latest_session), session.label, session.status.name.lowercase())
            }
            Text(
                stringResource(R.string.monitoring_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun gpuCapabilityText(state: GpuCapabilityState): String = when (state) {
    GpuCapabilityState.IDENTIFIED_TELEMETRY_AVAILABLE -> stringResource(R.string.gpu_telemetry_available)
    GpuCapabilityState.IDENTIFIED_FREQUENCY_INACCESSIBLE -> stringResource(R.string.gpu_frequency_inaccessible)
    GpuCapabilityState.IDENTIFIED_UTILIZATION_INACCESSIBLE -> stringResource(R.string.gpu_utilization_inaccessible)
    GpuCapabilityState.ROOT_REQUIRED -> stringResource(R.string.root_required)
    GpuCapabilityState.PERMISSION_DENIED -> stringResource(R.string.permission_denied)
    GpuCapabilityState.DRIVER_PATH_UNAVAILABLE -> stringResource(R.string.gpu_driver_unavailable)
    GpuCapabilityState.GPU_NOT_IDENTIFIED -> stringResource(R.string.gpu_not_identified)
    GpuCapabilityState.UNSUPPORTED_DEVICE_TOPOLOGY -> stringResource(R.string.not_supported)
}

fun TestsScreen(model: AppViewModel) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val progress by model.benchmarkProgress.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf(false) }
    val readiness = model.readiness()
    val active = progress.sessionId != null && progress.status in setOf(
        SessionStatus.PREPARING, SessionStatus.WARMING_UP, SessionStatus.RUNNING, SessionStatus.COOLING_DOWN,
    )
    Page(stringResource(R.string.tests), stringResource(R.string.quick_test_description)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                stringResource(R.string.readiness),
                when (readiness) {
                    is ReadinessResult.Ready -> stringResource(R.string.ready)
                    is ReadinessResult.ReadyWithWarnings -> stringResource(R.string.ready_warnings)
                    is ReadinessResult.Blocked -> stringResource(R.string.blocked)
                },
                readiness.reasons.joinToString(" • ").takeIf(String::isNotBlank),
            )
            Text(stringResource(R.string.workloads), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.workload_list))
            if (settings.includeStorage) Text(stringResource(R.string.storage_budget), color = MaterialTheme.colorScheme.primary)
            if (active) {
                Text("${stringResource(R.string.progress)}: ${progress.completedSteps}/${progress.totalSteps}")
                progress.workload?.let { Text(it.name.replace('_', ' ').lowercase()) }
                OutlinedButton(onClick = model::cancelQuickTest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel_test))
                }
            } else {
                Button(
                    onClick = {
                        if (settings.includeStorage && !settings.storageNoticeAccepted) notice = true else model.startQuickTest()
                    },
                    enabled = readiness !is ReadinessResult.Blocked,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.start_test)) }
            }
        }
    }
    if (notice) {
        AlertDialog(
            onDismissRequest = { notice = false },
            title = { Text(stringResource(R.string.storage_notice_title)) },
            text = { Text(stringResource(R.string.storage_notice_body)) },
            confirmButton = {
                TextButton(onClick = { model.acceptStorageNotice(); notice = false; model.startQuickTest() }) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = { TextButton(onClick = { notice = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun SessionsScreen(model: AppViewModel) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    Page(stringResource(R.string.sessions)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sessions.isEmpty()) Text(stringResource(R.string.no_sessions))
            sessions.forEach { session -> SessionCard(session, model) }
        }
    }
}

@Composable
private fun SessionCard(session: BenchmarkSession, model: AppViewModel) {
    var expanded by remember(session.id) { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(session.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        DateFormat.getDateTimeInstance().format(Date(session.startedAtEpochMs)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(session.status.name.lowercase())
            }
            if (session.comparisonRole.name == "BASELINE") {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.baseline)) })
            }
            if (expanded) {
                HorizontalDivider()
                session.workloadResults.forEach { result ->
                    Text("${result.kind.name.replace('_', ' ')} — ${format(result.statistics.median, result.unit)}")
                }
                session.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (session.status == SessionStatus.COMPLETED) {
                        TextButton(onClick = { model.markBaseline(session.id) }) { Text(stringResource(R.string.mark_baseline)) }
                        TextButton(onClick = { model.compare(session.id) }) { Text(stringResource(R.string.compare)) }
                    }
                    TextButton(onClick = { model.deleteSession(session.id) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(model: AppViewModel) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val comparison by model.comparison.collectAsStateWithLifecycle()
    val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
    val selectedId = settings.selectedReportSessionId?.takeIf { id -> completed.any { it.id == id } }
    LaunchedEffect(settings.selectedReportSessionId, completed) {
        if (settings.selectedReportSessionId != null && selectedId == null) model.selectReportSession(null)
    }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    val selected = completed.firstOrNull { it.id == selectedId }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val id = pendingJson
        if (uri != null && id != null) model.exportJson(id, uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val id = pendingCsv
        if (uri != null && id != null) model.exportCsv(id, uri)
    }
    val comparisonJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) model.exportComparisonJson(uri)
    }
    val comparisonCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) model.exportComparisonCsv(uri)
    }
    Page(stringResource(R.string.reports), stringResource(R.string.report_local_only)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selected == null) {
                Text(stringResource(R.string.no_report_session_selected))
                Text(stringResource(R.string.select_session_report), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (completed.isEmpty()) {
                    Text(stringResource(R.string.no_completed_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.choose_report_session), style = MaterialTheme.typography.titleMedium)
                    completed.forEach { session ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(session.label)
                                Text(DateFormat.getDateTimeInstance().format(Date(session.startedAtEpochMs)), style = MaterialTheme.typography.bodySmall)
                            }
                            RadioButton(checked = false, onClick = { model.selectReportSession(session.id) })
                        }
                    }
                }
                Text(stringResource(R.string.export_requires_session), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.selected_session), style = MaterialTheme.typography.titleMedium)
                MetricCard(
                    selected.label,
                    DateFormat.getDateTimeInstance().format(Date(selected.startedAtEpochMs)),
                    stringResource(R.string.report_device_preset_methodology, selected.environment.deviceManufacturer, selected.environment.deviceModel, selected.environment.presetConfiguration.preset.name, selected.methodology.methodologyVersion),
                )
                TextButton(onClick = { model.selectReportSession(null) }) { Text(stringResource(R.string.change_session)) }
            }
            Button(
                enabled = selected != null,
                onClick = { selected?.let { pendingJson = it.id; jsonLauncher.launch("fluxlab-${it.id}.json") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.export_json)) }
            OutlinedButton(
                enabled = selected != null,
                onClick = { selected?.let { pendingCsv = it.id; csvLauncher.launch("fluxlab-${it.id}.csv") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.export_csv)) }
            comparison?.let { value ->
                HorizontalDivider()
                Text(stringResource(R.string.comparison_summary), style = MaterialTheme.typography.titleMedium)
                Text(comparisonCompatibilityText(value.compatibility.state), color = MaterialTheme.colorScheme.primary)
                value.compatibility.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(onClick = { comparisonJsonLauncher.launch("fluxlab-comparison.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_comparison_json)) }
                OutlinedButton(onClick = { comparisonCsvLauncher.launch("fluxlab-comparison.csv") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_comparison_csv)) }
                value.workloads.forEach { item ->
                    Text("${item.kind.name}: ${item.percentageDelta?.let { format(it, "%") } ?: "—"} · ${item.confidence.name}")
                }
            }
        }
    }
}

@Composable
private fun statusText(raw: String): String = when (localizedStatusKey(raw)) {
    LocalizedStatusKey.UP_TO_DATE -> stringResource(R.string.status_up_to_date)
    LocalizedStatusKey.UPDATE_REQUIRED -> stringResource(R.string.status_update_required)
    LocalizedStatusKey.PARTIALLY_AVAILABLE -> stringResource(R.string.status_partially_available)
    LocalizedStatusKey.INVALID_DATA_FORMAT -> stringResource(R.string.status_invalid_data_format)
    LocalizedStatusKey.UNAVAILABLE -> stringResource(R.string.unavailable)
    LocalizedStatusKey.UNKNOWN_STATUS -> stringResource(R.string.status_unknown)
}

@Composable
private fun comparisonCompatibilityText(state: com.febricahyaa.fluxlab.model.ComparisonCompatibilityState): String = when (state) {
    com.febricahyaa.fluxlab.model.ComparisonCompatibilityState.COMPATIBLE -> stringResource(R.string.comparison_compatible)
    com.febricahyaa.fluxlab.model.ComparisonCompatibilityState.COMPATIBLE_WITH_WARNINGS -> stringResource(R.string.comparison_compatible_warnings)
    com.febricahyaa.fluxlab.model.ComparisonCompatibilityState.INCOMPATIBLE_METHODOLOGY -> stringResource(R.string.comparison_incompatible_methodology)
}

@Composable
fun SettingsScreen(model: AppViewModel) {
    val settings by model.settings.collectAsStateWithLifecycle()
    var resetDialog by remember { mutableStateOf(false) }
    Page(stringResource(R.string.settings)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingSection(stringResource(R.string.theme)) {
                ThemeSetting.entries.forEach { value ->
                    FilterChip(settings.theme == value, { model.setTheme(value) }, label = { Text(themeName(value)) })
                }
            }
            SettingSection(stringResource(R.string.sampling_interval)) {
                listOf(500L, 1_000L, 2_000L).forEach { value ->
                    FilterChip(
                        selected = settings.samplingIntervalMs == value,
                        onClick = { model.setSamplingInterval(value) },
                        label = { Text(if (value < 1_000) "${value} ms" else "${value / 1_000} s") },
                    )
                }
            }
            ToggleRow(stringResource(R.string.include_storage), settings.includeStorage, model::setIncludeStorage)
            ToggleRow(stringResource(R.string.advanced_metrics), settings.advancedMetrics, model::setAdvanced)
            SettingSection(stringResource(R.string.metric_units)) {
                UnitSetting.entries.forEach { value ->
                    FilterChip(settings.units == value, { model.setUnits(value) }, label = { Text(value.name) })
                }
            }
            SettingSection(stringResource(R.string.language)) {
                LanguageSetting.entries.forEach { value ->
                    FilterChip(settings.language == value, { model.setLanguage(value) }, label = { Text(languageName(value)) })
                }
            }
            MetricCard(
                stringResource(R.string.privacy),
                stringResource(R.string.report_local_only),
                stringResource(R.string.privacy_summary),
            )
            OutlinedButton(onClick = { resetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reset_history))
            }
        }
    }
    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text(stringResource(R.string.reset_history)) },
            text = { Text(stringResource(R.string.reset_history_confirm)) },
            confirmButton = {
                TextButton(onClick = { model.resetHistory(); resetDialog = false }) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}

@Composable
private fun StatusChip(label: String, value: String, positive: Boolean) {
    val color = if (positive) Color(0xFF75C48A) else MaterialTheme.colorScheme.outline
    AssistChip(onClick = {}, label = { Text("$label · $value") }, leadingIcon = {
        Canvas(Modifier.size(9.dp)) { drawCircle(color) }
    })
}

@Composable
private fun MetricCard(title: String, value: String, detail: String? = null) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CpuHistoryChart(samples: List<Pair<Long, Double?>>) {
    if (samples.count { it.second != null } < 2) return
    val lineColor = MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.cpu_usage_chart), style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(92.dp).padding(vertical = 8.dp)) {
                val start = samples.first().first
                val duration = (samples.last().first - start).coerceAtLeast(1L)
                var previous: Offset? = null
                samples.forEach { (time, value) ->
                    if (value == null) previous = null else {
                        val point = Offset(
                            ((time - start).toFloat() / duration) * size.width,
                            size.height - value.toFloat().coerceIn(0f, 100f) / 100f * size.height,
                        )
                        previous?.let { drawLine(lineColor, it, point, 3f) }
                        previous = point
                    }
                }
            }
            val elapsed = (samples.last().first - samples.first().first) / 1_000
            Text("0–100% · ${elapsed}s", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun rootText(root: RootState): String = when (root) {
    RootState.Unknown -> stringResource(R.string.unknown)
    RootState.Checking -> stringResource(R.string.checking)
    RootState.Available -> stringResource(R.string.available)
    RootState.Denied -> stringResource(R.string.denied)
    RootState.Unavailable -> stringResource(R.string.unavailable)
    is RootState.Error -> stringResource(R.string.unavailable)
}

@Composable
private fun themeName(value: ThemeSetting): String = stringResource(
    when (value) {
        ThemeSetting.SYSTEM -> R.string.theme_system
        ThemeSetting.LIGHT -> R.string.theme_light
        ThemeSetting.DARK -> R.string.theme_dark
    },
)

@Composable
private fun languageName(value: LanguageSetting): String = when (value) {
    LanguageSetting.SYSTEM -> stringResource(R.string.theme_system)
    LanguageSetting.ENGLISH -> "English"
    LanguageSetting.INDONESIAN -> "Bahasa Indonesia"
}

private fun format(value: Double, unit: String): String = String.format(Locale.getDefault(), "%.2f %s", value, unit)
private fun memoryValue(used: Long?, total: Long?): String = if (used != null && total != null) {
    String.format(Locale.getDefault(), "%.1f / %.1f GiB", used / 1_048_576.0, total / 1_048_576.0)
} else "—"
