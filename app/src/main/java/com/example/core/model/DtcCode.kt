package com.example.core.model

enum class DtcType {
    CONFIRMED,
    PENDING,
    PERMANENT
}

enum class DtcSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

data class DtcOnlineResource(
    val label: String,
    val url: String
)

data class DtcCode(
    val code: String,
    val type: DtcType,
    val ecuId: String = "7E8",
    val system: String = "Powertrain",
    val thaiTitle: String,
    val thaiExplanation: String,
    val englishDescription: String,
    val severity: DtcSeverity,
    val symptomsThai: List<String> = emptyList(),
    val potentialCausesThai: List<String> = emptyList(),
    val implicationsThai: String = "",
    val inspectionStepsThai: List<String> = emptyList(),
    val onlineResources: List<DtcOnlineResource> = emptyList(),
    val isGenericFallback: Boolean = false,
    val rawHex: String = "",
    val evidenceRef: String = ""
)

data class DtcLookupResult(
    val code: String,
    val isExactLocalMatch: Boolean,
    val system: String,
    val thaiTitle: String,
    val thaiExplanation: String,
    val englishDescription: String,
    val implicationsThai: String,
    val severity: DtcSeverity,
    val symptomsThai: List<String> = emptyList(),
    val potentialCausesThai: List<String> = emptyList(),
    val inspectionStepsThai: List<String> = emptyList(),
    val onlineResources: List<DtcOnlineResource> = emptyList(),
    val matchedScannedDtcs: List<DtcCode> = emptyList(),
    val queriedTimestamp: Long = System.currentTimeMillis()
)

enum class ScanStatus {
    IDLE,
    SCANNING,
    COMPLETED,
    FAILED
}

data class DtcScanResult(
    val status: ScanStatus = ScanStatus.IDLE,
    val confirmedCodes: List<DtcCode> = emptyList(),
    val pendingCodes: List<DtcCode> = emptyList(),
    val permanentCodes: List<DtcCode> = emptyList(),
    val modulesScanned: List<String> = emptyList(),
    val modulesFailed: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val evidenceRefs: List<String> = emptyList(),
    val scanEvidenceLogs: List<EvidenceLog> = emptyList(),
    val scanDurationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalFaults: Int
        get() = confirmedCodes.size + pendingCodes.size + permanentCodes.size
}
