package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.WorkloadKind
import com.febricahyaa.fluxlab.model.WorkloadResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class StorageBenchmarkSuite(private val cacheDirectory: File) {
    suspend fun run(): List<WorkloadResult> = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("quick-test-", ".bin", cacheDirectory)
        val data = deterministicBytes(8 * 1024 * 1024)
        try {
            // A 1 MiB warm-up plus 3 x 8 MiB measured writes and 4 x 1 MiB fsync writes:
            // 29 MiB total physical write request, under the advertised 32 MiB ceiling.
            FileOutputStream(temporary, false).use { it.write(data, 0, 1024 * 1024) }
            val write = measureThroughput(WorkloadKind.STORAGE_WRITE, 3) {
                FileOutputStream(temporary, false).use { output -> output.write(data) }
                data.size.toLong()
            }
            val read = measureThroughput(WorkloadKind.STORAGE_READ, 3) {
                val buffer = ByteArray(256 * 1024)
                var bytes = 0L
                FileInputStream(temporary).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                    }
                }
                bytes
            }
            val fsyncData = data.copyOf(1024 * 1024)
            FileOutputStream(temporary, false).use { output ->
                output.write(fsyncData)
                output.fd.sync()
            }
            val fsyncValues = mutableListOf<Double>()
            val fsyncDurations = mutableListOf<Long>()
            repeat(3) {
                ensureActive()
                val start = android.os.SystemClock.elapsedRealtimeNanos()
                FileOutputStream(temporary, false).use { output ->
                    output.write(fsyncData)
                    output.fd.sync()
                }
                val duration = android.os.SystemClock.elapsedRealtimeNanos() - start
                fsyncDurations += duration
                fsyncValues += duration / 1_000_000.0
            }
            val fsync = WorkloadResult(
                WorkloadKind.STORAGE_FSYNC, 1, WorkloadKind.STORAGE_FSYNC.unit,
                fsyncValues, fsyncDurations, BenchmarkStatistics.calculate(fsyncValues),
                checksum(fsyncData), warnings = listOf("Private-cache fsync latency; not full-device storage performance"),
            )
            listOf(write, read, fsync)
        } finally {
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit()
        }
    }

    fun cleanupInterruptedFiles() {
        cacheDirectory.listFiles { file -> file.name.startsWith("quick-test-") && file.name.endsWith(".bin") }
            ?.forEach { it.delete() }
    }

    private suspend fun measureThroughput(
        kind: WorkloadKind,
        repetitions: Int,
        block: () -> Long,
    ): WorkloadResult {
        val values = mutableListOf<Double>()
        val durations = mutableListOf<Long>()
        var bytes = 0L
        repeat(repetitions) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val start = android.os.SystemClock.elapsedRealtimeNanos()
            bytes = block()
            val duration = android.os.SystemClock.elapsedRealtimeNanos() - start
            require(bytes > 0L && duration > 0L)
            durations += duration
            values += (bytes / (1024.0 * 1024.0)) / (duration / 1_000_000_000.0)
        }
        return WorkloadResult(
            kind, 1, kind.unit, values, durations, BenchmarkStatistics.calculate(values),
            bytes.toString(), warnings = listOf("Private app storage; cache and filesystem effects apply"),
        )
    }

    private fun deterministicBytes(size: Int): ByteArray {
        var state = 0x46_4C_55_58_4CL
        return ByteArray(size) {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            state.toByte()
        }
    }

    private fun checksum(bytes: ByteArray): String = CRC32().apply { update(bytes) }.value.toString()
}
