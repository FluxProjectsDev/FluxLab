package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.Statistics
import kotlin.math.abs
import kotlin.math.sqrt

object BenchmarkStatistics {
    fun calculate(values: List<Double>): Statistics {
        require(values.isNotEmpty() && values.all(Double::isFinite)) { "Finite measurements are required" }
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        val mean = values.average()
        val variance = if (values.size > 1) values.sumOf { (it - mean) * (it - mean) } / (values.size - 1) else 0.0
        val deviation = sqrt(variance)
        val coefficient = if (abs(mean) > 1e-12) deviation / abs(mean) else null
        return Statistics(median, sorted.first(), sorted.last(), deviation, coefficient)
    }
}
