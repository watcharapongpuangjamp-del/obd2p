package com.example.core.elm327

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.transport.RawTxRxResult

enum class ElmResponseType {
    VALID_DATA,
    NO_DATA,
    UNABLE_TO_CONNECT,
    BUS_INIT_ERROR,
    BUS_BUSY,
    CAN_ERROR,
    STOPPED,
    BUFFER_FULL,
    TIMEOUT,
    UNKNOWN_ERROR,
    OK_RESPONSE
}

data class ElmResponse(
    val type: ElmResponseType,
    val rawResponse: String,
    val cleanPayload: String,
    val ecuHeader: String = "",
    val dataBytes: List<Int> = emptyList(),
    val latencyMs: Long = 0L,
    val quality: DataQuality = DataQuality.VALID,
    val provenance: DataProvenance = DataProvenance.REAL_HARDWARE,
    val rawTxRx: RawTxRxResult? = null,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = type == ElmResponseType.VALID_DATA || type == ElmResponseType.OK_RESPONSE
}
