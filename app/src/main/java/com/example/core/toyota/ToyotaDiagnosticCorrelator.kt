package com.example.core.toyota

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.DtcCode
import com.example.core.model.TelemetryPoint
import java.util.Locale

enum class ToyotaApplicabilityStatus { CANDIDATE_MATCH, UNCONFIRMED, NOT_APPLICABLE }
enum class ToyotaEvidenceCoverage { DTC_ONLY, DTC_PLUS_TELEMETRY }

data class ToyotaVehicleApplication(
    val modelCode: String, val engineCode: String, val productionYear: Int, val region: String = ""
)

data class ToyotaManualEvidence(
    val manualId: String, val documentSha256: String,
    val modelCode: String = "", val engineCode: String = "",
    val productionStartYear: Int? = null, val productionEndYear: Int? = null,
    val region: String = "", val kind: String, val value: String,
    val locator: String, val section: String = "", val excerpt: String = "",
    val extractionStatus: String = "candidate_unverified"
)

data class ToyotaManualEvidenceMatch(
    val evidence: ToyotaManualEvidence, val applicability: ToyotaApplicabilityStatus,
    val unknownFields: List<String>, val mismatchFields: List<String>
)

data class ToyotaDiagnosticCorrelation(
    val dtcCode: String, val dtcType: String,
    val evidenceCoverage: ToyotaEvidenceCoverage, val relatedPids: List<String>,
    val telemetryEvidenceRefs: List<String>, val manualEvidence: List<ToyotaManualEvidenceMatch>,
    val explanationThai: String, val limitations: List<String>
)

/**
 * Correlates DTCs with fresh validated generic OBD telemetry and local Toyota
 * manual evidence. It never diagnoses root cause or sends vehicle commands.
 */
