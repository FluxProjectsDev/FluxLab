package com.febricahyaa.fluxlab.benchmark

import android.os.SystemClock
import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.BenchmarkPresetConfig
import com.febricahyaa.fluxlab.model.WorkloadKind
import com.febricahyaa.fluxlab.model.WorkloadResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Bounded app-private storage measurements; this is not a physical UFS benchmark. */
class StorageBenchmarkSuite(private val cacheDirectory: File) {
    suspend fun run(config: BenchmarkPresetConfig = BenchmarkPresetConfig.forPreset(BenchmarkPreset.QUICK)): List<WorkloadResult> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        require(config.storageAllocationLimitBytes > 0L) { "Storage allocation limit must be positive" }
        require(cacheDirectory.exists() || cacheDirectory.mkdirs()) { "FluxLab cache directory is unavailable" }
        require(cacheDirectory.isDirectory && cacheDirectory.canWrite()) { "FluxLab cache directory is not writable" }
        val allocation = StorageSafety.evaluate(cacheDirectory.usableSpace, config.storageAllocationLimitBytes)
        check(allocation.allowed) { allocation.reason ?: "Insufficient private storage for selected preset" }
        val data = deterministicBytes(dataSizeBytes(config))
        val fsyncData = deterministicBytes(FSYNC_BYTES)
        val temporary = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, cacheDirectory)
        try {
            writeWarmup(temporary, data)
            val expectedChecksum = StorageIntegrity.checksum(data)
            val write = measureBufferedWrites(temporary, data, config.measuredRepetitionCount, expectedChecksum)
            val read = measureBufferedReads(temporary, data.size.toLong(), expectedChecksum, config.measuredRepetitionCount)
            writeWarmup(temporary, fsyncData)
            val fsync = measureDurableWrites(temporary, fsyncData, config.measuredRepetitionCount)
            listOf(write, read, fsync)
        } finally {
            deleteTemporary(temporary)
        }
    }

    fun cleanupInterruptedFiles() {
        cacheDirectory.listFiles { file -> isRecognizedTemporary(file) }?.forEach(::deleteTemporary)
    }

    private fun writeWarmup(file: File, data: ByteArray) = FileOutputStream(file, false).use { output ->
        output.write(data)
        output.flush()
    }

    private suspend fun measureBufferedWrites(file: File, data: ByteArray, repetitions: Int, expectedChecksum: String): WorkloadResult {
        val values = mutableListOf<Double>()
        val durations = mutableListOf<Long>()
        repeat(repetitions) {
            currentCoroutineContext().ensureActive()
            val start = SystemClock.elapsedRealtimeNanos()
            FileOutputStream(file, false).use { output -> output.write(data); output.flush() }
            val duration = SystemClock.elapsedRealtimeNanos() - start
            require(duration > 0L) { "Storage write duration was not measurable" }
            durations += duration
            values += (data.size / 1_048_576.0) / (duration / 1_000_000_000.0)
        }
        return WorkloadResult(WorkloadKind.STORAGE_WRITE, STORAGE_WORKLOAD_VERSION, WorkloadKind.STORAGE_WRITE.unit, values, durations, BenchmarkStatistics.calculate(values), expectedChecksum,
            warnings = STORAGE_WARNINGS + "Buffered sequential write; creation, flush, and close are included.")
    }

    private suspend fun measureBufferedReads(file: File, expectedBytes: Long, expectedChecksum: String, repetitions: Int): WorkloadResult {
        val values = mutableListOf<Double>()
        val durations = mutableListOf<Long>()
        repeat(repetitions) {
            currentCoroutineContext().ensureActive()
            val crc = CRC32()
            val buffer = ByteArray(256 * 1024)
            var bytes = 0L
            val start = SystemClock.elapsedRealtimeNanos()
            FileInputStream(file).use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) { crc.update(buffer, 0, count); bytes += count }
                }
            }
            val duration = SystemClock.elapsedRealtimeNanos() - start
            require(bytes == expectedBytes) { "Storage read size validation failed" }
            check(crc.value.toString() == expectedChecksum) { "Storage checksum validation failed" }
            require(duration > 0L) { "Storage read duration was not measurable" }
            durations += duration
            values += (bytes / 1_048_576.0) / (duration / 1_000_000_000.0)
        }
        return WorkloadResult(WorkloadKind.STORAGE_READ, STORAGE_WORKLOAD_VERSION, WorkloadKind.STORAGE_READ.unit, values, durations, BenchmarkStatistics.calculate(values), expectedChecksum,
            warnings = STORAGE_WARNINGS + "Buffered app-file read; page-cache effects apply and this is not physical UFS speed.")
    }

    private suspend fun measureDurableWrites(file: File, data: ByteArray, repetitions: Int): WorkloadResult {
        val values = mutableListOf<Double>()
        val durations = mutableListOf<Long>()
        val expectedChecksum = StorageIntegrity.checksum(data)
        repeat(repetitions) {
            currentCoroutineContext().ensureActive()
            FileOutputStream(file, false).use { output ->
                output.write(data); output.flush()
                val start = SystemClock.elapsedRealtimeNanos()
                output.fd.sync()
                val duration = SystemClock.elapsedRealtimeNanos() - start
                require(duration > 0L) { "Fsync duration was not measurable" }
                durations += duration; values += duration / 1_000_000.0
            }
        }
        return WorkloadResult(WorkloadKind.STORAGE_FSYNC, STORAGE_WORKLOAD_VERSION, WorkloadKind.STORAGE_FSYNC.unit, values, durations, BenchmarkStatistics.calculate(values, latencyMetric = true), expectedChecksum,
            warnings = STORAGE_WARNINGS + "Durable write with fsync; fsync excludes buffered write and close timing.")
    }

    private fun dataSizeBytes(config: BenchmarkPresetConfig): Int {
        val requested = ONE_MIB.toLong() * config.workloadScale.coerceAtLeast(1)
        val fixed = ONE_MIB.toLong() * (config.measuredRepetitionCount + 2)
        val available = config.storageAllocationLimitBytes - fixed
        check(available >= ONE_MIB.toLong() * (config.measuredRepetitionCount + 1)) { "Storage allocation limit is too small" }
        return requested.coerceAtMost(available / (config.measuredRepetitionCount + 1)).coerceAtLeast(ONE_MIB.toLong()).toInt()
    }

    private fun deleteTemporary(file: File) {
        if (file.exists() && !file.delete() && file.exists()) file.deleteOnExit()
    }

    private fun isRecognizedTemporary(file: File): Boolean = file.isFile && file.name.endsWith(FILE_SUFFIX) && RECOGNIZED_PREFIXES.any(file.name::startsWith)

    private fun deterministicBytes(size: Int): ByteArray {
        var state = 0x46_4C_55_58_4CL
        return ByteArray(size) {
            state = state xor (state shl 13); state = state xor (state ushr 7); state = state xor (state shl 17); state.toByte()
        }
    }

    private companion object {
        const val ONE_MIB = 1_048_576
        const val FSYNC_BYTES = ONE_MIB
        const val FILE_PREFIX = "fluxlab-benchmark-"
        const val FILE_SUFFIX = ".bin"
        const val STORAGE_WORKLOAD_VERSION = 3
        val RECOGNIZED_PREFIXES = setOf(FILE_PREFIX, "quick-test-")
        val STORAGE_WARNINGS = listOf(
            "Private app storage only; page cache, filesystem, encryption, controller cache, background I/O, and thermal state affect results.",
            "App-level buffered I/O is not a guaranteed physical UFS or full-device storage measurement.",
        )
    }
}

