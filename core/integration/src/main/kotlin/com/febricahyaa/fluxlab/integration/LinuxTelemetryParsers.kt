package com.febricahyaa.fluxlab.integration

data class CpuTimes(val fields: List<Long>) {
    val total: Long get() = fields.sum()
    val idle: Long get() = fields.getOrElse(3) { 0L } + fields.getOrElse(4) { 0L }
}

object ProcStatParser {
    fun parse(text: String): Map<String, CpuTimes> = text.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        val name = parts.firstOrNull() ?: return@mapNotNull null
        if (name != "cpu" && !name.matches(Regex("cpu\\d+"))) return@mapNotNull null
        val values = parts.drop(1).mapNotNull(String::toLongOrNull)
        if (values.size < 4) null else name to CpuTimes(values)
    }.toMap()

    fun usage(previous: CpuTimes?, current: CpuTimes?): Double? {
        if (previous == null || current == null) return null
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        if (totalDelta <= 0L || idleDelta < 0L) return null
        return ((totalDelta - idleDelta).toDouble() / totalDelta.toDouble() * 100.0).coerceIn(0.0, 100.0)
    }
}

object MemInfoParser {
    fun parse(text: String): Map<String, Long> = text.lineSequence().mapNotNull { line ->
        val split = line.indexOf(':')
        if (split <= 0) return@mapNotNull null
        val value = line.substring(split + 1).trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
        value?.let { line.substring(0, split) to it }
    }.toMap()
}

data class PsiValues(val someAvg10: Double?, val fullAvg10: Double?)

object PsiParser {
    fun parse(text: String): PsiValues {
        fun average(prefix: String): Double? = text.lineSequence().firstOrNull { it.startsWith(prefix) }
            ?.split(Regex("\\s+"))
            ?.firstOrNull { it.startsWith("avg10=") }
            ?.substringAfter('=')
            ?.toDoubleOrNull()
        return PsiValues(average("some "), average("full "))
    }
}

object BatteryPowerEstimator {
    fun watts(currentMicroamps: Long?, voltageMillivolts: Long?): Double? {
        if (currentMicroamps == null || voltageMillivolts == null) return null
        if (currentMicroamps == Long.MIN_VALUE || currentMicroamps == 0L || voltageMillivolts <= 0L) return null
        return kotlin.math.abs(currentMicroamps.toDouble()) * voltageMillivtsToVolts(voltageMillivolts) / 1_000_000.0
    }

    private fun voltageMillivtsToVolts(value: Long): Double = value / 1_000.0
}
