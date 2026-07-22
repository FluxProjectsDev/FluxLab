package com.febricahyaa.fluxlab.integration

import android.content.Context
import android.os.StatFs
import android.os.SystemClock
import com.febricahyaa.fluxlab.model.BatteryPowerConfidence
import com.febricahyaa.fluxlab.model.IdentityConfidence
import com.febricahyaa.fluxlab.model.StorageHealth
import com.febricahyaa.fluxlab.model.StorageHealthAvailability
import com.febricahyaa.fluxlab.model.StorageIdentity
import com.febricahyaa.fluxlab.model.StorageLifetimeParser
import com.febricahyaa.fluxlab.model.StorageTelemetry
import com.febricahyaa.fluxlab.model.StorageType
import com.febricahyaa.fluxlab.model.StorageIdentityProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolves the app-private mount through device-mapper and physical block topology. */
class AndroidStorageIdentityProvider(context: Context) : StorageIdentityProvider {
    private val appContext = context.applicationContext

    override suspend fun read(): StorageTelemetry = withContext(Dispatchers.IO) {
        val mounts = readMounts()
        val mount = mounts
            .filter { appContext.filesDir.absolutePath == it.mountPoint || appContext.filesDir.absolutePath.startsWith(it.mountPoint + "/") }
            .maxByOrNull { it.mountPoint.length }
        val logicalName = mount?.blockDevice?.let(::resolveBlockName)
        if (logicalName == null) {
            return@withContext StorageTelemetry(
                identity = StorageIdentity(
                    storageType = StorageType.UNKNOWN,
                    mountPoint = mount?.mountPoint,
                    filesystem = mount?.filesystem,
                    identitySource = "/proc/mounts",
                    warnings = listOf("The application-private filesystem mount source could not be resolved"),
                ),
                sampledAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            )
        }

        val nodes = loadTopology(logicalName)
        val resolved = StorageTopologyResolver.resolve(logicalName, nodes)
        val physicalName = resolved.physicalDevice ?: logicalName
        val physicalPath = File("/sys/class/block/$physicalName")
        val devicePath = File(physicalPath, "device")
        val model = readFirst(devicePath, listOf("model", "name", "product"))
        val vendor = readFirst(devicePath, listOf("vendor", "manufacturer"))
        val revision = readFirst(devicePath, listOf("rev", "revision"))
        val evidence = topologyEvidence(physicalPath, devicePath)
        val type = StorageParsers.detectType(physicalName, model, evidence.joinToString(" "))
        val logicalBlockSize = readLong(File(physicalPath, "queue/logical_block_size"))
        val physicalBlockSize = readLong(File(physicalPath, "queue/physical_block_size"))
        val sectorCount = readLong(File(physicalPath, "size"))
        val total = sectorCount?.let { count -> runCatching { Math.multiplyExact(count, 512L) }.getOrNull() }
        val stat = mount?.mountPoint?.let { runCatching { StatFs(it) }.getOrNull() }
        val available = stat?.let { runCatching { Math.multiplyExact(it.availableBlocksLong, it.blockSizeLong) }.getOrNull() }
        val transportEvidence = evidence.filter { it.contains("ufs", true) || it.contains("mmc", true) || it.contains("ufshcd", true) }
        val warnings = buildList {
            if (model == null) add("Storage model is not exposed")
            if (type == StorageType.UNKNOWN) add("Transport could not be verified from sysfs evidence")
            if (resolved.physicalDevice == null) add("No physical backing block device was resolved")
            if (type == StorageType.VIRTUAL) add("Virtual storage does not expose physical lifetime descriptors")
        }
        val identity = StorageIdentity(
            storageType = type,
            storageModel = model,
            storageVendor = vendor,
            storageRevision = revision,
            blockDevice = physicalPath.path,
            logicalBlockDevice = logicalName,
            physicalBackingDevice = resolved.physicalDevice?.let { "/sys/class/block/$it" },
            topologySteps = resolved.diagnostics,
            transportEvidence = transportEvidence,
            transportConfidence = StorageParsers.transportConfidence(type, transportEvidence),
            logicalBlockSize = logicalBlockSize,
            physicalBlockSize = physicalBlockSize,
            totalCapacityBytes = total,
            availableCapacityBytes = available,
            filesystem = mount?.filesystem,
            mountPoint = mount?.mountPoint,
            identitySource = "mount=" + mount.blockDevice + "; sysfs=" + physicalPath.path,
            identityConfidence = when {
                type == StorageType.UNKNOWN -> IdentityConfidence.LOW
                model != null && transportEvidence.isNotEmpty() -> IdentityConfidence.HIGH
                model != null -> IdentityConfidence.MEDIUM
                else -> IdentityConfidence.LOW
            },
            warnings = warnings,
        )
        StorageTelemetry(identity, readHealth(devicePath, type), SystemClock.elapsedRealtime())
    }

