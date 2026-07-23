package com.febricahyaa.fluxlab.integration

import android.os.Build
import com.febricahyaa.fluxlab.model.CapabilityState
import com.febricahyaa.fluxlab.model.CpuIdentity
import com.febricahyaa.fluxlab.model.CpuIdentityProvider
import com.febricahyaa.fluxlab.model.GpuCapabilityProvider
import com.febricahyaa.fluxlab.model.GpuCapabilityState
import com.febricahyaa.fluxlab.model.GpuTelemetry
import com.febricahyaa.fluxlab.model.IdentityConfidence
import com.febricahyaa.fluxlab.model.RootCommand
import com.febricahyaa.fluxlab.model.RootGateway
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

sealed interface ProbeValue {
    data class Available(val value: String) : ProbeValue
    data object Missing : ProbeValue
    data object PermissionDenied : ProbeValue
    data class Malformed(val raw: String) : ProbeValue
    data class Error(val reason: String) : ProbeValue
}

interface HardwareNodeSource {
    suspend fun read(path: String): ProbeValue
    fun directories(path: String): List<String>
    fun exists(path: String): Boolean
}

class FileHardwareNodeSource : HardwareNodeSource {
    override suspend fun read(path: String): ProbeValue = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists() || !file.isFile) return@withContext ProbeValue.Missing
        if (!file.canRead()) return@withContext ProbeValue.PermissionDenied
        try {
            val raw = file.readText().trim()
            if (raw.isBlank() || raw.length > MAX_VALUE_LENGTH || raw.any { it == '\u0000' }) {
                ProbeValue.Malformed(raw.take(MAX_VALUE_LENGTH))
            } else {
                ProbeValue.Available(raw)
            }
        } catch (_: SecurityException) {
            ProbeValue.PermissionDenied
        } catch (error: Exception) {
            ProbeValue.Error(error.javaClass.simpleName)
        }
    }

    override fun directories(path: String): List<String> = File(path).listFiles()
        ?.asSequence()?.filter { it.isDirectory }?.map { it.path }?.take(128)?.toList().orEmpty()

    override fun exists(path: String): Boolean = File(path).exists()

    private companion object { const val MAX_VALUE_LENGTH = 4_096 }
}

interface AndroidPropertySource {
    suspend fun read(name: String): ProbeValue
}

class ProcessAndroidPropertySource(private val timeoutMs: Long = 750L) : AndroidPropertySource {
    override suspend fun read(name: String): ProbeValue = withContext(Dispatchers.IO) {
        if (name !in ALLOWED_PROPERTIES) return@withContext ProbeValue.Error("Property is not allowlisted")
        var process: Process? = null
        try {
            val running = ProcessBuilder("getprop", name).start().also { process = it }
            val started = System.nanoTime()
            while (running.isAlive) {
                if (!currentCoroutineContext().isActive) throw CancellationException()
                if ((System.nanoTime() - started) / 1_000_000L >= timeoutMs) {
                    running.destroy()
                    if (!running.waitFor(100, TimeUnit.MILLISECONDS)) running.destroyForcibly()
                    return@withContext ProbeValue.Error("getprop timeout")
                }
                delay(20)
            }
            val stdout = running.inputStream.bufferedReader().readText().trim()
            val stderr = running.errorStream.bufferedReader().readText().trim()
            when {
                running.exitValue() != 0 -> ProbeValue.Error(stderr.ifBlank { "getprop exit ${running.exitValue()}" })
                stdout.isBlank() -> ProbeValue.Missing
                stdout.length > 256 || stdout.any { it == '\u0000' } -> ProbeValue.Malformed(stdout.take(256))
                else -> ProbeValue.Available(stdout)
            }
        } catch (cancelled: CancellationException) {
            process?.destroyForcibly()
            throw cancelled
        } catch (error: Exception) {
            process?.destroyForcibly()
            ProbeValue.Error(error.javaClass.simpleName)
        }
    }

    private companion object {
        val ALLOWED_PROPERTIES = setOf("ro.soc.model", "ro.soc.manufacturer", "ro.hardware")
    }
}

data class CpuBuildValues(
    val socModel: String?,
    val socManufacturer: String?,
    val hardware: String?,
    val board: String?,
    val device: String?,
    val model: String?,
    val supportedAbis: List<String>,
) {
    companion object {
        fun android(): CpuBuildValues = CpuBuildValues(
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null,
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else null,
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            device = Build.DEVICE,
            model = Build.MODEL,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
        )
    }
}

