package com.example.core.model

enum class PidPollingTier(
    val label: String,
    val cycleModulo: Int,
    val staleAfterMs: Long
) {
    FAST("FAST (1x)", 1, 3_000L),
    MEDIUM("MED (1/2x)", 2, 6_000L),
    SLOW("SLOW (1/4x)", 4, 12_000L)
}

/**
 * Validated channel state for a single OBD-II PID, pairing the latest
 * validated TelemetryPoint with its rolling time-series history and scheduler tier.
 */
data class TelemetryChannelState(
    val pidHex: String,
    val latest: TelemetryPoint,
    val history: List<TelemetryPoint> = emptyList(),
    val tier: PidPollingTier = PidPollingTier.MEDIUM,
    val isSupportedByEcu: Boolean = true,
    val effectiveHz: Double = 0.0
)

/**
 * Output of the Validated Diagnostic State Layer consumed by real-time
 * visualization components, deterministic rules, and AI analysis.
 */
data class ValidatedDiagnosticSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val provenance: DataProvenance = DataProvenance.SIMULATOR,
    val protocol: String = "Auto",
    val isPolling: Boolean = false,
    val averageLatencyMs: Long = 0L,
    val overallPollingHz: Double = 0.0,
    val supportedPids: Set<String> = emptySet(),
    val channels: Map<String, TelemetryChannelState> = emptyMap()
)
