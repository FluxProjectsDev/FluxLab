package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.Statistics
import com.febricahyaa.fluxlab.model.VariabilityClassification
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

object BenchmarkStatistics {
    fun calculate(values: List<Double>, latencyMetric: Boolean = false): Statistics {
        require(values.isNotEmpty() && values.all(Double::isFinite)) { "Finite measurements are required" }
        val sorted = values.sorted()
        val median = percentile(sorted, 0.5)
        val mean = values.average()
        val variance = if (values.size > 1) values.sumOf { (it - mean) * (it - mean) } / (values.size - 1) else 0.0
        val deviation = sqrt(variance)
        val coefficient = if (abs(mean) > 1e-12) deviation / abs(mean) else if (deviation == 0.0) 0.0 else null
        val variability = when {
            values.size < 2 || coefficient == null -> VariabilityClassification.UNRELIABLE
            coefficient <= 0.02 -> VariabilityClassification.STABLE
            coefficient <= 0.05 -> VariabilityClassification.ACCEPTABLE
            coefficient <= 0.10 -> VariabilityClassification.NOISY
            else -> VariabilityClassification.UNRELIABLE
        }
        return Statistics(
            median = median,
            minimum = sorted.first(),
            maximum = sorted.last(),
            standardDeviation = deviation,
            coefficientOfVariation = coefficient,
            sampleCount = values.size,
            arithmeticMean = mean,
            p95 = if (latencyMetric) percentile(sorted, 0.95) else null,
            variability = variability,
        )
    }

    private fun percentile(sorted: List<Double>, quantile: Double): Double {
        if (sorted.size == 1) return sorted.first()
        val rank = ceil(quantile * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