class AndroidCpuIdentityProvider(
    private val build: CpuBuildValues = CpuBuildValues.android(),
    private val properties: AndroidPropertySource = ProcessAndroidPropertySource(),
    private val nodes: HardwareNodeSource = FileHardwareNodeSource(),
    private val runtimeCoreCount: () -> Int = { Runtime.getRuntime().availableProcessors() },
) : CpuIdentityProvider {
    @Volatile private var cached: CpuIdentity? = null

    override suspend fun identify(): CpuIdentity = cached ?: detect().also { cached = it }

    private suspend fun detect(): CpuIdentity {
        val raw = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()
        add(raw, "Build.SOC_MODEL", build.socModel, warnings)
        add(raw, "Build.SOC_MANUFACTURER", build.socManufacturer, warnings)
        add(raw, "Build.HARDWARE", build.hardware, warnings)
        add(raw, "Build.BOARD", build.board, warnings)
        add(raw, "Build.DEVICE", build.device, warnings)
        add(raw, "Build.MODEL", build.model, warnings)
        readProperty("ro.soc.model", raw, warnings)
        readProperty("ro.soc.manufacturer", raw, warnings)
        readProperty("ro.hardware", raw, warnings)
        when (val cpuInfo = nodes.read("/proc/cpuinfo")) {
            is ProbeValue.Available -> parseCpuInfo(cpuInfo.value).forEach { (key, value) -> add(raw, "/proc/cpuinfo $key", value, warnings) }
            ProbeValue.PermissionDenied -> warnings += "/proc/cpuinfo permission denied"
            is ProbeValue.Malformed -> warnings += "/proc/cpuinfo malformed"
            else -> Unit
        }
        readNode("/sys/devices/system/cpu/present", raw, warnings)
        readNode("/sys/devices/system/cpu/possible", raw, warnings)

        val modelSources = listOf(
            "Build.SOC_MODEL", "getprop ro.soc.model", "/proc/cpuinfo model name", "/proc/cpuinfo Hardware",
            "Build.HARDWARE", "getprop ro.hardware", "Build.BOARD", "Build.DEVICE", "Build.MODEL",
        )
        val modelEntry = modelSources.firstNotNullOfOrNull { source -> raw[source]?.let { source to it } }
        val manufacturer = raw["Build.SOC_MANUFACTURER"] ?: raw["getprop ro.soc.manufacturer"]
            ?: raw["/proc/cpuinfo vendor_id"] ?: raw["/proc/cpuinfo CPU implementer"]
        val coreCount = parseCpuRange(raw["/sys/devices/system/cpu/present"])
            ?: parseCpuRange(raw["/sys/devices/system/cpu/possible"])
            ?: runtimeCoreCount().coerceAtLeast(1)
        val confidence = when (modelEntry?.first) {
            "Build.SOC_MODEL" -> IdentityConfidence.HIGH
            "getprop ro.soc.model", "/proc/cpuinfo model name", "/proc/cpuinfo Hardware" -> IdentityConfidence.MEDIUM
            null -> IdentityConfidence.UNAVAILABLE
            else -> IdentityConfidence.LOW
        }
        if (confidence == IdentityConfidence.LOW) warnings += "CPU model is a hardware/device fallback, not a verified SoC marketing name"
        val state = when {
            modelEntry != null && manufacturer != null -> CapabilityState.AVAILABLE
            modelEntry != null || manufacturer != null || raw.isNotEmpty() -> CapabilityState.PARTIAL
            warnings.any { "permission denied" in it } -> CapabilityState.PERMISSION_DENIED
            warnings.any { "malformed" in it } -> CapabilityState.MALFORMED
            else -> CapabilityState.UNAVAILABLE
        }
        return CpuIdentity(
            manufacturer = manufacturer,
            model = modelEntry?.second,
            hardware = raw["Build.HARDWARE"] ?: raw["getprop ro.hardware"],
            board = raw["Build.BOARD"],
            coreCount = coreCount,
            supportedAbis = build.supportedAbis.filter(String::isNotBlank),
            identitySource = modelEntry?.first,
            confidence = confidence,
            capabilityState = state,
            rawSources = raw.toMap(),
            warnings = warnings.distinct(),
        )
    }

    private suspend fun readProperty(name: String, raw: MutableMap<String, String>, warnings: MutableList<String>) {
        when (val value = properties.read(name)) {
            is ProbeValue.Available -> add(raw, "getprop $name", value.value, warnings)
            ProbeValue.PermissionDenied -> warnings += "getprop $name permission denied"
            is ProbeValue.Malformed -> warnings += "getprop $name malformed"
            is ProbeValue.Error -> warnings += "getprop $name: ${value.reason}"
            ProbeValue.Missing -> Unit
        }
    }

    private suspend fun readNode(path: String, raw: MutableMap<String, String>, warnings: MutableList<String>) {
        when (val value = nodes.read(path)) {
            is ProbeValue.Available -> add(raw, path, value.value, warnings)
            ProbeValue.PermissionDenied -> warnings += "$path permission denied"
            is ProbeValue.Malformed -> warnings += "$path malformed"
            else -> Unit
        }
    }

    private fun add(raw: MutableMap<String, String>, key: String, value: String?, warnings: MutableList<String>) {
        val cleaned = value?.trim()?.takeIf { it.isNotBlank() && it.length <= 256 && it.none { char -> char == '\u0000' } }
        if (cleaned != null) raw[key] = cleaned else if (!value.isNullOrBlank()) warnings += "$key malformed"
    }

    companion object {
        fun parseCpuRange(raw: String?): Int? {
            if (raw.isNullOrBlank()) return null
            val indexes = mutableSetOf<Int>()
            for (segment in raw.trim().split(',')) {
                val trimmed = segment.trim()
                if (trimmed.isEmpty()) return null
                if ('-' in trimmed) {
                    val parts = trimmed.split('-', limit = 2)
                    val start = parts.getOrNull(0)?.toIntOrNull()
                    val end = parts.getOrNull(1)?.toIntOrNull()
                    if (start == null || end == null || start !in 0..1023 || end !in start..1023) return null
                    indexes += start..end
                } else {
                    val index = trimmed.toIntOrNull() ?: return null
                    if (index !in 0..1023) return null
                    indexes += index
                }
            }
            return indexes.size.takeIf { it > 0 }
        }

        fun parseCpuInfo(raw: String): Map<String, String> = buildMap {
            raw.lineSequence().take(2_048).forEach { line ->
                val separator = line.indexOf(':')
                if (separator > 0) {
                    val key = line.substring(0, separator).trim()
                    val value = line.substring(separator + 1).trim()
                    if (key in setOf("Hardware", "model name", "vendor_id", "CPU implementer") && value.isNotBlank()) putIfAbsent(key, value)
                }
            }
        }
    }
}

