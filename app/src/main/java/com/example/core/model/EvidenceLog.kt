package com.example.core.model

enum class EvidenceDirection {
    TX, // Transmitted to ELM327 / ECU
    RX, // Received from ELM327 / ECU
    INTERNAL // Diagnostic Engine state change or Rule evaluation
}

data class EvidenceLog(
    val id: String,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val direction: EvidenceDirection,
    val rawBytesHex: String,
    val commandText: String,
    val responseText: String,
    val ecuHeader: String = "",
    val latencyMs: Long = 0L,
    val quality: DataQuality = DataQuality.VALID,
    val provenance: DataProvenance = DataProvenance.REAL_HARDWARE,
    val parsedMeaning: String = "",
    val sha256Hash: String = ""
)
