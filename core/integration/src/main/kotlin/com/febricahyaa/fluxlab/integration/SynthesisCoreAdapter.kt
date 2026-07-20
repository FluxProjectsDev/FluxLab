package com.febricahyaa.fluxlab.integration

import com.febricahyaa.fluxlab.model.RootCommand
import com.febricahyaa.fluxlab.model.RootGateway
import com.febricahyaa.fluxlab.model.RootState
import com.febricahyaa.fluxlab.model.SnapshotFreshness
import com.febricahyaa.fluxlab.model.SynthesisCoreReader
import com.febricahyaa.fluxlab.model.SynthesisCoreSnapshot
import com.febricahyaa.fluxlab.model.SynthesisReadResult
import kotlinx.coroutines.delay

class SynthesisCoreAdapter(
    private val rootGateway: RootGateway,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : SynthesisCoreReader {
    override suspend fun read(): SynthesisReadResult {
        if (rootGateway.checkAvailability() !is RootState.Available) {
            return SynthesisReadResult.Unavailable("Root is required to read the Flux runtime directory")
        }
        var lastWarnings = emptyList<String>()
        repeat(3) { attempt ->
            val payloadResult = rootGateway.execute(RootCommand.READ_SYNTHESIS_STATUS)
            if (!payloadResult.succeeded) {
                if (attempt == 2) return SynthesisReadResult.Unavailable(payloadResult.stderr.ifBlank { "Status file unavailable" })
                delay(75)
                return@repeat
            }
            val modifiedResult = rootGateway.execute(RootCommand.STAT_SYNTHESIS_STATUS)
            val modifiedMs = modifiedResult.stdout.trim().toLongOrNull()?.times(1_000L)
            val parsed = parse(payloadResult.stdout, modifiedMs)
            lastWarnings = parsed.warnings
            if (parsed.schemaVersion != null || parsed.extensions.isNotEmpty()) {
                return SynthesisReadResult.Success(parsed)
            }
            if (attempt < 2) delay(75)
        }
        return SynthesisReadResult.Malformed(lastWarnings.ifEmpty { listOf("No recognizable status fields") })
    }

    fun parse(payloadText: String, modifiedEpochMs: Long? = null): SynthesisCoreSnapshot {
        val payload = FlatPayloadParser.parse(payloadText)
        val fields = payload.fields
        val warnings = payload.warnings.toMutableList()
        fun value(vararg names: String): String? = names.firstNotNullOfOrNull(fields::get)
        fun integer(name: String, minimum: Long = Long.MIN_VALUE, maximum: Long = Long.MAX_VALUE): Long? {
            val raw = fields[name] ?: return null
            val parsed = raw.toLongOrNull()
            if (parsed == null || parsed !in minimum..maximum) warnings += "invalid $name"
            return parsed?.takeIf { it in minimum..maximum }
        }
        fun bool(vararg names: String): Boolean? {
            val raw = value(*names)?.lowercase() ?: return null
            return when (raw) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                else -> null.also { warnings += "invalid ${names.first()}" }
            }
        }
        fun decimal(vararg names: String): Double? {
            val raw = value(*names) ?: return null
            val parsed = raw.toDoubleOrNull()?.takeIf { it.isFinite() }
            if (parsed == null) warnings += "invalid ${names.first()}"
            return parsed
        }

        val legacyFocused = fields["focused_app"]?.split(Regex("\\s+"))
        val packageName = value("focused_package", "focused_application", "package")
            ?: legacyFocused?.getOrNull(0)
        val pid = integer("focused_pid", 0, Int.MAX_VALUE.toLong())?.toInt()
            ?: legacyFocused?.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }
        val uid = integer("focused_uid", 0, Int.MAX_VALUE.toLong())?.toInt()
            ?: legacyFocused?.getOrNull(2)?.toIntOrNull()?.takeIf { it >= 0 }
        val known = KNOWN_KEYS + setOf("focused_app", "focused_application", "package")
        val age = modifiedEpochMs?.let { (nowEpochMs() - it).coerceAtLeast(0L) }
        val freshness = when {
            modifiedEpochMs == null -> SnapshotFreshness.UNKNOWN
            age!! <= 3_000L -> SnapshotFreshness.FRESH
            age <= 10_000L -> SnapshotFreshness.DELAYED
            else -> SnapshotFreshness.STALE
        }
        return SynthesisCoreSnapshot(
            schemaVersion = integer("schema_version", 0, Int.MAX_VALUE.toLong())?.toInt(),
            sequence = integer("sequence", 0),
            updatedElapsedMs = integer("updated_elapsed_ms", 0),
            daemonPid = integer("daemon_pid", 0, Int.MAX_VALUE.toLong())?.toInt(),
            focusedPackage = packageName?.takeUnless { it == "none" || it == "null" },
            foregroundPid = pid,
            foregroundUid = uid,
            screenAwake = bool("screen_awake", "screen_on"),
            batterySaver = bool("battery_saver", "power_save"),
            zenMode = value("zen_mode")?.toIntOrNull()?.takeIf { it in 0..3 }.also {
                if (fields.containsKey("zen_mode") && it == null) warnings += "invalid zen_mode"
            },
            charging = bool("charging_state", "charging"),
            thermalAvailable = bool("thermal_available"),
            thermalValid = bool("thermal_valid"),
            thermalHeadroom = decimal("thermal_headroom", "thermal_status").takeIf {
                fields.containsKey("thermal_headroom") || fields["thermal_status"]?.contains('.') == true
            },
            thermalStatus = value("thermal_status_code")?.toIntOrNull()
                ?: fields["thermal_status"]?.takeUnless { it.contains('.') }?.toIntOrNull(),
            audioActive = bool("audio_active"),
            kernelIsGki = bool("kernel_is_gki"),
            fileModifiedEpochMs = modifiedEpochMs,
            freshness = freshness,
            extensions = fields.filterKeys { it !in known },
            warnings = warnings.distinct(),
        )
    }

    private companion object {
        val KNOWN_KEYS = setOf(
            "schema_version", "sequence", "updated_elapsed_ms", "daemon_pid",
            "foreground_available", "focused_package", "focused_pid", "focused_uid",
            "screen_available", "screen_awake", "screen_on", "power_available",
            "battery_saver", "power_save", "charging_available", "charging_state", "charging",
            "thermal_available", "thermal_valid", "thermal_headroom", "thermal_status",
            "thermal_status_code", "thermal_sample_elapsed_ms", "thermal_age_ms",
            "audio_available", "audio_active", "zen_available", "zen_mode", "kernel_is_gki",
        )
    }
}
