package com.febricahyaa.fluxlab.model

/** Stable resource keys for protocol values shown by Flux and SynthesisCore. */
enum class LocalizedStatusKey {
    UP_TO_DATE,
    UPDATE_REQUIRED,
    PARTIALLY_AVAILABLE,
    INVALID_DATA_FORMAT,
    UNAVAILABLE,
    UNKNOWN_STATUS,
}

fun localizedStatusKey(raw: String?): LocalizedStatusKey = when (raw?.trim()?.lowercase()) {
    "fresh" -> LocalizedStatusKey.UP_TO_DATE
    "stale", "delayed" -> LocalizedStatusKey.UPDATE_REQUIRED
    "partial" -> LocalizedStatusKey.PARTIALLY_AVAILABLE
    "malformed" -> LocalizedStatusKey.INVALID_DATA_FORMAT
    "unavailable" -> LocalizedStatusKey.UNAVAILABLE
    else -> LocalizedStatusKey.UNKNOWN_STATUS
}
