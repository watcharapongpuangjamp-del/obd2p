package com.example.core.export

import com.example.core.model.AiDiagnosticReport
import com.example.core.model.DeterministicFinding
import com.example.core.model.DtcScanResult
import com.example.core.model.EcuInfo
import com.example.core.model.EvidenceLog
import com.example.core.model.TelemetryPoint
import com.example.core.model.VehicleProfile
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface IReportExporter {
    fun exportSessionToJson(
        profile: VehicleProfile,
        telemetry: Map<String, TelemetryPoint>,
        dtcResult: DtcScanResult,
        ecus: List<EcuInfo>,
        ruleFindings: List<DeterministicFinding>,
        aiReport: AiDiagnosticReport?,
        evidenceLogs: List<EvidenceLog>
    ): String

    fun exportTelemetryToCsv(telemetry: Map<String, TelemetryPoint>): String

    fun exportDiagnosticCertificateText(
        profile: VehicleProfile,
        dtcResult: DtcScanResult,
        ruleFindings: List<DeterministicFinding>,
        aiReport: AiDiagnosticReport?,
        batteryVoltage: String
    ): String
}

class ReportExporter : IReportExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override fun exportSessionToJson(
        profile: VehicleProfile,
        telemetry: Map<String, TelemetryPoint>,
        dtcResult: DtcScanResult,
        ecus: List<EcuInfo>,
        ruleFindings: List<DeterministicFinding>,
        aiReport: AiDiagnosticReport?,
        evidenceLogs: List<EvidenceLog>
    ): String {
        val root = JSONObject().apply {
            put("app", "Thai Car OBD-II Pro V2.0")
            put("exportedAt", dateFormat.format(Date()))
            put("vehicle", JSONObject().apply {
                put("make", profile.make)
                put("model", profile.model)
                put("year", profile.year)
                put("engine", profile.engine)
                put("vin", profile.vin)
                put("protocol", profile.protocol)
            })

            put("discoveredEcus", JSONArray().apply {
                ecus.forEach { ecu ->
                    put(JSONObject().apply {
                        put("ecuId", ecu.ecuId)
                        put("moduleName", ecu.moduleName)
                        put("protocol", ecu.protocol)
                        put("confidence", ecu.confidence)
                        put("supportedPidsCount", ecu.supportedPids.size)
                    })
                }
            })

            put("telemetrySnapshot", JSONArray().apply {
                telemetry.values.forEach { p ->
                    put(JSONObject().apply {
                        put("pid", p.sourcePid)
                        put("name", p.name)
                        put("thaiName", p.thaiName)
                        put("value", p.value)
                        put("display", "${p.displayValue} ${p.unit}")
                        put("quality", p.quality.name)
                        put("provenance", p.provenance.name)
                        put("latencyMs", p.latencyMs)
                        put("evidenceRefId", p.evidenceRefId)
                    })
                }
            })

            put("dtcScan", JSONObject().apply {
                put("status", dtcResult.status.name)
                put("totalFaults", dtcResult.totalFaults)
                put("confirmedCodes", JSONArray().apply {
                    dtcResult.confirmedCodes.forEach { dtc ->
                        put(JSONObject().apply {
                            put("code", dtc.code)
                            put("system", dtc.system)
                            put("thaiTitle", dtc.thaiTitle)
                            put("severity", dtc.severity.name)
                            put("rawHex", dtc.rawHex)
                            put("evidenceRef", dtc.evidenceRef)
                        })
                    }
                })
            })

            put("deterministicFindings", JSONArray().apply {
                ruleFindings.forEach { r ->
                    put(JSONObject().apply {
                        put("ruleId", r.ruleId)
                        put("nameThai", r.ruleNameThai)
                        put("severity", r.severity.name)
                        put("triggerValue", r.triggerValue)
                        put("thresholdValue", r.thresholdValue)
                        put("evidenceRefs", JSONArray(r.evidenceRefs))
                    })
                }
            })

            if (aiReport != null) {
                put("aiDiagnosticReport", JSONObject().apply {
                    put("summaryThai", aiReport.summaryThai)
                    put("rootCauseThai", aiReport.rootCauseAnalysisThai)
                    put("severity", aiReport.severity.name)
                    put("confidence", "${aiReport.confidencePercentage}%")
                    put("estimatedCostThb", aiReport.estimatedRepairCostThb)
                    put("actionPlanThai", JSONArray(aiReport.actionPlanThai))
                })
            }

            put("evidenceChainSample", JSONArray().apply {
                evidenceLogs.take(50).forEach { ev ->
                    put(JSONObject().apply {
                        put("id", ev.id)
                        put("timestamp", ev.timestamp)
                        put("commandTx", ev.commandText)
                        put("responseRx", ev.responseText)
                        put("latencyMs", ev.latencyMs)
                        put("sha256", ev.sha256Hash)
                    })
                }
            })
        }

        return root.toString(2)
    }

    override fun exportTelemetryToCsv(telemetry: Map<String, TelemetryPoint>): String {
        val sb = StringBuilder()
        sb.append("PID,Name,ThaiName,Value,Unit,Quality,Provenance,LatencyMs,RawHex,EvidenceRef\n")
        telemetry.values.sortedBy { it.sourcePid }.forEach { p ->
            sb.append("${p.sourcePid},\"${p.name}\",\"${p.thaiName}\",${p.displayValue},\"${p.unit}\",${p.quality.name},${p.provenance.name},${p.latencyMs},\"${p.rawHexResponse}\",\"${p.evidenceRefId}\"\n")
        }
        return sb.toString()
    }

    override fun exportDiagnosticCertificateText(
        profile: VehicleProfile,
        dtcResult: DtcScanResult,
        ruleFindings: List<DeterministicFinding>,
        aiReport: AiDiagnosticReport?,
        batteryVoltage: String
    ): String {
        val sb = StringBuilder()
        sb.append("========================================================\n")
        sb.append("      THAI CAR OBD-II PRO V2.0 - DIAGNOSTIC CERTIFICATE\n")
        sb.append("      ใบรายงานผลการตรวจสุขภาพรถยนต์ทางอิเล็กทรอนิกส์\n")
        sb.append("========================================================\n\n")

        sb.append("ข้อมูลรถยนต์ (Vehicle Information):\n")
        sb.append(" - ยี่ห้อ / รุ่น: ${profile.make} ${profile.model} (${profile.year})\n")
        sb.append(" - เครื่องยนต์: ${profile.engine}\n")
        sb.append(" - เลขตัวถัง (VIN): ${profile.vin}\n")
        sb.append(" - โปรโตคอลการสื่อสาร: ${profile.protocol}\n")
        sb.append(" - แรงดันไฟแบตเตอรี่: $batteryVoltage\n")
        sb.append(" - วันที่ตรวจเช็ค: ${dateFormat.format(Date())}\n\n")

        sb.append("--------------------------------------------------------\n")
        sb.append("สรุปผลการสแกนรหัสข้อผิดพลาด (DTC Diagnostic Scan):\n")
        sb.append(" - รหัสที่ยืนยัน (Confirmed): ${dtcResult.confirmedCodes.size} รายการ\n")
        sb.append(" - รหัสที่กำลังรอตรวจสอบ (Pending): ${dtcResult.pendingCodes.size} รายการ\n")
        sb.append(" - รหัสถาวร (Permanent): ${dtcResult.permanentCodes.size} รายการ\n\n")

        if (dtcResult.confirmedCodes.isNotEmpty()) {
            sb.append("รายการโค้ดปัญหาที่พบ:\n")
            dtcResult.confirmedCodes.forEachIndexed { i, dtc ->
                sb.append(" [${i + 1}] ${dtc.code} - ${dtc.thaiTitle}\n")
                sb.append("     ระบบ: ${dtc.system} | ระดับความรุนแรง: ${dtc.severity}\n")
                sb.append("     คำอธิบาย: ${dtc.thaiExplanation}\n")
                sb.append("     หลักฐาน RAW Hex: ${dtc.rawHex} (ECU: ${dtc.ecuId})\n\n")
            }
        } else {
            sb.append(" -> ไม่พบรหัสข้อผิดพลาดในระบบเครื่องยนต์และระบบส่งกำลัง (0 Faults Found)\n\n")
        }

        if (ruleFindings.isNotEmpty()) {
            sb.append("--------------------------------------------------------\n")
            sb.append("ผลการตรวจสอบกฎความปลอดภัยเชิงกำหนด (Deterministic Rules):\n")
            ruleFindings.forEach { r ->
                sb.append(" * [${r.severity}] ${r.ruleNameThai}\n")
                sb.append("   ${r.descriptionThai}\n")
                sb.append("   คำแนะนำ: ${r.recommendedActionThai}\n\n")
            }
        }

        if (aiReport != null) {
            sb.append("--------------------------------------------------------\n")
            sb.append("การวิเคราะห์อัจฉริยะ (AI Expert Analysis):\n")
            sb.append(" - สรุป: ${aiReport.summaryThai}\n")
            sb.append(" - สาเหตุรากฐาน: ${aiReport.rootCauseAnalysisThai}\n")
            sb.append(" - ระดับความมั่นใจ: ${aiReport.confidencePercentage}%\n")
            sb.append(" - ประมาณการค่าซ่อมแซม: ${aiReport.estimatedRepairCostThb}\n")
            sb.append(" - แผนการดำเนินการ:\n")
            aiReport.actionPlanThai.forEach { action ->
                sb.append("    ✓ $action\n")
            }
            if (aiReport.safetyWarningThai != null) {
                sb.append("\n ⚠️ คำเตือน: ${aiReport.safetyWarningThai}\n")
            }
        }

        sb.append("\n========================================================\n")
        sb.append("หลักฐานการสื่อสาร (Evidence Chain): ตรวจสอบย้อนกลับได้ 100%\n")
        sb.append("========================================================\n")
        return sb.toString()
    }
}
