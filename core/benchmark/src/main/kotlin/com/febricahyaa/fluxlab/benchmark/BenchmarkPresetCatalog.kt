package com.febricahyaa.fluxlab.benchmark

import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.BenchmarkPresetConfig

/** User-facing description of a deterministic benchmark configuration. */
data class BenchmarkPresetDescription(
    val preset: BenchmarkPreset,
    val purpose: String,
    val durationHint: String,
    val warning: String? = null,
    val config: BenchmarkPresetConfig = BenchmarkPresetConfig.forPreset(preset),
)

object BenchmarkPresetCatalog {
    val all: List<BenchmarkPresetDescription> = listOf(
        BenchmarkPresetDescription(BenchmarkPreset.QUICK, "Smoke testing and implementation validation", "15–45 seconds"),
        BenchmarkPresetDescription(BenchmarkPreset.STANDARD, "Routine Flux OFF versus Flux ON comparison", "2–5 minutes"),
        BenchmarkPresetDescription(
            BenchmarkPreset.EXTENDED,
            "Sustained performance, thermal behaviour, and variability analysis",
            "8–15 minutes",
            "Sustained load may produce heat, consume battery, and be affected by screen recording.",
        ),
    )

    fun description(preset: BenchmarkPreset): BenchmarkPresetDescription = all.first { it.preset == preset }
}