    private fun loadTopology(start: String): Map<String, BlockTopologyNode> {
        val nodes = linkedMapOf<String, BlockTopologyNode>()
        val pending = ArrayDeque<String>()
        pending += start
        while (pending.isNotEmpty() && nodes.size < 128) {
            val name = pending.removeFirst()
            if (name in nodes) continue
            val path = File("/sys/class/block/$name")
            if (!path.exists()) {
                nodes[name] = BlockTopologyNode(name)
                continue
            }
            val slaves = File(path, "slaves").listFiles()?.map { it.name }.orEmpty()
            val holders = File(path, "holders").listFiles()?.map { it.name }.orEmpty()
            val subsystem = canonical(File(path, "subsystem"))
            val device = canonical(File(path, "device"))
            nodes[name] = BlockTopologyNode(name, slaves, holders, subsystem, device)
            slaves.forEach { if (it !in nodes) pending += it }
        }
        return nodes
    }

    private fun topologyEvidence(block: File, device: File): List<String> = listOfNotNull(
        block.path,
        canonical(block),
        canonical(File(block, "subsystem")),
        canonical(device),
        canonical(File(device, "subsystem")),
        canonical(File(device, "driver")),
        readFirst(device, listOf("model", "name", "product")),
    ).distinct()

    private fun readHealth(device: File, type: StorageType): StorageHealth {
        val names = listOf(
            "life_time_estimation_a", "life_time_estimation_b", "life_time_estimate_a",
            "device_life_time_est_typ_a", "device_life_time_est_typ_b",
            "pre_eol_info", "ext_csd",
        )
        val values = names.mapNotNull { name -> readText(File(device, name))?.let { name to it } }.toMap()
        if (values.isEmpty()) {
            return StorageHealth(
                availability = when (type) {
                    StorageType.UNKNOWN -> StorageHealthAvailability.UNKNOWN
                    StorageType.VIRTUAL -> StorageHealthAvailability.UNSUPPORTED
                    else -> StorageHealthAvailability.DESCRIPTOR_UNAVAILABLE
                },
                state = com.febricahyaa.fluxlab.model.StorageHealthState.INFORMATION_UNAVAILABLE,
                source = device.path,
                warnings = listOf("Storage lifetime descriptor is not exposed by this device"),
            )
        }
        val a = StorageLifetimeParser.parseBucket(
            values["life_time_estimation_a"] ?: values["device_life_time_est_typ_a"],
            device.path,
        )
        val b = StorageLifetimeParser.parseBucket(
            values["life_time_estimation_b"] ?: values["device_life_time_est_typ_b"],
            device.path,
        )
        val emmc = values["ext_csd"]?.let { StorageLifetimeParser.parseEmmcExtCsd(it, device.path) }
        return when {
            type == StorageType.EMMC && emmc != null -> emmc
            else -> StorageHealth(
                state = listOf(a.normalizedState, b.normalizedState).maxByOrNull { it.ordinal }
                    ?: com.febricahyaa.fluxlab.model.StorageHealthState.DESCRIPTOR_UNAVAILABLE,
                availability = when {
                    a.normalizedState == com.febricahyaa.fluxlab.model.StorageHealthState.DESCRIPTOR_MALFORMED || b.normalizedState == com.febricahyaa.fluxlab.model.StorageHealthState.DESCRIPTOR_MALFORMED -> StorageHealthAvailability.MALFORMED
                    a.normalizedState == com.febricahyaa.fluxlab.model.StorageHealthState.DESCRIPTOR_UNAVAILABLE && b.normalizedState == com.febricahyaa.fluxlab.model.StorageHealthState.DESCRIPTOR_UNAVAILABLE -> StorageHealthAvailability.DESCRIPTOR_UNAVAILABLE
                    else -> StorageHealthAvailability.AVAILABLE
                },
                lifetimeA = a,
                lifetimeB = b,
                rawDescriptor = values.entries.joinToString(";") { it.first + "=" + it.second },
                source = device.path,
                confidence = if (a.confidence == BatteryPowerConfidence.MEDIUM || b.confidence == BatteryPowerConfidence.MEDIUM) {
                    BatteryPowerConfidence.MEDIUM
                } else {
                    BatteryPowerConfidence.LOW
                },
                warnings = listOfNotNull(a.warning, b.warning),
            )
        }
    }

