package com.example.core.protocol

import com.example.core.elm327.ElmResponse
import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.TelemetryPoint
import java.util.Locale

object PidDecoder {

    data class PidDefinition(
        val pidHex: String,
        val name: String,
        val thaiName: String,
        val unit: String,
        val bytesRequired: Int,
        val decoder: (List<Int>) -> Double,
        val formatter: (Double) -> String
    )

    val PID_DEFINITIONS: Map<String, PidDefinition> = listOf(
        PidDefinition(
            pidHex = "04",
            name = "Engine Load",
            thaiName = "ภาระโหลดเครื่องยนต์",
            unit = "%",
            bytesRequired = 1,
            decoder = { bytes -> bytes[0] * 100.0 / 255.0 },
            formatter = { String.format(Locale.US, "%.1f", it) }
        ),
        PidDefinition(
            pidHex = "05",
            name = "Coolant Temp",
            thaiName = "อุณหภูมิน้ำหล่อเย็น",
            unit = "°C",
            bytesRequired = 1,
            decoder = { bytes -> (bytes[0] - 40).toDouble() },
            formatter = { String.format(Locale.US, "%.0f", it) }
        ),
        PidDefinition(
            pidHex = "06",
            name = "Short Term Fuel Trim B1",
            thaiName = "การปรับจ่ายน้ำมันระยะสั้น (STFT)",
            unit = "%",
            bytesRequired = 1,
            decoder = { bytes -> (bytes[0] - 128) * 100.0 / 128.0 },
            formatter = { String.format(Locale.US, "%+.1f", it) }
        ),
        PidDefinition(
            pidHex = "07",
            name = "Long Term Fuel Trim B1",
            thaiName = "การปรับจ่ายน้ำมันระยะยาว (LTFT)",
            unit = "%",
            bytesRequired = 1,
            decoder = { bytes -> (bytes[0] - 128) * 100.0 / 128.0 },
            formatter = { String.format(Locale.US, "%+.1f", it) }
        ),
        PidDefinition(
            pidHex = "0B",
            name = "Intake MAP (Boost)",
            thaiName = "แรงดันไอดี / บูสต์เทอร์โบ",
            unit = "kPa",
            bytesRequired = 1,
            decoder = { bytes -> bytes[0].toDouble() },
            formatter = { String.format(Locale.US, "%.0f", it) }
        ),
        PidDefinition(
            pidHex = "0C",
            name = "Engine RPM",
            thaiName = "รอบเครื่องยนต์",
            unit = "RPM",
            bytesRequired = 2,
            decoder = { bytes -> ((bytes[0] * 256) + bytes[1]) / 4.0 },
            formatter = { String.format(Locale.US, "%.0f", it) }
        ),
        PidDefinition(
            pidHex = "0D",
            name = "Vehicle Speed",
            thaiName = "ความเร็วรถยนต์",
            unit = "km/h",
            bytesRequired = 1,
            decoder = { bytes -> bytes[0].toDouble() },
            formatter = { String.format(Locale.US, "%.0f", it) }
        ),
        PidDefinition(
            pidHex = "0E",
            name = "Timing Advance",
            thaiName = "องศาจุดระเบิด",
            unit = "°",
            bytesRequired = 1,
            decoder = { bytes -> (bytes[0] / 2.0) - 64.0 },
            formatter = { String.format(Locale.US, "%+.1f", it) }
        ),
        PidDefinition(
            pidHex = "0F",
            name = "Intake Air Temp (IAT)",
            thaiName = "อุณหภูมิอากาศไอดี",
            unit = "°C",
            bytesRequired = 1,
            decoder = { bytes -> (bytes[0] - 40).toDouble() },
            formatter = { String.format(Locale.US, "%.0f", it) }
        ),
        PidDefinition(
            pidHex = "10",
            name = "MAF Air Flow Rate",
            thaiName = "มวลอากาศไอดี (MAF)",
            unit = "g/s",
            bytesRequired = 2,
            decoder = { bytes -> ((bytes[0] * 256) + bytes[1]) / 100.0 },
            formatter = { String.format(Locale.US, "%.2f", it) }
        ),
        PidDefinition(
            pidHex = "11",
            name = "Throttle Position (TPS)",
            thaiName = "ตำแหน่งลิ้นเร่ง (TPS)",
            unit = "%",
            bytesRequired = 1,
            decoder = { bytes -> bytes[0] * 100.0 / 255.0 },
            formatter = { String.format(Locale.US, "%.1f", it) }
        ),
        PidDefinition(
            pidHex = "1F",
            name = "Engine Run Time",
            thaiName = "เวลาติดเครื่องยนต์",
            unit = "sec",
            bytesRequired = 2,
            decoder = { bytes -> ((bytes[0] * 256) + bytes[1]).toDouble() },
            formatter = { String.format(Locale.US, "%.0f", it) }
        ),
        PidDefinition(
            pidHex = "2F",
            name = "Fuel Tank Level",
            thaiName = "ระดับน้ำมันในถัง",
            unit = "%",
            bytesRequired = 1,
            decoder = { bytes -> bytes[0] * 100.0 / 255.0 },
            formatter = { String.format(Locale.US, "%.1f", it) }
        ),
        PidDefinition(
            pidHex = "33",
            name = "Barometric Pressure",
            thaiName = "ความกดอากาศบรรยากาศ",
            unit = "kPa",
            bytesRequired = 1,
            decoder = { bytes -> bytes[0].toDouble() },
            formatter = { String.format(Locale.US, "%.0f", it) }
        ),
        PidDefinition(
            pidHex = "42",
            name = "Control Module Voltage",
            thaiName = "แรงดันไฟกล่อง ECU",
            unit = "V",
            bytesRequired = 2,
            decoder = { bytes -> ((bytes[0] * 256) + bytes[1]) / 1000.0 },
            formatter = { String.format(Locale.US, "%.2f", it) }
        ),
        PidDefinition(
            pidHex = "5C",
            name = "Engine Oil Temp",
            thaiName = "อุณหภูมิน้ำมันเครื่อง",
            unit = "°C",
            bytesRequired = 1,
            decoder = { bytes -> (bytes[0] - 40).toDouble() },
            formatter = { String.format(Locale.US, "%.0f", it) }
        )
    ).associateBy { it.pidHex }