data class NormalizedCpuFrequency(val hz: Long, val sourceUnit: String, val warning: String? = null)

object CpuFrequencyParser {
    private const val MIN_HZ = 100_000L
    private const val MAX_HZ = 8_000_000_000L

    fun normalize(raw: String?, nodePath: String): NormalizedCpuFrequency? {
        val value = raw?.trim()?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        val lower = nodePath.lowercase()
        val result = when {
            lower.contains("hz") && !lower.contains("khz") && value in MIN_HZ..MAX_HZ ->
                NormalizedCpuFrequency(value, "Hz")
            value in 100_000L..10_000_000L ->
                NormalizedCpuFrequency(value * 1_000L, "kHz", "Normalized cpufreq value from kHz")
            value in 100L..10_000L ->
                NormalizedCpuFrequency(value * 1_000_000L, "MHz", "Normalized cpufreq value from MHz")
            else -> return null
        }
        return result.takeIf { it.hz in MIN_HZ..MAX_HZ }
    }
}

data class NormalizedFrequency(val hz: Long, val sourceUnit: String, val warning: String?)

data class GpuBusyCounters(val busy: Long, val total: Long)

object GpuParsers {
    fun parseKgslBusyCounters(raw: String): GpuBusyCounters? {
        val values = raw.trim().split(Regex("\\s+")).map { it.toLongOrNull() }
        if (values.size != 2 || values.any { it == null || it < 0L }) return null
        val busy = requireNotNull(values[0])
        val total = requireNotNull(values[1])
        return GpuBusyCounters(busy, total).takeIf { it.total > 0L && it.busy <= it.total }
    }