class ToyotaDiagnosticCorrelator(
    private val maxTelemetryAgeMs: Long = 15_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val relatedPidByDtc = mapOf(
        "P0171" to listOf("06", "07"), "P0172" to listOf("06", "07"),
        "P0101" to listOf("10", "04"),
        "P0115" to listOf("05"), "P0117" to listOf("05"),
        "P0118" to listOf("05"), "P0128" to listOf("05")
    )

    fun correlate(
        dtcs: List<DtcCode>, telemetry: Map<String, TelemetryPoint>,
        vehicle: ToyotaVehicleApplication,
        manualEvidence: List<ToyotaManualEvidence> = emptyList()
    ): List<ToyotaDiagnosticCorrelation> {
        val now = clock()
        return dtcs.map { dtc ->
            val code = dtc.code.trim().uppercase(Locale.US)
            val relatedPids = relatedPidByDtc[code].orEmpty()
            val usablePoints = relatedPids.mapNotNull { pid ->
                val point = telemetry[pid] ?: telemetry.values.firstOrNull { it.sourcePid.equals(pid, true) }
                point?.takeIf { isUsable(it, now) }
            }
            val manualMatches = manualEvidence
                .filter { it.kind == "dtc_candidate" && it.value.equals(code, true) && it.manualId.isNotBlank() && it.documentSha256.matches(Regex("^[a-f0-9]{64}$")) && it.extractionStatus == "candidate_unverified" }
                .map { matchApplication(it, vehicle) }
                .filter { it.applicability != ToyotaApplicabilityStatus.NOT_APPLICABLE }

            ToyotaDiagnosticCorrelation(
                dtcCode = code, dtcType = dtc.type.name,
                evidenceCoverage = if (usablePoints.isNotEmpty()) ToyotaEvidenceCoverage.DTC_PLUS_TELEMETRY else ToyotaEvidenceCoverage.DTC_ONLY,
                relatedPids = relatedPids,
                telemetryEvidenceRefs = usablePoints.map { it.evidenceRefId }.filter { it.isNotBlank() }.distinct(),
                manualEvidence = manualMatches,
                explanationThai = explanationFor(code, usablePoints.map { it.sourcePid }.distinct()),
                limitations = listOf(
                    "Correlation is a triage aid, not a confirmed root-cause diagnosis.",
                    "Generic OBD PID values require interpretation under the exact engine, operating state, and OEM test conditions.",
                    "Toyota manual evidence is machine-extracted and remains unverified until checked against the complete original page.",
                    "A matching DTC or telemetry value does not prove that a listed component is defective."
                )
            )
        }
    }

    private fun isUsable(point: TelemetryPoint, now: Long): Boolean {
        val age = now - point.timestamp
        return point.quality == DataQuality.VALID &&
            point.provenance in setOf(DataProvenance.REAL_HARDWARE, DataProvenance.SIMULATOR) &&
            point.value.isFinite() && age in 0..maxTelemetryAgeMs
    }

    private fun matchApplication(evidence: ToyotaManualEvidence, vehicle: ToyotaVehicleApplication): ToyotaManualEvidenceMatch {
        val unknown = mutableListOf<String>()
        val mismatches = mutableListOf<String>()
        if (evidence.modelCode.isBlank()) unknown += "modelCode"
        else if (!evidence.modelCode.equals(vehicle.modelCode, true)) mismatches += "modelCode"
        if (evidence.engineCode.isBlank()) unknown += "engineCode"
        else if (!evidence.engineCode.equals(vehicle.engineCode, true)) mismatches += "engineCode"

        val start = evidence.productionStartYear
        val end = evidence.productionEndYear
        if (start == null) unknown += "productionStartYear"
        else if (vehicle.productionYear < start) mismatches += "productionYear"
        if (end != null && vehicle.productionYear > end) mismatches += "productionYear"

        if (vehicle.region.isNotBlank()) {
            if (evidence.region.isBlank()) unknown += "region"
            else if (!evidence.region.equals(vehicle.region, true)) mismatches += "region"
        }
        val status = when {
            mismatches.isNotEmpty() -> ToyotaApplicabilityStatus.NOT_APPLICABLE
            unknown.isNotEmpty() -> ToyotaApplicabilityStatus.UNCONFIRMED
            else -> ToyotaApplicabilityStatus.CANDIDATE_MATCH
        }
        return ToyotaManualEvidenceMatch(evidence, status, unknown.distinct(), mismatches.distinct())
    }

    private fun explanationFor(code: String, pids: List<String>): String {
        val pidText = if (pids.isEmpty()) "ยังไม่มีค่า PID ที่ผ่านเกณฑ์คุณภาพและความสดใหม่สำหรับใช้ประกอบ"
            else "พบข้อมูล PID ${pids.joinToString(", ")} ที่ผ่านเกณฑ์คุณภาพ"
        return when (code) {
            "P0171", "P0172" -> "$code เกี่ยวข้องกับการควบคุมส่วนผสมเชื้อเพลิง; $pidText แต่ค่า Fuel Trim ต้องพิจารณาสภาวะเครื่องยนต์และข้อมูล Bank ที่ตรงกัน"
            "P0101" -> "$code เกี่ยวข้องกับช่วง/สมรรถนะวงจร MAF; $pidText ยังไม่สามารถสรุปว่าเซนเซอร์เสีย"
            "P0115", "P0117", "P0118", "P0128" -> "$code เกี่ยวข้องกับวงจรหรือสมรรถนะอุณหภูมิน้ำหล่อเย็น; $pidText ต้องตรวจสอบอุณหภูมิจริงและเงื่อนไขการทดสอบ"
            "P0087" -> "$code ระบุแรงดันระบบเชื้อเพลิงต่ำกว่าที่ ECU คาดหวัง; ยังไม่มี PID มาตรฐานที่รองรับใน correlator นี้ จึงต้องใช้ข้อมูลแรงดันรางจากเครื่องมือ/คู่มือที่ตรงรุ่น"
            else -> "$code ถูกนำมาจัดกลุ่มกับหลักฐานคู่มือ Toyota ที่ตรงรหัส; $pidText"
        }
    }
}
