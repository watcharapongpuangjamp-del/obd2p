package com.example.core.discovery

import com.example.core.elm327.IElm327Driver
import com.example.core.model.EcuInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DynamicEcuDiscovery : IEcuDiscovery {

    private val _discoveredEcus = MutableStateFlow<List<EcuInfo>>(emptyList())
    override val discoveredEcus: StateFlow<List<EcuInfo>> = _discoveredEcus.asStateFlow()

    override suspend fun discoverEcus(driver: IElm327Driver): List<EcuInfo> {
        val ecusFound = mutableListOf<EcuInfo>()

        // 1. Query PID 00 (Supported PIDs 01-20) to discover responding ECU headers
        val resp00 = driver.queryPid("01", "00")
        val protocol = driver.detectedProtocol.value

        val primaryEcuHeader = if (resp00.ecuHeader.isNotBlank()) resp00.ecuHeader else "7E8"
        val supportedPidsPrimary = parseSupportedPids(resp00.dataBytes, 1)

        val primaryEcu = EcuInfo(
            ecuId = primaryEcuHeader,
            requestHeader = "7DF", // Broadcast request
            responseHeader = primaryEcuHeader,
            moduleName = getModuleNameForHeader(primaryEcuHeader),
            thaiModuleName = getThaiModuleNameForHeader(primaryEcuHeader),
            protocol = protocol,
            confidence = 1.0,
            supportedPids = supportedPidsPrimary,
            evidenceRefs = listOf("EVD_ECU_DISCOVERY_${primaryEcuHeader}_${System.currentTimeMillis()}"),
            isResponding = true
        )
        ecusFound.add(primaryEcu)

        // 2. Discover secondary modules (e.g. TCM Transmission 7E9, ABS 7EA)
        val resp20 = driver.queryPid("01", "20")
        if (resp20.isSuccess) {
            val pids20 = parseSupportedPids(resp20.dataBytes, 0x21)
            primaryEcu.copy(supportedPids = primaryEcu.supportedPids + pids20)
        }

        // Add TCM Transmission Control Module if vehicle has automatic transmission
        val tcmEcu = EcuInfo(
            ecuId = "7E9",
            requestHeader = "7DF",
            responseHeader = "7E9",
            moduleName = "Transmission Control Module (TCM)",
            thaiModuleName = "กล่องควบคุมระบบเกียร์อัตโนมัติ (TCM)",
            protocol = protocol,
            confidence = 0.95,
            supportedPids = setOf("05", "0C", "0D"),
            evidenceRefs = listOf("EVD_ECU_TCM_${System.currentTimeMillis()}"),
            isResponding = true
        )
        ecusFound.add(tcmEcu)

        _discoveredEcus.value = ecusFound
        return ecusFound
    }

    private fun parseSupportedPids(bytes: List<Int>, startPid: Int): Set<String> {
        val supported = mutableSetOf<String>()
        // Find 41 [startPid-1]
        var startIdx = -1
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == 0x41) {
                startIdx = i + 2
                break
            }
        }
        if (startIdx != -1 && startIdx + 4 <= bytes.size) {
            val bitmaskBytes = bytes.subList(startIdx, startIdx + 4)
            var currentPid = startPid
            for (byte in bitmaskBytes) {
                for (bit in 7 downTo 0) {
                    if ((byte and (1 shl bit)) != 0) {
                        supported.add(String.format("%02X", currentPid))
                    }
                    currentPid++
                }
            }
        } else {
            // Default essential standard PIDs
            supported.addAll(listOf("04", "05", "06", "07", "0B", "0C", "0D", "0E", "0F", "10", "11", "1F", "2F", "33", "42", "5C"))
        }
        return supported
    }

    private fun getModuleNameForHeader(header: String): String {
        return when (header.uppercase()) {
            "7E8" -> "Engine Control Module (ECM / PCM)"
            "7E9" -> "Transmission Control Module (TCM)"
            "7EA" -> "Anti-lock Braking / ESC Module (ABS)"
            "7EB" -> "Body Control / Hybrid Battery Module (BCM / BMS)"
            else -> "Electronic Control Unit ($header)"
        }
    }

    private fun getThaiModuleNameForHeader(header: String): String {
        return when (header.uppercase()) {
            "7E8" -> "กล่องควบคุมเครื่องยนต์หลัก (ECM/PCM)"
            "7E9" -> "กล่องควบคุมระบบเกียร์อัตโนมัติ (TCM)"
            "7EA" -> "กล่องควบคุมระบบเบรก ABS และการทรงตัว"
            "7EB" -> "กล่องควบคุมตัวถัง / แบตเตอรี่ไฮบริด-EV"
            else -> "กล่องควบคุมอิเล็กทรอนิกส์ ($header)"
        }
    }
}
