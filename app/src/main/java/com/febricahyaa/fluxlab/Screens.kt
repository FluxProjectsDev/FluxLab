package com.febricahyaa.fluxlab

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.MemoryPressure
import com.febricahyaa.fluxlab.model.GpuCapabilityState
import com.febricahyaa.fluxlab.model.localizedStatusKey
import com.febricahyaa.fluxlab.model.LocalizedStatusKey
import com.febricahyaa.fluxlab.model.ReadinessResult
import com.febricahyaa.fluxlab.model.RootState
import com.febricahyaa.fluxlab.model.SessionStatus
import com.febricahyaa.fluxlab.model.SynthesisReadResult
import com.febricahyaa.fluxlab.model.BenchmarkPresetConfig
import com.febricahyaa.fluxlab.model.BenchmarkStage
import com.febricahyaa.fluxlab.model.BenchmarkVisualMode
import com.febricahyaa.fluxlab.model.MonitoringState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Page(
    title: String? = null,
    subtitle: String? = null,
    showHeader: Boolean = true,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showHeader && title != null) {
            item {
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item { content() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LegacyOverviewScreen(model: AppViewModel, onNavigate: (String) -> Unit = {}) {
    val state by model.dashboard.collectAsStateWithLifecycle()
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val telemetry = state.telemetry
    Page(showHeader = false) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(54.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            FluxWaveform()
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 520.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HeroStatusItem(Modifier.weight(1f), stringResource(R.string.root_status), rootText(state.root), state.root is RootState.Available, Icons.Default.Security) {
                                        onNavigate("overview/flux")
                                    }
                                    HeroStatusItem(Modifier.weight(1f), stringResource(R.string.flux_status), stringResource(if (state.flux.installed) R.string.installed else R.string.not_installed), state.flux.installed, Icons.Default.Bolt) {
                                        onNavigate("overview/flux")
                                    }
                                }
                                HeroStatusItem(Modifier.fillMaxWidth(), stringResource(R.string.synthesis_status), synthesisStatus(state.synthesis), state.synthesis is SynthesisReadResult.Success, Icons.Default.Sync) {
                                    onNavigate("overview/synthesiscore")
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HeroStatusItem(Modifier.weight(1f), stringResource(R.string.root_status), rootText(state.root), state.root is RootState.Available, Icons.Default.Security) {
                                    onNavigate("overview/flux")
                                }
                                HeroStatusItem(Modifier.weight(1f), stringResource(R.string.flux_status), stringResource(if (state.flux.installed) R.string.installed else R.string.not_installed), state.flux.installed, Icons.Default.Bolt) {
                                    onNavigate("overview/flux")
                                }
                                HeroStatusItem(Modifier.weight(1f), stringResource(R.string.synthesis_status), synthesisStatus(state.synthesis), state.synthesis is SynthesisReadResult.Success, Icons.Default.Sync) {
                                    onNavigate("overview/synthesiscore")
                                }
                            }
                        }
                    }
                }
            }
            state.flux.activeProfile?.let {
                MetricCard(stringResource(R.string.active_profile), it, state.flux.versionName, Icons.Default.Tune, onClick = {
                    onNavigate("overview/profile")
                })
            }
            if (telemetry == null) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.checking))
                    }
                }
            } else {
                val cpuName = listOfNotNull(telemetry.cpu.identity.manufacturer, telemetry.cpu.identity.model)
                    .joinToString(" ").ifBlank { stringResource(R.string.unknown) }
                MetricCard(
                    stringResource(R.string.cpu),
                    cpuName,
                    telemetry.cpu.totalUsagePercent?.let { usage ->
                        usageText(usage) + " • " + (telemetry.cpu.aggregateFrequencyHz?.let { format(it / 1_000_000_000.0, "GHz") } ?: stringResource(R.string.frequency_unavailable)) +
                            " • " + telemetry.cpu.coreCount + " " + stringResource(R.string.cores)
                    } ?: stringResource(R.string.collecting_initial_samples),
                    Icons.Default.Memory,
                    onClick = { onNavigate("overview/cpu") },
                    sparkline = state.cpuHistory.map { it.second },
                )
                MetricCard(
                    stringResource(R.string.gpu),
                    listOfNotNull(telemetry.gpu.vendor, telemetry.gpu.model).joinToString(" ").ifBlank { gpuCapabilityText(telemetry.gpu.capabilityState) },
                    (telemetry.gpu.currentFrequencyHz?.let { format(it / 1_000_000.0, "MHz") } ?: stringResource(R.string.frequency_unavailable)) + " • " +
                        (telemetry.gpu.utilizationPercent?.let { usageText(it) } ?: stringResource(R.string.gpu_utilization_unavailable)),
                    Icons.Default.GraphicEq,
                    onClick = { onNavigate("overview/gpu") },
                    sparkline = state.gpuHistory.map { it.second },
                )
                MetricCard(
                    stringResource(R.string.memory),
                    memoryValue(telemetry.memory.usedKb, telemetry.memory.totalKb),
                    listOfNotNull(
                        telemetry.memory.zramBytes?.let { stringResource(R.string.zram_value, formatBytes(it)) },
                        telemetry.memory.psiSomeAvg10?.let { stringResource(R.string.psi_value, format(it, "%")) } ?: stringResource(R.string.psi_unavailable),
                        telemetry.memory.pressure.takeIf { it != MemoryPressure.UNAVAILABLE }?.let { stringResource(R.string.psi_pressure, memoryPressureText(it)) },
                    ).joinToString(" • "),
                    Icons.Default.DataUsage,
                    onClick = { onNavigate("overview/memory") },
                    sparkline = state.memoryHistory.map { it.second },
                )
                MetricCard(
                    stringResource(R.string.storage),
                    storageSummary(telemetry),
                    telemetry.storage.identity.availableCapacityBytes?.let { stringResource(R.string.available_value, formatBytes(it)) }
                        ?: stringResource(R.string.storage_identity_unavailable),
                    Icons.Default.Storage,
                    onClick = { onNavigate("overview/storage") },
                )
                MetricCard(
                    stringResource(R.string.thermal),
                    telemetry.thermal.hottestZone?.temperatureCelsius?.let { format(it, "°C") } ?: stringResource(R.string.not_supported),
                    telemetry.thermal.hottestZone?.let { stringResource(R.string.hottest_sensor, it.name) }
                        ?: stringResource(R.string.thermal_status_unavailable),
                    Icons.Default.DeviceThermostat,
                    onClick = { onNavigate("overview/thermal") },
                    sparkline = state.thermalHistory.map { it.second },
                )
                MetricCard(
                    stringResource(R.string.battery),
                    telemetry.battery.levelPercent?.let { "$it%" } ?: stringResource(R.string.unknown),
                    batteryPowerText(telemetry),
                    Icons.Default.BatteryChargingFull,
                    onClick = { onNavigate("overview/battery") },
                    sparkline = state.batteryHistory.map { it.second },
                )
            }
            sessions.firstOrNull()?.let { session ->
                MetricCard(stringResource(R.string.latest_session), sessionLabel(session), sessionStatusText(session.status), Icons.Default.Analytics, onClick = {
                    onNavigate("overview/latest-session")
                })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(monitoringStateText(state.monitoringState), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { model.setLiveMonitoring(state.monitoringState != MonitoringState.ACTIVE) }) {
                    Text(stringResource(if (state.monitoringState == MonitoringState.ACTIVE) R.string.stop_live_monitoring else R.string.start_live_monitoring))
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun OverviewScreen(model: AppViewModel, onNavigate: (String) -> Unit = {}) {
    val state by model.dashboard.collectAsStateWithLifecycle()
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val telemetry = state.telemetry
    Page(showHeader = false) {
        Column(verticalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = FluxShapes.material.large,
                elevation = CardDefaults.cardElevation(defaultElevation = FluxElevation.hero),
            ) {
                Column(Modifier.padding(FluxSpacing.largeCardPadding), verticalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.size(FluxSpacing.heroIconContainer), contentAlignment = androidx.compose.ui.Alignment.Center) { FluxWaveform() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onNavigate("settings") }) { Icon(Icons.Default.Tune, stringResource(R.string.settings)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(monitoringStateText(state.monitoringState), style = MaterialTheme.typography.titleSmall)
                            Text(samplingIntervalText(settings.samplingIntervalMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(state.monitoringStatus.warning ?: stringResource(R.string.monitoring_ready), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FluxSpacing.compactGap)) {
                        val monitoringRunning = state.monitoringState != MonitoringState.INACTIVE && state.monitoringState != MonitoringState.PAUSED
                        Button(onClick = { model.setLiveMonitoring(!monitoringRunning) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(if (monitoringRunning) R.string.stop_live_monitoring else R.string.start_live_monitoring))
                        }
                        OutlinedButton(onClick = { onNavigate("settings") }) {
                            Icon(Icons.Default.Tune, stringResource(R.string.settings))
                            Text(stringResource(R.string.settings))
                        }
                    }
                }
            }
            Text(stringResource(R.string.system_status), style = MaterialTheme.typography.titleMedium)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 720.dp) 3 else 2
                FlowRow(
                    maxItemsInEachRow = columns,
                    horizontalArrangement = Arrangement.spacedBy(FluxSpacing.compactGap),
                    verticalArrangement = Arrangement.spacedBy(FluxSpacing.compactGap),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HeroStatusItem(Modifier.fillMaxWidth(1f / columns), stringResource(R.string.root_status), rootText(state.root), state.root is RootState.Available, Icons.Default.Security) { onNavigate("overview/root") }
                    HeroStatusItem(Modifier.fillMaxWidth(1f / columns), stringResource(R.string.flux_status), stringResource(if (state.flux.installed) R.string.installed else R.string.not_installed), state.flux.installed, Icons.Default.Bolt) { onNavigate("overview/flux") }
                    HeroStatusItem(Modifier.fillMaxWidth(1f / columns), stringResource(R.string.synthesis_status), synthesisStatus(state.synthesis), state.synthesis is SynthesisReadResult.Success, Icons.Default.Sync) { onNavigate("overview/synthesiscore") }
                }
            }
            Text(stringResource(R.string.system_summary), style = MaterialTheme.typography.titleMedium)
            if (telemetry == null) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(FluxSpacing.cardInternalPadding), horizontalArrangement = Arrangement.spacedBy(FluxSpacing.compactGap)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.checking))
                    }
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth >= 720.dp) 3 else 2
                    FlowRow(
                        maxItemsInEachRow = columns,
                        horizontalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap),
                        verticalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MetricCard(
                            stringResource(R.string.cpu),
                            telemetry.cpu.totalUsagePercent?.let(::usageText) ?: stringResource(R.string.collecting_initial_samples),
                            listOfNotNull(
                                telemetry.cpu.aggregateFrequencyHz?.let { format(it / 1_000_000_000.0, "GHz") } ?: stringResource(R.string.frequency_unavailable),
                                stringResource(R.string.cores_value, telemetry.cpu.coreCount),
                                listOfNotNull(telemetry.cpu.identity.manufacturer, telemetry.cpu.identity.model).joinToString(" ").takeIf(String::isNotBlank),
                            ).joinToString(" • "),
                            Icons.Default.Memory,
                            modifier = Modifier.fillMaxWidth(1f / columns),
                            onClick = { onNavigate("overview/cpu") },
                            sparkline = state.cpuHistory.map { it.second },
                            metric = FluxMetric.CPU,
                            gaugeProgress = telemetry.cpu.totalUsagePercent?.div(100.0)?.toFloat(),
                        )
                        MetricCard(
                            stringResource(R.string.gpu),
                            telemetry.gpu.utilizationPercent?.let(::usageText) ?: telemetry.gpu.currentFrequencyHz?.let { format(it / 1_000_000.0, "MHz") } ?: stringResource(R.string.not_supported),
                            listOfNotNull(
                                telemetry.gpu.currentFrequencyHz?.let { format(it / 1_000_000.0, "MHz") },
                                telemetry.gpu.utilizationPercent?.let { stringResource(R.string.live_sample) } ?: stringResource(R.string.gpu_utilization_unavailable),
                                listOfNotNull(telemetry.gpu.vendor, telemetry.gpu.model).joinToString(" ").takeIf(String::isNotBlank),
                            ).joinToString(" • "),
                            Icons.Default.GraphicEq,
                            modifier = Modifier.fillMaxWidth(1f / columns),
                            onClick = { onNavigate("overview/gpu") },
                            sparkline = state.gpuHistory.map { it.second },
                            metric = FluxMetric.GPU,
                            gaugeProgress = telemetry.gpu.utilizationPercent?.div(100.0)?.toFloat(),
                        )
                        val memoryRatio = telemetry.memory.usedKb?.toDouble()?.let { used -> telemetry.memory.totalKb?.takeIf { it > 0L }?.let { used / it } }
                        MetricCard(
                            stringResource(R.string.memory),
                            memoryValue(telemetry.memory.usedKb, telemetry.memory.totalKb),
                            listOfNotNull(
                                telemetry.memory.zramBytes?.let { stringResource(R.string.zram_value, formatBytes(it)) },
                                telemetry.memory.psiSomeAvg10?.let { stringResource(R.string.psi_value, format(it, "%")) } ?: stringResource(R.string.psi_unavailable),
                                telemetry.memory.pressure.takeIf { it != MemoryPressure.UNAVAILABLE }?.let { stringResource(R.string.psi_pressure, memoryPressureText(it)) },
                            ).joinToString(" • "),
                            Icons.Default.DataUsage,
                            modifier = Modifier.fillMaxWidth(1f / columns),
                            onClick = { onNavigate("overview/memory") },
                            sparkline = state.memoryHistory.map { it.second },
                            metric = FluxMetric.MEMORY,
                            gaugeProgress = memoryRatio?.toFloat(),
                        )
                        MetricCard(
                            stringResource(R.string.storage),
                            telemetry.storage.identity.totalCapacityBytes?.let(::formatBytes) ?: stringResource(R.string.not_supported),
                            listOfNotNull(storageTypeText(telemetry.storage.identity.storageType), telemetry.storage.identity.availableCapacityBytes?.let { stringResource(R.string.available_value, formatBytes(it)) } ?: stringResource(R.string.storage_identity_unavailable)).joinToString(" • "),
                            Icons.Default.Storage,
                            modifier = Modifier.fillMaxWidth(1f / columns),
                            onClick = { onNavigate("overview/storage") },
                            metric = FluxMetric.STORAGE,
                            gaugeProgress = telemetry.storage.identity.totalCapacityBytes?.let { total -> telemetry.storage.identity.availableCapacityBytes?.let { available -> (1.0 - available.toDouble() / total).toFloat() } },
                        )
                        MetricCard(
                            stringResource(R.string.thermal),
                            telemetry.thermal.hottestZone?.temperatureCelsius?.let { format(it, "°C") } ?: stringResource(R.string.not_supported),
                            telemetry.thermal.hottestZone?.let { stringResource(R.string.hottest_sensor, it.name) } ?: stringResource(R.string.thermal_status_unavailable),
                            Icons.Default.DeviceThermostat,
                            modifier = Modifier.fillMaxWidth(1f / columns),
                            onClick = { onNavigate("overview/thermal") },
                            sparkline = state.thermalHistory.map { it.second },
                            metric = FluxMetric.THERMAL,
                        )
                        MetricCard(
                            stringResource(R.string.battery),
                            telemetry.battery.levelPercent?.let { "$it%" } ?: stringResource(R.string.unknown),
                            batteryPowerText(telemetry),
                            Icons.Default.BatteryChargingFull,
                            modifier = Modifier.fillMaxWidth(1f / columns),
                            onClick = { onNavigate("overview/battery") },
                            sparkline = state.batteryHistory.map { it.second },
                            metric = FluxMetric.BATTERY,
                            gaugeProgress = telemetry.battery.levelPercent?.div(100f),
                        )
                    }
                }
            }
            Text(stringResource(R.string.latest_session), style = MaterialTheme.typography.titleMedium)
            sessions.firstOrNull()?.let { session ->
                MetricCard(stringResource(R.string.latest_session), sessionLabel(session), sessionStatusText(session.status), Icons.Default.Analytics, onClick = { onNavigate("overview/latest-session") })
            } ?: MetricCard(stringResource(R.string.latest_session), stringResource(R.string.no_sessions), stringResource(R.string.no_completed_sessions), Icons.Default.Analytics, onClick = { onNavigate("sessions") }, metric = FluxMetric.UNAVAILABLE)
        }
    }
}