    fun deltaUtilization(previous: GpuBusyCounters?, current: GpuBusyCounters?): Double? {
        if (previous == null || current == null) return null
        val busyDelta = current.busy - previous.busy
        val totalDelta = current.total - previous.total
        if (busyDelta < 0L || totalDelta <= 0L || busyDelta > totalDelta) return null
        return (busyDelta.toDouble() / totalDelta.toDouble() * 100.0).takeIf { it.isFinite() }
    }

    fun normalizeFrequency(raw: String): NormalizedFrequency? {
        val value = raw.trim().toLongOrNull()?.takeIf { it > 0 } ?: return null
        val normalized = when {
            value in 10_000_000L..5_000_000_000L -> NormalizedFrequency(value, "Hz", null)
            value in 10_000L..5_000_000L -> NormalizedFrequency(
                value * 1_000L,
                "kHz (magnitude validated)",
                "Frequency normalized from kHz based on validated GPU range",
            )
            else -> return null
        }
        return normalized.takeIf { it.hz in 10_000_000L..5_000_000_000L }
    }

    fun parseKgslBusy(raw: String): Double? {
        val fields = raw.trim().split(Regex("\\s+")).mapNotNull(String::toLongOrNull)
        if (fields.size != 2) return null
        val busy = fields[0]
        val total = fields[1]
        if (busy < 0 || total <= 0 || busy > total) return null
        return (busy.toDouble() / total * 100.0).takeIf { it in 0.0..100.0 }
    }
}

