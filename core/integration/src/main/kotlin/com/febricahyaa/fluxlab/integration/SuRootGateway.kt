package com.febricahyaa.fluxlab.integration

import com.febricahyaa.fluxlab.model.RootCommand
import com.febricahyaa.fluxlab.model.RootCommandResult
import com.febricahyaa.fluxlab.model.RootGateway
import com.febricahyaa.fluxlab.model.RootState
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class SuRootGateway(private val timeoutMs: Long = 4_000L) : RootGateway {
    private val mutableState = MutableStateFlow<RootState>(RootState.Unknown)
    override val state: StateFlow<RootState> = mutableState.asStateFlow()
    private var checked = false

    override suspend fun checkAvailability(force: Boolean): RootState {
        if (checked && !force) return mutableState.value
        checked = true
        mutableState.value = RootState.Checking
        if (!File("/system/bin/su").exists() && !commandOnPath("su")) {
            return RootState.Unavailable.also { mutableState.value = it }
        }
        val result = runFixed(ALLOWLIST.getValue(RootCommand.CHECK_ID))
        val detected = when {
            result.timedOut -> RootState.Denied
            result.cancelled -> RootState.Unknown
            result.succeeded && result.stdout.contains("uid=0") -> RootState.Available
            result.stderr.contains("denied", ignoreCase = true) ||
                result.stderr.contains("not allowed", ignoreCase = true) -> RootState.Denied
            result.exitCode == 1 -> RootState.Denied
            else -> RootState.Error(result.stderr.ifBlank { "su exited ${result.exitCode}" }.take(256))
        }
        mutableState.value = detected
        return detected
    }

    override suspend fun execute(command: RootCommand): RootCommandResult {
        if (command != RootCommand.CHECK_ID && checkAvailability() !is RootState.Available) {
            return RootCommandResult(126, "", "Root unavailable")
        }
        return runFixed(ALLOWLIST.getValue(command))
    }

    private suspend fun runFixed(command: String): RootCommandResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            coroutineScope {
                val runningProcess = ProcessBuilder("su", "-c", command).start().also { process = it }
                val stdout = async(Dispatchers.IO) { runningProcess.inputStream.bufferedReader().readText() }
                val stderr = async(Dispatchers.IO) { runningProcess.errorStream.bufferedReader().readText() }
                val started = System.nanoTime()
                while (runningProcess.isAlive) {
                    if (!currentCoroutineContext().isActive) throw CancellationException()
                    if ((System.nanoTime() - started) / 1_000_000L >= timeoutMs) {
                        runningProcess.destroy()
                        if (!runningProcess.waitFor(200, TimeUnit.MILLISECONDS)) runningProcess.destroyForcibly()
                        return@coroutineScope RootCommandResult(-1, stdout.await(), stderr.await(), timedOut = true)
                    }
                    delay(40)
                }
                RootCommandResult(runningProcess.exitValue(), stdout.await(), stderr.await())
            }
        } catch (cancelled: CancellationException) {
            process?.destroy()
            if (process?.isAlive == true) process?.destroyForcibly()
            throw cancelled
        } catch (error: Exception) {
            process?.destroyForcibly()
            RootCommandResult(-1, "", error.message.orEmpty())
        }
    }

    private fun commandOnPath(name: String): Boolean =
        System.getenv("PATH").orEmpty().split(':').any { File(it, name).canExecute() }

    private companion object {
        val ALLOWLIST = mapOf(
            RootCommand.CHECK_ID to "id",
            RootCommand.FLUX_MODULE_EXISTS to "test -d /data/adb/modules/flux",
            RootCommand.FLUX_CONFIG_EXISTS to "test -d /data/adb/.config/flux",
            RootCommand.READ_MODULE_PROP to "cat /data/adb/modules/flux/module.prop",
            RootCommand.READ_CURRENT_PROFILE to "cat /data/adb/.config/flux/current_profile",
            RootCommand.READ_RUNTIME_STATUS to "cat /data/adb/.config/flux/runtime_status.json",
            RootCommand.READ_SYNTHESIS_STATUS to "cat /data/adb/.config/flux/synthesis_core.json",
            RootCommand.STAT_SYNTHESIS_STATUS to "stat -c %Y /data/adb/.config/flux/synthesis_core.json",
            RootCommand.READ_KGSL_GPU_MODEL to "cat /sys/class/kgsl/kgsl-3d0/gpu_model",
            RootCommand.READ_KGSL_GPUCLK to "cat /sys/class/kgsl/kgsl-3d0/gpuclk",
            RootCommand.READ_KGSL_MAX_GPUCLK to "cat /sys/class/kgsl/kgsl-3d0/max_gpuclk",
            RootCommand.READ_KGSL_DEVFREQ_CUR to "cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            RootCommand.READ_KGSL_DEVFREQ_MAX to "cat /sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            RootCommand.READ_KGSL_DEVFREQ_MIN to "cat /sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            RootCommand.READ_KGSL_AVAILABLE_FREQUENCIES to "cat /sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            RootCommand.READ_KGSL_GPUBUSY to "cat /sys/class/kgsl/kgsl-3d0/gpubusy",
            RootCommand.FIND_FLUX_DAEMON to "pidof fluxd",
        )
    }
}
