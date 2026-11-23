package com.example.core.scanner

import com.example.core.elm327.ElmResponse
import com.example.core.elm327.ElmResponseType
import com.example.core.elm327.IElm327Driver
import com.example.core.model.DtcClearResult
import com.example.core.model.DtcCode
import com.example.core.model.DtcScanResult
import com.example.core.model.DtcType
import com.example.core.model.EvidenceDirection
import com.example.core.model.EvidenceLog
import com.example.core.model.ScanStatus
import com.example.core.protocol.DtcDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest

interface IDtcScanner {
    val scanResult: StateFlow<DtcScanResult>
    suspend fun performFullScan(driver: IElm327Driver): DtcScanResult
    suspend fun clearCodes(driver: IElm327Driver): DtcClearResult
}

class DtcScanner : IDtcScanner {

    private data class ServiceScan(
        val requestService: String,
        val responseService: Int,
        val type: DtcType,
        val label: String
    )

    private val services = listOf(
        ServiceScan("03", 0x43, DtcType.CONFIRMED, "Confirmed DTCs"),
        ServiceScan("07", 0x47, DtcType.PENDING, "Pending DTCs"),
        ServiceScan("0A", 0x4A, DtcType.PERMANENT, "Permanent DTCs")
    )

    private val _scanResult = MutableStateFlow(DtcScanResult())
    override val scanResult: StateFlow<DtcScanResult> = _scanResult.asStateFlow()

    override suspend fun performFullScan(driver: IElm327Driver): DtcScanResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (!driver.isInitialized.value ||
            driver.getProvenance() == com.example.core.model.DataProvenance.UNKNOWN
        ) {
            val failed = DtcScanResult(
                status = ScanStatus.FAILED,
                errors = listOf("DTC scan blocked: ELM327 adapter is not initialized or transport provenance is unknown."),
                timestamp = System.currentTimeMillis()
            )
            _scanResult.value = failed
            return@withContext failed
        }

        _scanResult.value = DtcScanResult(status = ScanStatus.SCANNING)
        val evidenceRefs = mutableListOf<String>()
        val evidenceLogs = mutableListOf<EvidenceLog>()
        val modulesScanned = linkedSetOf<String>()
        val modulesFailed = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val codesByType = mutableMapOf<DtcType, List<DtcCode>>()
        var successfulServices = 0

        for ((index, service) in services.withIndex()) {
            val response = driver.queryPid(service.requestService, "")
            val responseIsValid = response.type == ElmResponseType.VALID_DATA &&
                response.dataBytes.contains(service.responseService)

            val timestamp = response.rawTxRx?.timestamp ?: System.currentTimeMillis()
            val evidenceId = "EVD_DTC_${service.requestService}_$timestamp"
            evidenceRefs.add(evidenceId)

            val decoded = if (responseIsValid) {
                successfulServices++
                response.ecuHeader.takeIf { it.isNotBlank() }?.let { modulesScanned.add(it.uppercase()) }
                DtcDecoder.decodeDtcResponse(response, service.type, evidenceId)
            } else {
                val detail = response.errorMessage ?: response.cleanPayload.ifBlank { response.type.name }
                val failure = "Service ${service.requestService} (${service.label}) was not verified: $detail"
                modulesFailed.add("Service ${service.requestService}")
                errors.add(failure)
                emptyList()
            }
            codesByType[service.type] = decoded
            evidenceLogs.add(buildScanEvidenceLog(evidenceId, service.requestService, service.type, response, decoded))
            if (index < services.lastIndex) delay(100)
        }

        val result = DtcScanResult(
            status = if (successfulServices == services.size) ScanStatus.COMPLETED else ScanStatus.FAILED,
            confirmedCodes = codesByType[DtcType.CONFIRMED].orEmpty(),
            pendingCodes = codesByType[DtcType.PENDING].orEmpty(),
            permanentCodes = codesByType[DtcType.PERMANENT].orEmpty(),
            modulesScanned = modulesScanned.map { "$it (ECU identity unverified)" },
            modulesFailed = modulesFailed,
            errors = errors,
            evidenceRefs = evidenceRefs,
            scanEvidenceLogs = evidenceLogs,
            scanDurationMs = System.currentTimeMillis() - start,
            timestamp = System.currentTimeMillis()
        )

        _scanResult.value = result
        return@withContext result
    }

    override suspend fun clearCodes(driver: IElm327Driver): DtcClearResult = withContext(Dispatchers.IO) {
        val evidenceId = "EVD_CLEAR_04_${System.currentTimeMillis()}_${(100..999).random()}"
        if (!driver.isInitialized.value ||
            driver.getProvenance() == com.example.core.model.DataProvenance.UNKNOWN
        ) {
            return@withContext DtcClearResult(
                status = com.example.core.model.DtcClearStatus.COMMUNICATION_ERROR,
                commandTx = "04",
                cleanPayload = "NOT CONNECTED",
                errorMessageThai = "ไม่สามารถส่งคำสั่งล้างรหัสได้ เนื่องจากอุปกรณ์ยังไม่พร้อมหรือไม่ทราบแหล่งข้อมูล",
                errorMessageEnglish = "Clear DTC blocked: adapter is not initialized or transport provenance is unknown.",
                evidenceRef = evidenceId,
                provenance = com.example.core.model.DataProvenance.UNKNOWN,
                quality = com.example.core.model.DataQuality.UNAVAILABLE
            )
        }

        val clearResp = driver.clearTroubleCodes()
        val result = DtcDecoder.decodeClearDtcResponse(clearResp, evidenceId)

        if (result.isSuccess) {
            _scanResult.value = _scanResult.value.copy(
                // Service 04 does not erase Permanent DTCs. Do not claim the whole
                // DTC scan is current; a fresh scan is required to verify the ECU.
                confirmedCodes = emptyList(),
                pendingCodes = emptyList(),
                permanentCodes = _scanResult.value.permanentCodes,
                evidenceRefs = _scanResult.value.evidenceRefs + evidenceId,
                timestamp = System.currentTimeMillis()
            )
        }
        return@withContext result
    }

    private fun buildScanEvidenceLog(
        id: String,
        serviceCmd: String,
        type: DtcType,
        response: ElmResponse,
        decoded: List<DtcCode>
    ): EvidenceLog {
        val rawResponseBytes = response.rawTxRx?.rawBytes
            ?.takeIf { it.isNotEmpty() }
            ?: response.rawResponse.toByteArray(Charsets.US_ASCII)
        val ts = response.rawTxRx?.timestamp ?: System.currentTimeMillis()
        val rawHex = rawResponseBytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        val parsedHex = response.dataBytes.joinToString(" ") { "%02X".format(it) }
        val codeSummary = when {
            response.type != ElmResponseType.VALID_DATA -> "response not verified"
            decoded.isEmpty() -> "valid response; no DTCs decoded"
            else -> decoded.joinToString(", ") { it.code }
        }

        return EvidenceLog(
            id = id,
            sessionId = "",
            timestamp = ts,
            direction = EvidenceDirection.RX,
            rawBytesHex = rawHex.ifBlank { parsedHex },
            commandText = serviceCmd,
            responseText = response.rawResponse.ifBlank { response.cleanPayload },
            ecuHeader = response.ecuHeader,
            latencyMs = response.latencyMs,
            quality = response.quality,
            provenance = response.provenance,
            parsedMeaning = "Service $serviceCmd (${type.name}): $codeSummary",
            sha256Hash = sha256(rawResponseBytes)
        )
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