@Composable
internal fun FluxWaveform() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(42.dp)) {
        val points = listOf(0.0f, .25f, .7f, .35f, .85f, .45f, 1.0f)
        points.zipWithNext().forEachIndexed { index, pair ->
            drawLine(
                color = color,
                start = Offset(index * size.width / (points.size - 1), size.height * (1f - pair.first)),
                end = Offset((index + 1) * size.width / (points.size - 1), size.height * (1f - pair.second)),
                strokeWidth = 4f,
            )
        }
    }
}

@Composable
private fun HeroStatusItem(
    modifier: Modifier,
    label: String,
    value: String,
    positive: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun synthesisStatus(value: SynthesisReadResult): String {
    val success = value as? SynthesisReadResult.Success
    return if (success == null) stringResource(R.string.unavailable) else statusText(success.snapshot.freshness.name)
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

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun TestsScreen(model: AppViewModel, onNavigate: (String) -> Unit = {}) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val dashboard by model.dashboard.collectAsStateWithLifecycle()
    val progress by model.benchmarkProgress.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf(false) }
    var warningNotice by remember { mutableStateOf(false) }
    val readiness = model.readiness()
    val presetConfig = BenchmarkPresetConfig.forPreset(settings.preset)
    val active = progress.sessionId != null && progress.status in setOf(
        SessionStatus.PREPARING, SessionStatus.WARMING_UP, SessionStatus.RUNNING, SessionStatus.COOLING_DOWN,
    )
    Page(stringResource(R.string.tests), stringResource(R.string.preset_summary, presetLabel(settings.preset), pluralStringResource(R.plurals.repetitions, presetConfig.measuredRepetitionCount))) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.benchmark_preset), style = MaterialTheme.typography.titleMedium)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 720.dp) 3 else 2
                FlowRow(maxItemsInEachRow = columns, horizontalArrangement = Arrangement.spacedBy(FluxSpacing.compactGap), verticalArrangement = Arrangement.spacedBy(FluxSpacing.compactGap)) {
                    BenchmarkPreset.entries.forEach { preset ->
                        val config = BenchmarkPresetConfig.forPreset(preset)
                        MetricCard(
                            presetLabel(preset),
                            presetPurpose(preset),
                            stringResource(R.string.preset_card_summary, durationText(config.maximumDurationMs), config.measuredRepetitionCount, 6 + if (settings.includeStorage) 3 else 0),
                            Icons.Default.Analytics,
                            modifier = Modifier.fillMaxWidth(1f / columns),
                            onClick = { model.setPreset(preset) },
                            metric = if (settings.preset == preset) FluxMetric.CPU else FluxMetric.UNAVAILABLE,
                        )
                    }
                }
            }
            MetricCard(
                stringResource(R.string.readiness),
                when (readiness) {
                    is ReadinessResult.Ready -> stringResource(R.string.ready)
                    is ReadinessResult.ReadyWithWarnings -> stringResource(R.string.ready_warnings)
                    is ReadinessResult.Blocked -> stringResource(R.string.blocked)
                },
                readiness.reasons.joinToString(" • ").takeIf(String::isNotBlank),
                metric = when (readiness) {
                    is ReadinessResult.Blocked -> FluxMetric.ERROR
                    is ReadinessResult.ReadyWithWarnings -> FluxMetric.WARNING
                    is ReadinessResult.Ready -> FluxMetric.SUCCESS
                },
            )
            Text(stringResource(R.string.system_status), style = MaterialTheme.typography.titleMedium)
            MetricCard(stringResource(R.string.thermal), dashboard.telemetry?.thermal?.let { thermalReadinessText(it.eligibility) } ?: stringResource(R.string.checking), dashboard.telemetry?.thermal?.hottestZone?.temperatureCelsius?.let { format(it, "°C") } ?: stringResource(R.string.thermal_status_unavailable), Icons.Default.DeviceThermostat, metric = FluxMetric.THERMAL)
            MetricCard(stringResource(R.string.battery), dashboard.telemetry?.battery?.let { chargingStateText(it.chargingState) } ?: stringResource(R.string.checking), dashboard.telemetry?.battery?.levelPercent?.let { "$it%" } ?: stringResource(R.string.unknown), Icons.Default.BatteryChargingFull, metric = FluxMetric.BATTERY)
            MetricCard(stringResource(R.string.storage), if (settings.includeStorage) stringResource(R.string.ready) else stringResource(R.string.not_supported), stringResource(R.string.storage_budget, formatBytes(presetConfig.storageAllocationLimitBytes)), Icons.Default.Storage, metric = FluxMetric.STORAGE)
            Text(stringResource(R.string.visual_mode_value, stringResource(if (settings.visualMode == BenchmarkVisualMode.FULL) R.string.visual_full else R.string.visual_reduced)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.screen_recording_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.workloads), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.workload_list))
            if (settings.includeStorage) Text(stringResource(R.string.storage_budget, formatBytes(presetConfig.storageAllocationLimitBytes)), color = MaterialTheme.colorScheme.primary)
            if (active) {
                Text("${stringResource(R.string.progress)}: ${progress.completedSteps}/${progress.totalSteps}")
                progress.workload?.let { Text(workloadText(it)) }
                OutlinedButton(onClick = model::cancelQuickTest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel_test))
                }
            } else {
                Button(
                    onClick = {
                        when {
                            settings.includeStorage && !settings.storageNoticeAccepted -> notice = true
                            readiness is ReadinessResult.ReadyWithWarnings && settings.confirmReadinessWarnings -> warningNotice = true
                            else -> { model.startQuickTest(); onNavigate("benchmark/active") }
                        }
                    },
                    enabled = readiness !is ReadinessResult.Blocked,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(presetCta(settings.preset)) }
            }
        }
    }
    if (notice) {
        AlertDialog(
            onDismissRequest = { notice = false },
            title = { Text(stringResource(R.string.storage_notice_title)) },
            text = { Text(stringResource(R.string.storage_notice_body)) },
            confirmButton = {
                TextButton(onClick = { model.acceptStorageNotice(); notice = false; model.startQuickTest(); onNavigate("benchmark/active") }) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = { TextButton(onClick = { notice = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (warningNotice) {
        AlertDialog(
            onDismissRequest = { warningNotice = false },
            title = { Text(stringResource(R.string.readiness_warning_title)) },
            text = { Text(stringResource(R.string.readiness_warning_body, readiness.reasons.joinToString(" • "))) },
            confirmButton = {
                TextButton(onClick = { warningNotice = false; model.startQuickTest(); onNavigate("benchmark/active") }) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = { TextButton(onClick = { warningNotice = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun SessionsScreen(model: AppViewModel, onNavigate: (String) -> Unit = {}) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    Page(stringResource(R.string.sessions)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sessions.isEmpty()) Text(stringResource(R.string.no_sessions))
            sessions.forEach { session -> SessionCard(session, model, onNavigate) }
        }
    }
}

@Composable
private fun SessionCard(session: BenchmarkSession, model: AppViewModel, onNavigate: (String) -> Unit) {
    var deleteRequested by remember(session.id) { mutableStateOf(false) }
    Card(
        onClick = { onNavigate("sessions/${session.id}") },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(sessionLabel(session), fontWeight = FontWeight.SemiBold)
                    Text(
                        DateFormat.getDateTimeInstance().format(Date(session.startedAtEpochMs)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(sessionStatusText(session.status))
            }
            if (session.comparisonRole.name == "BASELINE") {
                Text(
                    stringResource(R.string.baseline),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            session.workloadResults.firstOrNull()?.let { result ->
                Text("${workloadText(result.kind)} — ${engineeringFormat(result.statistics.median, result.unit)}", style = MaterialTheme.typography.bodySmall)
            }
            session.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session.status == SessionStatus.COMPLETED) {
                    TextButton(onClick = { model.markBaseline(session.id) }) { Text(stringResource(R.string.mark_baseline)) }
                    TextButton(onClick = { model.compare(session.id) }) { Text(stringResource(R.string.compare)) }
                }
                TextButton(onClick = { deleteRequested = true }) { Text(stringResource(R.string.delete)) }
            }
        }
    }
    if (deleteRequested) {
        AlertDialog(
            onDismissRequest = { deleteRequested = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_session_confirm)) },
            confirmButton = {
                TextButton(onClick = { model.deleteSession(session.id); deleteRequested = false }) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleteRequested = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun ReportsScreen(model: AppViewModel) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val comparison by model.comparison.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
    val selectedId = settings.selectedReportSessionId?.takeIf { id -> completed.any { it.id == id } }
    LaunchedEffect(settings.selectedReportSessionId, completed) {
        if (settings.selectedReportSessionId != null && selectedId == null) model.selectReportSession(null)
    }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }
    var exportedMime by remember { mutableStateOf("application/json") }
    val selected = completed.firstOrNull { it.id == selectedId }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val id = pendingJson
        if (uri != null && id != null) { model.exportJson(id, uri); exportedUri = uri; exportedMime = "application/json" }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val id = pendingCsv
        if (uri != null && id != null) { model.exportCsv(id, uri); exportedUri = uri; exportedMime = "text/csv" }
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
                                Text(sessionLabel(session))
                                Text(DateFormat.getDateTimeInstance().format(Date(session.startedAtEpochMs)), style = MaterialTheme.typography.bodySmall)
                            }
                            RadioButton(selected = false, onClick = { model.selectReportSession(session.id) })
                        }
                    }
                }
                Text(stringResource(R.string.export_requires_session), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.selected_session), style = MaterialTheme.typography.titleMedium)
                MetricCard(
                    sessionLabel(selected),
                    DateFormat.getDateTimeInstance().format(Date(selected.startedAtEpochMs)),
                    stringResource(R.string.report_device_preset_methodology, selected.environment.deviceManufacturer, selected.environment.deviceModel, presetLabel(selected.environment.presetConfiguration.preset), selected.methodology.methodologyVersion),
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
            exportedUri?.let { uri ->
                OutlinedButton(
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = exportedMime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, null))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.share_report)) }
            }
            comparison?.let { value ->
                HorizontalDivider()
                Text(stringResource(R.string.comparison_summary), style = MaterialTheme.typography.titleMedium)
                Text(comparisonCompatibilityText(value.compatibility.state), color = MaterialTheme.colorScheme.primary)
                value.compatibility.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(onClick = { comparisonJsonLauncher.launch("fluxlab-comparison.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_comparison_json)) }
                OutlinedButton(onClick = { comparisonCsvLauncher.launch("fluxlab-comparison.csv") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_comparison_csv)) }
                value.workloads.forEach { item ->
                    Text("${workloadText(item.kind)}: ${item.percentageDelta?.let { format(it, "%") } ?: stringResource(R.string.not_supported)} · ${confidenceText(item.confidence)}")
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
fun SettingsScreen(model: AppViewModel, onNavigate: (String) -> Unit = {}) {
    val settings by model.settings.collectAsStateWithLifecycle()
    var resetDialog by remember { mutableStateOf(false) }
    Page(stringResource(R.string.settings)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingSection(stringResource(R.string.theme)) {
                ThemeSetting.entries.forEach { value ->
                    FilterChip(settings.theme == value, { model.setTheme(value) }, label = { Text(themeName(value)) })
                }
            }
            SettingSection(stringResource(R.string.color_style)) {
                ColorStyle.entries.forEach { value ->
                    FilterChip(settings.colorStyle == value, { model.setColorStyle(value) }, label = { Text(colorStyleName(value)) })
                }
            }
            SettingSection(stringResource(R.string.sampling_interval)) {
                listOf(500L, 1_000L, 2_000L).forEach { value ->
                    FilterChip(
                        selected = settings.samplingIntervalMs == value,
                        onClick = { model.setSamplingInterval(value) },
                        label = { Text(samplingIntervalText(value)) },
                    )
                }
            }
            ToggleRow(stringResource(R.string.include_storage), settings.includeStorage, model::setIncludeStorage)
            ToggleRow(stringResource(R.string.advanced_metrics), settings.advancedMetrics, model::setAdvanced)
            SettingSection(stringResource(R.string.monitoring_settings)) {
                ToggleRow(stringResource(R.string.start_automatically), settings.autoStartMonitoring, model::setAutoStartMonitoring)
                ToggleRow(stringResource(R.string.preserve_last_sample), settings.preserveLastSample, model::setPreserveLastSample)
                ToggleRow(stringResource(R.string.idle_monitoring_policy), settings.stopMonitoringWhenIdle, model::setStopMonitoringWhenIdle)
            }
            SettingSection(stringResource(R.string.benchmark_settings)) {
                ToggleRow(stringResource(R.string.confirm_readiness_warnings), settings.confirmReadinessWarnings, model::setConfirmReadinessWarnings)
                ToggleRow(stringResource(R.string.screen_recording_declaration), settings.screenRecordingDeclared, model::setScreenRecordingDeclared)
            }
            SettingSection(stringResource(R.string.benchmark_visuals)) {
                BenchmarkVisualMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.visualMode == mode,
                        onClick = { model.setVisualMode(mode) },
                        label = { Text(stringResource(if (mode == BenchmarkVisualMode.FULL) R.string.visual_full else R.string.visual_reduced)) },
                    )
                }
            }
            SettingSection(stringResource(R.string.metric_units)) {
                UnitSetting.entries.forEach { value ->
                    FilterChip(settings.units == value, { model.setUnits(value) }, label = { Text(unitText(value)) })
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
            MetricCard(
                stringResource(R.string.about_legal),
                stringResource(R.string.about_legal_subtitle),
                stringResource(R.string.about_description),
                Icons.Default.Info,
                onClick = { onNavigate("about") },
                metric = FluxMetric.CPU,
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
private fun StatusChip(label: String, value: String, positive: Boolean, icon: ImageVector = Icons.Default.Info, onClick: () -> Unit = {}) {
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    AssistChip(onClick = onClick, label = { Text(label + " · " + value) }, leadingIcon = { Icon(icon, label, tint = color) })
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    detail: String? = null,
    icon: ImageVector = Icons.Default.Info,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    sparkline: List<Double?> = emptyList(),
    metric: FluxMetric? = null,
    gaugeProgress: Float? = null,
) {
    val accent = metric?.let { fluxMetricColor(it) } ?: MaterialTheme.colorScheme.primary
    val description = listOfNotNull(title, value, detail).joinToString(", ")
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics {
            role = Role.Button
            contentDescription = description
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = FluxShapes.material.medium,
    ) {
        Row(
            Modifier.padding(FluxSpacing.cardInternalPadding),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(FluxSpacing.cardInternalPadding + 28.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Icon(icon, title, tint = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = accent)
                Text(value, style = MaterialTheme.typography.titleLarge)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (gaugeProgress != null) {
                FluxGauge(gaugeProgress, accent, Modifier.size(58.dp), value)
            } else if (sparkline.count { it != null } >= 2) {
                FluxSparkline(sparkline, accent, Modifier.size(width = 76.dp, height = 42.dp), stringResource(R.string.chart_history, title))
            } else {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.open_detail), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Sparkline(samples: List<Double?>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val valid = samples.mapIndexedNotNull { index, value -> value?.takeIf { it.isFinite() }?.let { index to it } }
        if (valid.size < 2) return@Canvas
        val min = valid.minOf { it.second }
        val max = valid.maxOf { it.second }.coerceAtLeast(min + 0.0001)
        valid.zipWithNext().forEach { (from, to) ->
            val start = Offset(
                from.first.toFloat() / (samples.lastIndex.coerceAtLeast(1)) * size.width,
                size.height - ((from.second - min) / (max - min)).toFloat() * size.height,
            )
            val end = Offset(
                to.first.toFloat() / (samples.lastIndex.coerceAtLeast(1)) * size.width,
                size.height - ((to.second - min) / (max - min)).toFloat() * size.height,
            )
            drawLine(color, start, end, strokeWidth = 3f)
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
            Text(stringResource(R.string.cpu_chart_scale, elapsed), style = MaterialTheme.typography.bodySmall)
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
private fun samplingIntervalText(value: Long): String = if (value < 1_000L) {
    stringResource(R.string.sampling_interval_milliseconds, value.toInt())
} else {
    stringResource(R.string.sampling_interval_seconds, (value / 1_000L).toInt())
}

@Composable
private fun colorStyleName(value: ColorStyle): String = stringResource(
    if (value == ColorStyle.FLUX) R.string.color_style_flux else R.string.color_style_dynamic,
)

@Composable
private fun languageName(value: LanguageSetting): String = when (value) {
    LanguageSetting.SYSTEM -> stringResource(R.string.theme_system)
    LanguageSetting.ENGLISH -> stringResource(R.string.language_english)
    LanguageSetting.INDONESIAN -> stringResource(R.string.language_indonesian)
}



@Composable
private fun unitText(value: UnitSetting): String = stringResource(if (value == UnitSetting.SI) R.string.unit_si else R.string.unit_iec)

private fun format(value: Double, unit: String): String = String.format(Locale.getDefault(), "%.2f %s", value, unit)
@Composable
private fun memoryValue(used: Long?, total: Long?): String = if (used != null && total != null) {
    String.format(Locale.getDefault(), "%.1f / %.1f GiB", used / 1_048_576.0, total / 1_048_576.0)
} else stringResource(R.string.not_supported)

@Composable
private fun storageSummary(telemetry: com.febricahyaa.fluxlab.model.DeviceTelemetrySnapshot): String {
    val type = storageTypeText(telemetry.storage.identity.storageType)
    val capacity = telemetry.storage.identity.totalCapacityBytes?.let(::formatBytes) ?: stringResource(R.string.unknown)
    return type + " • " + capacity
}

@Composable
private fun storageTypeText(type: com.febricahyaa.fluxlab.model.StorageType): String = when (type) {
    com.febricahyaa.fluxlab.model.StorageType.UFS -> "UFS"
    com.febricahyaa.fluxlab.model.StorageType.EMMC -> "eMMC"
    com.febricahyaa.fluxlab.model.StorageType.VIRTUAL -> stringResource(R.string.virtual_storage)
    com.febricahyaa.fluxlab.model.StorageType.PERMISSION_DENIED -> stringResource(R.string.permission_denied)
    com.febricahyaa.fluxlab.model.StorageType.MALFORMED -> stringResource(R.string.malformed)
    com.febricahyaa.fluxlab.model.StorageType.UNSUPPORTED -> stringResource(R.string.not_supported)
    com.febricahyaa.fluxlab.model.StorageType.UNKNOWN -> stringResource(R.string.storage_type_unknown)
}

@Composable
private fun batteryPowerText(telemetry: com.febricahyaa.fluxlab.model.DeviceTelemetrySnapshot): String {
    val state = chargingStateText(telemetry.battery.chargingState)
    val power = telemetry.battery.calculatedPowerWatts?.let { format(it, "W") } ?: stringResource(R.string.power_unavailable)
    return buildList {
        add(state + " • " + power)
        telemetry.battery.diagnostics.capacity.currentCharge.normalizedMilliAmpHours?.let {
            add(stringResource(R.string.current_charge_short, format(it, "mAh")))
        }
        telemetry.battery.diagnostics.capacity.estimatedSoHPercent?.let {
            add(stringResource(R.string.estimated_health_short, format(it, "%")))
        }
    }.joinToString(" • ")
}

@Composable
private fun chargingStateText(state: com.febricahyaa.fluxlab.model.ChargingState): String = when (state) {
    com.febricahyaa.fluxlab.model.ChargingState.CHARGING -> stringResource(R.string.charging)
    com.febricahyaa.fluxlab.model.ChargingState.DISCHARGING -> stringResource(R.string.discharging)
    com.febricahyaa.fluxlab.model.ChargingState.FULL -> stringResource(R.string.full)
    com.febricahyaa.fluxlab.model.ChargingState.NOT_CHARGING -> stringResource(R.string.not_charging)
    com.febricahyaa.fluxlab.model.ChargingState.UNKNOWN -> stringResource(R.string.power_state_unknown)
}

@Composable
private fun monitoringStateText(state: MonitoringState): String = when (state) {
    MonitoringState.INACTIVE -> stringResource(R.string.monitoring_inactive)
    MonitoringState.STARTING -> stringResource(R.string.monitoring_starting)
    MonitoringState.COLLECTING_INITIAL_SAMPLES -> stringResource(R.string.collecting_initial_samples)
    MonitoringState.ACTIVE -> stringResource(R.string.monitoring_active)
    MonitoringState.PAUSED -> stringResource(R.string.monitoring_paused)
    MonitoringState.TEMPORARILY_UNAVAILABLE -> stringResource(R.string.monitoring_temporarily_unavailable)
    MonitoringState.UNSUPPORTED -> stringResource(R.string.not_supported)
    MonitoringState.PERMISSION_DENIED -> stringResource(R.string.permission_denied)
    MonitoringState.MALFORMED -> stringResource(R.string.malformed)
    MonitoringState.STALE -> stringResource(R.string.stale)
    MonitoringState.FAILED -> stringResource(R.string.monitoring_failed)
}

@Composable
private fun memoryPressureText(value: MemoryPressure): String = when (value) {
    MemoryPressure.NORMAL -> stringResource(R.string.psi_pressure_normal)
    MemoryPressure.ELEVATED -> stringResource(R.string.psi_pressure_elevated)
    MemoryPressure.HIGH -> stringResource(R.string.psi_pressure_high)
    MemoryPressure.UNAVAILABLE -> stringResource(R.string.psi_unavailable)
}
private fun usageText(value: Double): String = format(value, "%")

@Composable
private fun sessionLabel(session: BenchmarkSession): String = when (session.label) {
    "Quick Test" -> presetLabel(BenchmarkPreset.QUICK)
    "Standard Test" -> presetLabel(BenchmarkPreset.STANDARD)
    "Extended Test" -> presetLabel(BenchmarkPreset.EXTENDED)
    else -> session.label
}

@Composable
private fun sessionStatusText(status: SessionStatus): String = stringResource(when (status) {
    SessionStatus.COMPLETED -> R.string.completed
    SessionStatus.RUNNING -> R.string.running
    SessionStatus.CANCELLED -> R.string.cancelled
    SessionStatus.FAILED -> R.string.failed
    SessionStatus.PREPARING -> R.string.preparing
    SessionStatus.WARMING_UP -> R.string.warming_up
    SessionStatus.COOLING_DOWN -> R.string.cooling_down
})
private fun formatBytes(value: Long): String = when {
    value >= 1_073_741_824L -> String.format(Locale.getDefault(), "%.1f GiB", value / 1_073_741_824.0)
    value >= 1_048_576L -> String.format(Locale.getDefault(), "%.1f MiB", value / 1_048_576.0)
    else -> value.toString() + " B"
}

enum class MetricDetailKind { CPU, GPU, MEMORY, THERMAL, ROOT, FLUX, SYNTHESIS, PROFILE }

@Composable
private fun detailTitle(kind: MetricDetailKind): String = when (kind) {
    MetricDetailKind.CPU -> stringResource(R.string.cpu)
    MetricDetailKind.GPU -> stringResource(R.string.gpu)
    MetricDetailKind.MEMORY -> stringResource(R.string.memory)
    MetricDetailKind.THERMAL -> stringResource(R.string.thermal)
    MetricDetailKind.ROOT -> stringResource(R.string.root_status)
    MetricDetailKind.FLUX -> stringResource(R.string.flux_status)
    MetricDetailKind.SYNTHESIS -> stringResource(R.string.synthesis_status)
    MetricDetailKind.PROFILE -> stringResource(R.string.active_profile)
}

@Composable
fun MetricDetailScreen(model: AppViewModel, kind: MetricDetailKind, onBack: () -> Unit) {
    val state by model.dashboard.collectAsStateWithLifecycle()
    val telemetry = state.telemetry
    val tabLabels = when (kind) {
        MetricDetailKind.CPU -> listOf(R.string.summary, R.string.per_core, R.string.frequency, R.string.cluster)
        MetricDetailKind.GPU -> listOf(R.string.summary, R.string.frequency, R.string.utilization, R.string.technical)
        else -> emptyList()
    }
    var selectedTab by remember(kind) { mutableStateOf(0) }
    DetailPage(detailTitle(kind), onBack) {
        if (tabLabels.isNotEmpty()) {
            DetailTabs(tabLabels, selectedTab) { selectedTab = it }
            Text(stringResource(R.string.selected_detail_tab, stringResource(tabLabels[selectedTab])), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (kind) {
            MetricDetailKind.CPU -> telemetry?.let {
                if (selectedTab == 0) {
                    MetricCard(stringResource(R.string.cpu), it.cpu.identity.model ?: stringResource(R.string.unknown),
                        it.cpu.totalUsagePercent?.let { value -> usageText(value) } ?: stringResource(R.string.collecting_initial_samples),
                        Icons.Default.Memory, metric = FluxMetric.CPU)
                    DetailStats(
                        stringResource(R.string.cpu_usage_chart),
                        state.cpuHistory.mapNotNull { it.second },
                        "%",
                    )
                    CpuHistoryChart(state.cpuHistory)
                }
                if (selectedTab == 0 || selectedTab == 2) {
                    val aggregateFrequencies = state.telemetryHistory.mapNotNull { sample -> sample.cpu.aggregateFrequencyHz?.div(1_000_000_000.0) }
                    DetailStats(
                        stringResource(R.string.aggregate_frequency),
                        aggregateFrequencies,
                        "GHz",
                    )
                    Sparkline(
                        state.telemetryHistory.map { it.cpu.aggregateFrequencyHz?.div(1_000_000_000.0) },
                        Modifier.fillMaxWidth().height(100.dp),
                    )
                    Text(stringResource(R.string.minimum_frequency_value, it.cpu.cores.mapNotNull { core -> core.minimumFrequencyHz }.minOrNull()?.let { value -> format(value / 1_000_000_000.0, "GHz") } ?: stringResource(R.string.not_supported)))
                    Text(stringResource(R.string.maximum_frequency_value, it.cpu.cores.mapNotNull { core -> core.maximumFrequencyHz }.maxOrNull()?.let { value -> format(value / 1_000_000_000.0, "GHz") } ?: stringResource(R.string.not_supported)))
                    Text(stringResource(R.string.cpu_frequency_source, it.cpu.frequencySource ?: stringResource(R.string.not_supported)))
                }
                if (selectedTab == 1 || selectedTab == 3) {
                    it.cpu.cores.filter { core -> selectedTab != 3 || core.cluster != null }.forEach { core ->
                        val frequency = core.currentFrequencyHz?.let { value -> format(value / 1_000_000_000.0, "GHz") }
                            ?: stringResource(R.string.not_supported)
                        MetricCard(
                            stringResource(R.string.core_number, core.index),
                            core.usagePercent?.let { value -> usageText(value) } ?: stringResource(R.string.collecting_initial_samples),
                            listOfNotNull(
                                frequency,
                                if (core.online) stringResource(R.string.online) else stringResource(R.string.offline),
                                core.governor?.let { governor -> stringResource(R.string.governor_value, governor) },
                                core.cluster?.let { cluster -> stringResource(R.string.cluster_value, cluster) },
                            ).joinToString(" • "),
                            Icons.Default.Memory,
                        )
                    }
                }
            } ?: LoadingDetail()
            MetricDetailKind.GPU -> telemetry?.let {
                val model = listOfNotNull(it.gpu.vendor, it.gpu.model).joinToString(" ").ifBlank { stringResource(R.string.gpu_not_identified) }
                if (selectedTab == 0) {
                    MetricCard(stringResource(R.string.gpu), model,
                        it.gpu.currentFrequencyHz?.let { value -> format(value / 1_000_000.0, "MHz") } ?: stringResource(R.string.not_supported),
                        Icons.Default.GraphicEq, metric = FluxMetric.GPU)
                }
                if (selectedTab == 0 || selectedTab == 1) {
                    DetailStats(
                        stringResource(R.string.frequency),
                        state.telemetryHistory.mapNotNull { sample -> sample.gpu.currentFrequencyHz?.div(1_000_000.0) },
                        "MHz",
                    )
                    Sparkline(
                        state.telemetryHistory.map { it.gpu.currentFrequencyHz?.div(1_000_000.0) },
                        Modifier.fillMaxWidth().height(100.dp),
                    )
                    Text(stringResource(R.string.minimum_frequency_value, it.gpu.minimumFrequencyHz?.let { value -> format(value / 1_000_000.0, "MHz") } ?: stringResource(R.string.not_supported)))
                    Text(stringResource(R.string.maximum_frequency_value, it.gpu.maximumFrequencyHz?.let { value -> format(value / 1_000_000.0, "MHz") } ?: stringResource(R.string.not_supported)))
                    Text(stringResource(R.string.gpu_frequency_source, it.gpu.frequencySource ?: stringResource(R.string.not_supported)))
                }
                if (selectedTab == 0 || selectedTab == 2) {
                    Text(
                        it.gpu.utilizationPercent?.let { value -> stringResource(R.string.utilization_value, usageText(value)) }
                            ?: (it.gpu.utilizationAvailabilityReason ?: gpuCapabilityText(it.gpu.capabilityState)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val utilizationHistory = state.gpuHistory.mapNotNull { it.second }
                    if (utilizationHistory.size >= 2) {
                        DetailStats(stringResource(R.string.gpu_utilization_history), utilizationHistory, "%")
                        Sparkline(state.gpuHistory.map { it.second }, Modifier.fillMaxWidth().height(100.dp))
                    }
                }
                if (selectedTab == 3) {
                    Text(stringResource(R.string.technical_details), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.gpu_raw_model, it.gpu.model ?: stringResource(R.string.not_supported)))
                    Text(stringResource(R.string.gpu_driver_value, it.gpu.driver ?: stringResource(R.string.not_supported)))
                    it.gpu.driverPath?.let { path -> Text(stringResource(R.string.gpu_driver_path, path), style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.gpu_utilization_source, it.gpu.utilizationSource ?: it.gpu.utilizationAvailabilityReason ?: stringResource(R.string.gpu_utilization_unavailable)))
                    it.gpu.warnings.forEach { warning -> Text(warning, color = MaterialTheme.colorScheme.error) }
                }
            } ?: LoadingDetail()
            MetricDetailKind.MEMORY -> telemetry?.let {
                MetricCard(stringResource(R.string.memory), memoryValue(it.memory.usedKb, it.memory.totalKb),
                    stringResource(R.string.available_value, it.memory.availableKb?.let(::formatKib) ?: stringResource(R.string.not_supported)),
                    Icons.Default.DataUsage, metric = FluxMetric.MEMORY)
                Text(stringResource(R.string.memory_total_value, it.memory.totalKb?.let(::formatKib) ?: stringResource(R.string.not_supported)))
                Text(stringResource(R.string.memory_used_value, it.memory.usedKb?.let(::formatKib) ?: stringResource(R.string.not_supported)))
                DetailStats(stringResource(R.string.memory_used), state.memoryHistory.mapNotNull { it.second }, "%")
                Sparkline(state.memoryHistory.map { it.second }, Modifier.fillMaxWidth().height(100.dp))
                Text(stringResource(R.string.memory_breakdown), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.cached_value, it.memory.cachedKb?.let(::formatKib) ?: stringResource(R.string.not_supported)))
                Text(stringResource(R.string.buffers_value, it.memory.buffersKb?.let(::formatKib) ?: stringResource(R.string.not_supported)))
                Text(stringResource(R.string.swap_value, it.memory.swapUsedKb?.let(::formatKib) ?: stringResource(R.string.not_supported)))
                Text(stringResource(R.string.zram_value, it.memory.zramBytes?.let(::formatBytes) ?: stringResource(R.string.not_supported)))
                it.memory.zramCompressionRatio?.let { ratio -> Text(stringResource(R.string.zram_compression, format(ratio, "x"))) }
                if (it.memory.swapDevices.isNotEmpty()) Text(stringResource(R.string.swap_devices_value, it.memory.swapDevices.size))
                it.memory.zramOriginalDataBytes?.let { value -> Text(stringResource(R.string.zram_original, formatBytes(value))) }
                it.memory.zramMemoryUsedBytes?.let { value -> Text(stringResource(R.string.zram_used, formatBytes(value))) }
                it.memory.zramCompressedDataBytes?.let { value -> Text(stringResource(R.string.zram_compressed, formatBytes(value))) }
                Text(stringResource(R.string.psi), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.psi_some_value, it.memory.psiSomeAvg10?.let { value -> format(value, "%") } ?: stringResource(R.string.psi_unavailable)))
                Text(stringResource(R.string.psi_full_value, it.memory.psiFullAvg10?.let { value -> format(value, "%") } ?: stringResource(R.string.psi_unavailable)))
                Text(stringResource(R.string.psi_pressure_value, memoryPressureText(it.memory.pressure)))
                var showMemoryTechnical by remember { mutableStateOf(false) }
                TextButton(onClick = { showMemoryTechnical = !showMemoryTechnical }) {
                    Text(stringResource(if (showMemoryTechnical) R.string.hide_technical_details else R.string.show_technical_details))
                }
                if (showMemoryTechnical) it.memory.warnings.forEach { warning ->
                    Text(warning, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } ?: LoadingDetail()
            MetricDetailKind.THERMAL -> telemetry?.let {
                MetricCard(stringResource(R.string.thermal),
                    it.thermal.hottestZone?.temperatureCelsius?.let { value -> format(value, "°C") }
                        ?: stringResource(R.string.not_supported),
                    it.thermal.hottestZone?.let { zone -> stringResource(R.string.hottest_sensor, zone.name) }
                        ?: stringResource(R.string.thermal_status_unavailable),
                    Icons.Default.DeviceThermostat, metric = FluxMetric.THERMAL)
                DetailStats(stringResource(R.string.temperature), state.thermalHistory.mapNotNull { it.second }, "°C")
                Sparkline(state.thermalHistory.map { it.second }, Modifier.fillMaxWidth().height(100.dp))
                Text(thermalReadinessText(it.thermal.eligibility), color = MaterialTheme.colorScheme.primary)
                it.thermal.zones.groupBy { zone -> zone.group }.forEach { (group, zones) ->
                    Text(thermalGroupText(group), style = MaterialTheme.typography.titleMedium)
                    zones.filter { it.temperatureCelsius != null }.forEach { zone ->
                        Text(zone.name + " • " + format(requireNotNull(zone.temperatureCelsius), "°C"))
                    }
                }
                it.thermal.warnings.forEach { warning -> Text(warning, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                var showRaw by remember { mutableStateOf(false) }
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(stringResource(if (showRaw) R.string.hide_technical_details else R.string.show_technical_details))
                }
                if (showRaw) {
                    it.thermal.zones.forEach { zone ->
                        Text(zone.name + " • " + zone.sourcePath, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } ?: LoadingDetail()
            MetricDetailKind.ROOT -> {
                val available = state.root is RootState.Available
                MetricCard(
                    stringResource(R.string.root_status),
                    rootText(state.root),
                    if (available) stringResource(R.string.root_full_access) else stringResource(R.string.root_capability_unavailable),
                    Icons.Default.Security,
                    metric = if (available) FluxMetric.SUCCESS else FluxMetric.UNAVAILABLE,
                )
                Text(stringResource(R.string.root_detail_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MetricDetailKind.FLUX -> {
                MetricCard(stringResource(R.string.flux_status), stringResource(if (state.flux.installed) R.string.installed else R.string.not_installed), state.flux.versionName, Icons.Default.Bolt)
                state.flux.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            MetricDetailKind.SYNTHESIS -> {
                val value = state.synthesis as? SynthesisReadResult.Success
                MetricCard(stringResource(R.string.synthesis_status), value?.let { statusText(it.snapshot.freshness.name) } ?: stringResource(R.string.unavailable), "SynthesisCore", Icons.Default.Sync)
            }
            MetricDetailKind.PROFILE -> MetricCard(stringResource(R.string.active_profile), state.flux.activeProfile ?: stringResource(R.string.not_supported), state.flux.versionName, Icons.Default.Tune)
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun DetailTabs(labels: List<Int>, selected: Int, onSelected: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FluxSpacing.compactGap)) {
        labels.forEachIndexed { index, label ->
            FilterChip(selected = selected == index, onClick = { onSelected(index) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun DetailStats(title: String, values: List<Double>, unit: String) {
    if (values.isEmpty()) {
        Text(title + " • " + stringResource(R.string.not_supported), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text(
        stringResource(
            R.string.stats_summary,
            title,
            format(values.minOrNull() ?: 0.0, unit),
            format(values.average(), unit),
            format(values.maxOrNull() ?: 0.0, unit),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun thermalReadinessText(value: com.febricahyaa.fluxlab.model.ThermalEligibility): String = when (value) {
    com.febricahyaa.fluxlab.model.ThermalEligibility.READY -> stringResource(R.string.thermal_ready)
    com.febricahyaa.fluxlab.model.ThermalEligibility.READY_WITH_WARNING -> stringResource(R.string.thermal_ready_warning)
    com.febricahyaa.fluxlab.model.ThermalEligibility.COOLDOWN_RECOMMENDED -> stringResource(R.string.thermal_cooldown)
    com.febricahyaa.fluxlab.model.ThermalEligibility.BLOCKED_BY_THERMAL_CONDITION -> stringResource(R.string.thermal_blocked)
    com.febricahyaa.fluxlab.model.ThermalEligibility.THERMAL_STATUS_UNAVAILABLE -> stringResource(R.string.thermal_status_unavailable)
}

@Composable
private fun confidenceText(value: com.febricahyaa.fluxlab.model.IdentityConfidence): String = when (value) {
    com.febricahyaa.fluxlab.model.IdentityConfidence.HIGH -> stringResource(R.string.confidence_high)
    com.febricahyaa.fluxlab.model.IdentityConfidence.MEDIUM -> stringResource(R.string.confidence_medium)
    com.febricahyaa.fluxlab.model.IdentityConfidence.LOW -> stringResource(R.string.confidence_low)
    com.febricahyaa.fluxlab.model.IdentityConfidence.UNAVAILABLE -> stringResource(R.string.confidence_unavailable)
}

@Composable
private fun confidenceText(value: com.febricahyaa.fluxlab.model.Confidence): String = when (value) {
    com.febricahyaa.fluxlab.model.Confidence.INCONCLUSIVE -> stringResource(R.string.inconclusive)
    com.febricahyaa.fluxlab.model.Confidence.POSSIBLE_CHANGE -> stringResource(R.string.possible_change)
    com.febricahyaa.fluxlab.model.Confidence.LIKELY_CHANGE -> stringResource(R.string.likely_change)
}

@Composable
private fun thermalGroupText(value: com.febricahyaa.fluxlab.model.ThermalSensorGroup): String = when (value) {
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.CPU -> stringResource(R.string.thermal_group_cpu)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.GPU -> stringResource(R.string.thermal_group_gpu)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.BATTERY -> stringResource(R.string.thermal_group_battery)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.CHARGER -> stringResource(R.string.thermal_group_charger)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.MODEM -> stringResource(R.string.thermal_group_modem)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.WIFI -> stringResource(R.string.thermal_group_wifi)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.SKIN -> stringResource(R.string.thermal_group_skin)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.PMIC -> stringResource(R.string.thermal_group_pmic)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.CAMERA -> stringResource(R.string.thermal_group_camera)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.DISPLAY -> stringResource(R.string.thermal_group_display)
    com.febricahyaa.fluxlab.model.ThermalSensorGroup.OTHER -> stringResource(R.string.thermal_group_other)
}

@Composable
fun BatteryDetailScreen(model: AppViewModel, onBack: () -> Unit) {
    val state by model.dashboard.collectAsStateWithLifecycle()
    val telemetry = state.telemetry
    DetailPage(stringResource(R.string.battery), onBack) {
        if (telemetry == null) {
            LoadingDetail()
        } else {
            val battery = telemetry.battery
            MetricCard(
                stringResource(R.string.battery),
                battery.levelPercent?.let { it.toString() + "%" } ?: stringResource(R.string.unknown),
                batteryPowerText(telemetry),
                Icons.Default.BatteryChargingFull,
                metric = FluxMetric.BATTERY,
            )
            Text(stringResource(R.string.battery_overview), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.charging_state_value, chargingStateText(battery.chargingState)))
            battery.plugType?.let { Text(stringResource(R.string.power_source_value, it)) }
            battery.diagnostics.chargeTimeRemainingMs?.let { Text(stringResource(R.string.charge_time_value, it / 60_000L)) }
            battery.diagnostics.maximumChargingCurrentMicroamps?.let { Text(stringResource(R.string.max_charge_current_value, format(it / 1_000.0, "mA"))) }
            battery.diagnostics.maximumChargingVoltageMicrovolts?.let { Text(stringResource(R.string.max_charge_voltage_value, format(it / 1_000_000.0, "V"))) }
            battery.diagnostics.technology?.let { Text(stringResource(R.string.technology_value, it)) }

            val capacity = battery.diagnostics.capacity
            Text(stringResource(R.string.capacity), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.current_charge_value, capacity.currentCharge.normalizedMilliAmpHours?.let { format(it, "mAh") } ?: stringResource(R.string.not_supported)))
            Text(stringResource(R.string.design_capacity_value, capacity.designCapacity.normalizedMilliAmpHours?.let { format(it, "mAh") } ?: stringResource(R.string.not_supported)))
            Text(stringResource(R.string.full_charge_capacity_value, capacity.fullChargeCapacity.normalizedMilliAmpHours?.let { format(it, "mAh") } ?: stringResource(R.string.not_supported)))
            Text(stringResource(R.string.estimated_health_value, capacity.estimatedSoHPercent?.let { format(it, "%") } ?: stringResource(R.string.numeric_health_unavailable)))
            capacity.sohWarning?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Text(stringResource(R.string.android_health), style = MaterialTheme.typography.titleMedium)
            Text(batteryHealthText(battery.diagnostics.health))
            Text(stringResource(R.string.cycle_count_value, battery.diagnostics.cycleCount?.toString() ?: stringResource(R.string.cycle_count_unavailable)))

            Text(stringResource(R.string.electrical_telemetry), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.current_value, battery.normalizedCurrentAmps?.let { format(it * 1_000.0, "mA") } ?: stringResource(R.string.not_supported)))
            Text(stringResource(R.string.average_current_value, battery.averageCurrentMicroamps?.let { format(it / 1_000.0, "mA") } ?: stringResource(R.string.not_supported)))
            Text(stringResource(R.string.voltage_value, battery.normalizedVoltageVolts?.let { format(it, "V") } ?: stringResource(R.string.not_supported)))
            Text(stringResource(R.string.power_value, battery.calculatedPowerWatts?.let { format(it, "W") } ?: stringResource(R.string.power_unavailable)))
            Text(stringResource(R.string.temperature_value, battery.temperatureCelsius?.let { format(it, "°C") } ?: stringResource(R.string.not_supported)))

            Text(stringResource(R.string.battery_history), style = MaterialTheme.typography.titleMedium)
            val graphSamplesFor: (BatteryGraph) -> List<Double?> = { option ->
                when (option) {
                    BatteryGraph.POWER -> state.telemetryHistory.map { it.battery.calculatedPowerWatts }
                    BatteryGraph.CURRENT -> state.telemetryHistory.map { it.battery.normalizedCurrentAmps?.times(1_000.0) }
                    BatteryGraph.VOLTAGE -> state.telemetryHistory.map { it.battery.normalizedVoltageVolts }
                    BatteryGraph.TEMPERATURE -> state.telemetryHistory.map { it.battery.temperatureCelsius }
                }
            }
            var graph by remember { mutableStateOf(BatteryGraph.POWER) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BatteryGraph.entries.forEach { option ->
                    val available = graphSamplesFor(option).count { it != null } >= 2
                    FilterChip(selected = graph == option, enabled = available, onClick = { graph = option }, label = { Text(batteryGraphLabel(option)) })
                }
            }
            val graphSamples = graphSamplesFor(graph)
            if (graphSamples.count { it != null } < 2) {
                Text(stringResource(R.string.graph_unavailable_reason), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Sparkline(graphSamples, Modifier.fillMaxWidth().height(100.dp))
                DetailStats(batteryGraphLabel(graph), graphSamples.mapNotNull { it }, if (graph == BatteryGraph.CURRENT) "mA" else graph.unit)
            }
            Text(stringResource(R.string.battery_percentage_history), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.charging_state_timeline), style = MaterialTheme.typography.titleMedium)
            BatteryStateTimeline(state.telemetryHistory.map { it.battery.chargingState })
            Sparkline(state.batteryHistory.map { it.second }, Modifier.fillMaxWidth().height(80.dp))
            battery.powerWarnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            battery.diagnostics.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun BatteryStateTimeline(states: List<com.febricahyaa.fluxlab.model.ChargingState>) {
    val samples = states.takeLast(60)
    if (samples.isEmpty()) {
        Text(stringResource(R.string.graph_unavailable_reason), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val timelineDescription = stringResource(R.string.charging_state_timeline)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        Modifier.fillMaxWidth().height(24.dp).semantics {
            contentDescription = timelineDescription
        },
    ) {
        val segmentWidth = size.width / samples.size
        samples.forEachIndexed { index, state ->
            drawRect(
                color = when (state) {
                    com.febricahyaa.fluxlab.model.ChargingState.CHARGING -> primaryColor
                    com.febricahyaa.fluxlab.model.ChargingState.FULL -> tertiaryColor
                    com.febricahyaa.fluxlab.model.ChargingState.DISCHARGING -> secondaryColor
                    else -> outlineColor
                },
                topLeft = Offset(index * segmentWidth, 0f),
                size = androidx.compose.ui.geometry.Size(segmentWidth - 1f, size.height),
            )
        }
    }
}
private enum class BatteryGraph(val unit: String) { POWER("W"), CURRENT("mA"), VOLTAGE("V"), TEMPERATURE("°C") }

@Composable
private fun batteryGraphLabel(value: BatteryGraph): String = when (value) {
    BatteryGraph.POWER -> stringResource(R.string.power)
    BatteryGraph.CURRENT -> stringResource(R.string.current)
    BatteryGraph.VOLTAGE -> stringResource(R.string.voltage)
    BatteryGraph.TEMPERATURE -> stringResource(R.string.temperature)
}

@Composable
private fun batteryHealthText(value: com.febricahyaa.fluxlab.model.BatteryHealthState): String = when (value) {
    com.febricahyaa.fluxlab.model.BatteryHealthState.GOOD -> stringResource(R.string.health_good)
    com.febricahyaa.fluxlab.model.BatteryHealthState.OVERHEAT -> stringResource(R.string.health_overheat)
    com.febricahyaa.fluxlab.model.BatteryHealthState.DEAD -> stringResource(R.string.health_dead)
    com.febricahyaa.fluxlab.model.BatteryHealthState.OVER_VOLTAGE -> stringResource(R.string.health_over_voltage)
    com.febricahyaa.fluxlab.model.BatteryHealthState.UNSPECIFIED_FAILURE -> stringResource(R.string.health_unspecified_failure)
    com.febricahyaa.fluxlab.model.BatteryHealthState.COLD -> stringResource(R.string.health_cold)
    com.febricahyaa.fluxlab.model.BatteryHealthState.UNKNOWN -> stringResource(R.string.health_unknown)
}

@Composable
fun StorageDetailScreen(model: AppViewModel, onBack: () -> Unit) {
    val state by model.dashboard.collectAsStateWithLifecycle()
    val storage = state.telemetry?.storage
    val sessions by model.sessions.collectAsStateWithLifecycle()
    DetailPage(stringResource(R.string.storage), onBack) {
        if (storage == null) {
            LoadingDetail()
        } else {
            MetricCard(
                stringResource(R.string.storage),
                storageTypeText(storage.identity.storageType) + " • " +
                    (storage.identity.totalCapacityBytes?.let(::formatBytes) ?: stringResource(R.string.unknown)),
                storage.identity.storageModel ?: stringResource(R.string.storage_identity_unavailable),
                Icons.Default.Storage,
                metric = FluxMetric.STORAGE,
            )
            Text(stringResource(R.string.identity), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.storage_type_value, storageTypeText(storage.identity.storageType)))
            storage.identity.storageVendor?.let { Text(stringResource(R.string.vendor_value, it)) }
            storage.identity.storageRevision?.let { Text(stringResource(R.string.revision_value, it)) }
            Text(stringResource(R.string.identity_confidence_value, confidenceText(storage.identity.identityConfidence)))
            if (storage.identity.storageType == com.febricahyaa.fluxlab.model.StorageType.UNKNOWN) {
                Text(stringResource(R.string.storage_unknown_reason), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.capacity), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.total_value, storage.identity.totalCapacityBytes?.let(::formatBytes) ?: stringResource(R.string.not_supported)))
            Text(stringResource(R.string.available_value, storage.identity.availableCapacityBytes?.let(::formatBytes) ?: stringResource(R.string.not_supported)))
            storage.identity.physicalNominalCapacityBytes?.let { Text(stringResource(R.string.physical_capacity_value, formatBytes(it))) }
            Text(stringResource(R.string.capacity_source_value, storage.identity.capacitySource ?: stringResource(R.string.not_supported)))
            Text(stringResource(R.string.filesystem_value, storage.identity.filesystem ?: stringResource(R.string.not_supported)))
            storage.identity.mountPoint?.let { Text(stringResource(R.string.mount_value, it)) }

            Text(stringResource(R.string.health_and_lifetime), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.health_availability_value, storageHealthAvailabilityText(storage.health.availability)))
            if (storage.health.availability == com.febricahyaa.fluxlab.model.StorageHealthAvailability.AVAILABLE) {
                Text(stringResource(R.string.lifetime_a_value, lifetimeText(storage.health.lifetimeA)))
                Text(stringResource(R.string.lifetime_b_value, lifetimeText(storage.health.lifetimeB)))
                storage.health.preEndOfLife?.let { Text(stringResource(R.string.pre_eol_value, it)) }
            } else {
                Text(stringResource(R.string.health_not_exposed), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.storage_performance_separate), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.storage_current_io), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.storage_current_io_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.storage_benchmark_results), style = MaterialTheme.typography.titleMedium)
            val storageResults = sessions.flatMap { session ->
                session.workloadResults.filter { result ->
                    result.kind == com.febricahyaa.fluxlab.model.WorkloadKind.STORAGE_READ ||
                        result.kind == com.febricahyaa.fluxlab.model.WorkloadKind.STORAGE_WRITE ||
                        result.kind == com.febricahyaa.fluxlab.model.WorkloadKind.STORAGE_FSYNC
                }
            }
            if (storageResults.isEmpty()) {
                Text(stringResource(R.string.no_storage_benchmark_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                storageResults.take(6).forEach { result ->
                    Text(workloadText(result.kind) + " • " + engineeringFormat(result.statistics.median, result.unit))
                }
            }
            Text(stringResource(R.string.storage_methodology), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.storage_methodology_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.technical_topology), style = MaterialTheme.typography.titleMedium)
            var showTopology by remember { mutableStateOf(false) }
            TextButton(onClick = { showTopology = !showTopology }) {
                Text(stringResource(if (showTopology) R.string.hide_technical_details else R.string.show_technical_details))
            }
            if (showTopology) {
                storage.identity.physicalBackingDevice?.let { Text(stringResource(R.string.physical_device_value, it)) }
                storage.identity.topologySteps.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                storage.identity.transportEvidence.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                storage.identity.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                storage.health.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun storageHealthAvailabilityText(value: com.febricahyaa.fluxlab.model.StorageHealthAvailability): String = when (value) {
    com.febricahyaa.fluxlab.model.StorageHealthAvailability.AVAILABLE -> stringResource(R.string.health_available)
    com.febricahyaa.fluxlab.model.StorageHealthAvailability.DESCRIPTOR_UNAVAILABLE -> stringResource(R.string.health_descriptor_unavailable)
    com.febricahyaa.fluxlab.model.StorageHealthAvailability.PERMISSION_DENIED -> stringResource(R.string.permission_denied)
    com.febricahyaa.fluxlab.model.StorageHealthAvailability.UNSUPPORTED -> stringResource(R.string.not_supported)
    com.febricahyaa.fluxlab.model.StorageHealthAvailability.MALFORMED -> stringResource(R.string.malformed)
    com.febricahyaa.fluxlab.model.StorageHealthAvailability.UNKNOWN -> stringResource(R.string.unknown)
}

@Composable
private fun lifetimeText(value: com.febricahyaa.fluxlab.model.StorageLifetimeEstimate): String =
    if (value.rangeStartPercent != null && value.rangeEndPercent != null) {
        value.rangeStartPercent.toString() + "–" + value.rangeEndPercent + "%"
    } else {
        stringResource(R.string.not_supported)
    }

@Composable
fun ActiveBenchmarkScreen(model: AppViewModel, onBack: () -> Unit) {
    val progress by model.benchmarkProgress.collectAsStateWithLifecycle()
    val state by model.dashboard.collectAsStateWithLifecycle()
    var cancelRequested by remember { mutableStateOf(false) }
    DetailPage(progress.preset?.let { presetLabel(it) } ?: stringResource(R.string.active_benchmark), onBack) {
        val fraction = if (progress.totalSteps > 0) ((progress.completedSteps.toFloat() + progress.currentRepetition.toFloat() / progress.totalRepetitions.coerceAtLeast(1)) / progress.totalSteps) else 0f
        FluxGauge(fraction.coerceIn(0f, 1f), fluxMetricColor(FluxMetric.CPU), Modifier.size(128.dp), "${(fraction * 100f).toInt()}%")
        Text(stageText(progress.stage), style = MaterialTheme.typography.headlineSmall)
        progress.workload?.let { Text(stringResource(R.string.workload_current, workloadText(it)), style = MaterialTheme.typography.titleMedium) }
        Text(stringResource(R.string.workload_progress, progress.completedSteps, progress.totalSteps))
        progress.currentRepetition.takeIf { it > 0 }?.let {
            Text(stringResource(R.string.repetition_progress, it, progress.totalRepetitions))
        }
        progress.elapsedMs.takeIf { it > 0L }?.let { Text(stringResource(R.string.elapsed_value, it / 1_000L)) }
        progress.estimatedRemainingMs?.let { Text(stringResource(R.string.remaining_value, it / 1_000L)) }
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        val totalWorkUnits = progress.totalWorkUnits
        val workloadFraction = when {
            totalWorkUnits != null && totalWorkUnits > 0L -> progress.completedWorkUnits.toFloat() / totalWorkUnits.toFloat()
            progress.totalRepetitions > 0 -> progress.currentRepetition.toFloat() / progress.totalRepetitions
            else -> fraction
        }
        progress.workload?.let { WorkloadVisual(it, progress.stage, progress.visualMode, workloadFraction, progress.activeThreadCount) }
        Text(stringResource(R.string.workload_timeline), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.workload_completed_count, progress.completedSteps, progress.totalWorkloads))
        Text(stringResource(R.string.workload_pending_count, (progress.totalWorkloads - progress.completedSteps).coerceAtLeast(0)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        MetricCard(
            stringResource(R.string.cpu),
            state.telemetry?.cpu?.totalUsagePercent?.let { format(it, "%") } ?: stringResource(R.string.not_supported),
            stringResource(R.string.live_sample),
            Icons.Default.Memory,
            metric = FluxMetric.CPU,
        )
        MetricCard(
            stringResource(R.string.thermal),
            state.telemetry?.thermal?.hottestZone?.temperatureCelsius?.let { format(it, "°C") } ?: stringResource(R.string.not_supported),
            thermalReadinessText(state.telemetry?.thermal?.eligibility ?: com.febricahyaa.fluxlab.model.ThermalEligibility.THERMAL_STATUS_UNAVAILABLE),
            Icons.Default.DeviceThermostat,
            metric = FluxMetric.THERMAL,
        )
        MetricCard(
            stringResource(R.string.gpu),
            state.telemetry?.gpu?.utilizationPercent?.let { format(it, "%") }
                ?: state.telemetry?.gpu?.currentFrequencyHz?.let { format(it / 1_000_000.0, "MHz") }
                ?: stringResource(R.string.not_supported),
            state.telemetry?.gpu?.utilizationPercent?.let { stringResource(R.string.live_sample) }
                ?: stringResource(R.string.gpu_utilization_unavailable),
            Icons.Default.GraphicEq,
            metric = FluxMetric.GPU,
        )
        MetricCard(
            stringResource(R.string.power),
            state.telemetry?.battery?.calculatedPowerWatts?.let { format(it, "W") } ?: stringResource(R.string.power_unavailable),
            state.telemetry?.battery?.let { chargingStateText(it.chargingState) } ?: stringResource(R.string.power_state_unknown),
            Icons.Default.BatteryChargingFull,
            metric = FluxMetric.BATTERY,
        )
        OutlinedButton(onClick = { cancelRequested = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel_test))
        }
    }
    if (cancelRequested) {
        AlertDialog(
            onDismissRequest = { cancelRequested = false },
            title = { Text(stringResource(R.string.cancel_benchmark_title)) },
            text = { Text(stringResource(R.string.cancel_benchmark_body)) },
            confirmButton = { TextButton(onClick = { model.cancelQuickTest(); cancelRequested = false }) { Text(stringResource(R.string.confirm_delete)) } },
            dismissButton = { TextButton(onClick = { cancelRequested = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun WorkloadVisual(
    kind: com.febricahyaa.fluxlab.model.WorkloadKind,
    stage: BenchmarkStage,
    mode: com.febricahyaa.fluxlab.model.BenchmarkVisualMode,
    progress: Float,
    activeThreadCount: Int?,
) {
    if (stage != BenchmarkStage.RUNNING || mode == com.febricahyaa.fluxlab.model.BenchmarkVisualMode.REDUCED) {
        Text(stringResource(R.string.reduced_visuals), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val color = MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(84.dp).padding(12.dp)) {
            val lanes = when (kind) {
                com.febricahyaa.fluxlab.model.WorkloadKind.CPU_MULTI_THREADED -> activeThreadCount?.coerceIn(2, 16) ?: 1
                com.febricahyaa.fluxlab.model.WorkloadKind.MEMORY_COPY,
                com.febricahyaa.fluxlab.model.WorkloadKind.MEMORY_FILL,
                com.febricahyaa.fluxlab.model.WorkloadKind.STORAGE_READ,
                com.febricahyaa.fluxlab.model.WorkloadKind.STORAGE_WRITE -> 3
                else -> 1
            }
            val bounded = progress.coerceIn(0f, 1f)
            when (kind) {
                com.febricahyaa.fluxlab.model.WorkloadKind.CPU_FLOATING_POINT -> {
                    val points = (0..24).map { index ->
                        val x = index / 24f * size.width
                        val y = size.height / 2f + kotlin.math.sin(index / 24f * Math.PI * 4.0).toFloat() * size.height / 3f
                        Offset(x, y)
                    }
                    points.zipWithNext().forEach { (from, to) -> drawLine(color, from, to, strokeWidth = 3f) }
                    drawCircle(color, 6f, Offset(size.width * bounded, size.height / 2f))
                }
                com.febricahyaa.fluxlab.model.WorkloadKind.CPU_INTEGER,
                com.febricahyaa.fluxlab.model.WorkloadKind.MEMORY_LATENCY,
                com.febricahyaa.fluxlab.model.WorkloadKind.STORAGE_FSYNC -> {
                    drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 3f)
                    drawCircle(color, 7f, Offset(size.width * bounded, size.height / 2f))
                }
                else -> repeat(lanes) { lane ->
                    val y = (lane + 1) * size.height / (lanes + 1)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f)
                    drawCircle(color, 6f, Offset(size.width * bounded, y))
                }
            }
        }
    }
}

@Composable
fun SessionDetailScreen(model: AppViewModel, onBack: () -> Unit, sessionId: String? = null) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    DetailPage(stringResource(R.string.latest_session), onBack) {
        sessions.firstOrNull { it.id == sessionId }?.let { session ->
            MetricCard(sessionLabel(session), sessionStatusText(session.status), presetLabel(session.environment.presetConfiguration.preset), Icons.Default.Analytics)
            session.workloadResults.forEach { result -> Text(workloadText(result.kind) + " • " + engineeringFormat(result.statistics.median, result.unit)) }
        } ?: sessions.firstOrNull()?.let { session ->
            MetricCard(sessionLabel(session), sessionStatusText(session.status), presetLabel(session.environment.presetConfiguration.preset), Icons.Default.Analytics)
            session.workloadResults.forEach { result -> Text(workloadText(result.kind) + " • " + engineeringFormat(result.statistics.median, result.unit)) }
        } ?: Text(stringResource(R.string.no_sessions))
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun DetailPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                }
            },
        )
        LazyColumn(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { content() }
        }
    }
}

@Composable private fun LoadingDetail() { CircularProgressIndicator() }

@Composable
private fun presetLabel(preset: BenchmarkPreset): String = stringResource(when (preset) {
    BenchmarkPreset.QUICK -> R.string.quick_label
    BenchmarkPreset.STANDARD -> R.string.standard_label
    BenchmarkPreset.EXTENDED -> R.string.extended_label
})

@Composable
private fun presetPurpose(preset: BenchmarkPreset): String = stringResource(when (preset) {
    BenchmarkPreset.QUICK -> R.string.preset_purpose_quick
    BenchmarkPreset.STANDARD -> R.string.preset_purpose_standard
    BenchmarkPreset.EXTENDED -> R.string.preset_purpose_extended
})

@Composable
private fun durationText(milliseconds: Long): String = stringResource(R.string.duration_seconds, (milliseconds / 1_000L).toInt())

@Composable
private fun presetCta(preset: BenchmarkPreset): String = stringResource(when (preset) {
    BenchmarkPreset.QUICK -> R.string.quick_cta
    BenchmarkPreset.STANDARD -> R.string.standard_cta
    BenchmarkPreset.EXTENDED -> R.string.extended_cta
})

@Composable
private fun stageText(stage: BenchmarkStage): String = stringResource(when (stage) {
    BenchmarkStage.IDLE -> R.string.stage_idle
    BenchmarkStage.PREFLIGHT -> R.string.stage_preflight
    BenchmarkStage.AWAITING_CONFIRMATION -> R.string.stage_awaiting_confirmation
    BenchmarkStage.COUNTDOWN -> R.string.stage_countdown
    BenchmarkStage.WARMUP -> R.string.stage_warmup
    BenchmarkStage.RUNNING -> R.string.running
    BenchmarkStage.INTER_WORKLOAD_COOLDOWN -> R.string.stage_inter_cooldown
    BenchmarkStage.FINALIZING -> R.string.stage_finalizing
    BenchmarkStage.COMPLETED -> R.string.completed
    BenchmarkStage.CANCELLING -> R.string.stage_cancelling
    BenchmarkStage.CANCELLED -> R.string.stage_cancelled
    BenchmarkStage.FAILED -> R.string.stage_failed
})

@Composable
private fun workloadText(kind: com.febricahyaa.fluxlab.model.WorkloadKind): String = stringResource(when (kind) {
    com.febricahyaa.fluxlab.model.WorkloadKind.CPU_INTEGER -> R.string.workload_cpu_integer
    com.febricahyaa.fluxlab.model.WorkloadKind.CPU_FLOATING_POINT -> R.string.workload_cpu_float
    com.febricahyaa.fluxlab.model.WorkloadKind.CPU_MULTI_THREADED -> R.string.workload_cpu_multi
    com.febricahyaa.fluxlab.model.WorkloadKind.MEMORY_COPY -> R.string.workload_memory_copy
    com.febricahyaa.fluxlab.model.WorkloadKind.MEMORY_FILL -> R.string.workload_memory_fill
    com.febricahyaa.fluxlab.model.WorkloadKind.MEMORY_LATENCY -> R.string.workload_memory_latency
    com.febricahyaa.fluxlab.model.WorkloadKind.STORAGE_READ -> R.string.workload_storage_read
    com.febricahyaa.fluxlab.model.WorkloadKind.STORAGE_WRITE -> R.string.workload_storage_write
    com.febricahyaa.fluxlab.model.WorkloadKind.STORAGE_FSYNC -> R.string.workload_storage_fsync
})

private fun formatKib(value: Long): String = String.format(Locale.getDefault(), "%.1f GiB", value / 1_048_576.0)

private fun engineeringFormat(value: Double, unit: String): String {
    val divisor = when { unit == "ops/s" && value >= 1_000_000_000 -> 1_000_000_000.0; unit == "ops/s" && value >= 1_000_000 -> 1_000_000.0; unit == "ops/s" && value >= 1_000 -> 1_000.0; unit == "MiB/s" && value >= 1024 -> 1024.0; else -> 1.0 }
    val suffix = when { divisor == 1_000_000_000.0 -> "Gops/s"; divisor == 1_000_000.0 -> "Mops/s"; divisor == 1_000.0 && unit == "ops/s" -> "Kops/s"; divisor == 1024.0 -> "GiB/s"; else -> unit }
    return String.format(Locale.getDefault(), "%.2f %s", value / divisor, suffix)
}
