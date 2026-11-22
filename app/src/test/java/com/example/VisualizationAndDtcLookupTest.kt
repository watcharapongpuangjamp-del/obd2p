package com.example

import com.example.core.elm327.Elm327Driver
import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.DtcType
import com.example.core.model.PidPollingTier
import com.example.core.model.TelemetryPoint
import com.example.core.protocol.DtcKnowledgeBase
import com.example.core.scanner.DtcScanner
import com.example.core.scheduler.AdaptivePidScheduler
import com.example.core.transport.ITransport
import com.example.core.transport.RawTxRxResult
import com.example.core.transport.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

class VisualizationAndDtcLookupTest {

    private class ScriptedTransport(
        private val responses: Map<String, String>
    ) : ITransport {
        private val _state = MutableStateFlow<TransportState>(
            TransportState.Connected(
                deviceName = "Scripted Hardware",
                baudRate = 38400,
                provenance = DataProvenance.REAL_HARDWARE,
                isHardware = true
            )
        )
        override val state: StateFlow<TransportState> = _state.asStateFlow()

        private val _rxEvidenceFlow = MutableSharedFlow<RawTxRxResult>(extraBufferCapacity = 64)
        override val rxEvidenceFlow: SharedFlow<RawTxRxResult> = _rxEvidenceFlow.asSharedFlow()

        val sentCommands = mutableListOf<String>()

        override suspend fun connect(): Boolean = true
        override suspend fun disconnect() {
            _state.value = TransportState.Disconnected
        }

        override fun isConnected(): Boolean = _state.value is TransportState.Connected

        override suspend fun sendAndReceive(command: String, timeoutMs: Long): RawTxRxResult {
            val clean = command.trim().uppercase()
            sentCommands.add(clean)
            val rx = responses[clean] ?: "NO DATA\r\r>"
            val result = RawTxRxResult(
                commandTx = clean,
                responseRx = rx,
                latencyMs = 15L,
                isTimeout = false,
                rawBytes = rx.toByteArray(Charsets.US_ASCII),
                quality = DataQuality.VALID,
                provenance = DataProvenance.REAL_HARDWARE
            )
            _rxEvidenceFlow.tryEmit(result)
            return result
        }
    }

    @Test
    fun adaptiveScheduler_populatesValidatedSnapshotHistoryAndFiltersUnsupportedPids() = runBlocking {
        val transport = ScriptedTransport(
            responses = mapOf(
                "010C" to "7E8 04 41 0C 0F A0\r\r>", // 1000 RPM (FAST)
                "010D" to "7E8 03 41 0D 3C\r\r>",    // 60 km/h (FAST)
                "0104" to "7E8 03 41 04 80\r\r>",    // ~50.2% Load (MEDIUM)
                "0105" to "7E8 03 41 05 82\r\r>"     // 90 C Coolant (MEDIUM)
            )
        )
        val driver = Elm327Driver(transport)
        val scheduler = AdaptivePidScheduler(maxHistorySamples = 10)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Only 0C, 0D, 04, 05 supported; 11, 0B etc. unsupported
        val supported = setOf("0C", "0D", "04", "05")
        scheduler.startPolling(driver, scope, supported)

        delay(220)
        scheduler.stopPolling()
        scope.cancel()

        val snapshot = scheduler.validatedSnapshot.value
        assertEquals(DataProvenance.REAL_HARDWARE, snapshot.provenance)
        assertEquals(supported, snapshot.supportedPids)

        // Supported PIDs received valid samples and history
        val rpmChannel = snapshot.channels["0C"]
        assertNotNull(rpmChannel)
        assertEquals(DataQuality.VALID, rpmChannel!!.latest.quality)
        assertEquals(DataProvenance.REAL_HARDWARE, rpmChannel.latest.provenance)
        assertEquals(1000.0, rpmChannel.latest.value, 0.01)
        assertEquals(PidPollingTier.FAST, rpmChannel.tier)
        assertTrue(rpmChannel.history.isNotEmpty())
        assertTrue(rpmChannel.latest.timestamp > 0L)

        // Unsupported PID explicitly seeded as UNSUPPORTED and never polled
        val throttleChannel = snapshot.channels["11"]
        assertNotNull(throttleChannel)
        assertEquals(DataQuality.UNSUPPORTED, throttleChannel!!.latest.quality)
        assertFalse(throttleChannel.isSupportedByEcu)
        assertFalse(transport.sentCommands.contains("0111"))
    }

