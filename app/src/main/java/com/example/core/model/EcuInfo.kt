package com.example.core.model

data class EcuInfo(
    val ecuId: String,
    val requestHeader: String,
    val responseHeader: String,
    val moduleName: String,
    val thaiModuleName: String,
    val protocol: String,
    val confidence: Double,
    val supportedPids: Set<String> = emptySet(),
    val evidenceRefs: List<String> = emptyList(),
    val isResponding: Boolean = true
)
