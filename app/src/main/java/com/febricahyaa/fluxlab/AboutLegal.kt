package com.febricahyaa.fluxlab

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.content.res.Resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class AppVersionInfo(
    val appName: String,
    val versionName: String?,
    val versionCode: Long?,
    val buildType: String,
    val packageName: String,
    val installTimeEpochMs: Long?,
    val updateTimeEpochMs: Long?,
    val supportedAbis: List<String>,
    val minSdk: Int?,
    val targetSdk: Int?,
)

object AppVersionInfoReader {
    @Suppress("DEPRECATION")
    fun read(context: Context): AppVersionInfo {
        val packageManager = context.packageManager
        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            packageManager.getPackageInfo(context.packageName, 0)
        }
        val applicationInfo = packageInfo.applicationInfo
        return AppVersionInfo(
            appName = context.applicationInfo.loadLabel(packageManager).toString(),
            versionName = packageInfo.versionName?.takeIf(String::isNotBlank),
            versionCode = packageInfo.longVersionCode.takeIf { it > 0L },
            buildType = BuildConfig.BUILD_TYPE,
            packageName = context.packageName,
            installTimeEpochMs = packageInfo.firstInstallTime.takeIf { it > 0L },
            updateTimeEpochMs = packageInfo.lastUpdateTime.takeIf { it > 0L },
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            minSdk = applicationInfo?.minSdkVersion,
            targetSdk = applicationInfo?.targetSdkVersion,
        )
    }
}

enum class AboutDocument { LICENSES, PRIVACY, TERMS }

enum class ContentBlockKind { HEADING, PARAGRAPH, BULLET }

data class ContentBlock(
    val kind: ContentBlockKind,
    val text: String,
    val level: Int = 1,
)

fun parseMarkdown(source: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += ContentBlock(ContentBlockKind.PARAGRAPH, paragraph.joinToString(" "))
            paragraph.clear()
        }
    }

    source.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isBlank() -> flushParagraph()
            line.startsWith("#") -> {
                flushParagraph()
                val level = line.takeWhile { it == 35.toChar() }.length.coerceIn(1, 3)
                blocks += ContentBlock(ContentBlockKind.HEADING, line.drop(level).trim(), level)
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushParagraph()
                blocks += ContentBlock(ContentBlockKind.BULLET, line.drop(2).trim())
            }
            else -> paragraph += line
        }
    }
    flushParagraph()
    return blocks
}

enum class ChangelogCategory { NEW, IMPROVED, FIXED, KNOWN_LIMITATIONS }

enum class ReleaseChannel { STABLE, BETA, DEVELOPMENT }

data class ChangelogEntry(
    val versionName: String,
    val versionNameResource: Int,
    val releaseDate: String?,
    val channel: ReleaseChannel,
    val categories: Map<ChangelogCategory, List<Int>>,
    val indonesianCategories: Map<ChangelogCategory, List<Int>> = emptyMap(),
)

/** Maintained release metadata. User-facing release text lives in localized resources. */
object ChangelogCatalog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            versionName = "0.1.0",
            versionNameResource = R.string.changelog_version_0_1_0,
            releaseDate = null,
            channel = ReleaseChannel.STABLE,
            categories = mapOf(
                ChangelogCategory.NEW to listOf(
                    R.string.changelog_new_local_telemetry,
                    R.string.changelog_new_repeatable_benchmarks,
                ),
                ChangelogCategory.IMPROVED to listOf(
                    R.string.changelog_improved_material_dashboard,
                ),
                ChangelogCategory.KNOWN_LIMITATIONS to listOf(
                    R.string.changelog_limitation_oem_access,
                ),
            ),
            indonesianCategories = mapOf(
                ChangelogCategory.NEW to listOf(
                    R.string.changelog_new_local_telemetry,
                    R.string.changelog_new_repeatable_benchmarks,
                ),
                ChangelogCategory.IMPROVED to listOf(
                    R.string.changelog_improved_material_dashboard,
                ),
                ChangelogCategory.KNOWN_LIMITATIONS to listOf(
                    R.string.changelog_limitation_oem_access,
                ),
            ),
        ),
    )
}

data class UpdateRelease(
    val latestVersionName: String,
    val releaseDate: String?,
    val releaseSizeBytes: Long?,
    val channel: ReleaseChannel,
    val summary: String,
    val releaseUrl: String?,
    val releaseNotesUrl: String? = null,
)

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpdateAvailable(val release: UpdateRelease) : UpdateCheckState
    data class UpToDate(val currentVersionName: String?) : UpdateCheckState
    data class Failed(val reason: String) : UpdateCheckState
    data object NetworkUnavailable : UpdateCheckState
    data object SourceNotConfigured : UpdateCheckState
}

interface UpdateProvider {
    suspend fun check(current: AppVersionInfo): UpdateCheckState
}

/** Production-safe default until a project-owned update manifest is configured. */
class UnconfiguredUpdateProvider : UpdateProvider {
    override suspend fun check(_current: AppVersionInfo): UpdateCheckState = UpdateCheckState.SourceNotConfigured
}

enum class ContentLoadStatus { LOADING, READY, ERROR }

data class AboutLegalState(
    val version: AppVersionInfo? = null,
    val contentStatus: ContentLoadStatus = ContentLoadStatus.LOADING,
    val documents: Map<AboutDocument, List<ContentBlock>> = emptyMap(),
    val contentError: String? = null,
    val update: UpdateCheckState = UpdateCheckState.Idle,
)

class AboutLegalRepository(
    private val resources: Resources,
    private val updateProvider: UpdateProvider,
) {
    suspend fun load(language: LanguageSetting): Result<Map<AboutDocument, List<ContentBlock>>> = withContext(Dispatchers.IO) {
        runCatching {
            val indonesian = when (language) {
                LanguageSetting.INDONESIAN -> true
                LanguageSetting.ENGLISH -> false
                LanguageSetting.SYSTEM -> Locale.getDefault().language.equals("id", ignoreCase = true)
            }
            mapOf(
                AboutDocument.LICENSES to parseMarkdown(readRaw(if (indonesian) R.raw.open_source_licenses_in else R.raw.open_source_licenses)),
                AboutDocument.PRIVACY to parseMarkdown(readRaw(if (indonesian) R.raw.privacy_policy_in else R.raw.privacy_policy)),
                AboutDocument.TERMS to parseMarkdown(readRaw(if (indonesian) R.raw.terms_conditions_in else R.raw.terms_conditions)),
            )
        }
    }

    suspend fun checkForUpdates(current: AppVersionInfo): UpdateCheckState =
        withContext(Dispatchers.IO) { updateProvider.check(current) }

    private fun readRaw(resourceId: Int): String = resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
}

object AboutLinks {
    const val projectUrl = "https://github.com/FluxProjectsDev/FluxLab"
    val designerProfileUrl: String? = null
}
