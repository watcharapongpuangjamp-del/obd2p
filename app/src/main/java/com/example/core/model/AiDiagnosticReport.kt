package com.example.core.model

data class AiDiagnosticReport(
    val summaryThai: String,
    val summaryEnglish: String,
    val rootCauseAnalysisThai: String,
    val rootCauseAnalysisEnglish: String,
    val severity: DtcSeverity,
    val confidencePercentage: Int,
    val actionPlanThai: List<String>,
    val actionPlanEnglish: List<String>,
    val estimatedRepairCostThb: String,
    val safetyWarningThai: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val evidenceRefs: List<String> = emptyList(),
    val isAiGenerated: Boolean = true
)
