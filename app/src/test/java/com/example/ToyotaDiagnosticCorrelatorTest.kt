package com.example

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.DtcCode
import com.example.core.model.DtcSeverity
import com.example.core.model.DtcType
import com.example.core.model.TelemetryPoint
import com.example.core.toyota.ToyotaApplicabilityStatus
import com.example.core.toyota.ToyotaDiagnosticCorrelator
import com.example.core.toyota.ToyotaEvidenceCoverage
import com.example.core.toyota.ToyotaManualEvidence
import com.example.core.toyota.ToyotaVehicleApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToyotaDiagnosticCorrelatorTest {
    private val now = 1_800_000_000_000L
    private val vehicle = ToyotaVehicleApplication("GUN126", "1GD-FTV", 2022, "Thailand")

    private fun dtc(code: String) = DtcCode(
        code = code, type = DtcType.CONFIRMED, thaiTitle = code,
        thaiExplanation = "", englishDescription = code, severity = DtcSeverity.HIGH
    )

    private fun point(pid: String, quality: DataQuality = DataQuality.VALID,
                      provenance: DataProvenance = DataProvenance.REAL_HARDWARE,
                      timestamp: Long = now) = TelemetryPoint(
        id = "PID_$pid", name = pid, thaiName = pid, value = 1.0,
        displayValue = "1", unit = "", timestamp = timestamp,
        quality = quality, provenance = provenance, sourcePid = pid,
        evidenceRefId = "EVD_$pid"
    )

    private fun manual(
        modelCode: String = "GUN126", engine: String = "1GD-FTV",
        start: Int? = 2021, end: Int? = 2023, region: String = "Thailand"
    ) = ToyotaManualEvidence(
        manualId = "N00107", documentSha256 = "a".repeat(64),
        modelCode = modelCode, engineCode = engine,
        productionStartYear = start, productionEndYear = end, region = region,
        kind = "dtc_candidate", value = "P0087", locator = "pdf:page:12"
    )

    @Test fun validFuelTrimTelemetryRaisesOnlyToMediumCorrelation() {
        val result = ToyotaDiagnosticCorrelator(clock = { now }).correlate(
            listOf(dtc("P0171")), mapOf("06" to point("06"), "07" to point("07")), vehicle
        ).single()
        assertEquals(listOf("06", "07"), result.relatedPids)
        assertEquals(listOf("EVD_06", "EVD_07"), result.telemetryEvidenceRefs)
        assertEquals(ToyotaEvidenceCoverage.DTC_PLUS_TELEMETRY, result.evidenceCoverage)
    }

    @Test fun staleTelemetryIsNotUsed() {
        val result = ToyotaDiagnosticCorrelator(clock = { now }).correlate(
            listOf(dtc("P0171")), mapOf("06" to point("06", timestamp = now - 20_000)), vehicle
        ).single()
        assertTrue(result.telemetryEvidenceRefs.isEmpty())
        assertEquals(ToyotaEvidenceCoverage.DTC_ONLY, result.evidenceCoverage)
    }

    @Test fun estimatedTelemetryIsNotUsed() {
        val result = ToyotaDiagnosticCorrelator(clock = { now }).correlate(
            listOf(dtc("P0171")), mapOf("06" to point("06", provenance = DataProvenance.AI_INFERRED)), vehicle
        ).single()
        assertTrue(result.telemetryEvidenceRefs.isEmpty())
    }

    @Test fun p0087DoesNotPretendStandardRailPressurePidIsAvailable() {
        val result = ToyotaDiagnosticCorrelator(clock = { now }).correlate(
            listOf(dtc("P0087")), mapOf("23" to point("23")), vehicle
        ).single()
        assertTrue(result.relatedPids.isEmpty())
        assertTrue(result.explanationThai.contains("ยังไม่มี PID มาตรฐาน"))
    }

    @Test fun exactManualApplicationIsCandidateMatchNotVerified() {
        val result = ToyotaDiagnosticCorrelator(clock = { now }).correlate(
            listOf(dtc("P0087")), emptyMap(), vehicle, listOf(manual())
        ).single()
        assertEquals(ToyotaApplicabilityStatus.CANDIDATE_MATCH, result.manualEvidence.single().applicability)
        assertEquals("candidate_unverified", result.manualEvidence.single().evidence.extractionStatus)
    }

    @Test fun mismatchedEngineManualEvidenceIsExcluded() {
        val result = ToyotaDiagnosticCorrelator(clock = { now }).correlate(
            listOf(dtc("P0087")), emptyMap(), vehicle, listOf(manual(engine = "2GD-FTV"))
        ).single()
        assertTrue(result.manualEvidence.isEmpty())
    }

    @Test fun invalidManualFingerprintIsRejectedFromEvidenceResults() {
        val invalid = manual().copy(documentSha256 = "not-a-hash")
        val result = ToyotaDiagnosticCorrelator(clock = { now }).correlate(
            listOf(dtc("P0087")), emptyMap(), vehicle, listOf(invalid)
        ).single()
        assertTrue(result.manualEvidence.isEmpty())
    }

    @Test fun missingManualApplicationFieldsRemainUnconfirmed() {
        val result = ToyotaDiagnosticCorrelator(clock = { now }).correlate(
            listOf(dtc("P0087")), emptyMap(), vehicle,
            listOf(manual(modelCode = "", engine = "", start = null, region = ""))
        ).single()
        assertEquals(ToyotaApplicabilityStatus.UNCONFIRMED, result.manualEvidence.single().applicability)
    }
}
