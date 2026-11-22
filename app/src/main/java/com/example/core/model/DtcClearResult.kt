package com.example.core.model

/**
 * Status of an OBD-II Service 04 (Clear / Reset Diagnostic Information) operation.
 */
enum class DtcClearStatus {
    SUCCESS,              // ECU acknowledged with positive response 44 (or OK)
    ECU_REJECTED,         // ECU returned Negative Response Code (7F 04 <NRC>)
    NO_DATA,              // No response from vehicle ECU (e.g. ignition off)
    COMMUNICATION_ERROR   // Bus error, timeout, adapter disconnected
}

/**
 * Encapsulates the verifiable evidence and protocol outcome of a Service 04 operation.
 * Adheres to the principles of PROTOCOL EVIDENCE, RAW RESPONSE, and ATTRIBUTABLE DATA.
 */
data class DtcClearResult(
    val status: DtcClearStatus,
    val commandTx: String = "04",
    val responseRx: String = "",
    val cleanPayload: String = "",
    val rawHex: String = "",
    val ecuHeader: String = "7E8",
    val confirmationMessageThai: String = "",
    val confirmationMessageEnglish: String = "",
    val errorMessageThai: String? = null,
    val errorMessageEnglish: String? = null,
    val negativeResponseCode: String? = null,
    val nrcMeaningThai: String? = null,
    val latencyMs: Long = 0L,
    val evidenceRef: String = "",
    val sha256Hash: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val provenance: DataProvenance = DataProvenance.REAL_HARDWARE,
    val quality: DataQuality = DataQuality.VALID
) {
    val isSuccess: Boolean
        get() = status == DtcClearStatus.SUCCESS
}
