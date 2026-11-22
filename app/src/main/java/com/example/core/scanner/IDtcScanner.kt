package com.example.core.scanner

import com.example.core.elm327.ElmResponse
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

    private val _scanResult = MutableStateFlow(DtcScanResult())
    override val scanResult: StateFlow<DtcScanResult> = _scanResult.asStateFlow()

    override suspend fun performFullScan(driver: IElm327Driver): DtcScanResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        _scanResult.value = DtcScanResult(status = ScanStatus.SCANNING)

        val evidenceRefs = mutableListOf<String>()
        val scanEvidenceLogs = mutableListOf<EvidenceLog>()
        val modulesScanned = mutableListOf("7E8 (ECM)", "7E9 (TCM)")
        val modulesFailed = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 1. Service 03 - Confirmed DTCs
        val resp03 = driver.queryPid("03", "")
        val ev03Id = "EVD_DTC_03_${System.currentTimeMillis()}"
        evidenceRefs.add(ev03Id)
        val confirmedCodes = DtcDecoder.decodeDtcResponse(resp03, DtcType.CONFIRMED, ev03Id)
        scanEvidenceLogs.add(buildScanEvidenceLog(ev03Id, "03", DtcType.CONFIRMED, resp03, confirmedCodes))

        delay(100)

        // 2. Service 07 - Pending DTCs
        val resp07 = driver.queryPid("07", "")
        val ev07Id = "EVD_DTC_07_${System.currentTimeMillis()}"
        evidenceRefs.add(ev07Id)
        val pendingCodes = DtcDecoder.decodeDtcResponse(resp07, DtcType.PENDING, ev07Id)
        scanEvidenceLogs.add(buildScanEvidenceLog(ev07Id, "07", DtcType.PENDING, resp07, pendingCodes))

        delay(100)

        // 3. Service 0A - Permanent DTCs
        val resp0A = driver.queryPid("0A", "")
        val ev0AId = "EVD_DTC_0A_${System.currentTimeMillis()}"
        evidenceRefs.add(ev0AId)
        val permanentCodes = DtcDecoder.decodeDtcResponse(resp0A, DtcType.PERMANENT, ev0AId)
        scanEvidenceLogs.add(buildScanEvidenceLog(ev0AId, "0A", DtcType.PERMANENT, resp0A, permanentCodes))

        val totalDuration = System.currentTimeMillis() - start
        val result = DtcScanResult(
            status = ScanStatus.COMPLETED,
            confirmedCodes = confirmedCodes,
            pendingCodes = pendingCodes,
            permanentCodes = permanentCodes,
            modulesScanned = modulesScanned,
            modulesFailed = modulesFailed,
            errors = errors,
            evidenceRefs = evidenceRefs,
            scanEvidenceLogs = scanEvidenceLogs,
            scanDurationMs = totalDuration,
            timestamp = System.currentTimeMillis()
        )

        _scanResult.value = result
        return@withContext result
    }

    override suspend fun clearCodes(driver: IElm327Driver): DtcClearResult = withContext(Dispatchers.IO) {
        val evidenceId = "EVD_CLEAR_04_${System.currentTimeMillis()}_${(100..999).random()}"
        val clearResp = driver.clearTroubleCodes()
        val result = DtcDecoder.decodeClearDtcResponse(clearResp, evidenceId)

        if (result.isSuccess) {
            _scanResult.value = DtcScanResult(
                status = ScanStatus.COMPLETED,
                confirmedCodes = emptyList(),
                pendingCodes = emptyList(),
                permanentCodes = _scanResult.value.permanentCodes, // Permanent codes are retained until drive cycle confirms fix
                modulesScanned = _scanResult.value.modulesScanned,
                evidenceRefs = _scanResult.value.evidenceRefs + listOf(evidenceId),
                scanEvidenceLogs = _scanResult.value.scanEvidenceLogs,
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
        val ts = System.currentTimeMillis()
        val rawHex = response.dataBytes.joinToString(" ") { String.format("%02X", it) }
        val codeSummary = if (decoded.isEmpty()) "0 codes" else decoded.joinToString(", ") { it.code }
        val hash = sha256("$serviceCmd:${response.rawResponse}:$ts")
        return EvidenceLog(
            id = id,
            sessionId = "",
            timestamp = ts,
            direction = EvidenceDirection.RX,
            rawBytesHex = rawHex.ifBlank { response.cleanPayload },
            commandText = serviceCmd,
            responseText = response.rawResponse.ifBlank { response.cleanPayload },
            ecuHeader = response.ecuHeader.ifBlank { "7E8" },
            latencyMs = response.latencyMs,
            quality = response.quality,
            provenance = response.provenance,
            parsedMeaning = "Service $serviceCmd (${type.name}): $codeSummary",
            sha256Hash = hash
        )
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.fold("") { acc, b -> acc + "%02x".format(b) }
    }
}
