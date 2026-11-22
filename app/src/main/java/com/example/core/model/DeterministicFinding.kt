package com.example.core.model

import com.example.core.model.DtcSeverity

enum class FindingSeverity {
    CRITICAL,
    WARNING,
    ADVISORY,
    NORMAL
}

data class DeterministicFinding(
    val id: String,
    val ruleId: String,
    val ruleNameThai: String,
    val ruleNameEnglish: String,
    val severity: FindingSeverity,
    val descriptionThai: String,
    val descriptionEnglish: String,
    val recommendedActionThai: String,
    val evidenceRefs: List<String>,
    val triggerValue: String,
    val thresholdValue: String,
    val timestamp: Long = System.currentTimeMillis()
)
