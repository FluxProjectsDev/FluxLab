package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.BenchmarkWorkload
import com.febricahyaa.fluxlab.model.WorkloadKind
import com.febricahyaa.fluxlab.model.WorkloadResult
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal object NativeBridge {
    init { System.loadLibrary("fluxlab-benchmark") }

    external fun run(kind: Int, size: Long, iterations: Long, seed: Long, threads: Int): DoubleArray
    external fun cancel()
}

class NativeBenchmarkWorkload(
    override val kind: WorkloadKind,
    private val repetitions: Int = 5,
    private val seed: Long = 0x46_4C_55_58_4C,
) : BenchmarkWorkload {
    override val version: Int = 1

    override suspend fun run(): WorkloadResult = withContext(Dispatchers.Default) {
        val config = configuration(kind)
        NativeBridge.run(config.id, config.size, config.warmupIterations, seed, config.threads)
        val values = mutableListOf<Double>()
        val durations = mutableListOf<Long>()
        var checksum = 0.0
        var actualThreads = config.threads
        repeat(repetitions) { repetition ->
            ensureActive()
            val result = NativeBridge.run(
                config.id,
                config.size,
                config.iterations,
                seed + repetition * 7_919L,
                config.threads,
            )
            if (result.size != 4 || result[0] <= 0.0 || !result[0].isFinite()) throw CancellationException("Native workload cancelled")
            require(result[1] != 0.0 && result[2] > 0.0) { "Native workload validation failed" }
            values += result[0]
            checksum += result[1]
            durations += result[2].toLong()
            actualThreads = result[3].toInt().coerceAtLeast(1)
        }
        WorkloadResult(
            kind = kind,
            workloadVersion = version,
            unit = kind.unit,
            repetitions = values,
            durationsNs = durations,
            statistics = BenchmarkStatistics.calculate(values),
            validationChecksum = String.format(Locale.ROOT, "%.0f", checksum),
            threadCount = actualThreads,
            affinityForced = false,
        )
    }

    override fun cancel() = NativeBridge.cancel()

    private data class Configuration(
        val id: Int,
        val size: Long,
        val iterations: Long,
        val warmupIterations: Long,
        val threads: Int = 1,
    )

    private fun configuration(kind: WorkloadKind): Configuration = when (kind) {
        WorkloadKind.CPU_INTEGER -> Configuration(0, 0, 10_000_000, 1_000_000)
        WorkloadKind.CPU_FLOATING_POINT -> Configuration(1, 0, 5_000_000, 500_000)
        WorkloadKind.CPU_MULTI_THREADED -> Configuration(
            2, 0, 2_000_000, 250_000,
            Runtime.getRuntime().availableProcessors().coerceIn(2, 8),
        )
        WorkloadKind.MEMORY_COPY -> Configuration(3, 16L * 1024 * 1024, 8, 2)
        WorkloadKind.MEMORY_FILL -> Configuration(4, 16L * 1024 * 1024, 8, 2)
        WorkloadKind.MEMORY_LATENCY -> Configuration(5, 16L * 1024 * 1024, 4_000_000, 250_000)
        else -> error("$kind is not a native workload")
    }
}
