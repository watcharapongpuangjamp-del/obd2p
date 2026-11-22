package com.example.core.transport

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import kotlinx.coroutines.Dispatchers
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
 * USB-Serial OTG transport for wired ELM327 / STN1110 / OBDLink EX adapters
 * supporting FTDI, Silicon Labs CP210x, QinHeng CH340/CH341, Prolific PL2303, and CDC ACM.
 */
class UsbSerialTransport(
    private val context: Context,
    private var baudRate: Int = 38400
) : ITransport {

    companion object {
        const val VENDOR_FTDI = 0x0403
        const val VENDOR_PROLIFIC = 0x067B
        const val VENDOR_SILABS_CP210X = 0x10C4
        const val VENDOR_QINHENG_CH340 = 0x1A86
        const val VENDOR_ARDUINO_CDC = 0x2341

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

    private val _rxEvidenceFlow = MutableSharedFlow<RawTxRxResult>(extraBufferCapacity = 64)
    override val rxEvidenceFlow: SharedFlow<RawTxRxResult> = _rxEvidenceFlow.asSharedFlow()

    private val ioMutex = Mutex()
    private var usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var targetDeviceId: Int? = null
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    fun setTargetDevice(deviceId: Int?, baudRate: Int = this.baudRate) {
        this.targetDeviceId = deviceId
        this.baudRate = baudRate
    }

    fun getAvailableDevices(): List<UsbDevice> {
        return usbManager?.deviceList?.values?.toList().orEmpty()
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager?.hasPermission(device) == true
    }

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val manager = usbManager
            if (manager == null) {
                _state.value = TransportState.Error("USB Host Service unavailable on device")
                return@withContext false
            }

            val deviceList = manager.deviceList
            if (deviceList.isEmpty()) {
                _state.value = TransportState.Error(
                    "No USB OBD-II Adapter detected. Plug in ELM327 USB OTG cable or switch adapter mode."
                )
                return@withContext false
            }

            val targetDevice = if (targetDeviceId != null) {
                deviceList.values.firstOrNull { it.deviceId == targetDeviceId }
                    ?: deviceList.values.firstOrNull { isObdAdapter(it) }
                    ?: deviceList.values.first()
            } else {
                deviceList.values.firstOrNull { isObdAdapter(it) } ?: deviceList.values.first()
            }

            usbDevice = targetDevice
            val deviceName = targetDevice.productName ?: identifyChipset(targetDevice.vendorId)
            _state.value = TransportState.Connecting(deviceName)

            if (!manager.hasPermission(targetDevice)) {
                _state.value = TransportState.Error("USB Permission not granted for $deviceName")
                return@withContext false
            }

            val connection = manager.openDevice(targetDevice)
            if (connection == null) {
                _state.value = TransportState.Error("Failed to open USB device connection for $deviceName")
                return@withContext false
            }
            usbConnection = connection

            // Claim communication/data interface and locate bulk IN/OUT endpoints
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

            endpointIn = foundIn
            endpointOut = foundOut
            usbInterface = claimedIntf

            if (endpointIn == null || endpointOut == null) {
                _state.value = TransportState.Error("Bulk transfer endpoints not found on USB device $deviceName")
                closeConnectionQuietly()
                return@withContext false
            }

            // Configure serial baud rate and 8N1 framing for the USB-UART chipset
            configureSerialLineCoding(connection, targetDevice.vendorId, baudRate)

            _state.value = TransportState.Connected(
                deviceName = deviceName,
                baudRate = baudRate,
                provenance = DataProvenance.REAL_HARDWARE,
                isHardware = true
            )
            return@withContext true
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            closeConnectionQuietly()
            _state.value = TransportState.Disconnected
        }
    }

    override fun isConnected(): Boolean = _state.value is TransportState.Connected && usbConnection != null

    override suspend fun sendAndReceive(command: String, timeoutMs: Long): RawTxRxResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val start = System.currentTimeMillis()
            val conn = usbConnection
            val epOut = endpointOut
            val epIn = endpointIn

            if (conn == null || epOut == null || epIn == null) {
                return@withContext RawTxRxResult(
                    commandTx = command,
                    responseRx = "ERROR: USB_NOT_CONNECTED",
                    latencyMs = 0L,
                    isError = true,
                    quality = DataQuality.UNAVAILABLE,
                    provenance = DataProvenance.REAL_HARDWARE
                )
            }

            // Drain any stale RX buffer bytes before issuing command
            val drainBuf = ByteArray(256)
            while (conn.bulkTransfer(epIn, drainBuf, drainBuf.size, 15) > 0) {
                // Discard stale bytes
            }

            val txBytes = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
            val bytesSent = conn.bulkTransfer(epOut, txBytes, txBytes.size, 1000)

            if (bytesSent < 0) {
                return@withContext RawTxRxResult(
                    commandTx = command,
                    responseRx = "ERROR: TX_FAILED",
                    latencyMs = (System.currentTimeMillis() - start).coerceAtLeast(1L),
                    isError = true,
                    quality = DataQuality.INVALID,
                    provenance = DataProvenance.REAL_HARDWARE
                )
            }

            // Read response until '>' prompt or timeout
            val rxBuffer = ByteArray(1024)
            val sb = StringBuilder()
            val readDeadline = System.currentTimeMillis() + timeoutMs
            var promptReceived = false

            while (System.currentTimeMillis() < readDeadline) {
                val bytesRead = conn.bulkTransfer(epIn, rxBuffer, rxBuffer.size, 150)
                if (bytesRead > 0) {
                    val chunk = String(rxBuffer, 0, bytesRead, Charsets.US_ASCII)
                    sb.append(chunk)
                    if (sb.contains(">")) {
                        promptReceived = true
                        break
                    }
                }
            }

            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val responseStr = sb.toString().trim()
            val isTimeout = !promptReceived && responseStr.isEmpty()
            val isErr = responseStr.contains("ERROR", ignoreCase = true) ||
                responseStr.contains("UNABLE TO CONNECT", ignoreCase = true)

            val result = RawTxRxResult(
                commandTx = command,
                responseRx = if (responseStr.isEmpty()) "TIMEOUT\r\r>" else responseStr,
                latencyMs = elapsed,
                isTimeout = isTimeout,
                isError = isErr,
                rawBytes = responseStr.toByteArray(Charsets.US_ASCII),
                quality = when {
                    isTimeout -> DataQuality.TIMEOUT
                    isErr -> DataQuality.INVALID
                    else -> DataQuality.VALID
                },
                provenance = DataProvenance.REAL_HARDWARE,
                timestamp = System.currentTimeMillis()
            )

            _rxEvidenceFlow.tryEmit(result)
            return@withContext result
        }
    }

    private fun configureSerialLineCoding(conn: UsbDeviceConnection, vendorId: Int, baud: Int) {
        try {
            when (vendorId) {
                VENDOR_FTDI -> {
                    // FTDI SIO_RESET (0x00) & SIO_SET_MODEM_CTRL (0x01, DTR+RTS) & SIO_SET_BAUD_RATE (0x03)
                    conn.controlTransfer(0x40, 0x00, 0x0000, 0, null, 0, 500)
                    conn.controlTransfer(0x40, 0x01, 0x0303, 0, null, 0, 500)
                    val divisor = 3_000_000 / baud
                    conn.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 500)
                    conn.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 500) // 8N1
                }
                VENDOR_SILABS_CP210X -> {
                    // CP210x IFC_ENABLE (0x00, value=1), SET_BAUDRATE (0x1E), SET_LINE_CTL (0x03, 8N1 = 0x0800)
                    conn.controlTransfer(0x41, 0x00, 0x0001, 0, null, 0, 500)
                    val baudBytes = byteArrayOf(
                        (baud and 0xFF).toByte(),
                        ((baud shr 8) and 0xFF).toByte(),
                        ((baud shr 16) and 0xFF).toByte(),
                        ((baud shr 24) and 0xFF).toByte()
                    )
                    conn.controlTransfer(0x41, 0x1E, 0, 0, baudBytes, 4, 500)
                    conn.controlTransfer(0x41, 0x03, 0x0800, 0, null, 0, 500)
                    conn.controlTransfer(0x41, 0x07, 0x0303, 0, null, 0, 500) // DTR + RTS
                }
                VENDOR_QINHENG_CH340 -> {
                    // CH340 init + 8N1 baud rate setup
                    conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 500)
                    val baudFactor = when (baud) {
                        115200 -> 0xCC03
                        57600 -> 0x9803
                        else -> 0xB202 // 38400
                    }
                    conn.controlTransfer(0x40, 0x9A, 0x1312, baudFactor, null, 0, 500)
                    conn.controlTransfer(0x40, 0x9A, 0x2518, 0x00C3, null, 0, 500) // 8N1
                }
                else -> {
                    // USB CDC ACM SET_LINE_CODING (0x20) + SET_CONTROL_LINE_STATE (0x22)
                    val lineCoding = byteArrayOf(
                        (baud and 0xFF).toByte(),
                        ((baud shr 8) and 0xFF).toByte(),
                        ((baud shr 16) and 0xFF).toByte(),
                        ((baud shr 24) and 0xFF).toByte(),
                        0x00, // 1 stop bit
                        0x00, // no parity
                        0x08  // 8 data bits
                    )
                    conn.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 500)
                    conn.controlTransfer(0x21, 0x22, 0x0003, 0, null, 0, 500)
                }
            }
        } catch (_: Exception) {
            // Keep default adapter serial parameters if vendor control transfer is unsupported
        }
    }

    private fun closeConnectionQuietly() {
        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (_: Exception) {
        }
        usbConnection = null
        usbDevice = null
        usbInterface = null
        endpointIn = null
        endpointOut = null
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
