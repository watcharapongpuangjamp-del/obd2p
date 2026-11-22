package com.example.core.elm327

import com.example.core.model.DataProvenance
import com.example.core.transport.ITransport
import kotlinx.coroutines.flow.StateFlow

interface IElm327Driver {
    val isInitialized: StateFlow<Boolean>
    val adapterVersion: StateFlow<String>
    val detectedProtocol: StateFlow<String>
    val batteryVoltage: StateFlow<String>

    suspend fun initializeAdapter(): Boolean
    suspend fun sendAtCommand(command: String): ElmResponse
    suspend fun queryPid(service: String, pid: String): ElmResponse
    suspend fun readBatteryVoltage(): ElmResponse
    suspend fun clearTroubleCodes(): ElmResponse
    fun getProvenance(): DataProvenance
}
