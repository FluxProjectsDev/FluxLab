package com.febricahyaa.fluxlab

import androidx.metrics.performance.FrameData
import java.util.concurrent.atomic.AtomicLong

data class FrameTelemetrySummary(
    val observedFrames: Long = 0,
    val jankFrames: Long = 0,
    val maximumFrameDurationNanos: Long = 0,
)

class FrameTelemetry {
    private val observedFrames = AtomicLong()
    private val jankFrames = AtomicLong()
    private val maximumFrameDurationNanos = AtomicLong()

    fun record(frameData: FrameData) {
        observedFrames.incrementAndGet()
        if (frameData.isJank) jankFrames.incrementAndGet()
        updateMaximum(frameData.frameDurationUiNanos)
    }

    fun snapshot(): FrameTelemetrySummary = FrameTelemetrySummary(
        observedFrames = observedFrames.get(),
        jankFrames = jankFrames.get(),
        maximumFrameDurationNanos = maximumFrameDurationNanos.get(),
    )

    private fun updateMaximum(candidate: Long) {
        var current = maximumFrameDurationNanos.get()
        while (candidate > current && !maximumFrameDurationNanos.compareAndSet(current, candidate)) {
            current = maximumFrameDurationNanos.get()
        }
    }
}
