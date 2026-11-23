package com.example.core.transport

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ITransport {
    val state: StateFlow<TransportState>
    val rxEvidenceFlow: SharedFlow<RawTxRxResult>

    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun reconnect(maxRetries: Int = 3): Boolean
    fun requestPermission(): Boolean = true
    suspend fun sendRawBytes(data: ByteArray): Boolean
    suspend fun readRawBytes(timeoutMs: Long = 2000L, stopOnPrompt: Boolean = true): ByteArray
    suspend fun sendAndReceive(command: String, timeoutMs: Long = 2000L): RawTxRxResult
    fun isConnected(): Boolean
}
