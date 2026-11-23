package com.example.core.rules

import com.example.core.model.DeterministicFinding
import com.example.core.model.DtcScanResult
import com.example.core.model.FindingSeverity
import com.example.core.model.TelemetryPoint
import java.util.Locale

interface IDiagnosticRuleEngine {
    fun evaluateRules(
        telemetry: Map<String, TelemetryPoint>,
        dtcResult: DtcScanResult,
        batteryVoltageStr: String
    ): List<DeterministicFinding>
}

class DeterministicDiagnosticRuleEngine : IDiagnosticRuleEngine {

    override fun evaluateRules(
        telemetry: Map<String, TelemetryPoint>,
        dtcResult: DtcScanResult,
        batteryVoltageStr: String
    ): List<DeterministicFinding> {
        val findings = mutableListOf<DeterministicFinding>()
        // A deterministic rule may only consume current, validated measurements.
        // Stale, unavailable, unsupported, estimated, and unknown-source values
        // must not be treated as direct sensor evidence.
        val validTelemetry = telemetry.filterValues { point ->
            point.quality == com.example.core.model.DataQuality.VALID &&
                point.provenance in setOf(\n                    com.example.core.model.DataProvenance.REAL_HARDWARE,\n                    com.example.core.model.DataProvenance.SIMULATOR\n                ) &&
                point.value.isFinite()
        }

        // 1. Coolant Temperature Rule (PID 05)
        val ectPoint = validTelemetry["05"]
        if (ectPoint != null && ectPoint.value > 0) {
            val ect = ectPoint.value
            if (ect >= 110.0) {
                findings.add(
                    DeterministicFinding(
                        id = "RULE_ECT_CRITICAL_${System.currentTimeMillis()}",
                        ruleId = "RULE_OVERHEAT_01",
                        ruleNameThai = "ความร้อนหม้อน้ำวิกฤต (Critical Overheating)",
                        ruleNameEnglish = "Critical Engine Overheating Condition",
                        severity = FindingSeverity.CRITICAL,
                        descriptionThai = "อุณหภูมิน้ำหล่อเย็นสูงถึง ${ectPoint.displayValue}°C (เกินเกณฑ์ความปลอดภัย 105°C) เสี่ยงฝาสูบโก่งและเครื่องยนต์เสียหายรุนแรง",
                        descriptionEnglish = "Engine Coolant Temperature reached ${ectPoint.displayValue}°C exceeding safe operational threshold (105°C).",
                        recommendedActionThai = "จอดรถในที่ปลอดภัยทันที ดับเครื่องยนต์ และห้ามเปิดฝาหม้อน้ำขณะร้อนเด็ดขาด",
                        evidenceRefs = listOf(ectPoint.evidenceRefId.ifBlank { "EVD_ECT" }),
                        triggerValue = "${ectPoint.displayValue}°C",
                        thresholdValue = "105.0°C"
                    )
                )
            } else if (ect >= 98.0) {
                findings.add(
                    DeterministicFinding(
                        id = "RULE_ECT_WARN_${System.currentTimeMillis()}",
                        ruleId = "RULE_OVERHEAT_02",
                        ruleNameThai = "ความร้อนเครื่องยนต์เริ่มสูงผิดปกติ",
                        ruleNameEnglish = "Elevated Engine Temperature",
                        severity = FindingSeverity.WARNING,
                        descriptionThai = "อุณหภูมิน้ำหล่อเย็นอยู่ที่ ${ectPoint.displayValue}°C ควรเฝ้าระวังพัดลมหม้อน้ำและระดับน้ำในหม้อพัก",
                        descriptionEnglish = "Engine Coolant Temperature is running warm at ${ectPoint.displayValue}°C.",
                        recommendedActionThai = "ตรวจสอบระดับน้ำยาหล่อเย็นและการทำงานของพัดลมไฟฟ้า",
                        evidenceRefs = listOf(ectPoint.evidenceRefId.ifBlank { "EVD_ECT" }),
                        triggerValue = "${ectPoint.displayValue}°C",
                        thresholdValue = "98.0°C"
                    )
                )
            }
        }

        // 2. Battery & Alternator Charging Rule (PID 42 or ATRV)
        val voltVal = parseVoltage(batteryVoltageStr, validTelemetry["42"])
        val rpmVal = validTelemetry["0C"]?.value ?: 0.0
        if (voltVal > 0) {
            if (rpmVal > 600 && voltVal < 12.8) {
                findings.add(
                    DeterministicFinding(
                        id = "RULE_ALT_LOW_${System.currentTimeMillis()}",
                        ruleId = "RULE_CHARGING_01",
                        ruleNameThai = "ไดชาร์จไม่ทำงาน / แรงดันไฟต่ำขณะติดเครื่อง",
                        ruleNameEnglish = "Alternator Charging System Undervoltage",
                        severity = FindingSeverity.CRITICAL,
                        descriptionThai = "ตรวจพบแรงดันระบบไฟเพียง ${String.format(Locale.US, "%.1f", voltVal)}V ขณะเครื่องยนต์ทำงาน (เกณฑ์ปกติไดชาร์จ: 13.6V - 14.5V)",
                        descriptionEnglish = "Battery voltage is ${String.format(Locale.US, "%.1f", voltVal)}V while engine is running, indicating alternator failure.",
                        recommendedActionThai = "ตรวจสอบสายพานไดชาร์จและไดชาร์จ (Alternator) เสี่ยงแบตเตอรี่หมดกลางทาง",
                        evidenceRefs = listOf(validTelemetry["42"]?.evidenceRefId ?: "EVD_VOLT"),
                        triggerValue = "${String.format(Locale.US, "%.1f", voltVal)}V",
                        thresholdValue = "13.6V"
                    )
                )
            } else if (voltVal > 15.2) {
                findings.add(
                    DeterministicFinding(
                        id = "RULE_ALT_HIGH_${System.currentTimeMillis()}",
                        ruleId = "RULE_CHARGING_02",
                        ruleNameThai = "ไดชาร์จจ่ายไฟเกิน (Overcharging)",
                        ruleNameEnglish = "Charging System Overvoltage",
                        severity = FindingSeverity.WARNING,
                        descriptionThai = "แรงดันไฟสูงถึง ${String.format(Locale.US, "%.1f", voltVal)}V เสี่ยงทำลายกล่อง ECU และแบตเตอรี่บวม",
                        descriptionEnglish = "Battery voltage is high at ${String.format(Locale.US, "%.1f", voltVal)}V, potentially damaging electronic modules.",
                        recommendedActionThai = "ตรวจสอบ IC Regulator ในไดชาร์จโดยด่วน",
                        evidenceRefs = listOf(telemetry["42"]?.evidenceRefId ?: "EVD_VOLT"),
                        triggerValue = "${String.format(Locale.US, "%.1f", voltVal)}V",
                        thresholdValue = "15.0V"
                    )
                )
            }
        }

        // 3. Fuel Trim Anomaly (STFT 06 + LTFT 07)
        val stft = validTelemetry["06"]?.value
        val ltft = validTelemetry["07"]?.value
        if (stft != null && ltft != null) {
            val totalTrim = stft + ltft
            if (totalTrim > 18.0) {
                findings.add(
                    DeterministicFinding(
                        id = "RULE_TRIM_LEAN_${System.currentTimeMillis()}",
                        ruleId = "RULE_FUEL_01",
                        ruleNameThai = "ส่วนผสมเชื้อเพลิงบางผิดปกติ (Fuel System Too Lean)",
                        ruleNameEnglish = "Excessive Positive Fuel Trim Correction",
                        severity = FindingSeverity.WARNING,
                        descriptionThai = "ค่า Fuel Trim รวมสูงถึง ${String.format(Locale.US, "%+.1f", totalTrim)}% ECU ต้องชดเชยการจ่ายน้ำมันเพิ่มขึ้นอย่างมาก",
                        descriptionEnglish = "Total Fuel Trim correction is ${String.format(Locale.US, "%+.1f", totalTrim)}% indicating vacuum leak or fuel delivery restriction.",
                        recommendedActionThai = "ตรวจเช็คหารอยรั่วท่อแวคคั่มไอดี, ล้างแอร์โฟลว์ (MAF), และตรวจวัดแรงดันปั๊มติ๊ก",
                        evidenceRefs = listOf(validTelemetry["06"]?.evidenceRefId ?: "EVD_STFT", validTelemetry["07"]?.evidenceRefId ?: "EVD_LTFT"),
                        triggerValue = "${String.format(Locale.US, "%+.1f", totalTrim)}%",
                        thresholdValue = "+15.0%"
                    )
                )
            } else if (totalTrim < -18.0) {
                findings.add(
                    DeterministicFinding(
                        id = "RULE_TRIM_RICH_${System.currentTimeMillis()}",
                        ruleId = "RULE_FUEL_02",
                        ruleNameThai = "ส่วนผสมเชื้อเพลิงหนาผิดปกติ (Fuel System Too Rich)",
                        ruleNameEnglish = "Excessive Negative Fuel Trim Correction",
                        severity = FindingSeverity.WARNING,
                        descriptionThai = "ค่า Fuel Trim รวมติดลบ ${String.format(Locale.US, "%+.1f", totalTrim)}% ECU พยายามลดการจ่ายน้ำมัน",
                        descriptionEnglish = "Total Fuel Trim is negative ${String.format(Locale.US, "%+.1f", totalTrim)}% indicating rich running condition.",
                        recommendedActionThai = "ตรวจเช็คหัวฉีดรั่วซึม, กรองอากาศตัน, หรือตัวควบคุมแรงดันน้ำมัน",
                        evidenceRefs = listOf(telemetry["06"]?.evidenceRefId ?: "EVD_STFT"),
                        triggerValue = "${String.format(Locale.US, "%+.1f", totalTrim)}%",
                        thresholdValue = "-15.0%"
                    )
                )
            }
        }

        // 4. Turbo Boost / MAP Check (PID 0B)
        val mapPoint = validTelemetry["0B"]
        if (mapPoint != null && mapPoint.value > 220.0) {
            findings.add(
                DeterministicFinding(
                    id = "RULE_BOOST_HIGH_${System.currentTimeMillis()}",
                    ruleId = "RULE_BOOST_01",
                    ruleNameThai = "แรงดันบูสต์เทอร์โบสูง (High Turbo Boost Pressure)",
                    ruleNameEnglish = "High Intake Manifold Absolute Pressure",
                    severity = FindingSeverity.ADVISORY,
                    descriptionThai = "แรงดันไอดีอยู่ที่ ${mapPoint.displayValue} kPa (บูสต์ทำงานเต็มประสิทธิภาพ)",
                    descriptionEnglish = "Turbo boost active with MAP reading ${mapPoint.displayValue} kPa.",
                    recommendedActionThai = "ระบบอัดอากาศทำงานปกติ ควรรักษาความสะอาดของกรองอากาศ",
                    evidenceRefs = listOf(mapPoint.evidenceRefId.ifBlank { "EVD_MAP" }),
                    triggerValue = "${mapPoint.displayValue} kPa",
                    thresholdValue = "200.0 kPa"
                )
            )
        }

        // 5. DTC Critical Faults Check
        for (dtc in dtcResult.confirmedCodes) {
            if (dtc.severity == com.example.core.model.DtcSeverity.CRITICAL) {
                findings.add(
                    DeterministicFinding(
                        id = "RULE_DTC_CRIT_${dtc.code}_${System.currentTimeMillis()}",
                        ruleId = "RULE_DTC_CRITICAL",
                        ruleNameThai = "รหัสข้อผิดพลาดระดับวิกฤต: ${dtc.code}",
                        ruleNameEnglish = "Critical Fault Code Active: ${dtc.code}",
                        severity = FindingSeverity.CRITICAL,
                        descriptionThai = "${dtc.thaiTitle}: ${dtc.thaiExplanation}",
                        descriptionEnglish = "${dtc.code}: ${dtc.englishDescription}",
                        recommendedActionThai = dtc.potentialCausesThai.firstOrNull() ?: "นำรถเข้าศูนย์บริการหรืออู่ซ่อมโดยด่วน",
                        evidenceRefs = listOf(dtc.evidenceRef),
                        triggerValue = dtc.code,
                        thresholdValue = "NO_CRITICAL_DTC"
                    )
                )
            }
        }

        return findings
    }

    private fun parseVoltage(voltStr: String, point: TelemetryPoint?): Double {
        if (point != null && point.value > 0) return point.value
        val clean = voltStr.replace("V", "").replace("v", "").trim()
        return clean.toDoubleOrNull() ?: 0.0
    }
}