    @Test
    fun adaptiveScheduler_recordsManualTelemetryPointIntoValidatedSnapshot() {
        val scheduler = AdaptivePidScheduler()
        val manualPoint = TelemetryPoint(
            id = "PID_42",
            name = "Control Module Voltage",
            thaiName = "แรงดันไฟกล่อง ECU",
            value = 14.15,
            displayValue = "14.15",
            unit = "V",
            quality = DataQuality.VALID,
            provenance = DataProvenance.REAL_HARDWARE,
            sourcePid = "42",
            sourceEcuId = "7E8",
            latencyMs = 18L
        )
        scheduler.recordManualTelemetryPoint(manualPoint)

        val channel = scheduler.validatedSnapshot.value.channels["42"]
        assertNotNull(channel)
        assertEquals(14.15, channel!!.latest.value, 0.001)
        assertEquals(1, channel.history.size)
        assertEquals(DataProvenance.REAL_HARDWARE, channel.latest.provenance)
    }

    @Test
    fun dtcKnowledgeBase_decodesExactAndSaeJ2012FallbackWithLinks() {
        val exact = DtcKnowledgeBase.lookupDtc("  p0171 ").getOrThrow()
        assertEquals("P0171", exact.code)
        assertTrue(exact.isExactLocalMatch)
        assertTrue(exact.implicationsThai.isNotBlank())
        assertTrue(exact.potentialCausesThai.isNotEmpty())
        assertTrue(exact.inspectionStepsThai.isNotEmpty())
        assertTrue(exact.onlineResources.any { it.url.contains("p0171") })

        // SAE J2012 structured fallback for manufacturer-specific valid code
        val fallback = DtcKnowledgeBase.lookupDtc("P1604").getOrThrow()
        assertEquals("P1604", fallback.code)
        assertFalse(fallback.isExactLocalMatch)
        assertTrue(fallback.thaiExplanation.contains("SAE J2012"))
        assertTrue(fallback.implicationsThai.isNotBlank())
        assertTrue(fallback.onlineResources.isNotEmpty())

        // Invalid code format rejected cleanly
        val invalid = DtcKnowledgeBase.lookupDtc("INVALID99")
        assertTrue(invalid.isFailure)
    }

    @Test
    fun dtcScannerAndLookup_linkConfirmedPendingPermanentStatusToExactEvidenceChain() = runBlocking {
        // Service 03: P0171 (01 71), Service 07: P0420 (04 20), Service 0A: U0100 (C1 00)
        val transport = ScriptedTransport(
            responses = mapOf(
                "03" to "7E8 04 43 01 01 71\r\r>",
                "07" to "7E8 04 47 01 04 20\r\r>",
                "0A" to "7E8 04 4A 01 C1 00\r\r>"
            )
        )
        val driver = Elm327Driver(transport)
        val scanner = DtcScanner()

        val scanResult = scanner.performFullScan(driver)
        assertEquals(1, scanResult.confirmedCodes.size)
        assertEquals(1, scanResult.pendingCodes.size)
        assertEquals(1, scanResult.permanentCodes.size)

        val confirmedP0171 = scanResult.confirmedCodes.first()
        assertEquals("P0171", confirmedP0171.code)
        assertEquals(DtcType.CONFIRMED, confirmedP0171.type)
        assertTrue(confirmedP0171.onlineResources.isNotEmpty())

        // Verify exact 1-to-1 EvidenceLog linkage in scanEvidenceLogs
        val linkedEvidence = scanResult.scanEvidenceLogs.firstOrNull { it.id == confirmedP0171.evidenceRef }
        assertNotNull(linkedEvidence)
        assertTrue(linkedEvidence!!.sha256Hash.isNotBlank())
        assertEquals("03", linkedEvidence.commandText)

        // Lookup links P0420 to its active PENDING scan status and Evidence Chain ref
        val lookupP0420 = DtcKnowledgeBase.lookupDtc("P0420", scanResult).getOrThrow()
        assertEquals(1, lookupP0420.matchedScannedDtcs.size)
        assertEquals(DtcType.PENDING, lookupP0420.matchedScannedDtcs.first().type)
        assertEquals(scanResult.pendingCodes.first().evidenceRef, lookupP0420.matchedScannedDtcs.first().evidenceRef)

        // Lookup links U0100 to its active PERMANENT scan status
        val lookupU0100 = DtcKnowledgeBase.lookupDtc("U0100", scanResult).getOrThrow()
        assertEquals(1, lookupU0100.matchedScannedDtcs.size)
        assertEquals(DtcType.PERMANENT, lookupU0100.matchedScannedDtcs.first().type)
    }
}
