package com.example.core.elm327

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.core.model.DataQuality
import com.example.core.model.TelemetryPoint
import com.example.core.model.VehicleProfile
import com.example.core.protocol.PidDecoder
import com.example.core.transport.BluetoothElm327Transport
import com.example.core.transport.ITransport
import com.example.core.transport.RawTxRxResult
import com.example.core.transport.ReplayVehicleTransport
import com.example.core.transport.TransportState
import com.example.core.transport.UsbSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AdapterType(val label: String) {
    BLUETOOTH_CLASSIC("Bluetooth ELM327"),
    USB_SERIAL("USB-Serial OTG"),
    REPLAY_SIMULATOR("Vehicle Replay")
}

data class AdapterDeviceInfo(
    val id: String,
    val name: String,
    val addressOrPort: String,
    val type: AdapterType,
    val hasPermission: Boolean,
    val detail: String,
    val isLikelyObdAdapter: Boolean = true
)

data class StandardPidResult(
    val pidHex: String,
    val commandTx: String,
    val isSuccess: Boolean,
    val telemetryPoint: TelemetryPoint?,
    val elmResponse: ElmResponse,
    val rawTxRx: RawTxRxResult? = elmResponse.rawTxRx,
    val errorMessage: String? = elmResponse.errorMessage
)

interface IElm327AdapterManager {
    val adapterType: StateFlow<AdapterType>
    val transportState: StateFlow<TransportState>
    val availableAdapters: StateFlow<List<AdapterDeviceInfo>>
    val activeDevice: StateFlow<AdapterDeviceInfo?>
    val adapterVersion: StateFlow<String>
    val detectedProtocol: StateFlow<String>
    val batteryVoltage: StateFlow<String>
    val supportedPids: StateFlow<Set<String>>
    val rxEvidenceFlow: SharedFlow<RawTxRxResult>
    val driver: IElm327Driver
    val transport: ITransport

    fun refreshAvailableAdapters()
    fun hasBluetoothPermissions(): Boolean
    fun requiredBluetoothPermissions(): Array<String>
    fun requestUsbPermission(deviceId: Int): Boolean
    suspend fun connect(type: AdapterType, targetId: String? = null, baudRate: Int = 38400): Boolean
    suspend fun disconnect()
    suspend fun requestStandardPid(pidHex: String): StandardPidResult
    suspend fun requestStandardPids(pidHexList: List<String>): Map<String, StandardPidResult>
    suspend fun querySupportedPids(): Set<String>
    suspend fun sendAtCommand(command: String): ElmResponse
    suspend fun readBatteryVoltage(): ElmResponse
    fun setReplayProfile(profile: VehicleProfile)
    fun dispose()
}

/**
 * Central manager for initiating and managing Bluetooth Classic SPP, USB-Serial OTG,
 * and Replay transport connections to an ELM327 OBD-II adapter, and handling
 * validated standard SAE J1979 Service 01 PID requests.
 */
