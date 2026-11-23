package com.example.core.transport

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality

enum class TransportFailureKind {
    NONE,
    TIMEOUT,
    PERMISSION_DENIED,
    NOT_CONNECTED,
    DEVICE_NOT_FOUND,
    TX_ERROR,
    RX_ERROR,
    OPEN_FAILED,
    UNSUPPORTED_DEVICE,
    PROTOCOL_ERROR
}

sealed class TransportState {
    object Disconnected : TransportState()
    data class Connecting(val deviceName: String) : TransportState()
    data class Reconnecting(
        val deviceName: String,
        val attempt: Int,
        val maxAttempts: Int,
        val delayMs: Long = 0L
    ) : TransportState()
    data class PermissionRequired(
        val deviceName: String,
        val deviceId: Int? = null,
        val message: String = "USB Host permission required for $deviceName"
    ) : TransportState()
    data class Connected(
        val deviceName: String,
        val baudRate: Int,
        val provenance: DataProvenance,
        val isHardware: Boolean
    ) : TransportState()
    data class Timeout(
        val message: String,
        val commandTx: String = "",
        val elapsedMs: Long = 0L,
        val timestamp: Long = System.currentTimeMillis()
    ) : TransportState()
    data class Error(
        val message: String,
        val failureKind: TransportFailureKind = TransportFailureKind.OPEN_FAILED,
        val timestamp: Long = System.currentTimeMillis()
    ) : TransportState()
}

data class RawTxRxResult(
    val commandTx: String,
    val responseRx: String,
    val latencyMs: Long,
    val isTimeout: Boolean = false,
    val isError: Boolean = false,
    val rawBytes: ByteArray = ByteArray(0),
    val quality: DataQuality = DataQuality.VALID,
    val provenance: DataProvenance = DataProvenance.REAL_HARDWARE,
    val timestamp: Long = System.currentTimeMillis(),
    val txTimestamp: Long = timestamp,
    val rxTimestamp: Long = timestamp,
    val rawTxBytes: ByteArray = commandTx.toByteArray(Charsets.US_ASCII),
    val baudRate: Int = 38400,
    val failureKind: TransportFailureKind = when {
        isTimeout -> TransportFailureKind.TIMEOUT
        isError -> TransportFailureKind.RX_ERROR
        else -> TransportFailureKind.NONE
    }
)
