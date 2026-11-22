package com.example.core.protocol

import com.example.core.elm327.ElmResponse
import com.example.core.elm327.ElmResponseType
import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.DtcClearResult
import com.example.core.model.DtcClearStatus
import com.example.core.model.DtcCode
import com.example.core.model.DtcSeverity
import com.example.core.model.DtcType
import java.security.MessageDigest

object DtcDecoder {

    data class DtcKnowledge(
        val code: String,
        val system: String,
        val thaiTitle: String,
        val thaiExplanation: String,
        val englishDescription: String,
        val severity: DtcSeverity,
        val symptomsThai: List<String>,
        val causesThai: List<String>
    )

    private val DTC_DATABASE: Map<String, DtcKnowledge> = listOf(
        DtcKnowledge(
            code = "P0171",
            system = "Powertrain (Fuel & Air Metering)",
            thaiTitle = "ส่วนผสมบางเกินไป (System Too Lean Bank 1)",
            thaiExplanation = "กล่อง ECU ตรวจพบว่าอัตราส่วนอากาศต่อน้ำมันมีออกซิเจนมากเกินไป (น้ำมันจ่ายไม่พอ หรือมีอากาศรั่วเข้าท่อร่วมไอดี)",
            englishDescription = "Fuel Trim System Lean (Bank 1)",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เครื่องยนต์เดินสะดุดตอนเดินเบา", "อัตราเร่งวูบหน่วง", "ไฟ Check Engine โชว์"),
            causesThai = listOf("เซนเซอร์ MAF สกปรก", "ท่อยางแวคคั่มรั่ว", "ปั๊มติ๊กแรงดันตก", "กรองเบนซินอุดตัน")
        ),
        DtcKnowledge(
            code = "P0300",
            system = "Powertrain (Ignition / Misfire)",
            thaiTitle = "การจุดระเบิดผิดพลาดหลายสูบ (Random/Multiple Cylinder Misfire)",
            thaiExplanation = "ตรวจพบกระบอกสูบหลายสูบเกิดการจุดระเบิดไม่สมบูรณ์ เสี่ยงต่อความเสียหายของลูกสูบและแคทตาไลติก",
            englishDescription = "Random/Multiple Cylinder Misfire Detected",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("เครื่องสั่นอย่างรุนแรง", "รถไม่มีกำลัง", "ควันไอเสียมีกลิ่นน้ำมันดิบ"),
            causesThai = listOf("คอยล์จุดระเบิดเสื่อม", "หัวเทียนบอด/ห่าง", "หัวฉีดตัน", "กำลังอัดกระบอกสูบต่ำ")
        ),
        DtcKnowledge(
            code = "P0420",
            system = "Powertrain (Emissions)",
            thaiTitle = "ประสิทธิภาพแคทตาไลติกต่ำกว่าเกณฑ์ (Catalyst Efficiency Below Threshold)",
            thaiExplanation = "ตัวกรองไอเสีย (Catalytic Converter) ฟอกไอเสียได้ไม่ถึงมาตรฐาน มักเกิดจากแคทตันหรือเสื่อมสภาพ",
            englishDescription = "Catalyst System Efficiency Below Threshold (Bank 1)",
            severity = DtcSeverity.MEDIUM,
            symptomsThai = listOf("ไฟรูปเครื่องติดค้าง", "กลิ่นไอเสียเหม็นฉุน", "อัตราเร่งตื้อเล็กน้อย"),
            causesThai = listOf("แคทตาไลติกเสื่อมสภาพ", "เซนเซอร์ O2 ตัวล่าง (Downstream) ชำรุด", "ท่อไอเสียรั่วก่อนถึงแคท")
        ),
        DtcKnowledge(
            code = "P0401",
            system = "Powertrain (EGR Exhaust Recirculation)",
            thaiTitle = "การไหลเวียนไอเสีย EGR ต่ำเกินไป (EGR Flow Insufficient)",
            thaiExplanation = "วาล์ว EGR หรือท่อทางเดินไอเสียมีเขม่าคราบน้ำมันเกาะอุดตัน ทำให้ไอเสียไม่สามารถวนกลับมาเผาไหม้ได้ (เจอบ่อยในกระบะดีเซล Toyota Revo / Isuzu D-Max)",
            englishDescription = "Exhaust Gas Recirculation (EGR) Flow Insufficient Detected",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("เครื่องยนต์เขกขณะเร่ง", "ควันดำเพิ่มขึ้น", "กินน้ำมัน"),
            causesThai = listOf("คราบเขม่าคาร์บอนตันในวาล์ว EGR", "ท่อแป๊บ EGR อุดตัน", "มอเตอร์ไฟฟ้าคุม EGR เสีย")
        ),
        DtcKnowledge(
            code = "P0087",
            system = "Powertrain (Common-rail Fuel System)",
            thaiTitle = "แรงดันในรางคอมมอนเรลต่ำเกินไป (Fuel Rail/System Pressure Too Low)",
            thaiExplanation = "แรงดันน้ำมันดีเซลในรางหัวฉีดต่ำกว่าค่าเป้าหมาย เสี่ยงทำให้เครื่องยนต์ดับกะทันหันขณะแซงหรือขึ้นเขา",
            englishDescription = "Fuel Rail/System Pressure - Too Low",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("เครื่องยนต์วูบดับขณะเร่งแซง", "สตาร์ทติดยาก", "มีเสียงเขกผิดปกติ"),
            causesThai = listOf("กรองโซล่าดักน้ำตัน", "SCV Valve (Suction Control Valve) ติดขัด", "ปั๊มแรงดันสูงคอมมอนเรลมีปัญหา")
        ),
        DtcKnowledge(
            code = "P0101",
            system = "Powertrain (Air Induction)",
            thaiTitle = "สัญญาณเซนเซอร์แอร์โฟลว์ผิดช่วง (Mass Air Flow Circuit Range/Performance)",
            thaiExplanation = "สัญญาณวัดปริมาณอากาศจากแอร์โฟลว์ (MAF) ไม่สอดคล้องกับรอบเครื่องยนต์และลิ้นเร่ง",
            englishDescription = "Mass or Volume Air Flow Circuit Range/Performance",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("รถออกตัวอืด วิ่งไม่ออก", "รอบเดินเบาสวิงขึ้นลง", "สิ้นเปลืองเชื้อเพลิง"),
            causesThai = listOf("เซนเซอร์ MAF มีฝุ่นละอองเกาะ", "กรองอากาศอุดตันรุนแรง", "ขั้วปลั๊กเซนเซอร์หลวม/ขาด")
        ),
        DtcKnowledge(
            code = "P0118",
            system = "Powertrain (Engine Cooling)",
            thaiTitle = "สัญญาณเซนเซอร์ความร้อนน้ำหล่อเย็นสูงผิดปกติ (ECT Sensor Circuit High)",
            thaiExplanation = "วงจรเซนเซอร์วัดอุณหภูมิน้ำหล่อเย็นเปิดวงจรหรือหลุด พัดลมหม้อน้ำอาจทำงานตลอดเวลา",
            englishDescription = "Engine Coolant Temperature Sensor 1 Circuit High",
            severity = DtcSeverity.HIGH,
            symptomsThai = listOf("พัดลมหม้อน้ำหมุนสปีดสูงสุดตลอดเวลา", "เกจ์ความร้อนไม่ขึ้นหรือค้าง", "สตาร์ทติดยากตอนเครื่องเย็น"),
            causesThai = listOf("ปลั๊กเซนเซอร์ ECT หลุด/หัก", "สายไฟขาดใน", "ตัวเซนเซอร์ ECT ชำรุด")
        ),
        DtcKnowledge(
            code = "U0100",
            system = "Network (CAN Bus Communication)",
            thaiTitle = "ขาดการสื่อสารกับกล่องควบคุมเครื่องยนต์ ECM/PCM (Lost Comm with ECM)",
            thaiExplanation = "กล่องควบคุมอื่นในรถ (เช่น TCM เกียร์, ABS, เรือนไมล์) ไม่สามารถสื่อสารผ่านสาย CAN Bus กับกล่องเครื่องยนต์ได้",
            englishDescription = "Lost Communication with Engine Control Module (ECM/PCM)",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("เกียร์ไม่เปลี่ยนหรือเข้า Safe Mode", "ไฟเตือน ABS/ESP โชว์พร้อมกัน", "เรือนไมล์เข็มตก"),
            causesThai = listOf("แรงดันแบตเตอรี่ต่ำผิดปกติ", "สาย CAN Bus ขาดหรือช็อต", "ฟิวส์เลี้ยงกล่อง ECU ขาด")
        ),
        DtcKnowledge(
            code = "C0035",
            system = "Chassis (Braking & ABS)",
            thaiTitle = "เซนเซอร์ความเร็วล้อหน้าซ้ายขัดข้อง (Left Front Wheel Speed Sensor)",
            thaiExplanation = "กล่อง ABS ไม่ได้รับสัญญาณความเร็วจากล้อหน้าซ้าย ทำให้ระบบป้องกันล้อล็อก (ABS) และกันลื่น (TCS) ปิดการทำงาน",
            englishDescription = "Left Front Wheel Speed Sensor Circuit",
            severity = DtcSeverity.MEDIUM,
            symptomsThai = listOf("ไฟ ABS และ Traction Control โชว์", "ระบบเบรกยังใช้งานได้แบบธรรมดาไม่มี ABS"),
            causesThai = listOf("เซนเซอร์ ABS มีคราบโคลน/เศษเหล็กติด", "สายเซนเซอร์ที่ดุมล้อขาด", "ลูกปืนล้อหลวม")
        ),
        DtcKnowledge(
            code = "B1000",
            system = "Body (Airbag / SRS)",
            thaiTitle = "กล่องควบคุมระบบถุงลมนิรภัยขัดข้องภายใน (ECU Malfunction)",
            thaiExplanation = "กล่องควบคุมระบบความปลอดภัย SRS ถุงลมนิรภัยรายงานความผิดปกติภายใน",
            englishDescription = "Electronic Control Unit (ECU) Performance Malfunction",
            severity = DtcSeverity.CRITICAL,
            symptomsThai = listOf("ไฟเตือนถุงลมนิรภัย Airbag โชว์ค้าง"),
            causesThai = listOf("ไฟกระชากในระบบ", "กล่อง SRS เสื่อมสภาพ", "เคยเกิดอุบัติเหตุชนแล้วยังไม่รีเซ็ต")
        )
    ).associateBy { it.code }

