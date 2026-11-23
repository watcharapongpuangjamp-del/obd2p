package com.example.core.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth Classic SPP (RFCOMM) transport for ELM327 OBD-II adapters
 * (e.g., Vgate iCar, OBDLink MX+/LX, KONNWEI, generic ELM327 v1.5 BT).
 */
class BluetoothElm327Transport(
    private val context: Context,
    private var targetDeviceAddress: String? = null
) : ITransport {

    companion object {
        // Standard Bluetooth Serial Port Profile (SPP) UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private val OBD_NAME_KEYWORDS = listOf(
            "OBD", "ELM", "VGATE", "ICAR", "OBDLINK", "KONNWEI", "V-LINK", "CARISTA", "TONWON", "ANCEL"
        )

        fun isLikelyObdDeviceName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val upper = name.uppercase()
            return OBD_NAME_KEYWORDS.any { upper.contains(it) }
        }
    }

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _rxEvidenceFlow = MutableSharedFlow<RawTxRxResult>(extraBufferCapacity = 64)
    override val rxEvidenceFlow: SharedFlow<RawTxRxResult> = _rxEvidenceFlow.asSharedFlow()

    private val ioMutex = Mutex()
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var connectedDevice: BluetoothDevice? = null
    private var lastDeviceLabel: String = "Bluetooth ELM327"

    fun setTargetDeviceAddress(address: String?) {
        targetDeviceAddress = address
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun requestPermission(): Boolean = hasConnectPermission()

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!hasConnectPermission()) return emptyList()
        return try {
            adapter.bondedDevices?.toList().orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
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
                closeSocketQuietly()
            }
            _state.value = TransportState.Reconnecting(
                deviceName = lastDeviceLabel,
                attempt = attempt,
                maxAttempts = safeRetries,
                delayMs = backoffMs
            )
            delay(backoffMs)
            val ok = ioMutex.withLock {
                connectInternalLocked()
            }
            if (ok) return@withContext true
            if (_state.value is TransportState.PermissionRequired) return@withContext false
        }
        false
    }

    @SuppressLint("MissingPermission")
    private fun connectInternalLocked(): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.value = TransportState.Error(
                "Bluetooth hardware unavailable on this device",
                TransportFailureKind.UNSUPPORTED_DEVICE
            )
            return false
        }

        if (!hasConnectPermission()) {
            _state.value = TransportState.PermissionRequired(
                deviceName = lastDeviceLabel,
                message = "Bluetooth permission (BLUETOOTH_CONNECT) required"
            )
            return false
        }

        if (!adapter.isEnabled) {
            _state.value = TransportState.Error(
                "Bluetooth is turned off. Please enable Bluetooth to connect to ELM327.",
                TransportFailureKind.OPEN_FAILED
            )
            return false
        }

        val bonded = try {
            adapter.bondedDevices?.toList().orEmpty()
        } catch (se: SecurityException) {
            _state.value = TransportState.Error(
                "Bluetooth permission denied: ${se.message}",
                TransportFailureKind.PERMISSION_DENIED
            )
            return false
        }

        if (bonded.isEmpty()) {
            _state.value = TransportState.Error(
                "No paired Bluetooth OBD-II adapters found. Pair your ELM327 in Android Bluetooth settings first.",
                TransportFailureKind.DEVICE_NOT_FOUND
            )
            return false
        }

        val device = if (!targetDeviceAddress.isNullOrBlank()) {
            bonded.firstOrNull { it.address.equals(targetDeviceAddress, ignoreCase = true) }
                ?: bonded.firstOrNull { isLikelyObdDeviceName(it.name) }
        } else {
            bonded.firstOrNull { isLikelyObdDeviceName(it.name) } ?: bonded.firstOrNull()
        }

        if (device == null) {
            _state.value = TransportState.Error(
                "Selected Bluetooth ELM327 device ($targetDeviceAddress) not found in paired devices.",
                TransportFailureKind.DEVICE_NOT_FOUND
            )
            return false
        }

        val deviceLabel = try {
            device.name ?: device.address
        } catch (_: SecurityException) {
            device.address
        }
        lastDeviceLabel = deviceLabel
        _state.value = TransportState.Connecting(deviceLabel)

        if (hasScanPermission()) {
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (_: SecurityException) {
            }
        }

        closeSocketQuietly()

        var rfcommSocket: BluetoothSocket? = null
        try {
            rfcommSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            rfcommSocket.connect()
        } catch (secureEx: IOException) {
            try {
                rfcommSocket?.close()
            } catch (_: IOException) {
            }
            try {
                rfcommSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                rfcommSocket.connect()
            } catch (insecureEx: IOException) {
                try {
                    rfcommSocket?.close()
                } catch (_: IOException) {
                }
                _state.value = TransportState.Error(
                    "Failed to open RFCOMM SPP channel to $deviceLabel: ${insecureEx.message ?: secureEx.message}",
                    TransportFailureKind.OPEN_FAILED
                )
                return false
            }
        } catch (se: SecurityException) {
            _state.value = TransportState.Error(
                "Bluetooth security error: ${se.message}",
                TransportFailureKind.PERMISSION_DENIED
            )
            return false
        }

        return try {
            socket = rfcommSocket
            inputStream = rfcommSocket?.inputStream
            outputStream = rfcommSocket?.outputStream
            connectedDevice = device
            _state.value = TransportState.Connected(
                deviceName = deviceLabel,
                baudRate = 38400,
                provenance = DataProvenance.REAL_HARDWARE,
                isHardware = true
            )
            true
        } catch (ioe: IOException) {
            closeSocketQuietly()
            _state.value = TransportState.Error(
                "Failed to bind Bluetooth streams: ${ioe.message}",
                TransportFailureKind.OPEN_FAILED
            )
            false
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            closeSocketQuietly()
            connectedDevice = null
            _state.value = TransportState.Disconnected
        }
    }

    override fun isConnected(): Boolean {
        val st = _state.value
        return (st is TransportState.Connected || st is TransportState.Timeout) && socket?.isConnected == true
    }

    override suspend fun sendRawBytes(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val out = outputStream
            val activeSocket = socket
            if (activeSocket == null || !activeSocket.isConnected || out == null) {
                _state.value = TransportState.Error(
                    "Bluetooth socket not connected for raw TX",
                    TransportFailureKind.NOT_CONNECTED
                )
                return@withContext false
            }
            return@withContext try {
                val txTs = System.currentTimeMillis()
                out.write(data)
                out.flush()
                val rxTs = System.currentTimeMillis()
                _rxEvidenceFlow.tryEmit(
                    RawTxRxResult(
                        commandTx = data.joinToString(" ") { String.format("%02X", it) },
                        responseRx = "RAW_TX_OK (${data.size} bytes)",
                        latencyMs = (rxTs - txTs).coerceAtLeast(1L),
                        quality = DataQuality.VALID,
                        provenance = DataProvenance.REAL_HARDWARE,
                        timestamp = rxTs,
                        txTimestamp = txTs,
                        rxTimestamp = rxTs,
                        rawTxBytes = data
                    )
                )
                true
            } catch (ioe: IOException) {
                closeSocketQuietly()
                _state.value = TransportState.Error(
                    "Bluetooth raw write failed: ${ioe.message}",
                    TransportFailureKind.TX_ERROR
                )
                false
            }
        }
    }

    override suspend fun readRawBytes(timeoutMs: Long, stopOnPrompt: Boolean): ByteArray = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val input = inputStream ?: return@withContext ByteArray(0)
            readFromStreamLocked(input, timeoutMs, stopOnPrompt).first
        }
    }

    override suspend fun sendAndReceive(command: String, timeoutMs: Long): RawTxRxResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val txTimestamp = System.currentTimeMillis()
            val out = outputStream
            val input = inputStream
            val activeSocket = socket

            if (activeSocket == null || !activeSocket.isConnected || out == null || input == null) {
                val notConnResult = RawTxRxResult(
                    commandTx = command,
                    responseRx = "ERROR: BT_NOT_CONNECTED",
                    latencyMs = 0L,
                    isError = true,
                    quality = DataQuality.UNAVAILABLE,
                    provenance = DataProvenance.REAL_HARDWARE,
                    timestamp = txTimestamp,
                    txTimestamp = txTimestamp,
                    rxTimestamp = txTimestamp,
                    rawTxBytes = command.toByteArray(Charsets.US_ASCII),
                    failureKind = TransportFailureKind.NOT_CONNECTED
                )
                _rxEvidenceFlow.tryEmit(notConnResult)
                return@withContext notConnResult
            }

            try {
                while (input.available() > 0) {
                    input.read()
                }

                val txBytes = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
                out.write(txBytes)
                out.flush()

                val (rxBytes, promptReceived) = readFromStreamLocked(input, timeoutMs, stopOnPrompt = true)
                val rxTimestamp = System.currentTimeMillis()
                val elapsed = (rxTimestamp - txTimestamp).coerceAtLeast(1L)
                val responseStr = String(rxBytes, Charsets.US_ASCII).trim()
                val isTimeout = !promptReceived && responseStr.isEmpty()
                val isErr = responseStr.contains("ERROR", ignoreCase = true) ||
                    responseStr.contains("UNABLE TO CONNECT", ignoreCase = true)

                val failureKind = when {
                    isTimeout -> TransportFailureKind.TIMEOUT
                    isErr -> TransportFailureKind.PROTOCOL_ERROR
                    else -> TransportFailureKind.NONE
                }

                if (isTimeout) {
                    _state.value = TransportState.Timeout(
                        message = "Bluetooth response timeout on $command",
                        commandTx = command,
                        elapsedMs = elapsed,
                        timestamp = rxTimestamp
                    )
                } else if (_state.value is TransportState.Timeout) {
                    _state.value = TransportState.Connected(
                        deviceName = lastDeviceLabel,
                        baudRate = 38400,
                        provenance = DataProvenance.REAL_HARDWARE,
                        isHardware = true
                    )
                }

                val result = RawTxRxResult(
                    commandTx = command,
                    responseRx = if (responseStr.isEmpty()) "TIMEOUT\r\r>" else responseStr,
                    latencyMs = elapsed,
                    isTimeout = isTimeout,
                    isError = isErr,
                    rawBytes = rxBytes,
                    quality = when {
                        isTimeout -> DataQuality.TIMEOUT
                        isErr -> DataQuality.INVALID
                        else -> DataQuality.VALID
                    },
                    provenance = DataProvenance.REAL_HARDWARE,
                    timestamp = rxTimestamp,
                    txTimestamp = txTimestamp,
                    rxTimestamp = rxTimestamp,
                    rawTxBytes = txBytes,
                    failureKind = failureKind
                )

                _rxEvidenceFlow.tryEmit(result)
                return@withContext result
            } catch (ioe: IOException) {
                closeSocketQuietly()
                val rxTs = System.currentTimeMillis()
                _state.value = TransportState.Error(
                    "Bluetooth link lost: ${ioe.message}",
                    TransportFailureKind.RX_ERROR,
                    rxTs
                )
                val elapsed = (rxTs - txTimestamp).coerceAtLeast(1L)
                val errResult = RawTxRxResult(
                    commandTx = command,
                    responseRx = "ERROR: BT_IO_EXCEPTION (${ioe.message})",
                    latencyMs = elapsed,
                    isError = true,
                    quality = DataQuality.UNAVAILABLE,
                    provenance = DataProvenance.REAL_HARDWARE,
                    timestamp = rxTs,
                    txTimestamp = txTimestamp,
                    rxTimestamp = rxTs,
                    failureKind = TransportFailureKind.RX_ERROR
                )
                _rxEvidenceFlow.tryEmit(errResult)
                return@withContext errResult
            }
        }
    }

    private fun readFromStreamLocked(
        input: InputStream,
        timeoutMs: Long,
        stopOnPrompt: Boolean
    ): Pair<ByteArray, Boolean> {
        val collected = ByteArrayOutputStream()
        val readBuffer = ByteArray(512)
        val deadline = System.currentTimeMillis() + timeoutMs
        var promptReceived = false

        while (System.currentTimeMillis() < deadline) {
            val available = input.available()
            if (available > 0) {
                val toRead = available.coerceAtMost(readBuffer.size)
                val bytesRead = input.read(readBuffer, 0, toRead)
                if (bytesRead > 0) {
                    collected.write(readBuffer, 0, bytesRead)
                    if (stopOnPrompt) {
                        val str = collected.toString(Charsets.US_ASCII.name())
                        if (str.contains(">")) {
                            promptReceived = true
                            break
                        }
                    }
                }
            } else {
                Thread.sleep(10)
            }
        }
        return collected.toByteArray() to promptReceived
    }

    private fun closeSocketQuietly() {
        try {
            inputStream?.close()
        } catch (_: IOException) {
        }
        try {
            outputStream?.close()
        } catch (_: IOException) {
        }
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        inputStream = null
        outputStream = null
        socket = null
    }
}
