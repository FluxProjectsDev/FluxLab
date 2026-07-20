package com.febricahyaa.fluxlab.model

enum class StorageType { UFS, EMMC, UNKNOWN, VIRTUAL, UNSUPPORTED, PERMISSION_DENIED, MALFORMED }

enum class StorageHealthState {
    HEALTHY,
    WEAR_DETECTED,
    HIGH_WEAR,
    NEAR_END_OF_LIFE,
    INFORMATION_UNAVAILABLE,
    DESCRIPTOR_UNAVAILABLE,
    DESCRIPTOR_MALFORMED,
}

data class StorageLifetimeEstimate(
    val rawValue: String? = null,
    val rangeStartPercent: Int? = null,
    val rangeEndPercent: Int? = null,
    val normalizedState: StorageHealthState = StorageHealthState.INFORMATION_UNAVAILABLE,
    val source: String? = null,
    val parserVersion: Int = 1,
    val confidence: BatteryPowerConfidence = BatteryPowerConfidence.UNAVAILABLE,
    val warning: String? = null,
)

data class StorageIdentity(
    val storageType: StorageType = StorageType.UNKNOWN,
    val storageModel: String? = null,
    val storageVendor: String? = null,
    val storageRevision: String? = null,
    val blockDevice: String? = null,
    val logicalBlockSize: Long? = null,
    val physicalBlockSize: Long? = null,
    val totalCapacityBytes: Long? = null,
    val availableCapacityBytes: Long? = null,
    val filesystem: String? = null,
    val mountPoint: String? = null,
    val encryptionState: String? = null,
    val identitySource: String? = null,
    val identityConfidence: IdentityConfidence = IdentityConfidence.UNAVAILABLE,
    val warnings: List<String> = emptyList(),
)

data class StorageHealth(
    val state: StorageHealthState = StorageHealthState.INFORMATION_UNAVAILABLE,
    val lifetimeA: StorageLifetimeEstimate = StorageLifetimeEstimate(),
    val lifetimeB: StorageLifetimeEstimate = StorageLifetimeEstimate(),
    val preEndOfLife: String? = null,
    val rawDescriptor: String? = null,
    val source: String? = null,
    val parserVersion: Int = 1,
    val confidence: BatteryPowerConfidence = BatteryPowerConfidence.UNAVAILABLE,
    val warnings: List<String> = emptyList(),
)

data class StorageTelemetry(
    val identity: StorageIdentity = StorageIdentity(),
    val health: StorageHealth = StorageHealth(),
    val sampledAtElapsedRealtimeMs: Long? = null,
)

interface StorageIdentityProvider {
    suspend fun read(): StorageTelemetry
}

object StorageLifetimeParser {
    /** UFS and eMMC lifetime nibbles use coarse 10% buckets, not exact percentages. */
    fun parseBucket(raw: String?, source: String?): StorageLifetimeEstimate {
        val cleaned = raw?.trim()?.removePrefix("0x")?.removePrefix("0X")
        val value = cleaned?.toIntOrNull(16) ?: return StorageLifetimeEstimate(
            rawValue = raw,
            source = source,
            normalizedState = if (raw.isNullOrBlank()) StorageHealthState.DESCRIPTOR_UNAVAILABLE else StorageHealthState.DESCRIPTOR_MALFORMED,
            confidence = if (raw.isNullOrBlank()) BatteryPowerConfidence.UNAVAILABLE else BatteryPowerConfidence.LOW,
            warning = if (raw.isNullOrBlank()) null else "Reserved or malformed lifetime descriptor",
        )
        if (value !in 1..10) return StorageLifetimeEstimate(
            rawValue = raw,
            source = source,
            normalizedState = StorageHealthState.DESCRIPTOR_MALFORMED,
            confidence = BatteryPowerConfidence.LOW,
            warning = "Reserved lifetime descriptor value",
        )
        val start = (value - 1) * 10
        val state = when {
            value <= 2 -> StorageHealthState.HEALTHY
            value <= 5 -> StorageHealthState.WEAR_DETECTED
            value <= 8 -> StorageHealthState.HIGH_WEAR
            else -> StorageHealthState.NEAR_END_OF_LIFE
        }
        return StorageLifetimeEstimate(raw, start, value * 10, state, source, 1, BatteryPowerConfidence.MEDIUM)
    }

    fun parseEmmcExtCsd(raw: String?, source: String?): StorageHealth {
        if (raw.isNullOrBlank()) return StorageHealth(source = source, warnings = listOf("eMMC lifetime descriptor is unavailable"))
        val fields = raw.lineSequence().mapNotNull { line ->
            val split = line.split('=', limit = 2)
            if (split.size == 2) split[0].trim().uppercase() to split[1].trim() else null
        }.toMap()
        val a = parseBucket(fields["DEVICE_LIFE_TIME_EST_TYP_A"], source)
        val b = parseBucket(fields["DEVICE_LIFE_TIME_EST_TYP_B"], source)
        val pre = fields["PRE_EOL_INFO"]
        val preValid = pre?.removePrefix("0x")?.toIntOrNull(16)?.takeIf { it in 1..3 }
        val warnings = listOfNotNull(a.warning, b.warning, if (pre != null && preValid == null) "Reserved PRE_EOL_INFO value" else null)
        val state = listOf(a.normalizedState, b.normalizedState).maxByOrNull { it.ordinal } ?: StorageHealthState.DESCRIPTOR_UNAVAILABLE
        return StorageHealth(state, a, b, preValid?.toString(), raw, source, 1, BatteryPowerConfidence.MEDIUM, warnings)
    }
}

object StorageSafety {
    fun canAllocate(availableBytes: Long?, requestedBytes: Long, safetyMarginBytes: Long = 64L * 1_048_576L): Boolean {
        if (availableBytes == null || requestedBytes <= 0L || safetyMarginBytes < 0L) return false
        return requestedBytes <= availableBytes && availableBytes - requestedBytes >= safetyMarginBytes
    }
}

object StorageMetricLabels {
    const val BUFFERED_READ = "Buffered storage read"
    const val CACHED_READ = "Repeated cached storage read"
    const val BUFFERED_WRITE = "Buffered storage write"
    const val DURABLE_WRITE = "Durable storage write"
    const val FSYNC_LATENCY = "Fsync latency"
    const val FILE_CREATION_LATENCY = "File creation latency"
    const val FILE_CLOSE_LATENCY = "File close latency"
}