    private fun readMounts(): List<MountInfo> = readText(File("/proc/mounts"))?.lineSequence()?.mapNotNull { line ->
        val parts = line.split(Regex("\\s+"), limit = 6)
        if (parts.size >= 3) MountInfo(unescape(parts[0]), unescape(parts[1]), parts[2]) else null
    }?.toList().orEmpty()

    private fun resolveBlockName(source: String): String? {
        val names = listOfNotNull(
            source.substringAfterLast('/').takeIf(String::isNotBlank),
            canonical(File(source))?.substringAfterLast('/')?.takeIf(String::isNotBlank),
        )
        return names.firstOrNull { File("/sys/class/block/$it").exists() } ?: names.firstOrNull()
    }

    private fun canonical(file: File): String? = runCatching { file.canonicalPath }.getOrNull()?.takeIf(String::isNotBlank)
    private fun readFirst(parent: File, names: List<String>): String? = names.firstNotNullOfOrNull { readText(File(parent, it)) }
    private fun readLong(file: File): Long? = readText(file)?.toLongOrNull()?.takeIf { it >= 0L }
    private fun readText(file: File): String? = runCatching {
        file.takeIf { it.isFile && it.canRead() }?.readText()?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()
    private fun unescape(value: String): String = value.replace("\\040", " ").replace("\\011", "\t").replace("\\\\", "\\")

    private data class MountInfo(val blockDevice: String, val mountPoint: String, val filesystem: String)
}

object StorageParsers {
    fun detectType(blockName: String, model: String?, devicePath: String): StorageType {
        val value = listOf(blockName, model.orEmpty(), devicePath).joinToString(" ").lowercase()
        return when {
            "/virtual/" in value || "virtual" in value || blockName.startsWith("loop") -> StorageType.VIRTUAL
            "ufshcd" in value || "ufs" in value -> StorageType.UFS
            blockName.startsWith("mmcblk") || "/mmc/" in value || "mmc" in value || "emmc" in value -> StorageType.EMMC
            blockName.isBlank() -> StorageType.MALFORMED
            else -> StorageType.UNKNOWN
        }
    }

    fun transportConfidence(type: StorageType, evidence: List<String>): IdentityConfidence = when {
        type == StorageType.UNKNOWN || type == StorageType.MALFORMED -> IdentityConfidence.LOW
        evidence.size >= 2 -> IdentityConfidence.HIGH
        evidence.isNotEmpty() -> IdentityConfidence.MEDIUM
        else -> IdentityConfidence.LOW
    }
}
