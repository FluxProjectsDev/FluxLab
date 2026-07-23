package com.febricahyaa.fluxlab.model

enum class MonitoringState {
    INACTIVE,
    STARTING,
    COLLECTING_INITIAL_SAMPLES,
    ACTIVE,
    PAUSED,
    TEMPORARILY_UNAVAILABLE,
    PERMISSION_DENIED,
    UNSUPPORTED,
    MALFORMED,
    STALE,
    FAILED,
}

data class TelemetrySourceStatus(
    val state: MonitoringState = MonitoringState.INACTIVE,
    val reason: String? = null,
    val source: String? = null,
    val lastSuccessfulSampleElapsedMs: Long? = null,
    val consecutiveFailureCount: Int = 0,
    val warning: String? = null,
)

/** Fixed-size history used by charts; adding a sample never grows without bound. */
class SampleRingBuffer<T>(private val capacity: Int = 120) {
    init { require(capacity > 0) }
    private val values = ArrayDeque<T>(capacity)

    @Synchronized
    fun add(value: T) {
        if (values.size == capacity) values.removeFirst()
        values.addLast(value)
    }

    @Synchronized
    fun snapshot(): List<T> = values.toList()

    @Synchronized
    fun clear() = values.clear()

    val size: Int get() = synchronized(this) { values.size }
}
