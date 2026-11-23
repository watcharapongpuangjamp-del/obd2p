package com.example

import com.example.core.elm327.Elm327Driver
import com.example.core.elm327.ElmResponse
import com.example.core.elm327.ElmResponseType
import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.DtcClearStatus
import com.example.core.protocol.DtcDecoder
import com.example.core.scanner.DtcScanner
import com.example.core.transport.ReplayVehicleTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcClearServiceTest {

    @Test
    fun decodeClearDtcResponse_positiveCanResponse_returnsSuccess() {
        val response = ElmResponse(
            type = ElmResponseType.VALID_DATA,
            rawResponse = "7E8 01 44\r\r>",
            cleanPayload = "44",
            ecuHeader = "7E8",
            dataBytes = listOf(0x01, 0x44),
            latencyMs = 28L,
            provenance = DataProvenance.REAL_HARDWARE,
            quality = DataQuality.VALID
        )

        val result = DtcDecoder.decodeClearDtcResponse(response, "EVD_TEST_001")

        assertEquals(DtcClearStatus.SUCCESS, result.status)
        assertTrue(result.isSuccess)
        assertEquals("04", result.commandTx)
        assertEquals("7E8 01 44\r\r>", result.responseRx)
        assertEquals("7E8", result.ecuHeader)
        assertEquals("EVD_TEST_001", result.evidenceRef)
        assertTrue(result.sha256Hash.isNotBlank())
        assertTrue(result.confirmationMessageThai.contains("ตอบรับคำสั่ง Service 04"))
        assertTrue(result.confirmationMessageEnglish.contains("positive response (44)"))
    }

    @Test
    fun decodeClearDtcResponse_negativeResponseCode22_returnsEcuRejected() {
        // 7E8 03 7F 04 22 -> Negative Response Code 0x22 (Conditions Not Correct - e.g. Engine Running)
        val response = ElmResponse(
            type = ElmResponseType.VALID_DATA,
            rawResponse = "7E8 03 7F 04 22\r\r>",
            cleanPayload = "7F 04 22",
            ecuHeader = "7E8",
            dataBytes = listOf(0x03, 0x7F, 0x04, 0x22),
            latencyMs = 35L,
            provenance = DataProvenance.REAL_HARDWARE,
            quality = DataQuality.VALID
        )

        val result = DtcDecoder.decodeClearDtcResponse(response, "EVD_TEST_002")

        assertEquals(DtcClearStatus.ECU_REJECTED, result.status)
        assertFalse(result.isSuccess)
        assertEquals("22", result.negativeResponseCode)
        assertNotNull(result.errorMessageThai)
        assertTrue(result.errorMessageThai!!.contains("สภาวะไม่เหมาะสม") || result.errorMessageThai!!.contains("KOEO"))
        assertTrue(result.errorMessageEnglish!!.contains("NRC 22"))
    }

    @Test
    fun decodeClearDtcResponse_noData_returnsNoDataStatus() {
        val response = ElmResponse(
            type = ElmResponseType.NO_DATA,
            rawResponse = "NO DATA\r\r>",
            cleanPayload = "NO DATA",
            latencyMs = 1500L,
            provenance = DataProvenance.REAL_HARDWARE,
            quality = DataQuality.UNAVAILABLE
        )

        val result = DtcDecoder.decodeClearDtcResponse(response, "EVD_TEST_003")

        assertEquals(DtcClearStatus.NO_DATA, result.status)
        assertFalse(result.isSuccess)
        assertEquals(DataQuality.UNAVAILABLE, result.quality)
        assertTrue(result.errorMessageThai!!.contains("NO DATA"))
    }

    @Test
    fun dtcScanner_clearCodes_endToEndReplayTransport_clearsDtcRecords() = runBlocking {
        val transport = ReplayVehicleTransport()
        transport.connect()
        val driver = Elm327Driver(transport)
        driver.initializeAdapter()

        val scanner = DtcScanner()
        // 1. Initial scan with fault codes present
        val initialScan = scanner.performFullScan(driver)
        assertTrue(initialScan.totalFaults > 0)

        // 2. Clear DTCs via Service 04
        val clearResult = scanner.clearCodes(driver)

        assertTrue(clearResult.isSuccess)
        assertEquals(DtcClearStatus.SUCCESS, clearResult.status)
        assertEquals("04", clearResult.commandTx)
        assertTrue(clearResult.responseRx.contains("44"))
        assertTrue(clearResult.evidenceRef.isNotBlank())
        assertTrue(clearResult.sha256Hash.isNotBlank())

        // 3. Verify scan result state in scanner
        val stateAfterClear = scanner.scanResult.value
        assertEquals(0, stateAfterClear.confirmedCodes.size)
        assertEquals(0, stateAfterClear.pendingCodes.size)
        assertTrue(stateAfterClear.evidenceRefs.contains(clearResult.evidenceRef))
    }
}
