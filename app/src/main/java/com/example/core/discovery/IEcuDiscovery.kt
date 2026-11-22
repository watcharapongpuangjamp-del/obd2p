package com.example.core.discovery

import com.example.core.elm327.IElm327Driver
import com.example.core.model.EcuInfo
import kotlinx.coroutines.flow.StateFlow

interface IEcuDiscovery {
    val discoveredEcus: StateFlow<List<EcuInfo>>
    suspend fun discoverEcus(driver: IElm327Driver): List<EcuInfo>
}
