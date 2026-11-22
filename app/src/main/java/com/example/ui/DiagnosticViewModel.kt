package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ai.GeminiAiDiagnosticService
import com.example.core.ai.IAiDiagnosticService
import com.example.core.discovery.DynamicEcuDiscovery
import com.example.core.discovery.IEcuDiscovery
import com.example.core.elm327.AdapterType
import com.example.core.elm327.Elm327AdapterManager
import com.example.core.elm327.IElm327AdapterManager
import com.example.core.export.IReportExporter
import com.example.core.export.ReportExporter
import com.example.core.model.EvidenceDirection
import com.example.core.model.EvidenceLog
import com.example.core.model.VehicleProfile
import com.example.core.rules.DeterministicDiagnosticRuleEngine
import com.example.core.rules.IDiagnosticRuleEngine
import com.example.core.scanner.DtcScanner
import com.example.core.scanner.IDtcScanner
import com.example.core.scheduler.AdaptivePidScheduler
import com.example.core.scheduler.IPidScheduler
import com.example.data.local.AppDatabase
import com.example.data.local.EvidenceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiagnosticViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val evidenceRepo = EvidenceRepository(db.diagnosticDao())

    val adapterManager: IElm327AdapterManager = Elm327AdapterManager(
        context = application,
        initialProfile = VehicleProfile.THAI_PRESETS[0]
    )

    private val ecuDiscovery: IEcuDiscovery = DynamicEcuDiscovery()
    private val scheduler: IPidScheduler = AdaptivePidScheduler()
    private val dtcScanner: IDtcScanner = DtcScanner()
    private val ruleEngine: IDiagnosticRuleEngine = DeterministicDiagnosticRuleEngine()
    private val aiService: IAiDiagnosticService = GeminiAiDiagnosticService()
    val reportExporter: IReportExporter = ReportExporter()

    private val _uiState = MutableStateFlow(DiagnosticUiState())
    val uiState: StateFlow<DiagnosticUiState> = _uiState.asStateFlow()

    private var telemetrySubscriptionJob: Job? = null

    init {
        observeAdapterManager()
        observeTelemetry()
        observeSavedSessions()
        // Auto-connect in Replay mode on startup for immediate out-of-the-box experience
        connectAdapter(AdapterType.REPLAY_SIMULATOR)
    }

    fun selectTab(tab: DiagnosticTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setAdapterSheetVisible(visible: Boolean) {
        if (visible) {
            refreshAdapters()
        }
        _uiState.update { it.copy(isAdapterSheetVisible = visible) }
    }

    fun refreshAdapters() {
        adapterManager.refreshAvailableAdapters()
        _uiState.update {
            it.copy(hasBluetoothPermission = adapterManager.hasBluetoothPermissions())
        }
    }

    fun onBluetoothPermissionUpdated() {
        refreshAdapters()
    }

    fun requestUsbPermission(deviceId: Int) {
        val alreadyGranted = adapterManager.requestUsbPermission(deviceId)
        if (alreadyGranted) {
            connectAdapter(AdapterType.USB_SERIAL, deviceId.toString())
        }
    }

    fun requiredBluetoothPermissions(): Array<String> {
        return adapterManager.requiredBluetoothPermissions()
    }

    fun setVehicleProfile(profile: VehicleProfile) {
        _uiState.update { it.copy(selectedProfile = profile) }
        adapterManager.setReplayProfile(profile)
        if (adapterManager.transport.isConnected()) {
            viewModelScope.launch {
                discoverEcus()
                scanDtcs()
            }
        }
    }

    fun toggleHardwareMode(useHardware: Boolean) {
        val targetType = if (useHardware) AdapterType.USB_SERIAL else AdapterType.REPLAY_SIMULATOR
        connectAdapter(targetType)
    }

    fun connect() {
        connectAdapter(_uiState.value.adapterType, _uiState.value.activeDevice?.id)
    }

    fun connectAdapter(
        type: AdapterType,
        targetId: String? = null,
        baudRate: Int = 38400
    ) {
        viewModelScope.launch {
            scheduler.stopPolling()
            _uiState.update {
                it.copy(
                    isPolling = false,
                    adapterType = type,
                    isHardwareMode = type != AdapterType.REPLAY_SIMULATOR
                )
            }

            val connected = adapterManager.connect(type, targetId, baudRate)
            if (connected) {
                val driver = adapterManager.driver
                val sessionId = evidenceRepo.createSession(
                    profile = _uiState.value.selectedProfile,
                    adapterName = driver.adapterVersion.value,
                    provenance = driver.getProvenance()
                )
                _uiState.update {
                    it.copy(
                        activeSessionId = sessionId,
                        adapterVersion = driver.adapterVersion.value,
                        activeProtocol = driver.detectedProtocol.value,
                        batteryVoltage = driver.batteryVoltage.value,
                        isAdapterSheetVisible = false,
                        userNoticeMessage = "Connected via ${type.label} (${driver.detectedProtocol.value})"
                    )
                }

                // Discover ECUs, start live telemetry polling, and perform initial DTC scan
                discoverEcus()
                startTelemetryPolling()
                scanDtcs()
            } else {
                _uiState.update {
                    it.copy(
                        userNoticeMessage = "Unable to connect via ${type.label}. Check adapter & permissions."
                    )
                }
            }
        }
    }

    fun disconnect() {
        scheduler.stopPolling()
        viewModelScope.launch {
            adapterManager.disconnect()
            _uiState.update {
                it.copy(
                    isPolling = false,
                    userNoticeMessage = "Disconnected from OBD-II Adapter"
                )
            }
        }
    }

    fun requestStandardPid(pidHex: String) {
        viewModelScope.launch {
            val result = adapterManager.requestStandardPid(pidHex)
            val updatedTelemetry = if (result.telemetryPoint != null) {
                _uiState.value.telemetryMap.toMutableMap().apply {
                    put(result.pidHex, result.telemetryPoint)
                }
            } else {
                _uiState.value.telemetryMap
            }

            _uiState.update {
                it.copy(
                    lastStandardPidResult = result,
                    telemetryMap = updatedTelemetry,
                    userNoticeMessage = if (result.isSuccess && result.telemetryPoint != null) {
                        "PID ${result.pidHex}: ${result.telemetryPoint.displayValue} ${result.telemetryPoint.unit} (${result.elmResponse.latencyMs}ms)"
                    } else {
                        result.errorMessage ?: "PID ${result.pidHex} unavailable"
                    }
                )
            }
        }
    }

    private fun discoverEcus() {
        viewModelScope.launch {
            val ecus = ecuDiscovery.discoverEcus(adapterManager.driver)
            _uiState.update { it.copy(discoveredEcus = ecus) }
        }
    }

    private fun startTelemetryPolling() {
        scheduler.startPolling(adapterManager.driver, viewModelScope)
        _uiState.update { it.copy(isPolling = true) }
    }

    fun toggleTelemetryPolling() {
        if (_uiState.value.isPolling) {
            scheduler.stopPolling()
            _uiState.update { it.copy(isPolling = false) }
        } else {
            scheduler.startPolling(adapterManager.driver, viewModelScope)
            _uiState.update { it.copy(isPolling = true) }
        }
    }

    fun scanDtcs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDtcScanning = true) }
            val result = dtcScanner.performFullScan(adapterManager.driver)
            evidenceRepo.recordDtcList(
                _uiState.value.activeSessionId,
                result.confirmedCodes + result.pendingCodes + result.permanentCodes
            )

            val rules = ruleEngine.evaluateRules(
                telemetry = _uiState.value.telemetryMap,
                dtcResult = result,
                batteryVoltageStr = adapterManager.batteryVoltage.value
            )

            _uiState.update {
                it.copy(
                    dtcScanResult = result,
                    isDtcScanning = false,
                    ruleFindings = rules,
                    userNoticeMessage = if (result.totalFaults > 0) "พบรหัสปัญหา ${result.totalFaults} รายการ" else "ไม่พบรหัสข้อผิดพลาด (0 Faults)"
                )
            }

            generateAiReport()
        }
    }

    fun requestClearTroubleCodes() {
        _uiState.update { it.copy(isClearConfirmDialogVisible = true) }
    }

    fun dismissClearConfirmDialog() {
        _uiState.update { it.copy(isClearConfirmDialogVisible = false) }
    }

    fun dismissClearResult() {
        _uiState.update { it.copy(lastClearResult = null) }
    }

    fun clearTroubleCodes() {
        requestClearTroubleCodes()
    }

    fun executeClearTroubleCodes() {
        _uiState.update {
            it.copy(
                isClearConfirmDialogVisible = false,
                isDtcClearing = true
            )
        }
        viewModelScope.launch {
            val clearResult = dtcScanner.clearCodes(adapterManager.driver)

            val ev = EvidenceLog(
                id = clearResult.evidenceRef,
                sessionId = _uiState.value.activeSessionId,
                timestamp = clearResult.timestamp,
                direction = EvidenceDirection.TX,
                rawBytesHex = clearResult.rawHex,
                commandText = clearResult.commandTx,
                responseText = clearResult.responseRx,
                ecuHeader = clearResult.ecuHeader,
                latencyMs = clearResult.latencyMs,
                quality = clearResult.quality,
                provenance = clearResult.provenance,
                parsedMeaning = if (clearResult.isSuccess) {
                    "Service 04 Positive Response (44): DTC Memory Cleared, MIL Reset"
                } else {
                    "Service 04 Failed: ${clearResult.errorMessageEnglish ?: clearResult.cleanPayload}"
                },
                sha256Hash = clearResult.sha256Hash
            )

            evidenceRepo.recordClearDtcEvidence(
                sessionId = _uiState.value.activeSessionId,
                evidenceId = clearResult.evidenceRef,
                commandTx = clearResult.commandTx,
                responseRx = clearResult.responseRx,
                latencyMs = clearResult.latencyMs,
                provenance = clearResult.provenance,
                quality = clearResult.quality,
                ecuHeader = clearResult.ecuHeader,
                timestamp = clearResult.timestamp
            )

            if (clearResult.isSuccess) {
                evidenceRepo.clearDtcsForSession(_uiState.value.activeSessionId)
            }

            val updatedLogs = (listOf(ev) + _uiState.value.evidenceLogs).take(200)

            val rules = ruleEngine.evaluateRules(
                telemetry = _uiState.value.telemetryMap,
                dtcResult = dtcScanner.scanResult.value,
                batteryVoltageStr = adapterManager.batteryVoltage.value
            )

            _uiState.update {
                it.copy(
                    dtcScanResult = dtcScanner.scanResult.value,
                    isDtcClearing = false,
                    lastClearResult = clearResult,
                    evidenceLogs = updatedLogs,
                    ruleFindings = rules,
                    userNoticeMessage = if (clearResult.isSuccess) {
                        clearResult.confirmationMessageThai
                    } else {
                        clearResult.errorMessageThai ?: "ไม่สามารถล้างรหัสได้ (ECU ปฏิเสธคำสั่ง)"
                    }
                )
            }

            if (clearResult.isSuccess) {
                generateAiReport()
            }
        }
    }

    fun generateAiReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiGenerating = true) }
            val report = aiService.generateAiReport(
                profile = _uiState.value.selectedProfile,
                telemetry = _uiState.value.telemetryMap,
                dtcResult = _uiState.value.dtcScanResult,
                ecus = _uiState.value.discoveredEcus,
                ruleFindings = _uiState.value.ruleFindings,
                batteryVoltage = _uiState.value.batteryVoltage
            )
            _uiState.update {
                it.copy(
                    aiReport = report,
                    isAiGenerating = false
                )
            }
        }
    }

    fun sendCustomTerminalCommand(cmd: String) {
        val clean = cmd.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val driver = adapterManager.driver
            val resp = if (clean.startsWith("AT", ignoreCase = true)) {
                driver.sendAtCommand(clean)
            } else {
                val service = clean.take(2)
                val pid = clean.drop(2).trim()
                driver.queryPid(service, pid)
            }

            val newEv = EvidenceLog(
                id = "EVD_USER_${System.currentTimeMillis()}",
                sessionId = _uiState.value.activeSessionId,
                timestamp = System.currentTimeMillis(),
                direction = EvidenceDirection.TX,
                rawBytesHex = resp.rawResponse,
                commandText = clean,
                responseText = resp.rawResponse,
                ecuHeader = resp.ecuHeader.ifBlank { "7E8" },
                latencyMs = resp.latencyMs,
                quality = resp.quality,
                provenance = driver.getProvenance(),
                parsedMeaning = resp.cleanPayload
            )

            val updatedList = listOf(newEv) + _uiState.value.evidenceLogs
            _uiState.update {
                it.copy(
                    evidenceLogs = updatedList.take(200),
                    userTerminalInput = ""
                )
            }
        }
    }

    fun setTerminalInput(input: String) {
        _uiState.update { it.copy(userTerminalInput = input) }
    }

    fun showEvidenceDetail(evidence: EvidenceLog) {
        _uiState.update {
            it.copy(
                selectedEvidence = evidence,
                isEvidenceDialogVisible = true
            )
        }
    }

    fun dismissEvidenceDialog() {
        _uiState.update {
            it.copy(
                selectedEvidence = null,
                isEvidenceDialogVisible = false
            )
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(userNoticeMessage = null) }
    }

    private fun observeAdapterManager() {
        viewModelScope.launch {
            adapterManager.transportState.collectLatest { state ->
                _uiState.update { it.copy(transportState = state) }
            }
        }
        viewModelScope.launch {
            adapterManager.availableAdapters.collectLatest { adapters ->
                _uiState.update {
                    it.copy(
                        availableAdapters = adapters,
                        hasBluetoothPermission = adapterManager.hasBluetoothPermissions()
                    )
                }
            }
        }
        viewModelScope.launch {
            adapterManager.activeDevice.collectLatest { dev ->
                _uiState.update { it.copy(activeDevice = dev) }
            }
        }
        viewModelScope.launch {
            adapterManager.supportedPids.collectLatest { pids ->
                _uiState.update { it.copy(supportedPids = pids) }
            }
        }
        viewModelScope.launch {
            adapterManager.adapterVersion.collectLatest { ver ->
                _uiState.update { it.copy(adapterVersion = ver) }
            }
        }
        viewModelScope.launch {
            adapterManager.detectedProtocol.collectLatest { proto ->
                _uiState.update { it.copy(activeProtocol = proto) }
            }
        }
        viewModelScope.launch {
            adapterManager.rxEvidenceFlow.collect { rx ->
                val ev = EvidenceLog(
                    id = "EVD_${System.currentTimeMillis()}_${(100..999).random()}",
                    sessionId = _uiState.value.activeSessionId,
                    timestamp = rx.timestamp,
                    direction = if (rx.commandTx.isNotBlank()) EvidenceDirection.TX else EvidenceDirection.RX,
                    rawBytesHex = rx.responseRx,
                    commandText = rx.commandTx,
                    responseText = rx.responseRx,
                    latencyMs = rx.latencyMs,
                    quality = rx.quality,
                    provenance = rx.provenance
                )
                val currentLogs = listOf(ev) + _uiState.value.evidenceLogs
                _uiState.update { it.copy(evidenceLogs = currentLogs.take(200)) }
                if (_uiState.value.activeSessionId.isNotBlank()) {
                    evidenceRepo.recordTxRxEvidence(_uiState.value.activeSessionId, rx)
                }
            }
        }
    }

    private fun observeTelemetry() {
        telemetrySubscriptionJob?.cancel()
        telemetrySubscriptionJob = viewModelScope.launch {
            scheduler.telemetryMap.collectLatest { telemetry ->
                val volt = adapterManager.batteryVoltage.value
                val rules = ruleEngine.evaluateRules(
                    telemetry = telemetry,
                    dtcResult = _uiState.value.dtcScanResult,
                    batteryVoltageStr = volt
                )

                _uiState.update {
                    it.copy(
                        telemetryMap = telemetry,
                        ruleFindings = rules,
                        batteryVoltage = volt,
                        averageLatencyMs = scheduler.averageLatencyMs.value
                    )
                }
            }
        }
    }

    private fun observeSavedSessions() {
        viewModelScope.launch {
            evidenceRepo.allSessions.collectLatest { sessions ->
                _uiState.update { it.copy(savedSessions = sessions) }
            }
        }
    }

    override fun onCleared() {
        scheduler.stopPolling()
        adapterManager.dispose()
        super.onCleared()
    }
}