    fun decodeTelemetryPoint(pidHex: String, response: ElmResponse): TelemetryPoint? {
        val def = PID_DEFINITIONS[pidHex] ?: return null

        if (!response.isSuccess) {
            return TelemetryPoint(
                id = "PID_$pidHex",
                name = def.name,
                thaiName = def.thaiName,
                value = 0.0,
                displayValue = "N/A",
                unit = def.unit,
                quality = response.quality,
                provenance = response.provenance,
                sourcePid = pidHex,
                sourceEcuId = response.ecuHeader,
                latencyMs = response.latencyMs,
                rawHexResponse = response.rawResponse
            )
        }

        // Service 01 positive response bytes should start after '41 [PID]'
        val bytes = response.dataBytes
        // Look for pattern: ... 41 [pidByte] [data...]
        val pidInt = pidHex.toIntOrNull(16) ?: return null
        var dataStartIndex = -1

        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == 0x41 && bytes[i + 1] == pidInt) {
                dataStartIndex = i + 2
                break
            }
        }

        if (dataStartIndex == -1 || dataStartIndex + def.bytesRequired > bytes.size) {
            return TelemetryPoint(
                id = "PID_$pidHex",
                name = def.name,
                thaiName = def.thaiName,
                value = 0.0,
                displayValue = "ERR",
                unit = def.unit,
                quality = DataQuality.INVALID,
                provenance = response.provenance,
                sourcePid = pidHex,
                sourceEcuId = response.ecuHeader,
                latencyMs = response.latencyMs,
                rawHexResponse = response.rawResponse
            )
        }

        val dataSublist = bytes.subList(dataStartIndex, dataStartIndex + def.bytesRequired)
        val computedVal = def.decoder(dataSublist)

        return TelemetryPoint(
            id = "PID_$pidHex",
            name = def.name,
            thaiName = def.thaiName,
            value = computedVal,
            displayValue = def.formatter(computedVal),
            unit = def.unit,
            timestamp = System.currentTimeMillis(),
            quality = DataQuality.VALID,
            provenance = response.provenance,
            sourcePid = pidHex,
            sourceEcuId = response.ecuHeader,
            latencyMs = response.latencyMs,
            rawHexResponse = response.cleanPayload,
            evidenceRefId = "EVD_${System.currentTimeMillis()}_$pidHex"
        )
    }

    /**
     * Decodes SAE J1979 Service 01 supported PIDs bitmask responses (PIDs 00, 20, 40, 60, ...).
     * Expects response frame containing [0x41, basePid - 1, A, B, C, D].
     */
    fun decodeSupportedPidsBitmask(response: ElmResponse, startPid: Int): Set<String> {
        if (!response.isSuccess) return emptySet()
        val bytes = response.dataBytes
        val queryPid = (startPid - 1).coerceAtLeast(0)
        var startIdx = -1

        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == 0x41 && bytes[i + 1] == queryPid) {
                startIdx = i + 2
                break
            }
        }
        // Fallback if queryPid wasn't matched strictly but 0x41 is present
        if (startIdx == -1) {
            for (i in 0 until bytes.size - 1) {
                if (bytes[i] == 0x41) {
                    startIdx = i + 2
                    break
                }
            }
        }

        if (startIdx == -1 || startIdx + 4 > bytes.size) return emptySet()

        val supported = linkedSetOf<String>()
        val bitmaskBytes = bytes.subList(startIdx, startIdx + 4)
        var currentPid = startPid
        for (byte in bitmaskBytes) {
            for (bit in 7 downTo 0) {
                if ((byte and (1 shl bit)) != 0) {
                    supported.add(String.format(Locale.US, "%02X", currentPid))
                }
                currentPid++
            }
        }
        return supported
    }
}
