package com.example

import com.example.core.elm327.AdapterType
import com.example.core.elm327.Elm327AdapterManager
import com.example.core.elm327.Elm327Driver
import com.example.core.elm327.ElmResponseType
import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.protocol.PidDecoder
import com.example.core.transport.ITransport
import com.example.core.transport.RawTxRxResult
import com.example.core.transport.TransportState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327AdapterManagerTest {

    private class FakeHardwareTransport(
        private val responseMap: Map<String, String>,
        private val simulateTimeoutCommands: Set<String> = emptySet()
    ) : ITransport {
        private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
        override val state: StateFlow<TransportState> = _state.asStateFlow()

        private val _rxEvidenceFlow = MutableSharedFlow<RawTxRxResult>(extraBufferCapacity = 64)
        override val rxEvidenceFlow: SharedFlow<RawTxRxResult> = _rxEvidenceFlow.asSharedFlow()

        val recordedCommands = mutableListOf<String>()

        override suspend fun connect(): Boolean {
            _state.value = TransportState.Connected(
                deviceName = "Fake ELM327 BT",
                baudRate = 38400,
                provenance = DataProvenance.REAL_HARDWARE,
                isHardware = true
            )
            return true
        }

        override suspend fun disconnect() {
            _state.value = TransportState.Disconnected
        }

        override suspend fun reconnect(maxRetries: Int): Boolean = connect()

        override suspend fun sendRawBytes(data: ByteArray): Boolean = isConnected()

        override suspend fun readRawBytes(timeoutMs: Long, stopOnPrompt: Boolean): ByteArray = ByteArray(0)

        override fun isConnected(): Boolean = _state.value is TransportState.Connected

        override suspend fun sendAndReceive(command: String, timeoutMs: Long): RawTxRxResult {
            val clean = command.trim().uppercase()
            recordedCommands.add(clean)

            if (simulateTimeoutCommands.contains(clean)) {
                val timeoutResult = RawTxRxResult(
                    commandTx = clean,
                    responseRx = "TIMEOUT\r\r>",
                    latencyMs = timeoutMs,
                    isTimeout = true,
                    quality = DataQuality.TIMEOUT,
                    provenance = DataProvenance.REAL_HARDWARE
                )
                _rxEvidenceFlow.tryEmit(timeoutResult)
                return timeoutResult
            }

            val rx = responseMap[clean] ?: "NO DATA\r\r>"
            val result = RawTxRxResult(
                commandTx = clean,
                responseRx = rx,
                latencyMs = 24L,
                isTimeout = false,
                isError = rx.contains("ERROR"),
                rawBytes = rx.toByteArray(Charsets.US_ASCII),
                quality = DataQuality.VALID,
                provenance = DataProvenance.REAL_HARDWARE
            )
            _rxEvidenceFlow.tryEmit(result)
            return result
        }
    }

    @Test
    fun driver_parsesSpacelessAndSearchingCanResponsesAccurately() = runBlocking {
        val fakeTransport = FakeHardwareTransport(
            responseMap = mapOf(
                "ATZ" to "ELM327 v1.5\r\r>",
                "ATE0" to "OK\r\r>",
                "ATL0" to "OK\r\r>",
                "ATS1" to "OK\r\r>",
                "ATH1" to "OK\r\r>",
                "ATSP0" to "OK\r\r>",
                "ATDP" to "AUTO, ISO 15765-4 (CAN 11/500)\r\r>",
                "ATRV" to "13.8V\r\r>",
                // Spaceless CAN frame with SEARCHING... prefix: 7E8 | 04 41 0C 1A F8 -> RPM = ((0x1A*256)+0xF8)/4 = 1726.0
                "010C" to "SEARCHING...\r7E804410C1AF8\r\r>",
                // Spaced CAN frame: 7E8 03 41 05 82 -> Coolant = 0x82 (130) - 40 = 90 C
                "0105" to "7E8 03 41 05 82\r\r>"
            )
        )

        val driver = Elm327Driver(fakeTransport)
        assertTrue(driver.initializeAdapter())
        assertEquals("ELM327 v1.5", driver.adapterVersion.value)
        assertEquals("ISO 15765-4 (CAN 11/500)", driver.detectedProtocol.value)
        assertEquals("13.8V", driver.batteryVoltage.value)

        val rpmResp = driver.queryPid("01", "0C")
        assertEquals(ElmResponseType.VALID_DATA, rpmResp.type)
        assertEquals("7E8", rpmResp.ecuHeader)
        val rpmPoint = PidDecoder.decodeTelemetryPoint("0C", rpmResp)
        assertNotNull(rpmPoint)
        assertEquals(1726.0, rpmPoint!!.value, 0.01)
        assertEquals(DataQuality.VALID, rpmPoint.quality)
        assertEquals(DataProvenance.REAL_HARDWARE, rpmPoint.provenance)

        val coolantResp = driver.queryPid("01", "05")
        val coolantPoint = PidDecoder.decodeTelemetryPoint("05", coolantResp)
        assertNotNull(coolantPoint)
        assertEquals(90.0, coolantPoint!!.value, 0.01)
    }

    @Test
    fun adapterManager_connectsDiscoversSupportedPidsAndHandlesStandardPidRequests() = runBlocking {
        val fakeTransport = FakeHardwareTransport(
            responseMap = mapOf(
                "ATZ" to "ELM327 v2.1\r\r>",
                "ATE0" to "OK\r\r>",
                "ATL0" to "OK\r\r>",
                "ATS1" to "OK\r\r>",
                "ATH1" to "OK\r\r>",
                "ATSP0" to "OK\r\r>",
                "ATDP" to "ISO 15765-4 (CAN 11/500)\r\r>",
                "ATRV" to "14.2V\r\r>",
                // 0100: BE 3F B8 13 -> includes PID 04, 05, 0B, 0C, 0D, 10, 11, 1F, 20
                "0100" to "7E8 06 41 00 BE 3F B8 13\r\r>",
                "0120" to "7E8 06 41 20 80 07 E0 11\r\r>",
                "0140" to "7E8 06 41 40 7A D0 00 00\r\r>",
                "010C" to "7E8 04 41 0C 0F A0\r\r>", // 1000 RPM
                "010D" to "7E8 03 41 0D 50\r\r>",    // 80 km/h
                "0105" to "7E8 03 41 05 7D\r\r>",    // 85 C
                "0142" to "7E8 04 41 42 37 78\r\r>", // 14.20 V
                "0199" to "NO DATA\r\r>"
            ),
            simulateTimeoutCommands = setOf("015C")
        )

        val manager = Elm327AdapterManager(
            context = null,
            registerSystemReceivers = false,
            customTransportOverride = fakeTransport
        )

        val connected = manager.connect(AdapterType.BLUETOOTH_CLASSIC)
        assertTrue(connected)
        assertEquals("ELM327 v2.1", manager.adapterVersion.value)
        assertTrue(manager.supportedPids.value.containsAll(listOf("04", "05", "0C", "0D", "11", "42")))

        // Request standard PIDs
        val batch = manager.requestStandardPids(listOf("0C", "0D", "05", "42"))
        assertEquals(1000.0, batch["0C"]?.telemetryPoint?.value ?: 0.0, 0.01)
        assertEquals(80.0, batch["0D"]?.telemetryPoint?.value ?: 0.0, 0.01)
        assertEquals(85.0, batch["05"]?.telemetryPoint?.value ?: 0.0, 0.01)
        assertEquals(14.2, batch["42"]?.telemetryPoint?.value ?: 0.0, 0.01)

        // Unsupported PID returns UNAVAILABLE without fabricated values
        val unsupported = manager.requestStandardPid("99")
        assertFalse(unsupported.isSuccess)
        assertEquals(ElmResponseType.NO_DATA, unsupported.elmResponse.type)

        // Timeout PID returns TIMEOUT quality
        val timedOut = manager.requestStandardPid("5C")
        assertFalse(timedOut.isSuccess)
        assertEquals(DataQuality.TIMEOUT, timedOut.elmResponse.quality)

        // Parallel standard PID requests safely serialize via Mutex
        val concurrentResults = listOf("0C", "0D", "05", "42").map { pid ->
            async { manager.requestStandardPid(pid) }
        }.awaitAll()
        assertTrue(concurrentResults.all { it.isSuccess })

        manager.disconnect()
        manager.dispose()
    }

    @Test
    fun adapterManager_replayModeConnectsAndPollsStandardPids() = runBlocking {
        val manager = Elm327AdapterManager(
            context = null,
            registerSystemReceivers = false
        )

        val connected = manager.connect(AdapterType.REPLAY_SIMULATOR)
        assertTrue(connected)
        assertEquals("ELM327 v1.5", manager.adapterVersion.value)

        val rpm = manager.requestStandardPid("0C")
        assertTrue(rpm.isSuccess)
        assertNotNull(rpm.telemetryPoint)
        assertEquals(DataProvenance.SIMULATOR, rpm.telemetryPoint!!.provenance)
        assertTrue(rpm.telemetryPoint!!.value >= 600.0)

        val speed = manager.requestStandardPid("0D")
        assertTrue(speed.isSuccess)
        assertNotNull(speed.telemetryPoint)

        manager.disconnect()
        manager.dispose()
    }
}
