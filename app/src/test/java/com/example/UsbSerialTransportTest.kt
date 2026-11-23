package com.example

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.transport.RawTxRxResult
import com.example.core.transport.TransportFailureKind
import com.example.core.transport.TransportState
import com.example.core.transport.UsbSerialPortHandle
import com.example.core.transport.UsbSerialTransport
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class UsbSerialTransportTest {

    private class FakeUsbSerialPortHandle(
        override val deviceName: String = "FTDI FT232R USB-OBD",
        override val deviceId: Int = 101,
        override val vendorId: Int = UsbSerialTransport.VENDOR_FTDI,
        private val activeBaudRate: Int = 38400,
        private val failNextWriteCode: Int? = null,
        private val hardReadErrorOnCommands: Set<String> = emptySet(),
        private val timeoutCommands: Set<String> = emptySet(),
        private val customResponses: Map<String, String> = mapOf(
            "AT" to "OK\r\r>",
            "ATZ" to "ELM327 v1.5\r\r>",
            "010C" to "7E8 04 41 0C 0F A0\r\r>",
            "010D" to "7E8 03 41 0D 3C\r\r>"
        )
    ) : UsbSerialPortHandle {

        var configuredBaud: Int = 38400
            private set
        var forceWriteFailureCode: Int? = failNextWriteCode
        var closed: Boolean = false
            private set

        private val rxQueue = ArrayDeque<ByteArray>()
        private var lastWrittenCommand: String = ""

        override fun configureLineCoding(vendorId: Int, baudRate: Int): Boolean {
            configuredBaud = baudRate
            return true
        }

        override fun bulkWrite(data: ByteArray, timeoutMs: Int): Int {
            forceWriteFailureCode?.let { err ->
                forceWriteFailureCode = null
                return err
            }
            val cmd = String(data, Charsets.US_ASCII).trim().uppercase()
            lastWrittenCommand = cmd

            // Only emit RX payload when UART baud rate matches adapter's configured baud rate
            if (configuredBaud != activeBaudRate) {
                return data.size
            }

            when {
                hardReadErrorOnCommands.contains(cmd) -> {
                    // Signal read error in bulkRead
                }
                timeoutCommands.contains(cmd) -> {
                    // Leave rxQueue empty so readFromPortLocked times out
                }
                else -> {
                    val reply = customResponses[cmd] ?: "NO DATA\r\r>"
                    rxQueue.addLast(reply.toByteArray(Charsets.US_ASCII))
                }
            }
            return data.size
        }

        override fun bulkRead(buffer: ByteArray, timeoutMs: Int): Int {
            if (hardReadErrorOnCommands.contains(lastWrittenCommand) && configuredBaud == activeBaudRate) {
                lastWrittenCommand = ""
                return -19 // ENODEV / hard I/O disconnect error
            }
            val nextChunk = rxQueue.pollFirst() ?: return -1
            val count = minOf(buffer.size, nextChunk.size)
            System.arraycopy(nextChunk, 0, buffer, 0, count)
            if (count < nextChunk.size) {
                rxQueue.addFirst(nextChunk.copyOfRange(count, nextChunk.size))
            }
            return count
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun connect_permissionMissing_emitsPermissionRequiredState() = runBlocking {
        val transport = UsbSerialTransport(
            context = null,
            baudRate = 38400,
            portOpener = {
                UsbSerialTransport.PortOpenOutcome.PermissionMissing(
                    deviceName = "Silicon Labs CP210x",
                    deviceId = 42
                )
            }
        )

        val connected = transport.connect()
        assertFalse(connected)
        assertFalse(transport.isConnected())

        val state = transport.state.value
        assertTrue(state is TransportState.PermissionRequired)
        state as TransportState.PermissionRequired
        assertEquals("Silicon Labs CP210x", state.deviceName)
        assertEquals(42, state.deviceId)
    }

    @Test
    fun connect_autoBaudNegotiation_fallsBackFrom38400To115200() = runBlocking {
        val port = FakeUsbSerialPortHandle(
            deviceName = "OBDLink EX 115200",
            activeBaudRate = 115200
        )
        val transport = UsbSerialTransport(
            context = null,
            baudRate = 38400,
            portOpener = { UsbSerialTransport.PortOpenOutcome.Opened(port) }
        )

        val connected = transport.connect()
        assertTrue(connected)
        assertTrue(transport.isConnected())
        assertEquals(115200, transport.getCurrentBaudRate())
        assertEquals(115200, port.configuredBaud)

        val state = transport.state.value as TransportState.Connected
        assertEquals(115200, state.baudRate)
        assertEquals(DataProvenance.REAL_HARDWARE, state.provenance)
        assertTrue(state.isHardware)
    }

    @Test
    fun reconnect_usesExponentialBackoffAndRecoversAfterTransientFailures() = runBlocking {
        var openAttempts = 0
        val port = FakeUsbSerialPortHandle(activeBaudRate = 38400)
        val transport = UsbSerialTransport(
            context = null,
            baudRate = 38400,
            portOpener = {
                openAttempts++
                if (openAttempts < 3) {
                    UsbSerialTransport.PortOpenOutcome.Failed(
                        message = "Transient USB busy ($openAttempts)",
                        failureKind = TransportFailureKind.OPEN_FAILED
                    )
                } else {
                    UsbSerialTransport.PortOpenOutcome.Opened(port)
                }
            }
        )

        val observedReconnectingStates = mutableListOf<TransportState.Reconnecting>()
        val collectJob = launch {
            transport.state.collect { st ->
                if (st is TransportState.Reconnecting) {
                    observedReconnectingStates.add(st)
                }
            }
        }

        val recovered = transport.reconnect(maxRetries = 3)
        collectJob.cancel()

        assertTrue(recovered)
        assertEquals(3, openAttempts)
        assertTrue(transport.isConnected())
        assertTrue(transport.state.value is TransportState.Connected)
        assertTrue(observedReconnectingStates.any { it.attempt == 1 && it.delayMs == 400L })
        assertTrue(observedReconnectingStates.any { it.attempt == 2 && it.delayMs == 800L })
        assertTrue(observedReconnectingStates.any { it.attempt == 3 && it.delayMs == 1600L })
    }

    @Test
    fun reconnect_stopsImmediatelyWhenPermissionRequired() = runBlocking {
        var openAttempts = 0
        val transport = UsbSerialTransport(
            context = null,
            baudRate = 38400,
            portOpener = {
                openAttempts++
                UsbSerialTransport.PortOpenOutcome.PermissionMissing("CH340 OBD", 7)
            }
        )

        val ok = transport.reconnect(maxRetries = 3)
        assertFalse(ok)
        assertEquals(1, openAttempts)
        assertTrue(transport.state.value is TransportState.PermissionRequired)
    }

    @Test
    fun rawTxRxHelpers_sendRawBytesAndReadRawBytes_emitEvidenceAndHandleTxError() = runBlocking {
        val port = FakeUsbSerialPortHandle(activeBaudRate = 38400)
        val transport = UsbSerialTransport(
            context = null,
            baudRate = 38400,
            portOpener = { UsbSerialTransport.PortOpenOutcome.Opened(port) }
        )

        assertTrue(transport.connect())

        val evidenceEvents = mutableListOf<RawTxRxResult>()
        val collector = launch {
            transport.rxEvidenceFlow.take(2).collect { evidenceEvents.add(it) }
        }

        val txOk = transport.sendRawBytes("ATZ\r".toByteArray(Charsets.US_ASCII))
        assertTrue(txOk)

        val rxBytes = transport.readRawBytes(timeoutMs = 500L, stopOnPrompt = true)
        assertEquals("ELM327 v1.5\r\r>", String(rxBytes, Charsets.US_ASCII))

        // Trigger a TX failure on next write
        port.forceWriteFailureCode = -5
        val txFail = transport.sendRawBytes("ATRV\r".toByteArray(Charsets.US_ASCII))
        assertFalse(txFail)
        assertFalse(transport.isConnected())

        val errState = transport.state.value as TransportState.Error
        assertEquals(TransportFailureKind.TX_ERROR, errState.failureKind)
        assertTrue(port.closed)

        collector.join()
        assertEquals(2, evidenceEvents.size)
        assertFalse(evidenceEvents[0].isError)
        assertEquals(TransportFailureKind.NONE, evidenceEvents[0].failureKind)
        assertTrue(evidenceEvents[1].isError)
        assertEquals(TransportFailureKind.TX_ERROR, evidenceEvents[1].failureKind)
    }

    @Test
    fun sendAndReceive_distinguishesSuccessTimeoutAndHardwareReadErrorWithTimestamps() = runBlocking {
        val port = FakeUsbSerialPortHandle(
            activeBaudRate = 38400,
            timeoutCommands = setOf("015C"),
            hardReadErrorOnCommands = setOf("0105")
        )
        val transport = UsbSerialTransport(
            context = null,
            baudRate = 38400,
            portOpener = { UsbSerialTransport.PortOpenOutcome.Opened(port) }
        )
        assertTrue(transport.connect())

        // 1. Successful transaction
        val okRes = transport.sendAndReceive("010C", timeoutMs = 400L)
        assertFalse(okRes.isTimeout)
        assertFalse(okRes.isError)
        assertEquals(DataQuality.VALID, okRes.quality)
        assertEquals(TransportFailureKind.NONE, okRes.failureKind)
        assertTrue(okRes.responseRx.contains("41 0C 0F A0"))
        assertTrue(okRes.txTimestamp > 0L)
        assertTrue(okRes.rxTimestamp >= okRes.txTimestamp)
        assertTrue(transport.state.value is TransportState.Connected)

        // 2. Command Timeout -> Timeout state, transport remains connected & recovers on next valid RX
        val timeoutRes = transport.sendAndReceive("015C", timeoutMs = 220L)
        assertTrue(timeoutRes.isTimeout)
        assertFalse(timeoutRes.isError)
        assertEquals(DataQuality.TIMEOUT, timeoutRes.quality)
        assertEquals(TransportFailureKind.TIMEOUT, timeoutRes.failureKind)
        assertTrue(transport.state.value is TransportState.Timeout)
        assertTrue(transport.isConnected())

        val recoveredRes = transport.sendAndReceive("010D", timeoutMs = 400L)
        assertFalse(recoveredRes.isTimeout)
        assertEquals(DataQuality.VALID, recoveredRes.quality)
        assertTrue(transport.state.value is TransportState.Connected)

        // 3. Hard USB RX Read Failure -> Error(RX_ERROR) and port closed
        val rxErrRes = transport.sendAndReceive("0105", timeoutMs = 400L)
        assertTrue(rxErrRes.isError)
        assertFalse(rxErrRes.isTimeout)
        assertEquals(DataQuality.INVALID, rxErrRes.quality)
        assertEquals(TransportFailureKind.RX_ERROR, rxErrRes.failureKind)
        assertFalse(transport.isConnected())

        val errState = transport.state.value as TransportState.Error
        assertEquals(TransportFailureKind.RX_ERROR, errState.failureKind)
        assertTrue(port.closed)
    }
}
