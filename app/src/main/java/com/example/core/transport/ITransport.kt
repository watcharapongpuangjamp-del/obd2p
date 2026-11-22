package com.example.core.transport

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ITransport {
    val state: StateFlow<TransportState>
    val rxEvidenceFlow: SharedFlow<RawTxRxResult>

    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun sendAndReceive(command: String, timeoutMs: Long = 2000L): RawTxRxResult
    fun isConnected(): Boolean
}
