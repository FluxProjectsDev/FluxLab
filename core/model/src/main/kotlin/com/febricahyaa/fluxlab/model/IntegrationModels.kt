package com.febricahyaa.fluxlab.model

import kotlinx.coroutines.flow.StateFlow

sealed interface RootState {
    data object Unknown : RootState
    data object Checking : RootState
    data object Available : RootState
    data object Denied : RootState
    data object Unavailable : RootState
    data class Error(val reason: String) : RootState
}

data class RootCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
) {
    val succeeded: Boolean get() = exitCode == 0 && !timedOut && !cancelled
}

enum class RootCommand {
    CHECK_ID,
    FLUX_MODULE_EXISTS,
    FLUX_CONFIG_EXISTS,
    READ_MODULE_PROP,
    READ_CURRENT_PROFILE,
    READ_RUNTIME_STATUS,
    READ_SYNTHESIS_STATUS,
    STAT_SYNTHESIS_STATUS,
    FIND_FLUX_DAEMON,
}

interface RootGateway {
    val state: StateFlow<RootState>
    suspend fun checkAvailability(force: Boolean = false): RootState
    suspend fun execute(command: RootCommand): RootCommandResult
}

data class FluxInstallation(
    val installed: Boolean = false,
    val enabled: Boolean? = null,
    val runtimeAvailable: Boolean = false,
    val daemonAlive: Boolean? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val activeProfile: String? = null,
    val kernelType: String? = null,
    val configDirectory: String = "/data/adb/.config/flux",
    val synthesisCoreAvailable: Boolean = false,
    val lastStatusUpdateEpochMs: Long? = null,
    val warnings: List<String> = emptyList(),
)

interface FluxRuntimeReader {
    suspend fun readInstallation(): FluxInstallation
}

enum class SnapshotFreshness { FRESH, DELAYED, STALE, UNAVAILABLE, UNKNOWN }

data class SynthesisCoreSnapshot(
    val schemaVersion: Int? = null,
    val sequence: Long? = null,
    val updatedElapsedMs: Long? = null,
    val daemonPid: Int? = null,
    val focusedPackage: String? = null,
    val foregroundPid: Int? = null,
    val foregroundUid: Int? = null,
    val screenAwake: Boolean? = null,
    val batterySaver: Boolean? = null,
    val zenMode: Int? = null,
    val charging: Boolean? = null,
    val thermalAvailable: Boolean? = null,
    val thermalValid: Boolean? = null,
    val thermalHeadroom: Double? = null,
    val thermalStatus: Int? = null,
    val audioActive: Boolean? = null,
    val kernelIsGki: Boolean? = null,
    val fileModifiedEpochMs: Long? = null,
    val freshness: SnapshotFreshness = SnapshotFreshness.UNKNOWN,
    val extensions: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

sealed interface SynthesisReadResult {
    data class Success(val snapshot: SynthesisCoreSnapshot) : SynthesisReadResult
    data class Unavailable(val reason: String) : SynthesisReadResult
    data class Malformed(val warnings: List<String>) : SynthesisReadResult
}

interface SynthesisCoreReader {
    suspend fun read(): SynthesisReadResult
}
