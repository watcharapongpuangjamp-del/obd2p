package com.example.data.local

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.DtcCode
import com.example.core.model.EvidenceDirection
import com.example.core.model.EvidenceLog
import com.example.core.model.VehicleProfile
import com.example.core.transport.RawTxRxResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class EvidenceRepository(private val dao: DiagnosticDao) {

    val allSessions: Flow<List<DiagnosticSessionEntity>> = dao.getAllSessions()
    val recentEvidence: Flow<List<EvidenceLog>> = dao.getLatestEvidence().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun createSession(profile: VehicleProfile, adapterName: String, provenance: DataProvenance): String {
        val sessionId = "SESSION_${System.currentTimeMillis()}"
        val entity = DiagnosticSessionEntity(
            sessionId = sessionId,
            vehicleMake = profile.make,
            vehicleModel = profile.model,
            vin = profile.vin,
            protocol = profile.protocol,
            adapterName = adapterName,
            provenance = provenance.name,
            startTime = System.currentTimeMillis()
        )
        dao.insertSession(entity)
        return sessionId
    }

    suspend fun recordTxRxEvidence(sessionId: String, result: RawTxRxResult, ecuHeader: String = "7E8") {
        val hash = calculateSha256("${result.commandTx}:${result.responseRx}:${result.timestamp}")
        val entity = EvidenceLogEntity(
            id = "EVD_${System.currentTimeMillis()}_${(1000..9999).random()}",
            sessionId = sessionId,
            timestamp = result.timestamp,
            direction = if (result.commandTx.isNotBlank()) "TX_RX" else "RX",
            commandTx = result.commandTx,
            responseRx = result.responseRx,
            latencyMs = result.latencyMs,
            quality = result.quality.name,
            provenance = result.provenance.name,
            ecuHeader = ecuHeader,
            sha256Hash = hash
        )
        dao.insertEvidence(entity)
    }

    suspend fun recordDtcList(sessionId: String, dtcList: List<DtcCode>) {
        val entities = dtcList.map { dtc ->
            DtcRecordEntity(
                id = "DTC_REC_${System.currentTimeMillis()}_${dtc.code}",
                sessionId = sessionId,
                code = dtc.code,
                type = dtc.type.name,
                system = dtc.system,
                thaiTitle = dtc.thaiTitle,
                englishDescription = dtc.englishDescription,
                severity = dtc.severity.name,
                rawHex = dtc.rawHex,
                evidenceRef = dtc.evidenceRef,
                timestamp = System.currentTimeMillis()
            )
        }
        dao.insertDtcBatch(entities)
    }

    suspend fun clearDtcsForSession(sessionId: String) {
        dao.clearDtcsForSession(sessionId)
    }

    suspend fun recordClearDtcEvidence(
        sessionId: String,
        evidenceId: String,
        commandTx: String,
        responseRx: String,
        latencyMs: Long,
        provenance: DataProvenance,
        quality: DataQuality,
        ecuHeader: String = "7E8",
        timestamp: Long = System.currentTimeMillis()
    ): EvidenceLogEntity {
        val hash = calculateSha256("$commandTx:$responseRx:$timestamp")
        val entity = EvidenceLogEntity(
            id = evidenceId,
            sessionId = sessionId,
            timestamp = timestamp,
            direction = "TX_RX",
            commandTx = commandTx,
            responseRx = responseRx,
            latencyMs = latencyMs,
            quality = quality.name,
            provenance = provenance.name,
            ecuHeader = ecuHeader,
            sha256Hash = hash
        )
        dao.insertEvidence(entity)
        return entity
    }

    private fun calculateSha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun EvidenceLogEntity.toDomain(): EvidenceLog {
        return EvidenceLog(
            id = id,
            sessionId = sessionId,
            timestamp = timestamp,
            direction = if (commandTx.isNotBlank()) EvidenceDirection.TX else EvidenceDirection.RX,
            rawBytesHex = responseRx,
            commandText = commandTx,
            responseText = responseRx,
            ecuHeader = ecuHeader,
            latencyMs = latencyMs,
            quality = try { DataQuality.valueOf(quality) } catch (_: Exception) { DataQuality.VALID },
            provenance = try { DataProvenance.valueOf(provenance) } catch (_: Exception) { DataProvenance.REAL_HARDWARE },
            parsedMeaning = "Parsed ECU Response",
            sha256Hash = sha256Hash
        )
    }
}
