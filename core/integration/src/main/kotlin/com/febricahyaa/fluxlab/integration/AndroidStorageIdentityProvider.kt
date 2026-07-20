package com.febricahyaa.fluxlab.integration

import android.content.Context
import android.os.StatFs
import com.febricahyaa.fluxlab.model.BatteryPowerConfidence
import com.febricahyaa.fluxlab.model.IdentityConfidence
import com.febricahyaa.fluxlab.model.StorageHealth
import com.febricahyaa.fluxlab.model.StorageIdentity
import com.febricahyaa.fluxlab.model.StorageLifetimeParser
import com.febricahyaa.fluxlab.model.StorageTelemetry
import com.febricahyaa.fluxlab.model.StorageType
import com.febricahyaa.fluxlab.model.StorageIdentityProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads storage metadata opportunistically; missing nodes are normal capability states. */
class AndroidStorageIdentityProvider(context: Context) : StorageIdentityProvider {
    private val appContext = context.applicationContext

    override suspend fun read(): StorageTelemetry = withContext(Dispatchers.IO) {
        val mounts = readMounts()
        val candidates = File("/sys/block").listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name !in IGNORED_BLOCKS && !it.name.startsWith("loop") }
            ?.take(64)
            ?.toList()
            .orEmpty()
        val device = candidates.firstOrNull { it.name.startsWith("mmcblk") || it.name.startsWith("sd") }
            ?: candidates.firstOrNull()
        if (device == null) {
            return@withContext StorageTelemetry(
                identity = StorageIdentity(storageType = StorageType.UNKNOWN, warnings = listOf("No readable block storage topology was found")),
            )
        }
        val devicePath = File(device, "device")
        val model = readFirst(devicePath, listOf("model", "name", "product"))
        val vendor = readFirst(devicePath, listOf("vendor", "manufacturer"))
        val revision = readFirst(devicePath, listOf("rev", "revision"))
        val type = StorageParsers.detectType(device.name, model, devicePath.path)
        val logical = readLong(File(device, "queue/logical_block_size"))
        val physical = readLong(File(device, "queue/physical_block_size"))
        val sectorCount = readLong(File(device, "size"))
        val blockSize = logical ?: 512L
        val total = sectorCount?.let { count -> count.takeIf { it > 0L }?.times(512L) }
        val mount = mounts.firstOrNull { it.blockDevice.endsWith(device.name) || it.blockDevice == device.path }?.mountPoint
        val stat = mount?.let { runCatching { StatFs(it) }.getOrNull() }
        val available = stat?.availableBlocksLong?.times(stat.blockSizeLong)
        val filesystem = mounts.firstOrNull { it.mountPoint == mount }?.filesystem
        val identityWarnings = buildList {
            if (model == null) add("Storage model is not exposed")
            if (type == StorageType.UNKNOWN) add("Storage type could not be verified from block metadata")
            if (type == StorageType.VIRTUAL) add("Virtual storage does not expose physical lifetime descriptors")
        }
        val identity = StorageIdentity(
            storageType = type,
            storageModel = model,
            storageVendor = vendor,
            storageRevision = revision,
            blockDevice = device.path,
            logicalBlockSize = logical,
            physicalBlockSize = physical,
            totalCapacityBytes = total,
            availableCapacityBytes = available,
            filesystem = filesystem,
            mountPoint = mount,
            identitySource = "${device.path} and /proc/mounts",
            identityConfidence = when {
                type == StorageType.UNKNOWN -> IdentityConfidence.LOW
                model != null -> IdentityConfidence.MEDIUM
                else -> IdentityConfidence.LOW
            },
            warnings = identityWarnings,
        )
        val health = readHealth(devicePath, type)
        StorageTelemetry(identity, health, android.os.SystemClock.elapsedRealtime())
    }

    private fun readHealth(device: File, type: StorageType): StorageHealth {
        val candidates = listOf(
            "life_time_estimation_a", "life_time_estimation_b", "life_time_estimate_a",
            "device_life_time_est_typ_a", "device_life_time_est_typ_b", "pre_eol_info", "ext_csd",
        )
        val values = candidates.mapNotNull { name -> readText(File(device, name))?.let { name to it } }.toMap()
        if (values.isEmpty()) return StorageHealth(source = device.path, warnings = listOf("Storage lifetime descriptor is unavailable"))
        val a = StorageLifetimeParser.parseBucket(values["life_time_estimation_a"] ?: values["device_life_time_est_typ_a"], device.path)
        val b = StorageLifetimeParser.parseBucket(values["life_time_estimation_b"] ?: values["device_life_time_est_typ_b"], device.path)
        val emmc = values["ext_csd"]?.let { StorageLifetimeParser.parseEmmcExtCsd(it, device.path) }
        return when {
            type == StorageType.EMMC && emmc != null -> emmc
            else -> StorageHealth(
                state = listOf(a.normalizedState, b.normalizedState).maxByOrNull { it.ordinal } ?: com.febricahyaa.fluxlab.model.StorageHealthState.INFORMATION_UNAVAILABLE,
                lifetimeA = a,
                lifetimeB = b,
                rawDescriptor = values.entries.joinToString(";") { "${it.key}=${it.value}" },
                source = device.path,
                confidence = if (a.confidence == BatteryPowerConfidence.MEDIUM || b.confidence == BatteryPowerConfidence.MEDIUM) BatteryPowerConfidence.MEDIUM else BatteryPowerConfidence.LOW,
                warnings = listOfNotNull(a.warning, b.warning),
            )
        }
    }

    private fun readMounts(): List<MountInfo> = readText(File("/proc/mounts"))?.lineSequence()?.mapNotNull { line ->
        val parts = line.split(' ')
        if (parts.size >= 3) MountInfo(parts[0], parts[1], parts[2]) else null
    }?.toList().orEmpty()

    private fun readFirst(parent: File, names: List<String>): String? = names.firstNotNullOfOrNull { name -> readText(File(parent, name)) }
    private fun readLong(file: File): Long? = readText(file)?.trim()?.toLongOrNull()?.takeIf { it >= 0L }
    private fun readText(file: File): String? = runCatching {
        file.takeIf { it.isFile && it.canRead() }?.readText()?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()

    private data class MountInfo(val blockDevice: String, val mountPoint: String, val filesystem: String)

    private companion object { val IGNORED_BLOCKS = setOf("ram0", "ram1", "zram0", "dm-0", "dm-1") }
}

object StorageParsers {
    fun detectType(blockName: String, model: String?, devicePath: String): StorageType {
        val value = listOf(blockName, model.orEmpty(), devicePath).joinToString(" ").lowercase()
        return when {
            "/virtual/" in value || blockName.startsWith("loop") -> StorageType.VIRTUAL
            "ufs" in value -> StorageType.UFS
            blockName.startsWith("mmcblk") || "emmc" in value || "ext_csd" in value -> StorageType.EMMC
            blockName.isBlank() -> StorageType.MALFORMED
            else -> StorageType.UNKNOWN
        }
    }
}