data class StorageAllocationDecision(val allowed: Boolean, val availableBytes: Long, val requestedBytes: Long, val safetyMarginBytes: Long, val remainingBytes: Long, val reason: String?)

object StorageSafety {
    const val DEFAULT_SAFETY_MARGIN_BYTES = 32L * 1_048_576L
    fun evaluate(availableBytes: Long, requestedBytes: Long, safetyMarginBytes: Long = DEFAULT_SAFETY_MARGIN_BYTES): StorageAllocationDecision {
        val valid = availableBytes >= 0L && requestedBytes > 0L && safetyMarginBytes >= 0L
        val required = requestedBytes + safetyMarginBytes
        val allowed = valid && required >= requestedBytes && availableBytes >= required
        val reason = when { !valid -> "Storage allocation values are invalid"; allowed -> null; else -> "Insufficient private storage: ${requestedBytes / 1_048_576} MiB requested with ${safetyMarginBytes / 1_048_576} MiB safety margin" }
        return StorageAllocationDecision(allowed, availableBytes, requestedBytes, safetyMarginBytes, availableBytes - requestedBytes, reason)
    }
}

object StorageMetricLabels {
    fun english(kind: WorkloadKind): String = when (kind) {
        WorkloadKind.STORAGE_WRITE -> "Buffered sequential write"
        WorkloadKind.STORAGE_READ -> "Buffered app-file read"
        WorkloadKind.STORAGE_FSYNC -> "Durable write with fsync"
        else -> kind.name
    }
    fun indonesian(kind: WorkloadKind): String = when (kind) {
        WorkloadKind.STORAGE_WRITE -> "Penulisan berurutan berbasis buffer"
        WorkloadKind.STORAGE_READ -> "Pembacaan file aplikasi berbasis buffer"
        WorkloadKind.STORAGE_FSYNC -> "Penulisan tahan lama dengan fsync"
        else -> kind.name
    }
    fun methodologyNote(indonesian: Boolean): String = if (indonesian) "Hasil I/O aplikasi dipengaruhi cache halaman Linux dan sistem berkas; ini bukan kecepatan UFS fisik yang dijamin." else "App-level I/O is affected by the Linux page cache and filesystem; it is not guaranteed physical UFS speed."
}

object StorageIntegrity {
    fun checksum(bytes: ByteArray): String = CRC32().apply { update(bytes) }.value.toString()
    fun isValid(bytes: ByteArray, expectedChecksum: String): Boolean = checksum(bytes) == expectedChecksum
}