    fun decodeDtcResponse(response: ElmResponse, type: DtcType): List<DtcCode> {
        if (!response.isSuccess) return emptyList()

        val bytes = response.dataBytes
        if (bytes.isEmpty()) return emptyList()

        val expectedServiceReply = when (type) {
            DtcType.CONFIRMED -> 0x43
            DtcType.PENDING -> 0x47
            DtcType.PERMANENT -> 0x4A
        }

        // Find service reply byte index
        var serviceIndex = -1
        for (i in bytes.indices) {
            if (bytes[i] == expectedServiceReply) {
                serviceIndex = i
                break
            }
        }

        if (serviceIndex == -1) return emptyList()

        val dtcPayload = bytes.drop(serviceIndex + 1)
        val dtcList = mutableListOf<DtcCode>()

        // Check if first byte is count
        val codeBytes = if (dtcPayload.isNotEmpty() && dtcPayload.size % 2 != 0) {
            dtcPayload.drop(1)
        } else {
            dtcPayload
        }

        var i = 0
        while (i < codeBytes.size - 1) {
            val b1 = codeBytes[i]
            val b2 = codeBytes[i + 1]

            if (b1 != 0 || b2 != 0) {
                val codeStr = parseDtcBytes(b1, b2)
                val rawHex = String.format("%02X %02X", b1, b2)
                val knowledge = DTC_DATABASE[codeStr]

                val dtc = if (knowledge != null) {
                    DtcCode(
                        code = codeStr,
                        type = type,
                        ecuId = response.ecuHeader,
                        system = knowledge.system,
                        thaiTitle = knowledge.thaiTitle,
                        thaiExplanation = knowledge.thaiExplanation,
                        englishDescription = knowledge.englishDescription,
                        severity = knowledge.severity,
                        symptomsThai = knowledge.symptomsThai,
                        potentialCausesThai = knowledge.causesThai,
                        rawHex = rawHex,
                        evidenceRef = "DTC_EVD_${System.currentTimeMillis()}_$codeStr"
                    )
                } else {
                    val systemPrefix = when (codeStr.first()) {
                        'P' -> "Powertrain (ระบบขับเคลื่อนและเครื่องยนต์)"
                        'C' -> "Chassis (ระบบช่วงล่างและเบรก)"
                        'B' -> "Body (ระบบตัวถังและความปลอดภัย)"
                        'U' -> "Network (ระบบสื่อสารกล่องควบคุม)"
                        else -> "General Diagnostic Code"
                    }
                    DtcCode(
                        code = codeStr,
                        type = type,
                        ecuId = response.ecuHeader,
                        system = systemPrefix,
                        thaiTitle = "รหัสข้อผิดพลาด $codeStr",
                        thaiExplanation = "ตรวจพบโค้ดแจ้งเตือนความผิดปกติในระบบ $systemPrefix กรุณาตรวจสอบด้วยเครื่องมือสแกนเฉพาะทาง",
                        englishDescription = "Generic Manufacturer Diagnostic Trouble Code ($codeStr)",
                        severity = DtcSeverity.HIGH,
                        symptomsThai = listOf("ไฟ Check Engine โชว์", "อาจมีผลกระทบต่อสมรรถนะของรถ"),
                        potentialCausesThai = listOf("เซนเซอร์หรืออุปกรณ์ในระบบ $systemPrefix ทำงานบกพร่อง"),
                        rawHex = rawHex,
                        evidenceRef = "DTC_EVD_${System.currentTimeMillis()}_$codeStr"
                    )
                }
                dtcList.add(dtc)
            }
            i += 2
        }

        return dtcList
    }

