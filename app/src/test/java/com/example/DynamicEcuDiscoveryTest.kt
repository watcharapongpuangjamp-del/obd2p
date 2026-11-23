package com.example

import com.example.core.discovery.DynamicEcuDiscovery
import com.example.core.elm327.ElmResponse
import com.example.core.elm327.ElmResponseType
import com.example.core.elm327.IElm327Driver
import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.DtcClearResult
import com.example.core.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicEcuDiscoveryTest {

    private class FakeDriver(
        private val responses: Map<String, ElmResponse>,
        initialized: Boolean = true
    ) : IElm327Driver {
        override val isInitialized = MutableStateFlow(initialized)
        override val adapterVersion = MutableStateFlow("ELM327 test")
        override val detectedProtocol = MutableStateFlow("ISO 15765-4 (CAN 11/500)")
        override val batteryVoltage = MutableStateFlow("13.8V")

        override suspend fun initializeAdapter(): Boolean = true
        override suspend fun sendAtCommand(command: String): ElmResponse =
            error("Unexpected AT command: $command")
        override suspend fun queryPid(service: String, pid: String): ElmResponse =
            responses["${service.uppercase()}${pid.uppercase()}"]
                ?: ElmResponse(
                    type = ElmResponseType.NO_DATA,
                    rawResponse = "NO DATA",
                    cleanPayload = "NO DATA",
                    quality = DataQuality.UNAVAILABLE,
                    provenance = DataProvenance.REAL_HARDWARE
                )
        override suspend fun readBatteryVoltage(): ElmResponse =
            error("Unexpected voltage query")
        override suspend fun clearTroubleCodes(): ElmResponse =
            error("Unexpected clear command")
        override fun getProvenance(): DataProvenance = DataProvenance.REAL_HARDWARE
    }

    private fun validPidResponse(header: String, pid: Int, bitmap: List<Int>) = ElmResponse(
        type = ElmResponseType.VALID_DATA,
        rawResponse = "$header 06 41 ${"%02X".format(pid)}",
        cleanPayload = "41 ${"%02X".format(pid)}",
        ecuHeader = header,
        dataBytes = listOf(0x06, 0x41, pid) + bitmap,
        quality = DataQuality.VALID,
        provenance = DataProvenance.REAL_HARDWARE
    )

    @Test
    fun discoversOnlyTheEcuThatActuallyRespondedAndMergesPid20() = runBlocking {
        val driver = FakeDriver(
            mapOf(
                "0100" to validPidResponse("7E8", 0x00, listOf(0xBE, 0x3F, 0xB8, 0x13)),
                "0120" to validPidResponse("7E8", 0x20, listOf(0x80, 0x07, 0xE0, 0x11))
            )
        )

        val result = DynamicEcuDiscovery().discoverEcus(driver)

        assertEquals(1, result.size)
        assertEquals("7E8", result.single().ecuId)
        assertTrue(result.single().isResponding)
        assertTrue(result.single().supportedPids.contains("21"))
        assertFalse(result.any { it.ecuId == "7E9" })
    }

    @Test
    fun noDataDoesNotCreateFallbackEcuOrGuessSupportedPids() = runBlocking {
        val driver = FakeDriver(emptyMap())

        val result = DynamicEcuDiscovery().discoverEcus(driver)

        assertTrue(result.isEmpty())
    }

    @Test
    fun uninitializedAdapterDoesNotRunDiscovery() = runBlocking {
        val driver = FakeDriver(emptyMap(), initialized = false)

        val result = DynamicEcuDiscovery().discoverEcus(driver)

        assertTrue(result.isEmpty())
    }
}
