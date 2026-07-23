package com.febricahyaa.fluxlab.data

import com.febricahyaa.fluxlab.model.BenchmarkSession
import com.febricahyaa.fluxlab.model.ComparisonCompatibilityAnalyzer
import com.febricahyaa.fluxlab.model.Confidence
import com.febricahyaa.fluxlab.model.SessionComparison
import com.febricahyaa.fluxlab.model.SessionStatus
import com.febricahyaa.fluxlab.model.WorkloadComparison
import kotlin.math.abs

object ComparisonEngine {
    fun compare(baseline: BenchmarkSession, candidate: BenchmarkSession): SessionComparison {
        require(baseline.status == SessionStatus.COMPLETED && candidate.status == SessionStatus.COMPLETED)
        val candidateByKind = candidate.workloadResults.associateBy { it.kind }
        val environmentWarnings = buildList {
            if (baseline.environment.androidFingerprint != candidate.environment.androidFingerprint) add("Android build differs")
            if (baseline.environment.kernelVersion != candidate.environment.kernelVersion) add("Kernel differs")
            if (baseline.environment.charging != candidate.environment.charging) add("Charging state differs")
            if (baseline.environment.flux.activeProfile != candidate.environment.flux.activeProfile) add("Flux profile differs")
        }
        val comparisons = baseline.workloadResults.map { base ->
            val current = candidateByKind[base.kind]
            if (current == null || current.workloadVersion != base.workloadVersion) {
                WorkloadComparison(base.kind, false, null, null, null, Confidence.INCONCLUSIVE, listOf("Workload version is missing or incompatible"))
            } else {
                val delta = current.statistics.median - base.statistics.median
                val percent = if (abs(base.statistics.median) > 1e-12) delta / abs(base.statistics.median) * 100.0 else null
                val improved = percent?.let { if (base.kind.higherIsBetter) it > 0 else it < 0 }
                val variation = maxOf(
                    base.statistics.coefficientOfVariation ?: 1.0,
                    current.statistics.coefficientOfVariation ?: 1.0,
                )
                val effect = abs(percent ?: 0.0) / 100.0
                val confidence = when {
                    percent == null || effect <= variation || environmentWarnings.size >= 2 -> Confidence.INCONCLUSIVE
                    effect >= variation * 2.0 && environmentWarnings.isEmpty() -> Confidence.LIKELY_CHANGE
                    else -> Confidence.POSSIBLE_CHANGE
                }
                WorkloadComparison(
                    base.kind, true, delta, percent, improved, confidence,
                    buildList {
                        if (variation > 0.05) add("High repetition variability")
                        addAll(environmentWarnings)
                    }.distinct(),
                )
            }
        }
        val compatibility = ComparisonCompatibilityAnalyzer.analyze(baseline, candidate)
        return SessionComparison(
            baseline.id, candidate.id, comparisons,
            (environmentWarnings + compatibility.warnings).distinct(), compatibility,
        )
    }
}
