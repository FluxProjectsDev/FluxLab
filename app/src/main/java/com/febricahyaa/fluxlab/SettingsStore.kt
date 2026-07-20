package com.febricahyaa.fluxlab

import com.febricahyaa.fluxlab.model.BenchmarkPreset

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("fluxlab_settings")

enum class ThemeSetting { SYSTEM, LIGHT, DARK }
enum class UnitSetting { SI, IEC }
enum class LanguageSetting { SYSTEM, ENGLISH, INDONESIAN }

data class AppSettings(
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val samplingIntervalMs: Long = 1_000L,
    val includeStorage: Boolean = true,
    val advancedMetrics: Boolean = false,
    val units: UnitSetting = UnitSetting.IEC,
    val language: LanguageSetting = LanguageSetting.SYSTEM,
    val storageNoticeAccepted: Boolean = false,
    val selectedReportSessionId: String? = null,
    val preset: BenchmarkPreset = BenchmarkPreset.QUICK,
)

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            theme = enumValue(preferences[THEME], ThemeSetting.SYSTEM),
            samplingIntervalMs = (preferences[INTERVAL] ?: 1_000L).coerceIn(250L, 10_000L),
            includeStorage = preferences[STORAGE] ?: true,
            advancedMetrics = preferences[ADVANCED] ?: false,
            units = enumValue(preferences[UNITS], UnitSetting.IEC),
            language = enumValue(preferences[LANGUAGE], LanguageSetting.SYSTEM),
            storageNoticeAccepted = preferences[NOTICE] ?: false,
            selectedReportSessionId = preferences[SELECTED_REPORT_SESSION],
            preset = enumValue(preferences[PRESET], BenchmarkPreset.QUICK),
        )
    }

    suspend fun setTheme(value: ThemeSetting) = context.settingsDataStore.edit { it[THEME] = value.name }
    suspend fun setInterval(value: Long) = context.settingsDataStore.edit { it[INTERVAL] = value.coerceIn(250L, 10_000L) }
    suspend fun setStorage(value: Boolean) = context.settingsDataStore.edit { it[STORAGE] = value }
    suspend fun setAdvanced(value: Boolean) = context.settingsDataStore.edit { it[ADVANCED] = value }
    suspend fun setUnits(value: UnitSetting) = context.settingsDataStore.edit { it[UNITS] = value.name }
    suspend fun setLanguage(value: LanguageSetting) = context.settingsDataStore.edit { it[LANGUAGE] = value.name }
    suspend fun acceptStorageNotice() = context.settingsDataStore.edit { it[NOTICE] = true }
    suspend fun setSelectedReportSessionId(id: String?) = context.settingsDataStore.edit {
        if (id.isNullOrBlank()) it.remove(SELECTED_REPORT_SESSION) else it[SELECTED_REPORT_SESSION] = id
    }
    suspend fun setPreset(value: BenchmarkPreset) = context.settingsDataStore.edit { it[PRESET] = value.name }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val INTERVAL = longPreferencesKey("sampling_interval_ms")
        val STORAGE = booleanPreferencesKey("include_storage")
        val ADVANCED = booleanPreferencesKey("advanced_metrics")
        val UNITS = stringPreferencesKey("units")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTICE = booleanPreferencesKey("storage_notice_accepted")
        val SELECTED_REPORT_SESSION = stringPreferencesKey("selected_report_session_id")
        val PRESET = stringPreferencesKey("benchmark_preset")
    }
}
