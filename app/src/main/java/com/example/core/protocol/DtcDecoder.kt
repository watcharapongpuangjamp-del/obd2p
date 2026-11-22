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

    fun decodeDtcResponse(
        response: ElmResponse,
        type: DtcType,
        evidenceRefOverride: String? = null
    ): List<DtcCode> {
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
                val linkedEvidenceRef = evidenceRefOverride
                    ?: "DTC_EVD_${System.currentTimeMillis()}_$codeStr"
                val dtc = DtcKnowledgeBase.toDtcCodeModel(
                    code = codeStr,
                    type = type,
                    ecuId = response.ecuHeader.ifBlank { "7E8" },
                    rawHex = rawHex,
                    evidenceRef = linkedEvidenceRef
                )
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
