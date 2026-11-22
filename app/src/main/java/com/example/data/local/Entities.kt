package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_sessions")
data class DiagnosticSessionEntity(
    @PrimaryKey val sessionId: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val vin: String,
    val protocol: String,
    val adapterName: String,
    val provenance: String,
    val startTime: Long,
    val endTime: Long = 0L,
    val totalFrames: Int = 0,
    val faultCount: Int = 0,
    val status: String = "ACTIVE"
)

@Entity(tableName = "evidence_logs")
data class EvidenceLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val direction: String,
    val commandTx: String,
    val responseRx: String,
    val latencyMs: Long,
    val quality: String,
    val provenance: String,
    val ecuHeader: String,
    val sha256Hash: String
)

@Entity(tableName = "dtc_records")
data class DtcRecordEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val code: String,
    val type: String,
    val system: String,
    val thaiTitle: String,
    val englishDescription: String,
    val severity: String,
    val rawHex: String,
    val evidenceRef: String,
    val timestamp: Long
)