class AndroidGpuCapabilityProvider(
    private val nodes: HardwareNodeSource = FileHardwareNodeSource(),
    private val rootGateway: RootGateway? = null,
    private val platformHardware: String = Build.HARDWARE,
) : GpuCapabilityProvider {
    private var previousBusy: GpuBusyCounters? = null

    override suspend fun sample(): GpuTelemetry {
        val warnings = mutableListOf<String>()
        val kgslLikely = nodes.exists(KGSL) || platformHardware.contains(Regex("qcom|qualcomm|msm|sm[0-9]", RegexOption.IGNORE_CASE))
        if (kgslLikely) return sampleKgsl(warnings)

        val candidate = nodes.directories("/sys/class/devfreq").firstOrNull { path ->
            val name = File(path).name.lowercase()
            listOf("gpu", "mali", "adreno", "kgsl").any(name::contains)
        } ?: return GpuTelemetry(
            vendor = null,
            model = null,
            currentFrequencyHz = null,
            minimumFrequencyHz = null,
            maximumFrequencyHz = null,
            frequencySource = null,
            capabilityState = GpuCapabilityState.GPU_NOT_IDENTIFIED,
            warnings = listOf("No recognized GPU driver topology was discovered"),
        )
        val name = File(candidate).name
        val lower = name.lowercase()
        val vendor = when {
            "mali" in lower -> "Arm"
            "adreno" in lower || "kgsl" in lower -> "Qualcomm"
            else -> null
        }
        val current = frequency("$candidate/cur_freq", null, warnings)
        val minimum = frequency("$candidate/min_freq", null, warnings)
        val maximum = frequency("$candidate/max_freq", null, warnings)
        return GpuTelemetry(
            vendor = vendor,
            model = name,
            currentFrequencyHz = current?.first?.hz,
            minimumFrequencyHz = minimum?.first?.hz,
            maximumFrequencyHz = maximum?.first?.hz,
            frequencySource = current?.second,
            driver = when { "mali" in lower -> "Mali devfreq"; "adreno" in lower || "kgsl" in lower -> "Adreno devfreq"; else -> "Android devfreq" },
            driverPath = candidate,
            identitySource = "devfreq directory name",
            capabilityState = if (current != null) GpuCapabilityState.IDENTIFIED_UTILIZATION_INACCESSIBLE else GpuCapabilityState.IDENTIFIED_FREQUENCY_INACCESSIBLE,
            warnings = (warnings + "GPU utilization is unavailable for this driver topology").distinct(),
        )
    }

    private suspend fun sampleKgsl(warnings: MutableList<String>): GpuTelemetry {
        val model = text("$KGSL/gpu_model", RootCommand.READ_KGSL_GPU_MODEL, warnings)
        val current = frequency("$KGSL/gpuclk", RootCommand.READ_KGSL_GPUCLK, warnings)
            ?: frequency("$KGSL/devfreq/cur_freq", RootCommand.READ_KGSL_DEVFREQ_CUR, warnings)
        val minimum = frequency("$KGSL/devfreq/min_freq", RootCommand.READ_KGSL_DEVFREQ_MIN, warnings)
        val maximum = frequency("$KGSL/max_gpuclk", RootCommand.READ_KGSL_MAX_GPUCLK, warnings)
            ?: frequency("$KGSL/devfreq/max_freq", RootCommand.READ_KGSL_DEVFREQ_MAX, warnings)
        val busy = text("$KGSL/gpubusy", RootCommand.READ_KGSL_GPUBUSY, warnings)
        val counters = busy?.first?.let(GpuParsers::parseKgslBusyCounters)
        val previous = previousBusy
        previousBusy = counters
        val utilization = GpuParsers.deltaUtilization(previous, counters)
        val utilizationReason = when {
            busy == null -> "GPU busy counters are unavailable"
            counters == null -> "GPU busy counters are malformed"
            previous == null -> "Collecting initial GPU busy samples"
            utilization == null -> "GPU busy counters reset or produced an invalid delta"
            else -> null
        }
        if (utilizationReason != null) warnings += utilizationReason
        val permissionDenied = warnings.any { "permission denied" in it.lowercase() }
        val rootRequired = warnings.any { "root unavailable" in it.lowercase() }
        val state = when {
            model == null && current == null && permissionDenied -> GpuCapabilityState.PERMISSION_DENIED
            model == null && current == null && rootRequired -> GpuCapabilityState.ROOT_REQUIRED
            model == null && current == null -> GpuCapabilityState.DRIVER_PATH_UNAVAILABLE
            current == null -> GpuCapabilityState.IDENTIFIED_FREQUENCY_INACCESSIBLE
            utilization == null -> GpuCapabilityState.IDENTIFIED_UTILIZATION_INACCESSIBLE
            else -> GpuCapabilityState.IDENTIFIED_TELEMETRY_AVAILABLE
        }
        return GpuTelemetry(
            vendor = "Qualcomm",
            model = model?.first,
            currentFrequencyHz = current?.first?.hz,
            minimumFrequencyHz = minimum?.first?.hz,
            maximumFrequencyHz = maximum?.first?.hz,
            frequencySource = current?.second,
            driver = "KGSL",
            driverPath = KGSL,
            utilizationPercent = utilization,
            identitySource = model?.second,
            utilizationSource = utilization?.let { busy?.second },
            utilizationAvailabilityReason = utilizationReason,
            capabilityState = state,
            warnings = warnings.distinct(),
        )
    }

    private suspend fun frequency(path: String, command: RootCommand?, warnings: MutableList<String>): Pair<NormalizedFrequency, String>? {
        val value = text(path, command, warnings) ?: return null
        val normalized = GpuParsers.normalizeFrequency(value.first)
        if (normalized == null) {
            warnings += "$path reported an unrecognized frequency unit or range"
            return null
        }
        normalized.warning?.let(warnings::add)
        return normalized to value.second
    }

    private suspend fun text(path: String, command: RootCommand?, warnings: MutableList<String>): Pair<String, String>? {
        when (val normal = nodes.read(path)) {
            is ProbeValue.Available -> return normal.value to path
            ProbeValue.PermissionDenied -> warnings += "$path permission denied"
            is ProbeValue.Malformed -> warnings += "$path malformed"
            is ProbeValue.Error -> warnings += "$path: ${normal.reason}"
            ProbeValue.Missing -> Unit
        }
        if (command == null || rootGateway == null) return null
        val root = rootGateway.execute(command)
        return when {
            root.succeeded && root.stdout.isNotBlank() -> root.stdout.trim() to "root:$path"
            root.timedOut -> null.also { warnings += "$path root read timed out" }
            root.cancelled -> null.also { warnings += "$path root read cancelled" }
            root.exitCode == 126 -> null.also { warnings += "$path root unavailable" }
            root.stderr.contains("denied", true) -> null.also { warnings += "$path root permission denied" }
            else -> null
        }
    }

    private companion object { const val KGSL = "/sys/class/kgsl/kgsl-3d0" }
}
