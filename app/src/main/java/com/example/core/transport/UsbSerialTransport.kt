package com.example.core.transport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Low-level USB port abstraction enabling deterministic JVM unit testing alongside
 * Android USB Host bulk/control transfers.
 */
interface UsbSerialPortHandle {
    val deviceName: String
    val deviceId: Int
    val vendorId: Int
    fun configureLineCoding(vendorId: Int, baudRate: Int): Boolean
    fun bulkWrite(data: ByteArray, timeoutMs: Int): Int
    fun bulkRead(buffer: ByteArray, timeoutMs: Int): Int
    fun close()
}

private class AndroidUsbSerialPortHandle(
    override val deviceName: String,
    override val deviceId: Int,
    override val vendorId: Int,
    private val connection: UsbDeviceConnection,
    private val claimedInterface: UsbInterface,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint
) : UsbSerialPortHandle {

    override fun configureLineCoding(vendorId: Int, baudRate: Int): Boolean {
        return try {
            when (vendorId) {
                UsbSerialTransport.VENDOR_FTDI -> {
                    connection.controlTransfer(0x40, 0x00, 0x0000, 0, null, 0, 500)
                    connection.controlTransfer(0x40, 0x01, 0x0303, 0, null, 0, 500)
                    val divisor = 3_000_000 / baudRate
                    connection.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 500)
                    connection.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 500)
                }
                UsbSerialTransport.VENDOR_SILABS_CP210X -> {
                    connection.controlTransfer(0x41, 0x00, 0x0001, 0, null, 0, 500)
                    val baudBytes = byteArrayOf(
                        (baudRate and 0xFF).toByte(),
                        ((baudRate shr 8) and 0xFF).toByte(),
                        ((baudRate shr 16) and 0xFF).toByte(),
                        ((baudRate shr 24) and 0xFF).toByte()
                    )
                    connection.controlTransfer(0x41, 0x1E, 0, 0, baudBytes, 4, 500)
                    connection.controlTransfer(0x41, 0x03, 0x0800, 0, null, 0, 500)
                    connection.controlTransfer(0x41, 0x07, 0x0303, 0, null, 0, 500)
                }
                UsbSerialTransport.VENDOR_QINHENG_CH340 -> {
                    connection.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 500)
                    val baudFactor = when (baudRate) {
                        115200 -> 0xCC03
                        57600 -> 0x9803
                        else -> 0xB202 // 38400
                    }
                    connection.controlTransfer(0x40, 0x9A, 0x1312, baudFactor, null, 0, 500)
                    connection.controlTransfer(0x40, 0x9A, 0x2518, 0x00C3, null, 0, 500)
                }
                UsbSerialTransport.VENDOR_PROLIFIC -> {
                    // Prolific PL2303 line coding (0x20) + modem control (DTR/RTS)
                    val lineCoding = byteArrayOf(
                        (baudRate and 0xFF).toByte(),
                        ((baudRate shr 8) and 0xFF).toByte(),
                        ((baudRate shr 16) and 0xFF).toByte(),
                        ((baudRate shr 24) and 0xFF).toByte(),
                        0x00,
                        0x00,
                        0x08
                    )
                    connection.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 500)
                    connection.controlTransfer(0x21, 0x22, 0x0003, 0, null, 0, 500)
                }
                else -> {
                    // USB CDC ACM SET_LINE_CODING (0x20) + SET_CONTROL_LINE_STATE (0x22)
                    val lineCoding = byteArrayOf(
                        (baudRate and 0xFF).toByte(),
                        ((baudRate shr 8) and 0xFF).toByte(),
                        ((baudRate shr 16) and 0xFF).toByte(),
                        ((baudRate shr 24) and 0xFF).toByte(),
                        0x00,
                        0x00,
                        0x08
                    )
                    connection.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 500)
                    connection.controlTransfer(0x21, 0x22, 0x0003, 0, null, 0, 500)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun bulkWrite(data: ByteArray, timeoutMs: Int): Int {
        return connection.bulkTransfer(endpointOut, data, data.size, timeoutMs)
    }

    override fun bulkRead(buffer: ByteArray, timeoutMs: Int): Int {
        return connection.bulkTransfer(endpointIn, buffer, buffer.size, timeoutMs)
    }

    override fun close() {
        try {
            connection.releaseInterface(claimedInterface)
            connection.close()
        } catch (_: Exception) {
        }
    }
}

