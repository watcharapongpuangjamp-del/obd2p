package com.example.core.elm327

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.transport.ITransport
import com.example.core.transport.RawTxRxResult
import com.example.core.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Elm327Driver(
    private val transport: ITransport
) : IElm327Driver {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _adapterVersion = MutableStateFlow("Unknown")
    override val adapterVersion: StateFlow<String> = _adapterVersion.asStateFlow()

    private val _detectedProtocol = MutableStateFlow("Auto")
    override val detectedProtocol: StateFlow<String> = _detectedProtocol.asStateFlow()

    private val _batteryVoltage = MutableStateFlow("0.0V")
    override val batteryVoltage: StateFlow<String> = _batteryVoltage.asStateFlow()

    private val ioMutex = Mutex()

    override fun getProvenance(): DataProvenance {
        val state = transport.state.value
        return when (state) {
            is TransportState.Connected -> state.provenance
            else -> DataProvenance.UNKNOWN
        }
    }

    override suspend fun initializeAdapter(): Boolean {
        _isInitialized.value = false
        if (!transport.isConnected() && !transport.connect()) return false
        if (transport.state.value !is TransportState.Connected) return false

        val resetResp = sendAtCommand("ATZ")
        if (resetResp.type in setOf(
                ElmResponseType.TIMEOUT,
                ElmResponseType.UNKNOWN_ERROR,
                ElmResponseType.UNABLE_TO_CONNECT,
                ElmResponseType.BUS_INIT_ERROR
            )
        ) return false

        if (resetResp.cleanPayload.isNotBlank() &&
            !resetResp.cleanPayload.equals("NO DATA", ignoreCase = true)
        ) {
            _adapterVersion.value = resetResp.cleanPayload
        }

        // Configuration is accepted only when the adapter explicitly answers OK.
        val requiredCommands = listOf("ATE0", "ATL0", "ATS1", "ATH1", "ATSP0")
        for (command in requiredCommands) {
            val response = sendAtCommand(command)
            if (response.type != ElmResponseType.OK_RESPONSE ||
                !response.cleanPayload.equals("OK", ignoreCase = true)
            ) {
                return false
            }
        }

        val protocolResponse = sendAtCommand("ATDP")
        if (protocolResponse.type != ElmResponseType.OK_RESPONSE ||
            protocolResponse.cleanPayload.isBlank() ||
            protocolResponse.cleanPayload.equals("?", ignoreCase = true)
        ) {
            return false
        }
        _detectedProtocol.value = protocolResponse.cleanPayload.removePrefix("AUTO,").trim()

        val voltageResponse = readBatteryVoltage()
        if (voltageResponse.type == ElmResponseType.VALID_DATA ||
            voltageResponse.cleanPayload.contains("V", ignoreCase = true)
        ) {
            _batteryVoltage.value = voltageResponse.cleanPayload
        }

        _isInitialized.value = true
        return true
    }

    override suspend fun sendAtCommand(command: String): ElmResponse = ioMutex.withLock {
        val cleanCmd = command.trim().uppercase()
        val timeout = if (cleanCmd == "ATZ") 3000L else 2000L
        val txRx = transport.sendAndReceive(cleanCmd, timeout)
        return parseResponse(cleanCmd, txRx.responseRx, txRx.latencyMs, txRx.quality, txRx.provenance, txRx)
    }

    override suspend fun queryPid(service: String, pid: String): ElmResponse = ioMutex.withLock {
        if (!transport.isConnected() || !_isInitialized.value) {
            return@withLock ElmResponse(
                type = ElmResponseType.UNABLE_TO_CONNECT,
                rawResponse = "",
                cleanPayload = "NOT CONNECTED",
                quality = DataQuality.UNAVAILABLE,
                provenance = DataProvenance.UNKNOWN,
                errorMessage = "ELM327 adapter is not connected and initialized"
            )
        }
        val cmd = "${service.trim().uppercase()}${pid.trim().uppercase()}"
        val txRx = transport.sendAndReceive(cmd, 3500L)
        return parseResponse(cmd, txRx.responseRx, txRx.latencyMs, txRx.quality, txRx.provenance, txRx)
    }

    override suspend fun readBatteryVoltage(): ElmResponse = ioMutex.withLock {
        val txRx = transport.sendAndReceive("ATRV", 2000L)
        val resp = parseResponse("ATRV", txRx.responseRx, txRx.latencyMs, txRx.quality, txRx.provenance, txRx)
        if (resp.cleanPayload.contains("V", ignoreCase = true)) {
            _batteryVoltage.value = resp.cleanPayload
        }
        return resp
    }

    override suspend fun clearTroubleCodes(): ElmResponse = ioMutex.withLock {
        if (!transport.isConnected() || !_isInitialized.value) {
            return@withLock ElmResponse(
                type = ElmResponseType.UNABLE_TO_CONNECT,
                rawResponse = "",
                cleanPayload = "NOT CONNECTED",
                quality = DataQuality.UNAVAILABLE,
                provenance = DataProvenance.UNKNOWN,
                errorMessage = "ELM327 adapter is not connected and initialized"
            )
        }
        val txRx = transport.sendAndReceive("04", 4000L)
        return parseResponse("04", txRx.responseRx, txRx.latencyMs, txRx.quality, txRx.provenance, txRx)
    }

    internal fun parseResponse(
        cmd: String,
        raw: String,
        latencyMs: Long,
        quality: DataQuality,
        provenance: DataProvenance,
        rawTxRx: RawTxRxResult? = null
    ): ElmResponse {
        val lines = raw
            .replace("\r", "\n")
            .replace(">", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("SEARCHING...", ignoreCase = true) }

        val sanitized = lines.joinToString(" ").trim()

        if (rawTxRx?.isTimeout == true || quality == DataQuality.TIMEOUT) {
            return ElmResponse(
                type = ElmResponseType.TIMEOUT,
                rawResponse = raw,
                cleanPayload = sanitized.ifBlank { "TIMEOUT" },
                latencyMs = latencyMs,
                quality = DataQuality.TIMEOUT,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = "Timed out waiting for ELM327 prompt (>)"
            )
        }

        if (sanitized.startsWith("ERROR:", ignoreCase = true) || sanitized == "?") {
            return ElmResponse(
                type = ElmResponseType.UNKNOWN_ERROR,
                rawResponse = raw,
                cleanPayload = sanitized,
                latencyMs = latencyMs,
                quality = DataQuality.INVALID,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = sanitized.ifBlank { "Unknown ELM327 error" }
            )
        }

        if (sanitized.contains("NO DATA", ignoreCase = true)) {
            return ElmResponse(
                type = ElmResponseType.NO_DATA,
                rawResponse = raw,
                cleanPayload = "NO DATA",
                latencyMs = latencyMs,
                quality = DataQuality.UNAVAILABLE,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = "No response from vehicle ECU"
            )
        }
        if (sanitized.contains("UNABLE TO CONNECT", ignoreCase = true)) {
            return ElmResponse(
                type = ElmResponseType.UNABLE_TO_CONNECT,
                rawResponse = raw,
                cleanPayload = "UNABLE TO CONNECT",
                latencyMs = latencyMs,
                quality = DataQuality.UNAVAILABLE,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = "ELM327 unable to establish bus connection with ECU"
            )
        }
        if (sanitized.contains("BUS INIT: ERROR", ignoreCase = true) || sanitized.contains("BUS INIT...ERROR", ignoreCase = true)) {
            return ElmResponse(
                type = ElmResponseType.BUS_INIT_ERROR,
                rawResponse = raw,
                cleanPayload = "BUS INIT ERROR",
                latencyMs = latencyMs,
                quality = DataQuality.INVALID,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = "CAN/KWP Bus initialization error"
            )
        }
        if (sanitized.contains("BUS BUSY", ignoreCase = true)) {
            return ElmResponse(
                type = ElmResponseType.BUS_BUSY,
                rawResponse = raw,
                cleanPayload = "BUS BUSY",
                latencyMs = latencyMs,
                quality = DataQuality.INVALID,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = "Vehicle CAN/OBD bus busy"
            )
        }
        if (sanitized.contains("CAN ERROR", ignoreCase = true)) {
            return ElmResponse(
                type = ElmResponseType.CAN_ERROR,
                rawResponse = raw,
                cleanPayload = "CAN ERROR",
                latencyMs = latencyMs,
                quality = DataQuality.INVALID,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = "CAN bus framing / parity error"
            )
        }
        if (sanitized.contains("STOPPED", ignoreCase = true)) {
            return ElmResponse(
                type = ElmResponseType.STOPPED,
                rawResponse = raw,
                cleanPayload = "STOPPED",
                latencyMs = latencyMs,
                quality = DataQuality.INVALID,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = "ELM327 transmission interrupted"
            )
        }
        if (sanitized.contains("BUFFER FULL", ignoreCase = true)) {
            return ElmResponse(
                type = ElmResponseType.BUFFER_FULL,
                rawResponse = raw,
                cleanPayload = "BUFFER FULL",
                latencyMs = latencyMs,
                quality = DataQuality.INVALID,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = "ELM327 RX buffer overrun"
            )
        }

        if (cmd.startsWith("AT")) {
            val isOk = when (cmd) {
                "ATZ", "ATDP" -> sanitized.isNotBlank()
                else -> sanitized.equals("OK", ignoreCase = true)
            }
            return ElmResponse(
                type = if (isOk) ElmResponseType.OK_RESPONSE else ElmResponseType.UNKNOWN_ERROR,
                rawResponse = raw,
                cleanPayload = sanitized,
                latencyMs = latencyMs,
                quality = if (isOk) quality else DataQuality.INVALID,
                provenance = provenance,
                rawTxRx = rawTxRx,
                errorMessage = if (isOk) null else "Unexpected response to $cmd: $sanitized"
            )
        }

        val firstFrameLine = lines.firstOrNull() ?: sanitized
        val (ecuHeader, hexBytes) = parseCanFrameTokens(firstFrameLine)
        return ElmResponse(
            type = if (hexBytes.isNotEmpty()) ElmResponseType.VALID_DATA else ElmResponseType.UNKNOWN_ERROR,
            rawResponse = raw,
            cleanPayload = sanitized,
            ecuHeader = ecuHeader,
            dataBytes = hexBytes,
            latencyMs = latencyMs,
            quality = if (hexBytes.isNotEmpty()) quality else DataQuality.INVALID,
            provenance = provenance,
            rawTxRx = rawTxRx,
            errorMessage = if (hexBytes.isEmpty()) "Unable to decode hex payload: $sanitized" else null
        )
    }

    private fun parseCanFrameTokens(frameLine: String): Pair<String, List<Int>> {
        val cleaned = frameLine.replace(Regex("\\b[0-9A-Fa-f]:"), " ").trim()
        val tokens = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return "" to emptyList()

        if (tokens.size == 1 && tokens[0].length > 2 && tokens[0].all { isHexChar(it) }) {
            val single = tokens[0]
            var header = ""
            val payloadHex = when {
                single.length >= 5 && single.length % 2 == 1 -> {
                    header = single.substring(0, 3)
                    single.substring(3)
                }
                single.length >= 10 && single.startsWith("18DA", ignoreCase = true) -> {
                    header = single.substring(0, 8)
                    single.substring(8)
                }
                else -> single
            }
            val bytes = payloadHex.chunked(2).mapNotNull { it.toIntOrNull(16) }
            return header to bytes
        }

        var ecuHeader = ""
        val payloadTokens = if ((tokens[0].length == 3 || tokens[0].length == 8) && tokens[0].all { isHexChar(it) }) {
            ecuHeader = tokens[0]
            tokens.drop(1)
        } else {
            tokens
        }

        val hexBytes = mutableListOf<Int>()
        for (token in payloadTokens) {
            if (token.length == 2) {
                token.toIntOrNull(16)?.let { hexBytes.add(it) }
            } else if (token.length > 2 && token.length % 2 == 0 && token.all { isHexChar(it) }) {
                token.chunked(2).mapNotNull { it.toIntOrNull(16) }.forEach { hexBytes.add(it) }
            }
        }
        return ecuHeader to hexBytes
    }

    private fun isHexChar(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
