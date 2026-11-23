package com.example.core.discovery

import com.example.core.elm327.ElmResponseType
import com.example.core.elm327.IElm327Driver
import com.example.core.model.EcuInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DynamicEcuDiscovery : IEcuDiscovery {

    private val _discoveredEcus = MutableStateFlow<List<EcuInfo>>(emptyList())
    override val discoveredEcus: StateFlow<List<EcuInfo>> = _discoveredEcus.asStateFlow()

    override suspend fun discoverEcus(driver: IElm327Driver): List<EcuInfo> {
        // Never retain a previous scan when the current adapter/ECU query fails.
        _discoveredEcus.value = emptyList()

        if (!driver.isInitialized.value) return emptyList()

        val response = driver.queryPid("01", "00")
        if (response.type != ElmResponseType.VALID_DATA ||
            response.ecuHeader.isBlank() ||
            !hasServicePid(response.dataBytes, 0x41, 0x00)
        ) {
            return emptyList()
        }

        val supported = parseSupportedPids(response.dataBytes, 0x00)
        val header = response.ecuHeader.uppercase()
        val primaryEcu = EcuInfo(
            ecuId = header,
            requestHeader = "7DF",
            responseHeader = header,
            moduleName = getModuleNameForHeader(header),
            thaiModuleName = getThaiModuleNameForHeader(header),
            protocol = driver.detectedProtocol.value,
            confidence = if (supported.isNotEmpty()) 1.0 else 0.75,
            supportedPids = supported,
            evidenceRefs = listOf("EVD_ECU_DISCOVERY_${header}_${response.rawTxRx?.timestamp ?: System.currentTimeMillis()}"),
            isResponding = true
        )

        // PID 20 reports additional PID support for the responding ECU.
        // Merge only a valid response from the same ECU; do not infer another ECU.
        val response20 = driver.queryPid("01", "20")
        val mergedEcu = if (
            response20.type == ElmResponseType.VALID_DATA &&
            response20.ecuHeader.equals(header, ignoreCase = true) &&
            hasServicePid(response20.dataBytes, 0x41, 0x20)
        ) {
            primaryEcu.copy(
                supportedPids = primaryEcu.supportedPids + parseSupportedPids(response20.dataBytes, 0x20),
                evidenceRefs = primaryEcu.evidenceRefs + "EVD_ECU_PID20_${header}_${response20.rawTxRx?.timestamp ?: System.currentTimeMillis()}"
            )
        } else {
            primaryEcu
        }

        _discoveredEcus.value = listOf(mergedEcu)
        return _discoveredEcus.value
    }

    private fun hasServicePid(bytes: List<Int>, service: Int, pid: Int): Boolean =
        bytes.windowed(2).any { it[0] == service && it[1] == pid }

    /**
     * Parse the four-byte support bitmap following 41 <base PID>.
     * An absent or malformed bitmap means "unknown", never a guessed PID list.
     */
    private fun parseSupportedPids(bytes: List<Int>, basePid: Int): Set<String> {
        val marker = bytes.indices.firstOrNull { index ->
            index + 5 <= bytes.size &&
                bytes[index] == 0x41 &&
                bytes[index + 1] == basePid
        } ?: return emptySet()

        val bitmap = bytes.subList(marker + 2, marker + 6)
        val supported = linkedSetOf<String>()
        var pid = basePid + 1
        for (byte in bitmap) {
            for (bit in 7 downTo 0) {
                if ((byte and (1 shl bit)) != 0) {
                    supported.add("%02X".format(pid))
                }
                pid++
            }
        }
        return supported
    }

    private fun getModuleNameForHeader(header: String): String =
        when (header.uppercase()) {
            "7E8" -> "Engine Control Module (ECM / PCM)"
            "7E9" -> "ECU responding at 0x7E9 (module identity unverified)"
            "7EA" -> "ECU responding at 0x7EA (module identity unverified)"
            "7EB" -> "ECU responding at 0x7EB (module identity unverified)"
            else -> "Electronic Control Unit ($header)"
        }

    private fun getThaiModuleNameForHeader(header: String): String =
        when (header.uppercase()) {
            "7E8" -> "กล่องควบคุมเครื่องยนต์ (ECM/PCM)"
            "7E9" -> "กล่อง ECU ที่ตอบกลับหมายเลข 7E9 (ยังไม่ยืนยันชนิด)"
            "7EA" -> "กล่อง ECU ที่ตอบกลับหมายเลข 7EA (ยังไม่ยืนยันชนิด)"
            "7EB" -> "กล่อง ECU ที่ตอบกลับหมายเลข 7EB (ยังไม่ยืนยันชนิด)"
            else -> "กล่องควบคุมอิเล็กทรอนิกส์ ($header)"
        }
}