/**
 * Hardened USB-Serial OTG transport for wired ELM327 / STN1110 / OBDLink EX adapters
 * supporting FTDI, Silicon Labs CP210x, QinHeng CH340/CH341, Prolific PL2303, and CDC ACM.
 *
 * Features:
 * - Android USB device discovery + scoped permission request with PermissionRequired state
 * - 38400 / 115200 bps auto-baud handshake negotiation
 * - Bounded exponential-backoff reconnect() lifecycle
 * - Raw TX/RX byte primitives + prompt-framed sendAndReceive()
 * - Distinct Timeout vs Error discrimination and full TX/RX Evidence Chain logging
 */
class UsbSerialTransport(
    private val context: Context?,
    private var baudRate: Int = 38400,
    private val portOpener: (() -> PortOpenOutcome)? = null
) : ITransport {

    sealed class PortOpenOutcome {
        data class Opened(val handle: UsbSerialPortHandle) : PortOpenOutcome()
        data class PermissionMissing(val deviceName: String, val deviceId: Int?) : PortOpenOutcome()
        data class Failed(val message: String, val failureKind: TransportFailureKind) : PortOpenOutcome()
    }

    companion object {
        private const val TAG = "UsbSerialTransport"
        const val ACTION_USB_PERMISSION = "com.example.ELM327_USB_PERMISSION"
        const val VENDOR_FTDI = 0x0403
        const val VENDOR_PROLIFIC = 0x067B
        const val VENDOR_SILABS_CP210X = 0x10C4
        const val VENDOR_QINHENG_CH340 = 0x1A86
        const val VENDOR_ARDUINO_CDC = 0x2341

        val AUTO_BAUD_CANDIDATES = listOf(38400, 115200)

        fun identifyChipset(vendorId: Int): String {
            return when (vendorId) {
                VENDOR_FTDI -> "FTDI FT232R/FT231X"
                VENDOR_PROLIFIC -> "Prolific PL2303"
                VENDOR_SILABS_CP210X -> "Silicon Labs CP210x"
                VENDOR_QINHENG_CH340 -> "QinHeng CH340/CH341"
                VENDOR_ARDUINO_CDC -> "USB CDC ACM"
                else -> "Generic USB Serial (VID: 0x${vendorId.toString(16).uppercase()})"
            }
        }
    }

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _rxEvidenceFlow = MutableSharedFlow<RawTxRxResult>(extraBufferCapacity = 128)
    override val rxEvidenceFlow: SharedFlow<RawTxRxResult> = _rxEvidenceFlow.asSharedFlow()

    private val ioMutex = Mutex()
    private val usbManager: UsbManager? =
        context?.getSystemService(Context.USB_SERVICE) as? UsbManager

    private var targetDeviceId: Int? = null
    private var lastDeviceName: String = "USB OBD-II Adapter"
    private var activePort: UsbSerialPortHandle? = null

    fun setTargetDevice(deviceId: Int?, baudRate: Int = this.baudRate) {
        this.targetDeviceId = deviceId
        this.baudRate = baudRate
    }

    fun getCurrentBaudRate(): Int = baudRate

    fun getAvailableDevices(): List<UsbDevice> {
        return usbManager?.deviceList?.values?.toList().orEmpty()
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager?.hasPermission(device) == true
    }

    override fun requestPermission(): Boolean {
        return requestPermissionForDevice(targetDeviceId)
    }

    fun requestPermissionForDevice(deviceId: Int? = targetDeviceId): Boolean {
        val ctx = context ?: return false
        val manager = usbManager ?: run {
            _state.value = TransportState.Error(
                message = "USB Host Service unavailable on device",
                failureKind = TransportFailureKind.UNSUPPORTED_DEVICE
            )
            return false
        }

        val devices = manager.deviceList.values.toList()
        if (devices.isEmpty()) {
            _state.value = TransportState.Error(
                message = "No USB OBD-II Adapter attached to request permission.",
                failureKind = TransportFailureKind.DEVICE_NOT_FOUND
            )
            return false
        }

        val device = (if (deviceId != null) devices.firstOrNull { it.deviceId == deviceId } else null)
            ?: devices.firstOrNull { isObdAdapter(it) }
            ?: devices.first()

        val devName = device.productName ?: identifyChipset(device.vendorId)
        lastDeviceName = devName
        targetDeviceId = device.deviceId

        if (manager.hasPermission(device)) {
            return true
        }

        _state.value = TransportState.PermissionRequired(
            deviceName = devName,
            deviceId = device.deviceId
        )

        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(ctx.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(ctx, device.deviceId, intent, flags)
        manager.requestPermission(device, pendingIntent)
        return false
    }

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            connectInternalLocked()
        }
    }

    override suspend fun reconnect(maxRetries: Int): Boolean = withContext(Dispatchers.IO) {
        val safeRetries = maxRetries.coerceIn(1, 5)
        for (attempt in 1..safeRetries) {
            val backoffMs = 400L * (1L shl (attempt - 1))
            ioMutex.withLock {
                closePortQuietly()
            }
            _state.value = TransportState.Reconnecting(
                deviceName = lastDeviceName,
                attempt = attempt,
                maxAttempts = safeRetries,
                delayMs = backoffMs
            )
            logSafely("Reconnecting to $lastDeviceName (attempt $attempt/$safeRetries, backoff=${backoffMs}ms)")
            delay(backoffMs)

            val ok = ioMutex.withLock {
                connectInternalLocked()
            }
            if (ok) {
                return@withContext true
            }

            // Do not spin-retry if user action is needed for OS permission
            if (_state.value is TransportState.PermissionRequired) {
                return@withContext false
            }
        }
        false
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            closePortQuietly()
            _state.value = TransportState.Disconnected
        }
    }

    override fun isConnected(): Boolean {
        val st = _state.value
        return (st is TransportState.Connected || st is TransportState.Timeout) && activePort != null
    }

    override suspend fun sendRawBytes(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val port = activePort
            if (port == null) {
                _state.value = TransportState.Error(
                    message = "USB serial port not connected for raw TX",
                    failureKind = TransportFailureKind.NOT_CONNECTED
                )
                return@withContext false
            }
            val txTs = System.currentTimeMillis()
            val sent = port.bulkWrite(data, 1000)
            val rxTs = System.currentTimeMillis()
            val ok = sent >= 0
            val result = RawTxRxResult(
                commandTx = data.joinToString(" ") { String.format("%02X", it) },
                responseRx = if (ok) "RAW_TX_OK ($sent bytes)" else "ERROR: TX_FAILED ($sent)",
                latencyMs = (rxTs - txTs).coerceAtLeast(1L),
                isTimeout = false,
                isError = !ok,
                rawBytes = if (ok) byteArrayOf() else data,
                quality = if (ok) DataQuality.VALID else DataQuality.INVALID,
                provenance = DataProvenance.REAL_HARDWARE,
                timestamp = rxTs,
                txTimestamp = txTs,
                rxTimestamp = rxTs,
                rawTxBytes = data,
                baudRate = baudRate,
                failureKind = if (ok) TransportFailureKind.NONE else TransportFailureKind.TX_ERROR
            )
            _rxEvidenceFlow.tryEmit(result)
            if (!ok) {
                closePortQuietly()
                _state.value = TransportState.Error(
                    message = "USB raw write failed (rc=$sent)",
                    failureKind = TransportFailureKind.TX_ERROR,
                    timestamp = rxTs
                )
            }
            ok
        }
    }

    override suspend fun readRawBytes(timeoutMs: Long, stopOnPrompt: Boolean): ByteArray = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val port = activePort ?: return@withContext ByteArray(0)
            readFromPortLocked(port, timeoutMs, stopOnPrompt).first
        }
    }

    override suspend fun sendAndReceive(command: String, timeoutMs: Long): RawTxRxResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val txTimestamp = System.currentTimeMillis()
            val port = activePort

            if (port == null) {
                val notConnResult = RawTxRxResult(
                    commandTx = command,
                    responseRx = "ERROR: USB_NOT_CONNECTED",
                    latencyMs = 0L,
                    isError = true,
                    quality = DataQuality.UNAVAILABLE,
                    provenance = DataProvenance.REAL_HARDWARE,
                    timestamp = txTimestamp,
                    txTimestamp = txTimestamp,
                    rxTimestamp = txTimestamp,
                    rawTxBytes = command.toByteArray(Charsets.US_ASCII),
                    baudRate = baudRate,
                    failureKind = TransportFailureKind.NOT_CONNECTED
                )
                _rxEvidenceFlow.tryEmit(notConnResult)
                return@withContext notConnResult
            }

            // Drain any stale unread bytes before transmitting
            val drainBuf = ByteArray(256)
            var drainGuard = 0
            while (drainGuard < 8 && port.bulkRead(drainBuf, 15) > 0) {
                drainGuard++
            }

            val txBytes = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
            val bytesSent = port.bulkWrite(txBytes, 1000)

            if (bytesSent < 0) {
                val failTs = System.currentTimeMillis()
                val elapsed = (failTs - txTimestamp).coerceAtLeast(1L)
                closePortQuietly()
                _state.value = TransportState.Error(
                    message = "USB TX transfer failed on ${port.deviceName}",
                    failureKind = TransportFailureKind.TX_ERROR,
                    timestamp = failTs
                )
                val txErrResult = RawTxRxResult(
                    commandTx = command,
                    responseRx = "ERROR: TX_FAILED",
                    latencyMs = elapsed,
                    isError = true,
                    quality = DataQuality.INVALID,
                    provenance = DataProvenance.REAL_HARDWARE,
                    timestamp = failTs,
                    txTimestamp = txTimestamp,
                    rxTimestamp = failTs,
                    rawTxBytes = txBytes,
                    baudRate = baudRate,
                    failureKind = TransportFailureKind.TX_ERROR
                )
                _rxEvidenceFlow.tryEmit(txErrResult)
                return@withContext txErrResult
            }

            val (rxBytes, promptReceived, readFailed) = readFromPortLocked(port, timeoutMs, stopOnPrompt = true)
            val rxTimestamp = System.currentTimeMillis()
            val elapsed = (rxTimestamp - txTimestamp).coerceAtLeast(1L)
            val responseStr = String(rxBytes, Charsets.US_ASCII).trim()

            if (readFailed) {
                closePortQuietly()
                _state.value = TransportState.Error(
                    message = "USB RX hardware read failure on ${port.deviceName}",
                    failureKind = TransportFailureKind.RX_ERROR,
                    timestamp = rxTimestamp
                )
                val rxErrResult = RawTxRxResult(
                    commandTx = command,
                    responseRx = "ERROR: RX_IO_FAILED",
                    latencyMs = elapsed,
                    isError = true,
                    rawBytes = rxBytes,
                    quality = DataQuality.INVALID,
                    provenance = DataProvenance.REAL_HARDWARE,
                    timestamp = rxTimestamp,
                    txTimestamp = txTimestamp,
                    rxTimestamp = rxTimestamp,
                    rawTxBytes = txBytes,
                    baudRate = baudRate,
                    failureKind = TransportFailureKind.RX_ERROR
                )
                _rxEvidenceFlow.tryEmit(rxErrResult)
                return@withContext rxErrResult
            }

            val isTimeout = !promptReceived && responseStr.isEmpty()
            val isProtocolError = responseStr.contains("ERROR", ignoreCase = true) ||
                responseStr.contains("UNABLE TO CONNECT", ignoreCase = true)

            val failureKind = when {
                isTimeout -> TransportFailureKind.TIMEOUT
                isProtocolError -> TransportFailureKind.PROTOCOL_ERROR
                else -> TransportFailureKind.NONE
            }

            if (isTimeout) {
                _state.value = TransportState.Timeout(
                    message = "Timed out after ${elapsed}ms waiting for response to $command",
                    commandTx = command,
                    elapsedMs = elapsed,
                    timestamp = rxTimestamp
                )
            } else if (_state.value is TransportState.Timeout) {
                // Restore Connected status once the adapter resumes responding cleanly
                _state.value = TransportState.Connected(
                    deviceName = port.deviceName,
                    baudRate = baudRate,
                    provenance = DataProvenance.REAL_HARDWARE,
                    isHardware = true
                )
            }

            val result = RawTxRxResult(
                commandTx = command,
                responseRx = if (isTimeout) "TIMEOUT\r\r>" else responseStr,
                latencyMs = elapsed,
                isTimeout = isTimeout,
                isError = isProtocolError,
                rawBytes = rxBytes,
                quality = when {
                    isTimeout -> DataQuality.TIMEOUT
                    isProtocolError -> DataQuality.INVALID
                    else -> DataQuality.VALID
                },
                provenance = DataProvenance.REAL_HARDWARE,
                timestamp = rxTimestamp,
                txTimestamp = txTimestamp,
                rxTimestamp = rxTimestamp,
                rawTxBytes = txBytes,
                baudRate = baudRate,
                failureKind = failureKind
            )

            logSafely("TX[$txTimestamp]='${command.trim()}' -> RX[$rxTimestamp]='${result.responseRx.replace("\r", " ")}' (${elapsed}ms, baud=$baudRate, kind=$failureKind)")
            _rxEvidenceFlow.tryEmit(result)
            return@withContext result
        }
    }

    private fun connectInternalLocked(): Boolean {
        closePortQuietly()

        val outcome = if (portOpener != null) {
            portOpener.invoke()
        } else {
            openAndroidUsbPort()
        }

        return when (outcome) {
            is PortOpenOutcome.PermissionMissing -> {
                lastDeviceName = outcome.deviceName
                _state.value = TransportState.PermissionRequired(
                    deviceName = outcome.deviceName,
                    deviceId = outcome.deviceId
                )
                false
            }
            is PortOpenOutcome.Failed -> {
                _state.value = TransportState.Error(
                    message = outcome.message,
                    failureKind = outcome.failureKind
                )
                false
            }
            is PortOpenOutcome.Opened -> {
                val port = outcome.handle
                lastDeviceName = port.deviceName
                _state.value = TransportState.Connecting(port.deviceName)

                // Negotiate baud rate: prefer configured baudRate, auto-fallback across 38400 & 115200
                val candidates = (listOf(baudRate) + AUTO_BAUD_CANDIDATES).distinct()
                var negotiatedBaud = baudRate
                var verified = false

                for (candidate in candidates) {
                    port.configureLineCoding(port.vendorId, candidate)
                    if (probeBaudRate(port, candidate)) {
                        negotiatedBaud = candidate
                        verified = true
                        break
                    }
                }

                // Keep the handle if no echo returned during passive probe? Wait: if ALL candidates fail,
                // differentiate clearly between baud probe timeout vs silent CDC serial bridge.
                if (!verified) {
                    logSafely("Auto-baud probe did not match ELM prompt across $candidates; locking primary baud $baudRate for ATZ init.")
                    port.configureLineCoding(port.vendorId, baudRate)
                }

                baudRate = negotiatedBaud
                activePort = port
                _state.value = TransportState.Connected(
                    deviceName = port.deviceName,
                    baudRate = negotiatedBaud,
                    provenance = DataProvenance.REAL_HARDWARE,
                    isHardware = true
                )
                true
            }
        }
    }

    private fun probeBaudRate(port: UsbSerialPortHandle, candidateBaud: Int): Boolean {
        // Drain buffer first
        val drain = ByteArray(128)
        var guard = 0
        while (guard < 4 && port.bulkRead(drain, 15) > 0) {
            guard++
        }

        val probeBytes = "AT\r".toByteArray(Charsets.US_ASCII)
        val written = port.bulkWrite(probeBytes, 250)
        if (written < 0) return false

        val (rx, promptFound, readErr) = readFromPortLocked(port, timeoutMs = 250L, stopOnPrompt = true)
        if (readErr) return false
        val text = String(rx, Charsets.US_ASCII)
        val valid = promptFound ||
            text.contains("OK", ignoreCase = true) ||
            text.contains("ELM", ignoreCase = true)
        logSafely("Baud probe $candidateBaud -> '${text.replace("\r", " ").trim()}' (valid=$valid)")
        return valid
    }

    private fun openAndroidUsbPort(): PortOpenOutcome {
        val manager = usbManager ?: return PortOpenOutcome.Failed(
            message = "USB Host Service unavailable on device",
            failureKind = TransportFailureKind.UNSUPPORTED_DEVICE
        )

        val deviceList = manager.deviceList
        if (deviceList.isEmpty()) {
            return PortOpenOutcome.Failed(
                message = "No USB OBD-II Adapter detected. Plug in ELM327 USB OTG cable or switch adapter mode.",
                failureKind = TransportFailureKind.DEVICE_NOT_FOUND
            )
        }

        val targetDevice = if (targetDeviceId != null) {
            deviceList.values.firstOrNull { it.deviceId == targetDeviceId }
                ?: deviceList.values.firstOrNull { isObdAdapter(it) }
                ?: deviceList.values.first()
        } else {
            deviceList.values.firstOrNull { isObdAdapter(it) } ?: deviceList.values.first()
        }

        val deviceName = targetDevice.productName ?: identifyChipset(targetDevice.vendorId)
        lastDeviceName = deviceName

        if (!manager.hasPermission(targetDevice)) {
            return PortOpenOutcome.PermissionMissing(deviceName, targetDevice.deviceId)
        }

        val connection = manager.openDevice(targetDevice) ?: return PortOpenOutcome.Failed(
            message = "Failed to open USB device connection for $deviceName",
            failureKind = TransportFailureKind.OPEN_FAILED
        )

        var foundIn: UsbEndpoint? = null
        var foundOut: UsbEndpoint? = null
        var claimedIntf: UsbInterface? = null

        for (i in 0 until targetDevice.interfaceCount) {
            val intf = targetDevice.getInterface(i)
            connection.claimInterface(intf, true)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && foundIn == null) {
                        foundIn = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT && foundOut == null) {
                        foundOut = ep
                    }
                }
            }
            if (foundIn != null && foundOut != null) {
                claimedIntf = intf
                break
            } else {
                connection.releaseInterface(intf)
            }
        }

        if (foundIn == null || foundOut == null || claimedIntf == null) {
            try {
                connection.close()
            } catch (_: Exception) {
            }
            return PortOpenOutcome.Failed(
                message = "Bulk transfer endpoints not found on USB device $deviceName",
                failureKind = TransportFailureKind.UNSUPPORTED_DEVICE
            )
        }

        return PortOpenOutcome.Opened(
            AndroidUsbSerialPortHandle(
                deviceName = deviceName,
                deviceId = targetDevice.deviceId,
                vendorId = targetDevice.vendorId,
                connection = connection,
                claimedInterface = claimedIntf,
                endpointIn = foundIn,
                endpointOut = foundOut
            )
        )
    }

    private fun readFromPortLocked(
        port: UsbSerialPortHandle,
        timeoutMs: Long,
        stopOnPrompt: Boolean
    ): Triple<ByteArray, Boolean, Boolean> {
        val rxBuffer = ByteArray(1024)
        val collected = java.io.ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs
        var promptReceived = false
        var hardReadFailure = false

        while (System.currentTimeMillis() < deadline) {
            val bytesRead = port.bulkRead(rxBuffer, 120)
            if (bytesRead > 0) {
                collected.write(rxBuffer, 0, bytesRead)
                if (stopOnPrompt) {
                    val str = collected.toString(Charsets.US_ASCII.name())
                    if (str.contains(">")) {
                        promptReceived = true
                        break
                    }
                }
            } else if (bytesRead < -1) {
                // Physical disconnect / ENODEV error code (distinct from -1 USB timeout)
                hardReadFailure = true
                break
            }
        }

        return Triple(collected.toByteArray(), promptReceived, hardReadFailure)
    }

    private fun closePortQuietly() {
        try {
            activePort?.close()
        } catch (_: Exception) {
        }
        activePort = null
    }

    private fun logSafely(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: RuntimeException) {
            // Ignored under pure JVM unit tests where android.util.Log isn't mocked
        }
    }

    fun isObdAdapter(device: UsbDevice): Boolean {
        val vId = device.vendorId
        return vId == VENDOR_FTDI ||
            vId == VENDOR_PROLIFIC ||
            vId == VENDOR_SILABS_CP210X ||
            vId == VENDOR_QINHENG_CH340 ||
            vId == VENDOR_ARDUINO_CDC
    }
}
