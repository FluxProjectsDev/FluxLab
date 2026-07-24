package com.febricahyaa.fluxlab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun AboutLegalHubScreen(model: AppViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val state by model.aboutLegal.collectAsStateWithLifecycle()
    AboutScaffold(stringResource(R.string.about_legal), onBack) {
        AboutHero(state.version)
        AboutSectionTitle(stringResource(R.string.about_designer_section))
        AboutDesignerCard(onClick = { onNavigate("about/credits") })
        AboutSectionTitle(stringResource(R.string.about_app_information))
        AboutActionRow(stringResource(R.string.about_version_title), stringResource(R.string.about_version_subtitle), Icons.Default.Info) { onNavigate("about/version") }
        AboutActionRow(stringResource(R.string.about_update_title), updateHubSubtitle(state.update), Icons.Default.Update) { onNavigate("about/update") }
        AboutActionRow(stringResource(R.string.about_changelog_title), stringResource(R.string.about_changelog_subtitle), Icons.Default.Assessment) { onNavigate("about/changelog") }
        AboutSectionTitle(stringResource(R.string.about_legal_information))
        AboutActionRow(stringResource(R.string.about_licenses_title), stringResource(R.string.about_licenses_subtitle), Icons.Default.Code) { onNavigate("about/licenses") }
        AboutActionRow(stringResource(R.string.about_privacy_title), stringResource(R.string.about_privacy_subtitle), Icons.Default.Security) { onNavigate("about/privacy") }
        AboutActionRow(stringResource(R.string.about_terms_title), stringResource(R.string.about_terms_subtitle), Icons.Default.Gavel) { onNavigate("about/terms") }
        AboutSectionTitle(stringResource(R.string.about_support_section))
        AboutSupportCard(onClick = { onNavigate("about/support") })
        Text(
            stringResource(R.string.about_footer, state.version?.versionName ?: stringResource(R.string.about_unavailable_value)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun VersionInformationScreen(model: AppViewModel, onBack: () -> Unit) {
    val state by model.aboutLegal.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val info = state.version
    AboutScaffold(stringResource(R.string.about_version_title), onBack) {
        if (info == null) {
            AboutUnavailableCard(stringResource(R.string.about_version_unavailable))
        } else {
            AboutHero(info)
            val versionTitle = stringResource(R.string.about_version_title)
            val versionSummary = listOf(
                stringResource(R.string.about_version_line, info.versionName ?: stringResource(R.string.about_unavailable_value)),
                stringResource(R.string.about_version_code_line, info.versionCode?.toString() ?: stringResource(R.string.about_unavailable_value)),
                stringResource(R.string.about_package_line, info.packageName),
                stringResource(R.string.about_build_type_line, info.buildType),
            ).joinToString("\n")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(versionTitle, versionSummary))
                    copied = true
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.about_copy_version))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (copied) R.string.about_copied else R.string.about_copy_version))
                }
                OutlinedButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, versionSummary) }
                    context.startActivity(Intent.createChooser(share, null))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, stringResource(R.string.about_share_version))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.about_share_version))
                }
            }
            AboutSectionTitle(stringResource(R.string.about_runtime_information))
            AboutInfoCard(listOf(
                stringResource(R.string.about_app_name_label) to info.appName,
                stringResource(R.string.about_version_name_label) to (info.versionName ?: stringResource(R.string.about_unavailable_value)),
                stringResource(R.string.about_version_code_label) to (info.versionCode?.toString() ?: stringResource(R.string.about_unavailable_value)),
                stringResource(R.string.about_build_type_label) to info.buildType,
                stringResource(R.string.about_package_label) to info.packageName,
                stringResource(R.string.about_install_time_label) to formatAboutTimestamp(info.installTimeEpochMs),
                stringResource(R.string.about_update_time_label) to formatAboutTimestamp(info.updateTimeEpochMs),
                stringResource(R.string.about_language_label) to aboutLanguageLabel(settings.language),
                stringResource(R.string.about_appearance_label) to aboutThemeLabel(settings.theme),
                stringResource(R.string.about_color_style_label) to stringResource(if (settings.colorStyle == ColorStyle.FLUX) R.string.color_style_flux else R.string.color_style_dynamic),
            ))
            AboutSectionTitle(stringResource(R.string.about_advanced_information))
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (showAdvanced) R.string.about_hide_advanced else R.string.about_show_advanced))
            }
            if (showAdvanced) AboutInfoCard(listOf(
                stringResource(R.string.about_supported_abis_label) to info.supportedAbis.joinToString(", ").ifBlank { stringResource(R.string.about_unavailable_value) },
                stringResource(R.string.about_min_sdk_label) to (info.minSdk?.toString() ?: stringResource(R.string.about_unavailable_value)),
                stringResource(R.string.about_target_sdk_label) to (info.targetSdk?.toString() ?: stringResource(R.string.about_unavailable_value)),
                stringResource(R.string.about_build_channel_label) to stringResource(R.string.about_build_channel_value),
            ))
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun ChangelogScreen(model: AppViewModel, onBack: () -> Unit) {
    val state by model.aboutLegal.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    var expandedVersions by remember { mutableStateOf(emptySet<String>()) }
    AboutScaffold(stringResource(R.string.about_changelog_title), onBack) {
        AboutHeaderCard(stringResource(R.string.about_changelog_header), stringResource(R.string.about_changelog_subtitle), Icons.Default.Assessment, FluxMetric.CPU)
        if (ChangelogCatalog.entries.isEmpty()) {
            AboutUnavailableCard(stringResource(R.string.about_changelog_empty))
        } else ChangelogCatalog.entries.forEach { entry ->
            val expanded = entry.versionName in expandedVersions
            val isCurrent = entry.versionName == state.version?.versionName
            Card(onClick = { expandedVersions = if (expanded) expandedVersions - entry.versionName else expandedVersions + entry.versionName }, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button; contentDescription = entry.versionName }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = FluxShapes.material.medium) {
                Column(Modifier.padding(FluxSpacing.cardInternalPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(entry.versionNameResource), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(listOfNotNull(entry.releaseDate ?: stringResource(R.string.about_release_date_unavailable), changelogChannelLabel(entry.channel)).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isCurrent) AboutBadge(stringResource(R.string.about_current_badge), FluxMetric.SUCCESS)
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, stringResource(R.string.about_expand_release))
                    }
                    if (expanded) ChangelogCategory.entries.forEach { category ->
                        val useIndonesian = settings.language == LanguageSetting.INDONESIAN ||
                            (settings.language == LanguageSetting.SYSTEM && Locale.getDefault().language.equals("id", ignoreCase = true))
                        val localizedCategories = if (useIndonesian) entry.indonesianCategories else entry.categories
                        localizedCategories[category]?.takeIf { it.isNotEmpty() }?.let { bullets ->
                            Text(changelogCategoryLabel(category), style = MaterialTheme.typography.titleSmall, color = fluxMetricColor(FluxMetric.CPU))
                            bullets.forEach { bullet -> Text("• ${stringResource(bullet)}", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun UpdateInformationScreen(model: AppViewModel, onBack: () -> Unit) {
    val state by model.aboutLegal.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTechnicalFailure by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.checkForUpdates() }
    AboutScaffold(stringResource(R.string.about_update_title), onBack) {
        AboutHeaderCard(stringResource(R.string.about_update_header), stringResource(R.string.about_update_subtitle), Icons.Default.Update, when (state.update) {
            is UpdateCheckState.UpdateAvailable -> FluxMetric.SUCCESS
            is UpdateCheckState.Failed -> FluxMetric.WARNING
            else -> FluxMetric.CPU
        })
        when (val update = state.update) {
            UpdateCheckState.Idle -> {
                AboutStatusCard(stringResource(R.string.about_update_idle), Icons.Default.Refresh, FluxMetric.CPU)
                Button(onClick = model::checkForUpdates, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_retry)) }
            }
            UpdateCheckState.Checking -> AboutStatusCard(stringResource(R.string.about_update_checking), Icons.Default.Refresh, FluxMetric.CPU, true)
            is UpdateCheckState.UpdateAvailable -> {
                val release = update.release
                AboutStatusCard(stringResource(R.string.about_update_available), Icons.Default.Update, FluxMetric.SUCCESS)
                AboutInfoCard(listOf(
                    stringResource(R.string.about_current_version_label) to (state.version?.versionName ?: stringResource(R.string.about_unavailable_value)),
                    stringResource(R.string.about_latest_version_label) to release.latestVersionName,
                    stringResource(R.string.about_release_date_label) to (release.releaseDate ?: stringResource(R.string.about_release_date_unavailable)),
                    stringResource(R.string.about_release_size_label) to formatAboutBytes(release.releaseSizeBytes),
                    stringResource(R.string.about_channel_label) to changelogChannelLabel(release.channel),
                ))
                Text(release.summary, style = MaterialTheme.typography.bodyMedium)
                release.releaseUrl?.let { url ->
                    Button(onClick = { openExternal(context, url) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, stringResource(R.string.about_download_now))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.about_download_now))
                    }
                }
                release.releaseNotesUrl?.let { url -> OutlinedButton(onClick = { openExternal(context, url) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_view_release_notes)) } }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_later)) }
            }
            is UpdateCheckState.UpToDate -> {
                AboutStatusCard(stringResource(R.string.about_update_up_to_date), Icons.Default.Sync, FluxMetric.SUCCESS)
                Text(stringResource(R.string.about_update_current_version, update.currentVersionName ?: stringResource(R.string.about_unavailable_value)))
                OutlinedButton(onClick = model::checkForUpdates, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_retry)) }
            }
            is UpdateCheckState.Failed -> {
                AboutStatusCard(stringResource(R.string.about_update_failed), Icons.Default.Refresh, FluxMetric.ERROR)
                TextButton(onClick = { showTechnicalFailure = !showTechnicalFailure }) {
                    Icon(if (showTechnicalFailure) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (showTechnicalFailure) R.string.about_hide_update_diagnostics else R.string.about_show_update_diagnostics))
                }
                if (showTechnicalFailure) {
                    Text(stringResource(R.string.about_update_failure_detail, update.reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = model::checkForUpdates, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_retry)) }
            }
            UpdateCheckState.NetworkUnavailable -> {
                AboutStatusCard(stringResource(R.string.about_update_network_unavailable), Icons.Default.Refresh, FluxMetric.WARNING)
                Button(onClick = model::checkForUpdates, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_retry)) }
            }
            UpdateCheckState.SourceNotConfigured -> {
                AboutStatusCard(stringResource(R.string.about_update_source_not_configured), Icons.Default.Info, FluxMetric.UNAVAILABLE)
                Text(stringResource(R.string.about_update_source_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = model::checkForUpdates, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_retry)) }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun LegalContentScreen(model: AppViewModel, document: AboutDocument, onBack: () -> Unit) {
    val state by model.aboutLegal.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AboutScaffold(aboutDocumentTitle(document), onBack) {
        when (state.contentStatus) {
            ContentLoadStatus.LOADING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.about_content_loading))
            }
            ContentLoadStatus.ERROR -> {
                AboutStatusCard(stringResource(R.string.about_content_error), Icons.Default.Refresh, FluxMetric.ERROR)
                Text(state.contentError ?: stringResource(R.string.about_unavailable_value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = model::reloadAboutLegal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_retry)) }
            }
            ContentLoadStatus.READY -> {
                val blocks = state.documents[document].orEmpty()
                if (blocks.isEmpty()) AboutUnavailableCard(stringResource(R.string.about_content_empty))
                else {
                    blocks.forEach { block ->
                        when (block.kind) {
                            ContentBlockKind.HEADING -> Text(block.text, style = if (block.level == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            ContentBlockKind.PARAGRAPH -> Text(block.text, style = MaterialTheme.typography.bodyLarge)
                            ContentBlockKind.BULLET -> Text("• ${block.text}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (document == AboutDocument.LICENSES) {
                        HorizontalDivider()
                        Text(stringResource(R.string.about_license_links), style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { openExternal(context, "https://www.apache.org/licenses/LICENSE-2.0") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_apache_license)) }
                        OutlinedButton(onClick = { openExternal(context, "https://www.eclipse.org/legal/epl-v10.html") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_epl_license)) }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun CreditsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    AboutScaffold(stringResource(R.string.about_credits_title), onBack) {
        AboutDesignerCard(
            enabled = AboutLinks.designerProfileUrl != null,
            onClick = { AboutLinks.designerProfileUrl?.let { openExternal(context, it) } },
        )
        Text(stringResource(R.string.about_designer_profile_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AboutSectionTitle(stringResource(R.string.about_project_credits))
        AboutInfoCard(listOf(
            stringResource(R.string.about_designer_name_label) to stringResource(R.string.about_designer_name),
            stringResource(R.string.about_designer_role_label) to stringResource(R.string.about_designer_role),
            stringResource(R.string.about_project_label) to stringResource(R.string.app_name),
        ))
        Button(onClick = { onNavigate("about/support") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Favorite, stringResource(R.string.about_support_title))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.about_support_title))
        }
        AboutLinks.designerProfileUrl?.let { url ->
            OutlinedButton(onClick = { openExternal(context, url) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, stringResource(R.string.about_open_profile))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.about_open_profile))
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun SupportDevelopmentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    AboutScaffold(stringResource(R.string.about_support_title), onBack) {
        AboutHeaderCard(stringResource(R.string.about_support_header), stringResource(R.string.about_support_description), Icons.Default.Favorite, FluxMetric.BATTERY)
        Text(stringResource(R.string.about_support_detail), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { openExternal(context, AboutLinks.projectUrl) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.OpenInNew, stringResource(R.string.about_project_page))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.about_project_page))
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun AboutScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back)) }
        })
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = FluxSpacing.screenHorizontalPadding, vertical = FluxSpacing.screenVerticalPadding), verticalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap)) {
            item { Column(verticalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap), content = content) }
        }
    }
}

