package com.febricahyaa.fluxlab.integration

import com.febricahyaa.fluxlab.model.FluxInstallation
import com.febricahyaa.fluxlab.model.FluxRuntimeReader
import com.febricahyaa.fluxlab.model.RootCommand
import com.febricahyaa.fluxlab.model.RootGateway
import com.febricahyaa.fluxlab.model.RootState

class FluxRuntimeAdapter(private val rootGateway: RootGateway) : FluxRuntimeReader {
    override suspend fun readInstallation(): FluxInstallation {
        if (rootGateway.checkAvailability() !is RootState.Available) {
            return FluxInstallation(warnings = listOf("Root is unavailable; Flux detection is limited"))
        }
        val warnings = mutableListOf<String>()
        val moduleExists = rootGateway.execute(RootCommand.FLUX_MODULE_EXISTS).succeeded
        val configExists = rootGateway.execute(RootCommand.FLUX_CONFIG_EXISTS).succeeded
        val moduleProp = rootGateway.execute(RootCommand.READ_MODULE_PROP)
        val properties = if (moduleProp.succeeded) parseProperties(moduleProp.stdout) else emptyMap()
        if (moduleExists && !moduleProp.succeeded) warnings += "Flux module metadata is unreadable"
        val daemon = rootGateway.execute(RootCommand.FIND_FLUX_DAEMON)
        val profile = rootGateway.execute(RootCommand.READ_CURRENT_PROFILE)
        val synthesis = rootGateway.execute(RootCommand.STAT_SYNTHESIS_STATUS)
        val runtime = rootGateway.execute(RootCommand.READ_RUNTIME_STATUS)
        val runtimeFields = if (runtime.succeeded) FlatPayloadParser.parse(runtime.stdout).fields else emptyMap()
        val disabled = properties["description"]?.contains("disabled", ignoreCase = true) == true
        return FluxInstallation(
            installed = moduleExists || properties["id"]?.equals("flux", ignoreCase = true) == true,
            enabled = if (moduleExists) !disabled else null,
            runtimeAvailable = configExists && (profile.succeeded || runtime.succeeded),
            daemonAlive = if (daemon.succeeded) daemon.stdout.trim().isNotEmpty() else null,
            versionName = properties["version"] ?: properties["versionName"],
            versionCode = (properties["versionCode"] ?: properties["version_code"])?.toLongOrNull(),
            activeProfile = profile.stdout.trim().takeIf { profile.succeeded && it.isNotEmpty() }
                ?: runtimeFields["active_profile"]
                ?: runtimeFields["verified_profile"],
            kernelType = runtimeFields["kernel_type"] ?: runtimeFields["kernel_is_gki"]?.let {
                if (it == "1" || it.equals("true", true)) "GKI" else "non-GKI"
            },
            synthesisCoreAvailable = synthesis.succeeded,
            lastStatusUpdateEpochMs = synthesis.stdout.trim().toLongOrNull()?.times(1_000L),
            warnings = warnings,
        )
    }

    internal fun parseProperties(text: String): Map<String, String> =
        text.lineSequence().mapNotNull { line ->
            val split = line.indexOf('=')
            if (split <= 0) null else line.substring(0, split).trim() to line.substring(split + 1).trim()
        }.toMap()
}