    fun parseDtcBytes(b1: Int, b2: Int): String {
        val prefixChar = when ((b1 and 0xC0) shr 6) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            3 -> 'U'
            else -> 'P'
        }
        val firstDigit = (b1 and 0x30) shr 4
        val secondDigit = b1 and 0x0F
        val thirdDigit = (b2 and 0xF0) shr 4
        val fourthDigit = b2 and 0x0F

        return String.format("%c%X%X%X%X", prefixChar, firstDigit, secondDigit, thirdDigit, fourthDigit)
    }

    /**
     * Decodes and validates OBD-II Service 04 response.
     * Evaluates positive acknowledgements (44 / OK), negative response codes (7F 04 <NRC>),
     * and hardware/bus communication failures.
     */
    fun decodeClearDtcResponse(response: ElmResponse, evidenceId: String): DtcClearResult {
        val payload = response.cleanPayload.trim()
        val raw = response.rawResponse
        val bytes = response.dataBytes
        val ecuHeader = response.ecuHeader.ifBlank { "7E8" }
        val timestamp = System.currentTimeMillis()
        val sha256 = calculateHash("04:$raw:$timestamp")

        // 1. Check for Negative Response Code (NRC): 7F 04 <NRC>
        val nrcIndex = bytes.indexOf(0x7F)
        if (nrcIndex != -1 && nrcIndex + 2 < bytes.size && bytes[nrcIndex + 1] == 0x04) {
            val nrcCodeInt = bytes[nrcIndex + 2]
            val nrcHex = String.format("%02X", nrcCodeInt)
            val (meaningTh, meaningEn) = getNrcDescription(nrcCodeInt)

            return DtcClearResult(
                status = DtcClearStatus.ECU_REJECTED,
                commandTx = "04",
                responseRx = raw,
                cleanPayload = payload,
                rawHex = bytes.joinToString(" ") { String.format("%02X", it) },
                ecuHeader = ecuHeader,
                confirmationMessageThai = "",
                confirmationMessageEnglish = "",
                errorMessageThai = "ECU ปฏิเสธคำสั่งล้างรหัสข้อผิดพลาด (Negative Response 7F 04 $nrcHex): $meaningTh",
                errorMessageEnglish = "ECU rejected Service 04 request (NRC $nrcHex): $meaningEn",
                negativeResponseCode = nrcHex,
                nrcMeaningThai = meaningTh,
                latencyMs = response.latencyMs,
                evidenceRef = evidenceId,
                sha256Hash = sha256,
                timestamp = timestamp,
                provenance = response.provenance,
                quality = DataQuality.VALID
            )
        }

        // 2. Check for Positive Response: 0x44 (Service 04 Positive Response) or "44" / "OK"
        val has44Byte = bytes.contains(0x44)
        val has44Token = payload.split("\\s+".toRegex()).any { it.equals("44", ignoreCase = true) }
        val isOkAck = response.type == ElmResponseType.OK_RESPONSE || payload.equals("OK", ignoreCase = true)

        if (has44Byte || has44Token || isOkAck) {
            return DtcClearResult(
                status = DtcClearStatus.SUCCESS,
                commandTx = "04",
                responseRx = raw,
                cleanPayload = payload,
                rawHex = if (bytes.isNotEmpty()) bytes.joinToString(" ") { String.format("%02X", it) } else "44",
                ecuHeader = ecuHeader,
                confirmationMessageThai = "กล่อง ECU ตอบรับคำสั่งล้างรหัสสำเร็จ (Positive Response: 44) รีเซ็ตไฟ Check Engine (MIL) และหน่วยความจำ DTC เรียบร้อยแล้ว",
                confirmationMessageEnglish = "ECU acknowledged Service 04 with positive response (44). All DTCs and Freeze Frame memory cleared successfully.",
                errorMessageThai = null,
                errorMessageEnglish = null,
                negativeResponseCode = null,
                nrcMeaningThai = null,
                latencyMs = response.latencyMs,
                evidenceRef = evidenceId,
                sha256Hash = sha256,
                timestamp = timestamp,
                provenance = response.provenance,
                quality = DataQuality.VALID
            )
        }

        // 3. Check for NO DATA
        if (response.type == ElmResponseType.NO_DATA || payload.contains("NO DATA", ignoreCase = true)) {
            return DtcClearResult(
                status = DtcClearStatus.NO_DATA,
                commandTx = "04",
                responseRx = raw,
                cleanPayload = "NO DATA",
                rawHex = "",
                ecuHeader = ecuHeader,
                confirmationMessageThai = "",
                confirmationMessageEnglish = "",
                errorMessageThai = "ไม่มีการตอบสนองจากกล่อง ECU (NO DATA) กรุณาตรวจสอบว่าสวิตช์กุญแจรถยนต์บิดไปที่ตำแหน่ง ON หรือยัง",
                errorMessageEnglish = "No data received from vehicle ECU. Ensure ignition switch is in ON position (KOEO).",
                negativeResponseCode = null,
                nrcMeaningThai = null,
                latencyMs = response.latencyMs,
                evidenceRef = evidenceId,
                sha256Hash = sha256,
                timestamp = timestamp,
                provenance = response.provenance,
                quality = DataQuality.UNAVAILABLE
            )
        }

        // 4. Other Communication Failures
        val errorText = response.errorMessage ?: payload.ifBlank { "Unknown Bus Error" }
        return DtcClearResult(
            status = DtcClearStatus.COMMUNICATION_ERROR,
            commandTx = "04",
            responseRx = raw,
            cleanPayload = payload,
            rawHex = bytes.joinToString(" ") { String.format("%02X", it) },
            ecuHeader = ecuHeader,
            confirmationMessageThai = "",
            confirmationMessageEnglish = "",
            errorMessageThai = "เกิดข้อผิดพลาดในการส่งคำสั่ง Service 04: $errorText",
            errorMessageEnglish = "Communication failure sending Service 04: $errorText",
            negativeResponseCode = null,
            nrcMeaningThai = null,
            latencyMs = response.latencyMs,
            evidenceRef = evidenceId,
            sha256Hash = sha256,
            timestamp = timestamp,
            provenance = response.provenance,
            quality = DataQuality.INVALID
        )
    }

    private fun getNrcDescription(nrc: Int): Pair<String, String> {
        return when (nrc) {
            0x11 -> "ฟังก์ชันไม่ได้รับการรองรับ (Service Not Supported)" to "Service Not Supported"
            0x12 -> "ฟังก์ชันย่อยไม่ถูกต้อง (Sub-Function Not Supported)" to "Sub-Function Not Supported"
            0x13 -> "ความยาวข้อความหรือรูปแบบไม่ถูกต้อง (Incorrect Format)" to "Incorrect Message Length Or Invalid Format"
            0x22 -> "สภาวะของรถไม่ถูกต้อง กรุณาดับเครื่องยนต์แต่เปิดสวิตช์กุญแจ ON (KOEO - Conditions Not Correct)" to "Conditions Not Correct (Engine running or speed > 0)"
            0x31 -> "พารามิเตอร์อยู่นอกช่วงที่กำหนด (Request Out Of Range)" to "Request Out Of Range"
            0x33 -> "ปฏิเสธการเข้าถึงความปลอดภัย (Security Access Denied)" to "Security Access Denied"
            0x78 -> "รับคำสั่งแล้ว กำลังประมวลผล (Response Pending)" to "Request Correctly Received, Response Pending"
            else -> "รหัสข้อผิดพลาดเฉพาะ $nrc (ECU Rejection Code)" to "General ECU Rejection Code $nrc"
        }
    }

    private fun calculateHash(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
