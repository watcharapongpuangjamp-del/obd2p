package com.example.core.transport

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality

sealed class TransportState {
    object Disconnected : TransportState()
    data class Connecting(val deviceName: String) : TransportState()
    data class Connected(
        val deviceName: String,
        val baudRate: Int,
        val provenance: DataProvenance,
        val isHardware: Boolean
    ) : TransportState()
    data class Error(val message: String, val timestamp: Long = System.currentTimeMillis()) : TransportState()
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
    val timestamp: Long = System.currentTimeMillis()
)
