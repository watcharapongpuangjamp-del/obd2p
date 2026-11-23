package com.example

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.DtcScanResult
import com.example.core.model.FindingSeverity
import com.example.core.model.TelemetryPoint
import com.example.core.rules.DeterministicDiagnosticRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicDiagnosticRuleEngineTest {

    private fun ectPoint(
        quality: DataQuality,
        provenance: DataProvenance
    ) = TelemetryPoint(
        id = "PID_05",
        name = "Engine Coolant Temperature",
        thaiName = "อุณหภูมิน้ำหล่อเย็น",
        value = 115.0,
        displayValue = "115",
        unit = "°C",
        quality = quality,
        provenance = provenance,
        sourcePid = "05",
        evidenceRefId = "EVD_ECT_TEST"
    )

    @Test
    fun staleTelemetryCannotTriggerCriticalFinding() {
        val findings = DeterministicDiagnosticRuleEngine().evaluateRules(
            telemetry = mapOf("05" to ectPoint(DataQuality.STALE, DataProvenance.REAL_HARDWARE)),
            dtcResult = DtcScanResult(),
            batteryVoltageStr = "0.0V"
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun unknownOrAiInferredTelemetryCannotTriggerCriticalFinding() {
        val engine = DeterministicDiagnosticRuleEngine()
        val unknown = engine.evaluateRules(
            mapOf("05" to ectPoint(DataQuality.VALID, DataProvenance.UNKNOWN)),
            DtcScanResult(),
            "0.0V"
        )
        val aiInferred = engine.evaluateRules(
            mapOf("05" to ectPoint(DataQuality.VALID, DataProvenance.AI_INFERRED)),
            DtcScanResult(),
            "0.0V"
        )

        assertTrue(unknown.isEmpty())
        assertTrue(aiInferred.isEmpty())
    }

    @Test
    fun validSimulatorTelemetryCanBeEvaluatedAndRemainsExplicitlySimulated() {
        val findings = DeterministicDiagnosticRuleEngine().evaluateRules(
            telemetry = mapOf("05" to ectPoint(DataQuality.VALID, DataProvenance.SIMULATOR)),
            dtcResult = DtcScanResult(),
            batteryVoltageStr = "0.0V"
        )

        assertEquals(1, findings.size)
        assertEquals(FindingSeverity.CRITICAL, findings.single().severity)
        assertEquals("EVD_ECT_TEST", findings.single().evidenceRefs.single())
    }
}
