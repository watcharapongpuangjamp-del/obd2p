package com.example.core.scanner

import com.example.core.elm327.IElm327Driver
import com.example.core.model.DtcClearResult
import com.example.core.model.DtcScanResult
import com.example.core.model.DtcType
import com.example.core.model.ScanStatus
import com.example.core.protocol.DtcDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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
        val modulesScanned = mutableListOf("7E8 (ECM)", "7E9 (TCM)")
        val modulesFailed = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 1. Service 03 - Confirmed DTCs
        val resp03 = driver.queryPid("03", "")
        evidenceRefs.add("EVD_DTC_03_${System.currentTimeMillis()}")
        val confirmedCodes = DtcDecoder.decodeDtcResponse(resp03, DtcType.CONFIRMED)

        delay(100)

        // 2. Service 07 - Pending DTCs
        val resp07 = driver.queryPid("07", "")
        evidenceRefs.add("EVD_DTC_07_${System.currentTimeMillis()}")
        val pendingCodes = DtcDecoder.decodeDtcResponse(resp07, DtcType.PENDING)

        delay(100)

        // 3. Service 0A - Permanent DTCs
        val resp0A = driver.queryPid("0A", "")
        evidenceRefs.add("EVD_DTC_0A_${System.currentTimeMillis()}")
        val permanentCodes = DtcDecoder.decodeDtcResponse(resp0A, DtcType.PERMANENT)

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
                timestamp = System.currentTimeMillis()
            )
        }
        return@withContext result
    }
}
