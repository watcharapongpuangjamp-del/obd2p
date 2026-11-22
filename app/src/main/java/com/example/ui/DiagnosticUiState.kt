package com.example.ui

import com.example.core.elm327.AdapterDeviceInfo
import com.example.core.elm327.AdapterType
import com.example.core.elm327.StandardPidResult
import com.example.core.model.AiDiagnosticReport
import com.example.core.model.DataProvenance
import com.example.core.model.DeterministicFinding
import com.example.core.model.DtcClearResult
import com.example.core.model.DtcScanResult
import com.example.core.model.EcuInfo
import com.example.core.model.EvidenceLog
import com.example.core.model.TelemetryPoint
import com.example.core.model.VehicleProfile
import com.example.core.transport.TransportState
import com.example.data.local.DiagnosticSessionEntity

enum class DiagnosticTab {
    DASHBOARD,
    DTC_SCAN,
    AI_MECHANIC,
    EVIDENCE_LOG,
    ECU_DISCOVERY,
    HISTORY_EXPORT
}

data class DiagnosticUiState(
    val currentTab: DiagnosticTab = DiagnosticTab.DASHBOARD,
    val selectedProfile: VehicleProfile = VehicleProfile.THAI_PRESETS[0],
    val adapterType: AdapterType = AdapterType.REPLAY_SIMULATOR,
    val transportState: TransportState = TransportState.Disconnected,
    val availableAdapters: List<AdapterDeviceInfo> = emptyList(),
    val activeDevice: AdapterDeviceInfo? = null,
    val supportedPids: Set<String> = emptySet(),
    val hasBluetoothPermission: Boolean = false,
    val isAdapterSheetVisible: Boolean = false,
    val isHardwareMode: Boolean = false,
    val adapterVersion: String = "ELM327 v1.5",
    val activeProtocol: String = "ISO 15765-4 (CAN 11/500)",
    val batteryVoltage: String = "14.2V",
    val isPolling: Boolean = false,
    val averageLatencyMs: Long = 32L,
    val telemetryMap: Map<String, TelemetryPoint> = emptyMap(),
    val lastStandardPidResult: StandardPidResult? = null,
    val dtcScanResult: DtcScanResult = DtcScanResult(),
    val isDtcScanning: Boolean = false,
    val isDtcClearing: Boolean = false,
    val lastClearResult: DtcClearResult? = null,
    val isClearConfirmDialogVisible: Boolean = false,
    val ruleFindings: List<DeterministicFinding> = emptyList(),
    val aiReport: AiDiagnosticReport? = null,
    val isAiGenerating: Boolean = false,
    val evidenceLogs: List<EvidenceLog> = emptyList(),
    val selectedEvidence: EvidenceLog? = null,
    val isEvidenceDialogVisible: Boolean = false,
    val discoveredEcus: List<EcuInfo> = emptyList(),
    val savedSessions: List<DiagnosticSessionEntity> = emptyList(),
    val userTerminalInput: String = "",
    val activeSessionId: String = "",
    val userNoticeMessage: String? = null
)