class Elm327AdapterManager(
    private val context: Context?,
    initialProfile: VehicleProfile = VehicleProfile.THAI_PRESETS[0],
    registerSystemReceivers: Boolean = true,
    private val customTransportOverride: ITransport? = null
) : IElm327AdapterManager {

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.ELM327_USB_PERMISSION"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val replayTransport = ReplayVehicleTransport(initialProfile)
    private val bluetoothTransport = context?.let { BluetoothElm327Transport(it) }
    private val usbTransport = context?.let { UsbSerialTransport(it) }

    private var _activeTransport: ITransport = customTransportOverride ?: replayTransport
    override val transport: ITransport
        get() = _activeTransport

    private var _driver: IElm327Driver = Elm327Driver(_activeTransport)
    override val driver: IElm327Driver
        get() = _driver

    private val _adapterType = MutableStateFlow(AdapterType.REPLAY_SIMULATOR)
    override val adapterType: StateFlow<AdapterType> = _adapterType.asStateFlow()

    private val _transportState = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val transportState: StateFlow<TransportState> = _transportState.asStateFlow()

    private val _availableAdapters = MutableStateFlow<List<AdapterDeviceInfo>>(emptyList())
    override val availableAdapters: StateFlow<List<AdapterDeviceInfo>> = _availableAdapters.asStateFlow()

    private val _activeDevice = MutableStateFlow<AdapterDeviceInfo?>(null)
    override val activeDevice: StateFlow<AdapterDeviceInfo?> = _activeDevice.asStateFlow()

    private val _adapterVersion = MutableStateFlow("Unknown")
    override val adapterVersion: StateFlow<String> = _adapterVersion.asStateFlow()

    private val _detectedProtocol = MutableStateFlow("Auto")
    override val detectedProtocol: StateFlow<String> = _detectedProtocol.asStateFlow()

    private val _batteryVoltage = MutableStateFlow("0.0V")
    override val batteryVoltage: StateFlow<String> = _batteryVoltage.asStateFlow()

    private val _supportedPids = MutableStateFlow<Set<String>>(emptySet())
    override val supportedPids: StateFlow<Set<String>> = _supportedPids.asStateFlow()

    private val _rxEvidenceFlow = MutableSharedFlow<RawTxRxResult>(extraBufferCapacity = 128)
    override val rxEvidenceFlow: SharedFlow<RawTxRxResult> = _rxEvidenceFlow.asSharedFlow()

    private var stateForwardJob: Job? = null
    private var rxForwardJob: Job? = null
    private var driverForwardJob: Job? = null
    private var receiverRegistered = false

    private val hardwareEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    refreshAvailableAdapters()
                    if (granted && device != null && _adapterType.value == AdapterType.USB_SERIAL) {
                        scope.launch {
                            connect(AdapterType.USB_SERIAL, device.deviceId.toString())
                        }
                    } else if (!granted && device != null) {
                        _transportState.value = TransportState.Error(
                            "USB permission denied for ${device.productName ?: device.deviceName}"
                        )
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refreshAvailableAdapters()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    refreshAvailableAdapters()
                    if (_adapterType.value == AdapterType.USB_SERIAL) {
                        scope.launch { disconnect() }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (_adapterType.value == AdapterType.BLUETOOTH_CLASSIC) {
                        scope.launch { disconnect() }
                    }
                }
            }
        }
    }

    init {
        if (registerSystemReceivers) {
            registerHardwareReceivers()
        }
        bindTransportObservers(_activeTransport, _driver)
        refreshAvailableAdapters()
    }

    private fun registerHardwareReceivers() {
        val ctx = context ?: return
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            ContextCompat.registerReceiver(
                ctx,
                hardwareEventReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (_: Exception) {
            receiverRegistered = false
        }
    }

    private fun bindTransportObservers(targetTransport: ITransport, targetDriver: IElm327Driver) {
        stateForwardJob?.cancel()
        rxForwardJob?.cancel()
        driverForwardJob?.cancel()

        stateForwardJob = scope.launch {
            targetTransport.state.collectLatest { st ->
                _transportState.value = st
            }
        }
        rxForwardJob = scope.launch {
            targetTransport.rxEvidenceFlow.collect { rx ->
                _rxEvidenceFlow.tryEmit(rx)
            }
        }
        driverForwardJob = scope.launch {
            launch { targetDriver.adapterVersion.collectLatest { _adapterVersion.value = it } }
            launch { targetDriver.detectedProtocol.collectLatest { _detectedProtocol.value = it } }
            launch { targetDriver.batteryVoltage.collectLatest { _batteryVoltage.value = it } }
        }
    }

    override fun hasBluetoothPermissions(): Boolean {
        val bt = bluetoothTransport ?: return false
        return bt.hasConnectPermission() && bt.hasScanPermission()
    }

    override fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    override fun requestUsbPermission(deviceId: Int): Boolean {
        val ctx = context ?: return false
        val usbManager = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val device = usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId } ?: return false
        if (usbManager.hasPermission(device)) return true

        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(ctx.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(ctx, deviceId, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
        return false
    }

    @SuppressLint("MissingPermission")
    override fun refreshAvailableAdapters() {
        val list = mutableListOf<AdapterDeviceInfo>()

        // 1. Paired Bluetooth ELM327 Adapters
        val btManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter: BluetoothAdapter? = btManager?.adapter
        val btTransport = bluetoothTransport
        if (btAdapter != null && btTransport != null && btTransport.hasConnectPermission()) {
            val bonded = btTransport.getBondedDevices()
            for (device in bonded) {
                val name = try {
                    device.name ?: "Bluetooth Device"
                } catch (_: SecurityException) {
                    "Bluetooth Device"
                }
                val isObd = BluetoothElm327Transport.isLikelyObdDeviceName(name)
                list.add(
                    AdapterDeviceInfo(
                        id = device.address,
                        name = name,
                        addressOrPort = device.address,
                        type = AdapterType.BLUETOOTH_CLASSIC,
                        hasPermission = true,
                        detail = if (isObd) "SPP OBD-II Adapter (Paired)" else "Bluetooth Paired Device",
                        isLikelyObdAdapter = isObd
                    )
                )
            }
        }

        // 2. Attached USB-Serial OTG Devices
        val usbTr = usbTransport
        val usbDevices = usbTr?.getAvailableDevices().orEmpty()
        for (device in usbDevices) {
            val isObd = usbTr?.isObdAdapter(device) == true
            val hasPerm = usbTr?.hasPermission(device) == true
            val chipName = UsbSerialTransport.identifyChipset(device.vendorId)
            list.add(
                AdapterDeviceInfo(
                    id = device.deviceId.toString(),
                    name = device.productName ?: chipName,
                    addressOrPort = "USB#${device.deviceId} (${device.deviceName})",
                    type = AdapterType.USB_SERIAL,
                    hasPermission = hasPerm,
                    detail = "$chipName | ${if (hasPerm) "Permission Granted" else "Needs USB Permission"}",
                    isLikelyObdAdapter = isObd
                )
            )
        }

        // 3. Always include Replay Simulator fallback option
        val profile = replayTransport.getCurrentProfile()
        list.add(
            AdapterDeviceInfo(
                id = "REPLAY_${profile.id}",
                name = "Replay Fixture (${profile.make} ${profile.model})",
                addressOrPort = "SIMULATOR",
                type = AdapterType.REPLAY_SIMULATOR,
                hasPermission = true,
                detail = "Deterministic ELM327 v1.5 Replay Stream",
                isLikelyObdAdapter = true
            )
        )

        // Sort likely OBD hardware adapters first
        _availableAdapters.value = list.sortedWith(
            compareByDescending<AdapterDeviceInfo> { it.type != AdapterType.REPLAY_SIMULATOR }
                .thenByDescending { it.isLikelyObdAdapter }
        )
    }

    override suspend fun connect(type: AdapterType, targetId: String?, baudRate: Int): Boolean {
        disconnect()
        _adapterType.value = type
        refreshAvailableAdapters()

        val selectedTransport: ITransport = when {
            customTransportOverride != null -> customTransportOverride
            type == AdapterType.BLUETOOTH_CLASSIC -> {
                val btTr = bluetoothTransport ?: return false
                btTr.setTargetDeviceAddress(targetId)
                btTr
            }
            type == AdapterType.USB_SERIAL -> {
                val usbTr = usbTransport ?: return false
                val parsedId = targetId?.toIntOrNull()
                if (parsedId != null) {
                    val usbDevice = usbTr.getAvailableDevices().firstOrNull { it.deviceId == parsedId }
                    if (usbDevice != null && !usbTr.hasPermission(usbDevice)) {
                        requestUsbPermission(parsedId)
                        _transportState.value = TransportState.Error(
                            "Requesting USB permission for ${usbDevice.productName ?: usbDevice.deviceName}..."
                        )
                        return false
                    }
                }
                usbTr.setTargetDevice(parsedId, baudRate)
                usbTr
            }
            else -> replayTransport
        }

        _activeTransport = selectedTransport
        _driver = Elm327Driver(selectedTransport)
        bindTransportObservers(selectedTransport, _driver)

        val initialized = _driver.initializeAdapter()
        if (!initialized) {
            return false
        }

        _adapterVersion.value = _driver.adapterVersion.value
        _detectedProtocol.value = _driver.detectedProtocol.value
        _batteryVoltage.value = _driver.batteryVoltage.value

        val matchingDevice = _availableAdapters.value.firstOrNull {
            it.type == type && (targetId == null || it.id == targetId)
        }
        _activeDevice.value = matchingDevice

        // Query ECU supported PIDs via Service 01 PIDs 00, 20, 40
        querySupportedPids()
        return true
    }

    override suspend fun disconnect() {
        try {
            _activeTransport.disconnect()
        } catch (_: Exception) {
        }
        _activeDevice.value = null
        _supportedPids.value = emptySet()
    }

    override suspend fun requestStandardPid(pidHex: String): StandardPidResult {
        val normalizedPid = pidHex.trim().uppercase().padStart(2, '0')
        val cmd = "01$normalizedPid"
        val elmResp = _driver.queryPid("01", normalizedPid)
        val telemetry = PidDecoder.decodeTelemetryPoint(normalizedPid, elmResp)
        val valid = elmResp.isSuccess && telemetry != null && telemetry.quality == DataQuality.VALID

        return StandardPidResult(
            pidHex = normalizedPid,
            commandTx = cmd,
            isSuccess = valid,
            telemetryPoint = telemetry,
            elmResponse = elmResp,
            rawTxRx = elmResp.rawTxRx,
            errorMessage = if (valid) null else (elmResp.errorMessage ?: "PID $normalizedPid unavailable or invalid")
        )
    }

    override suspend fun requestStandardPids(pidHexList: List<String>): Map<String, StandardPidResult> {
        val results = linkedMapOf<String, StandardPidResult>()
        for (pid in pidHexList) {
            val normalized = pid.trim().uppercase().padStart(2, '0')
            results[normalized] = requestStandardPid(normalized)
        }
        return results
    }

    override suspend fun querySupportedPids(): Set<String> {
        val discovered = linkedSetOf<String>()

        // 0100: PIDs 01..20
        val resp00 = _driver.queryPid("01", "00")
        val pids01To20 = PidDecoder.decodeSupportedPidsBitmask(resp00, 0x01)
        discovered.addAll(pids01To20)

        // Query 0120 only if PID 20 is advertised or as fallback check
        if (pids01To20.contains("20")) {
            val resp20 = _driver.queryPid("01", "20")
            val pids21To40 = PidDecoder.decodeSupportedPidsBitmask(resp20, 0x21)
            discovered.addAll(pids21To40)

            // Query 0140 only if PID 40 is advertised
            if (pids21To40.contains("40")) {
                val resp40 = _driver.queryPid("01", "40")
                val pids41To60 = PidDecoder.decodeSupportedPidsBitmask(resp40, 0x41)
                discovered.addAll(pids41To60)
            }
        }

        _supportedPids.value = discovered
        return discovered
    }

    override suspend fun sendAtCommand(command: String): ElmResponse {
        return _driver.sendAtCommand(command)
    }

    override suspend fun readBatteryVoltage(): ElmResponse {
        return _driver.readBatteryVoltage()
    }

    override fun setReplayProfile(profile: VehicleProfile) {
        replayTransport.setVehicleProfile(profile)
    }

    override fun dispose() {
        if (receiverRegistered) {
            try {
                context?.unregisterReceiver(hardwareEventReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
        stateForwardJob?.cancel()
        rxForwardJob?.cancel()
        driverForwardJob?.cancel()
        scope.cancel()
    }
}
