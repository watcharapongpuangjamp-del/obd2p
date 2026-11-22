package com.example.core.model

enum class DataQuality {
    VALID,
    UNAVAILABLE,
    UNSUPPORTED,
    TIMEOUT,
    INVALID,
    STALE
}

enum class DataProvenance {
    REAL_HARDWARE,
    SIMULATOR,
    USER_ENTERED,
    HISTORICAL,
    ESTIMATED,
    AI_INFERRED
}

data class TelemetryPoint(
    val id: String,
    val name: String,
    val thaiName: String,
    val value: Double,
    val displayValue: String,
    val unit: String,
    val timestamp: Long = System.currentTimeMillis(),
    val quality: DataQuality = DataQuality.VALID,
    val provenance: DataProvenance = DataProvenance.REAL_HARDWARE,
    val sourcePid: String,
    val sourceEcuId: String = "7E8",
    val latencyMs: Long = 0L,
    val rawHexResponse: String = "",
    val evidenceRefId: String = ""
)