@Composable
private fun AboutHero(info: AppVersionInfo?) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = FluxShapes.material.large, elevation = CardDefaults.cardElevation(defaultElevation = FluxElevation.hero)) {
        Column(Modifier.padding(FluxSpacing.largeCardPadding), verticalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap)) {
                Box(Modifier.size(FluxSpacing.heroIconContainer), contentAlignment = Alignment.Center) { FluxWaveform() }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AboutBadge(info?.versionName ?: stringResource(R.string.about_unavailable_value), FluxMetric.CPU)
            }
            Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AboutHeaderCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, metric: FluxMetric) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = FluxShapes.material.large) {
        Row(Modifier.padding(FluxSpacing.largeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap)) {
            Icon(icon, title, tint = fluxMetricColor(metric), modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AboutDesignerCard(enabled: Boolean = true, onClick: () -> Unit) {
    val designerName = stringResource(R.string.about_designer_name)
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics { if (enabled) { role = Role.Button }; contentDescription = designerName }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = FluxShapes.material.medium) {
        Row(Modifier.padding(FluxSpacing.largeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap)) {
            Surface(shape = FluxShapes.material.large, color = fluxMetricColor(FluxMetric.GPU).copy(alpha = .18f), modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, designerName, tint = fluxMetricColor(FluxMetric.GPU), modifier = Modifier.size(30.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.about_designed_by), style = MaterialTheme.typography.labelMedium, color = fluxMetricColor(FluxMetric.GPU))
                Text(stringResource(R.string.about_designer_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.about_designer_role), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, stringResource(R.string.open_detail), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutActionRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button; contentDescription = "$title, $subtitle" }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = FluxShapes.material.small) {
        Row(Modifier.padding(horizontal = FluxSpacing.cardInternalPadding, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap)) {
            Icon(icon, title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, stringResource(R.string.open_detail), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutSupportCard(onClick: () -> Unit) {
    val supportTitle = stringResource(R.string.about_support_title)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button; contentDescription = supportTitle }, colors = CardDefaults.cardColors(containerColor = fluxMetricColor(FluxMetric.BATTERY).copy(alpha = .14f)), shape = FluxShapes.material.medium) {
        Row(Modifier.padding(FluxSpacing.largeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FluxSpacing.cardGap)) {
            Icon(Icons.Default.Favorite, stringResource(R.string.about_support_title), tint = fluxMetricColor(FluxMetric.BATTERY), modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.about_support_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.about_support_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, stringResource(R.string.open_detail), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutInfoCard(items: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = FluxShapes.material.medium) {
        Column(Modifier.padding(FluxSpacing.cardInternalPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AboutStatusCard(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, metric: FluxMetric, showProgress: Boolean = false) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = FluxShapes.material.medium) {
        Row(Modifier.padding(FluxSpacing.cardInternalPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FluxSpacing.compactGap)) {
            if (showProgress) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = fluxMetricColor(metric)) else Icon(icon, text, tint = fluxMetricColor(metric), modifier = Modifier.size(24.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AboutUnavailableCard(message: String) = AboutStatusCard(message, Icons.Default.Info, FluxMetric.UNAVAILABLE)

@Composable
private fun AboutBadge(text: String, metric: FluxMetric) {
    Surface(color = fluxMetricColor(metric).copy(alpha = .16f), shape = FluxShapes.material.small) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = fluxMetricColor(metric))
    }
}

@Composable
private fun AboutSectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }

@Composable
private fun updateHubSubtitle(state: UpdateCheckState): String = when (state) {
    UpdateCheckState.Idle -> stringResource(R.string.about_update_subtitle)
    UpdateCheckState.Checking -> stringResource(R.string.about_update_checking)
    is UpdateCheckState.UpdateAvailable -> stringResource(R.string.about_update_available)
    is UpdateCheckState.UpToDate -> stringResource(R.string.about_update_up_to_date)
    is UpdateCheckState.Failed -> stringResource(R.string.about_update_failed)
    UpdateCheckState.NetworkUnavailable -> stringResource(R.string.about_update_network_unavailable)
    UpdateCheckState.SourceNotConfigured -> stringResource(R.string.about_update_source_not_configured)
}

@Composable
private fun changelogCategoryLabel(value: ChangelogCategory): String = stringResource(when (value) {
    ChangelogCategory.NEW -> R.string.about_category_new
    ChangelogCategory.IMPROVED -> R.string.about_category_improved
    ChangelogCategory.FIXED -> R.string.about_category_fixed
    ChangelogCategory.KNOWN_LIMITATIONS -> R.string.about_category_limitations
})

@Composable
private fun changelogChannelLabel(value: ReleaseChannel): String = stringResource(when (value) {
    ReleaseChannel.STABLE -> R.string.about_channel_stable
    ReleaseChannel.BETA -> R.string.about_channel_beta
    ReleaseChannel.DEVELOPMENT -> R.string.about_channel_development
})

@Composable
private fun aboutDocumentTitle(document: AboutDocument): String = stringResource(when (document) {
    AboutDocument.LICENSES -> R.string.about_licenses_title
    AboutDocument.PRIVACY -> R.string.about_privacy_title
    AboutDocument.TERMS -> R.string.about_terms_title
})

@Composable
private fun aboutLanguageLabel(value: LanguageSetting): String = stringResource(when (value) {
    LanguageSetting.SYSTEM -> R.string.theme_system
    LanguageSetting.ENGLISH -> R.string.language_english
    LanguageSetting.INDONESIAN -> R.string.language_indonesian
})

@Composable
private fun aboutThemeLabel(value: ThemeSetting): String = stringResource(when (value) {
    ThemeSetting.SYSTEM -> R.string.theme_system
    ThemeSetting.LIGHT -> R.string.theme_light
    ThemeSetting.DARK -> R.string.theme_dark
})

@Composable
private fun formatAboutTimestamp(value: Long?): String = value?.takeIf { it > 0L }?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: stringResource(R.string.about_unavailable_value)

@Composable
private fun formatAboutBytes(value: Long?): String = when {
    value == null -> stringResource(R.string.about_unavailable_value)
    value >= 1_073_741_824L -> "%.1f GiB".format(value / 1_073_741_824.0)
    value >= 1_048_576L -> "%.1f MiB".format(value / 1_048_576.0)
    else -> "$value B"
}

private fun openExternal(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        if (intent.resolveActivity(context.packageManager) == null) {
            android.widget.Toast.makeText(context, R.string.about_external_link_unavailable, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            context.startActivity(intent)
        }
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, R.string.about_external_link_unavailable, android.widget.Toast.LENGTH_SHORT).show()
    }
}
