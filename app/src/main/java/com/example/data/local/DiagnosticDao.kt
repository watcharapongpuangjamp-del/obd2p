package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticDao {

    // Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DiagnosticSessionEntity)

    @Query("SELECT * FROM diagnostic_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<DiagnosticSessionEntity>>

    @Query("SELECT * FROM diagnostic_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): DiagnosticSessionEntity?

    @Query("DELETE FROM diagnostic_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    // Evidence Logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidenceBatch(evidenceList: List<EvidenceLogEntity>)

    @Query("SELECT * FROM evidence_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getEvidenceForSession(sessionId: String): Flow<List<EvidenceLogEntity>>

    @Query("SELECT * FROM evidence_logs ORDER BY timestamp DESC LIMIT 200")
    fun getLatestEvidence(): Flow<List<EvidenceLogEntity>>

    @Query("SELECT COUNT(*) FROM evidence_logs WHERE sessionId = :sessionId")
    suspend fun getEvidenceCount(sessionId: String): Int

    // DTC Records
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDtc(dtc: DtcRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDtcBatch(dtcList: List<DtcRecordEntity>)

    @Query("SELECT * FROM dtc_records WHERE sessionId = :sessionId")
    fun getDtcsForSession(sessionId: String): Flow<List<DtcRecordEntity>>

    @Query("DELETE FROM dtc_records WHERE sessionId = :sessionId")
    suspend fun clearDtcsForSession(sessionId: String)
}
