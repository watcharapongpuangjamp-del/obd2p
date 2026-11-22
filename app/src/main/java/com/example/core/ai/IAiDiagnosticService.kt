package com.example.core.ai

import com.example.core.model.AiDiagnosticReport
import com.example.core.model.DeterministicFinding
import com.example.core.model.DtcScanResult
import com.example.core.model.EcuInfo
import com.example.core.model.TelemetryPoint
import com.example.core.model.VehicleProfile

interface IAiDiagnosticService {
    suspend fun generateAiReport(
        profile: VehicleProfile,
        telemetry: Map<String, TelemetryPoint>,
        dtcResult: DtcScanResult,
        ecus: List<EcuInfo>,
        ruleFindings: List<DeterministicFinding>,
        batteryVoltage: String
    ): AiDiagnosticReport
}
