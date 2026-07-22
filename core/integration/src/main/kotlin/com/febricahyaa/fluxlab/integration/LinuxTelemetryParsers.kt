package com.febricahyaa.fluxlab.integration

/**
 * Linux CPU counters are cumulative jiffies. The parser keeps only validated
 * numeric fields; interval math is deliberately separate and deterministic.
 */
data class CpuTimes(val fields: List<Long>) {
    private val normalized: List<Long> get() = (fields.take(10) + List((10 - fields.size).coerceAtLeast(0)) { 0L })
    val total: Long get() = normalized.fold(0L) { sum, value -> sum + value }
    val idle: Long get() = normalized[3] + normalized[4]
}

enum class CpuSampleState { COLLECTING_INITIAL_SAMPLES, ACTIVE, TEMPORARILY_UNAVAILABLE }

object ProcStatParser {
    private val cpuName = Regex("cpu\\d*")

    fun parse(text: String): Map<String, CpuTimes> = text.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        val name = parts.firstOrNull() ?: return@mapNotNull null
        if (!cpuName.matches(name)) return@mapNotNull null
        val rawValues = parts.drop(1)
        if (rawValues.size < 4 || rawValues.size > 10) return@mapNotNull null
        val values = rawValues.map { it.toLongOrNull() }
        if (values.any { it == null || it < 0L }) return@mapNotNull null
        name to CpuTimes(values.map { requireNotNull(it) })
    }.toMap()

    fun usage(previous: CpuTimes?, current: CpuTimes?): Double? {
        val previousValues = previous?.normalizedFields() ?: return null
        val currentValues = current?.normalizedFields() ?: return null
        if (currentValues.indices.any { currentValues[it] < previousValues[it] }) return null
        val previousTotal = previous.safeTotal() ?: return null
        val currentTotal = current.safeTotal() ?: return null
        val previousIdle = previous.safeIdle() ?: return null
        val currentIdle = current.safeIdle() ?: return null
        val totalDelta = currentTotal - previousTotal
        if (currentTotal < previousTotal || currentIdle < previousIdle) return null
        val idleDelta = currentIdle - previousIdle
        if (totalDelta <= 0L || idleDelta < 0L || idleDelta > totalDelta) return null
        return ((totalDelta - idleDelta).toDouble() / totalDelta.toDouble() * 100.0)
            .takeIf { it.isFinite() }
            ?.coerceIn(0.0, 100.0)
    }

    fun snapshotUsage(previous: Map<String, CpuTimes>, current: Map<String, CpuTimes>): Map<String, Double?> =
        current.keys.associateWith { name ->
            if (name !in previous) null else usage(previous[name], current[name])
        }

    fun sampleState(previous: Map<String, CpuTimes>, current: Map<String, CpuTimes>): CpuSampleState =
        if (current.isEmpty() || previous.isEmpty()) CpuSampleState.COLLECTING_INITIAL_SAMPLES
        else if (current.keys.any { it != "cpu" && it !in previous } || previous.keys.any { it != "cpu" && it !in current }) CpuSampleState.TEMPORARILY_UNAVAILABLE
        else CpuSampleState.ACTIVE

    private fun CpuTimes.normalizedFields(): List<Long> =
        (fields.take(10) + List((10 - fields.size).coerceAtLeast(0)) { 0L })

    private fun CpuTimes.safeTotal(): Long? = runCatching {
        normalizedFields().fold(0L) { sum, value -> Math.addExact(sum, value) }
    }.getOrNull()

    private fun CpuTimes.safeIdle(): Long? = runCatching {
        Math.addExact(normalizedFields()[3], normalizedFields()[4])
    }.getOrNull()
}

object MemInfoParser {
    fun parse(text: String): Map<String, Long> = text.lineSequence().mapNotNull { line ->
        val split = line.indexOf(':')
        if (split <= 0) return@mapNotNull null
        val valueParts = line.substring(split + 1).trim().split(Regex("\\s+"))
        val value = valueParts.firstOrNull()?.toLongOrNull()?.takeIf { it >= 0L }
        value?.let { line.substring(0, split).trim() to it }
    }.toMap()

    /** Cached memory is reclaimable kernel cache, excluding shared memory. */
    fun cachedKb(values: Map<String, Long>): Long? {
        val cached = (values["Cached"] ?: 0L) + (values["SReclaimable"] ?: 0L)
        val shmem = values["Shmem"] ?: 0L
        return (cached - shmem).coerceAtLeast(0L).takeIf { cached > 0L }
    }

    /** Used memory excludes MemAvailable, buffers, and reclaimable cache. */
    fun usedKb(values: Map<String, Long>): Long? {
        val total = values["MemTotal"] ?: return null
        val free = values["MemFree"] ?: return null
        val buffers = values["Buffers"] ?: 0L
        val cache = cachedKb(values) ?: 0L
        return (total - free - buffers - cache).coerceAtLeast(0L)
    }
}

data class PsiValues(
    val someAvg10: Double?,
    val fullAvg10: Double?,
    val someAvg60: Double? = null,
    val someAvg300: Double? = null,
    val fullAvg60: Double? = null,
    val fullAvg300: Double? = null,
)

object PsiParser {
    fun parse(text: String): PsiValues {
        fun averages(prefix: String): List<Double?> {
            val line = text.lineSequence().firstOrNull { it.trimStart().startsWith(prefix) }
                ?: return listOf(null, null, null)
            val values = line.trim().split(Regex("\\s+")).drop(1).associate {
                it.substringBefore('=') to it.substringAfter('=', "").toDoubleOrNull()
            }
            return listOf(values["avg10"], values["avg60"], values["avg300"])
        }
        val some = averages("some")
        val full = averages("full")
        return PsiValues(some[0], full[0], some[1], some[2], full[1], full[2])
    }
}

data class ZramTelemetry(
    val diskSizeBytes: Long?,
    val memoryUsedBytes: Long?,
    val originalDataBytes: Long?,
    val compressedDataBytes: Long?,
) {
    val compressionRatio: Double?
        get() = if (compressedDataBytes != null && compressedDataBytes > 0L && originalDataBytes != null) {
            originalDataBytes.toDouble() / compressedDataBytes.toDouble()
        } else null
}

object ZramParser {
    fun parse(values: Map<String, Long>): ZramTelemetry = ZramTelemetry(
        diskSizeBytes = values["disksize"]?.takeIf { it > 0L },
        memoryUsedBytes = values["mem_used_total"]?.takeIf { it >= 0L },
        originalDataBytes = values["orig_data_size"]?.takeIf { it >= 0L },
        compressedDataBytes = values["compr_data_size"]?.takeIf { it >= 0L },
    )
}

object BatteryPowerEstimator {
    fun watts(currentMicroamps: Long?, voltageMillivolts: Long?): Double? {
        if (currentMicroamps == null || voltageMillivolts == null) return null
        if (currentMicroamps == Long.MIN_VALUE || currentMicroamps == 0L || voltageMillivolts <= 0L) return null
        return kotlin.math.abs(currentMicroamps.toDouble()) * voltageMillivolts / 1_000_000.0 / 1_000.0
    }
}
