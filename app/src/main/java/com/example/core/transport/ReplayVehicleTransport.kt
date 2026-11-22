package com.example.core.transport

import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.core.model.VehicleProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin
import kotlin.random.Random

class ReplayVehicleTransport(
    private var vehicleProfile: VehicleProfile = VehicleProfile.THAI_PRESETS[0]
) : ITransport {

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _rxEvidenceFlow = MutableSharedFlow<RawTxRxResult>(extraBufferCapacity = 64)
    override val rxEvidenceFlow: SharedFlow<RawTxRxResult> = _rxEvidenceFlow.asSharedFlow()

    private var isEngineRunning = true
    private var simTime = 0.0
    private var isDtcCleared = false
    private var connectionTimestamp = 0L

    fun setVehicleProfile(profile: VehicleProfile) {
        this.vehicleProfile = profile
        this.isDtcCleared = false
    }

    fun getCurrentProfile(): VehicleProfile = vehicleProfile

    override suspend fun connect(): Boolean {
        _state.value = TransportState.Connecting("ELM327 USB v1.5 (Replay/Hardware Stream: ${vehicleProfile.model})")
        delay(400) // Simulated connection handshake
        connectionTimestamp = System.currentTimeMillis()
        _state.value = TransportState.Connected(
            deviceName = "ELM327 v1.5 OBD-II (${vehicleProfile.make} ${vehicleProfile.model})",
            baudRate = 38400,
            provenance = DataProvenance.SIMULATOR,
            isHardware = false
        )
        return true
    }

    override suspend fun disconnect() {
        _state.value = TransportState.Disconnected
    }

    override fun isConnected(): Boolean = _state.value is TransportState.Connected

    override suspend fun sendAndReceive(command: String, timeoutMs: Long): RawTxRxResult {
        val startNano = System.nanoTime()
        val cleanCmd = command.trim().uppercase()

        // Simulate realistic OBD CAN-bus latency (15ms - 55ms)
        val simLatency = Random.nextLong(18, 48)
        delay(simLatency)

        simTime += 0.15

        val responseString = generateResponseForCommand(cleanCmd)
        val elapsedMs = (System.nanoTime() - startNano) / 1_000_000

        val result = RawTxRxResult(
            commandTx = command,
            responseRx = responseString,
            latencyMs = elapsedMs,
            isTimeout = false,
            isError = responseString.contains("ERROR") || responseString.contains("UNABLE"),
            rawBytes = responseString.toByteArray(Charsets.US_ASCII),
            quality = DataQuality.VALID,
            provenance = DataProvenance.SIMULATOR,
            timestamp = System.currentTimeMillis()
        )

        _rxEvidenceFlow.tryEmit(result)
        return result
    }

    private fun generateResponseForCommand(cmd: String): String {
        return when {
            cmd == "ATZ" -> "ELM327 v1.5\r\r>"
            cmd == "ATE0" -> "OK\r\r>"
            cmd == "ATL0" -> "OK\r\r>"
            cmd == "ATH1" -> "OK\r\r>"
            cmd == "ATH0" -> "OK\r\r>"
            cmd == "ATSP0" || cmd == "ATSP6" -> "OK\r\r>"
            cmd == "ATDP" || cmd == "ATDPN" -> "ISO 15765-4 (CAN 11/500)\r\r>"
            cmd == "ATRV" -> {
                val voltage = 14.1 + 0.2 * sin(simTime * 0.5)
                String.format("%.1fV\r\r>", voltage)
            }
            cmd == "ATI" -> "ELM327 v1.5 Diagnostic Engine\r\r>"

            // Service 01: Read Real-Time Telemetry PIDs
            cmd.startsWith("01") -> handleService01(cmd.removePrefix("01").trim())

            // Service 03: Read Confirmed DTCs
            cmd == "03" -> handleService03()

            // Service 04: Clear DTCs (ISO 15765-4 CAN 11-bit: 7E8 01 44)
            cmd == "04" -> {
                isDtcCleared = true
                "7E8 01 44\r\r>"
            }

            // Service 07: Read Pending DTCs
            cmd == "07" -> handleService07()

            // Service 09: Vehicle Information
            cmd.startsWith("09") -> handleService09(cmd.removePrefix("09").trim())

            // Service 0A: Read Permanent DTCs
            cmd == "0A" -> handleService0A()

            else -> "NO DATA\r\r>"
        }
    }

    private fun handleService01(pid: String): String {
        return when (pid) {
            "00" -> "7E8 06 41 00 BE 3F B8 13\r\r>" // Supported PIDs 01-20
            "20" -> "7E8 06 41 20 80 07 E0 11\r\r>" // Supported PIDs 21-40
            "40" -> "7E8 06 41 40 7A D0 00 00\r\r>" // Supported PIDs 41-60

            // 04: Calculated Engine Load (0 - 100%) -> formula: A * 100 / 255
            "04" -> {
                val loadPct = 25.0 + 15.0 * sin(simTime * 0.8) + Random.nextDouble(-2.0, 2.0)
                val byteVal = (loadPct.coerceIn(0.0, 100.0) * 255 / 100).toInt()
                val hex = String.format("%02X", byteVal)
                "7E8 03 41 04 $hex\r\r>"
            }

            // 05: Engine Coolant Temp (-40 to 215 C) -> formula: A - 40
            "05" -> {
                val baseTemp = when (vehicleProfile.id) {
                    "toyota_revo_28" -> 88.0 + 2.0 * sin(simTime * 0.2)
                    "isuzu_dmax_19" -> 86.0 + 1.5 * sin(simTime * 0.2)
                    "honda_civic_fc" -> 92.0 + 3.0 * sin(simTime * 0.3)
                    else -> 89.0
                }
                val rawA = (baseTemp + 40.0).toInt().coerceIn(0, 255)
                val hex = String.format("%02X", rawA)
                "7E8 03 41 05 $hex\r\r>"
            }

            // 06: Short Term Fuel Trim Bank 1 (-100% to 99.2%) -> formula: (A - 128) * 100 / 128
            "06" -> {
                val stft = if (vehicleProfile.id == "honda_civic_fc" && !isDtcCleared) {
                    14.5 + 4.0 * sin(simTime * 1.5) // Lean condition trigger!
                } else {
                    1.2 + 2.0 * sin(simTime * 1.2)
                }
                val rawA = ((stft * 128 / 100) + 128).toInt().coerceIn(0, 255)
                val hex = String.format("%02X", rawA)
                "7E8 03 41 06 $hex\r\r>"
            }

            // 07: Long Term Fuel Trim Bank 1
            "07" -> {
                val ltft = if (vehicleProfile.id == "honda_civic_fc" && !isDtcCleared) 12.0 else 2.3
                val rawA = ((ltft * 128 / 100) + 128).toInt().coerceIn(0, 255)
                val hex = String.format("%02X", rawA)
                "7E8 03 41 07 $hex\r\r>"
            }

            // 0B: Intake Manifold Absolute Pressure (0 - 255 kPa) -> formula: A
            "0B" -> {
                val mapKpa = when (vehicleProfile.id) {
                    "toyota_revo_28", "isuzu_dmax_19" -> (140.0 + 45.0 * sin(simTime * 0.7)).toInt() // Turbo Diesel boost
                    "honda_civic_fc" -> (125.0 + 35.0 * sin(simTime * 0.9)).toInt() // Turbo Gasoline
                    else -> (98.0 + 10.0 * sin(simTime * 0.5)).toInt() // NA Eco Car
                }.coerceIn(30, 255)
                val hex = String.format("%02X", mapKpa)
                "7E8 03 41 0B $hex\r\r>"
            }

            // 0C: Engine RPM ((256*A + B) / 4)
            "0C" -> {
                val rpm = (780.0 + 1600.0 * (0.5 + 0.5 * sin(simTime * 0.6)) + Random.nextDouble(-20.0, 20.0)).coerceIn(650.0, 5500.0)
                val rawRpm = (rpm * 4).toInt()
                val a = (rawRpm shr 8) and 0xFF
                val b = rawRpm and 0xFF
                val hexA = String.format("%02X", a)
                val hexB = String.format("%02X", b)
                "7E8 04 41 0C $hexA $hexB\r\r>"
            }

            // 0D: Vehicle Speed (0 - 255 km/h) -> formula: A
            "0D" -> {
                val spd = (55.0 + 45.0 * sin(simTime * 0.4) + Random.nextDouble(-1.0, 1.0)).toInt().coerceIn(0, 180)
                val hex = String.format("%02X", spd)
                "7E8 03 41 0D $hex\r\r>"
            }

            // 0E: Timing Advance ((A / 2) - 64 deg)
            "0E" -> {
                val deg = 12.0 + 8.0 * sin(simTime * 0.8)
                val rawA = ((deg + 64.0) * 2).toInt().coerceIn(0, 255)
                val hex = String.format("%02X", rawA)
                "7E8 03 41 0E $hex\r\r>"
            }

            // 0F: Intake Air Temp (A - 40 C)
            "0F" -> {
                val iat = 38.0 + 3.0 * sin(simTime * 0.1) // Thailand tropical ambient
                val rawA = (iat + 40.0).toInt().coerceIn(0, 255)
                val hex = String.format("%02X", rawA)
                "7E8 03 41 0F $hex\r\r>"
            }

            // 10: MAF Air Flow Rate ((256*A + B) / 100 g/s)
            "10" -> {
                val maf = 18.5 + 24.0 * (0.5 + 0.5 * sin(simTime * 0.6))
                val raw = (maf * 100).toInt().coerceIn(0, 65535)
                val a = (raw shr 8) and 0xFF
                val b = raw and 0xFF
                val hexA = String.format("%02X", a)
                val hexB = String.format("%02X", b)
                "7E8 04 41 10 $hexA $hexB\r\r>"
            }

            // 11: Throttle Position (A * 100 / 255)
            "11" -> {
                val tps = 16.0 + 35.0 * (0.5 + 0.5 * sin(simTime * 0.6))
                val rawA = (tps * 255 / 100).toInt().coerceIn(0, 255)
                val hex = String.format("%02X", rawA)
                "7E8 03 41 11 $hex\r\r>"
            }

            // 1F: Run Time since engine start (256*A + B seconds)
            "1F" -> {
                val elapsedSec = ((System.currentTimeMillis() - connectionTimestamp) / 1000).toInt().coerceIn(10, 65000)
                val a = (elapsedSec shr 8) and 0xFF
                val b = elapsedSec and 0xFF
                val hexA = String.format("%02X", a)
                val hexB = String.format("%02X", b)
                "7E8 04 41 1F $hexA $hexB\r\r>"
            }

            // 2F: Fuel Tank Level Input (A * 100 / 255)
            "2F" -> {
                val level = 68.0 // 68%
                val rawA = (level * 255 / 100).toInt().coerceIn(0, 255)
                val hex = String.format("%02X", rawA)
                "7E8 03 41 2F $hex\r\r>"
            }

            // 33: Barometric Pressure (A kPa)
            "33" -> {
                val baro = 101 // Sea level Thailand pressure
                val hex = String.format("%02X", baro)
                "7E8 03 41 33 $hex\r\r>"
            }

            // 42: Control Module Voltage ((256*A + B) / 1000 V)
            "42" -> {
                val mv = (14150 + 200 * sin(simTime * 0.5)).toInt()
                val a = (mv shr 8) and 0xFF
                val b = mv and 0xFF
                val hexA = String.format("%02X", a)
                val hexB = String.format("%02X", b)
                "7E8 04 41 42 $hexA $hexB\r\r>"
            }

            // 5C: Engine Oil Temperature (A - 40 C)
            "5C" -> {
                val oil = 91.0 + 2.0 * sin(simTime * 0.1)
                val rawA = (oil + 40.0).toInt().coerceIn(0, 255)
                val hex = String.format("%02X", rawA)
                "7E8 03 41 5C $hex\r\r>"
            }

            else -> "7E8 03 7F 01 12\r\r>" // Sub-function not supported
        }
    }

    private fun handleService03(): String {
        if (isDtcCleared) {
            return "7E8 02 43 00\r\r>" // 0 DTCs
        }
        val codes = vehicleProfile.defaultDtcScenario
        if (codes.isEmpty()) return "7E8 02 43 00\r\r>"

        // Format DTC hex response: 43 [count] [code1_hi] [code1_lo] [code2_hi] [code2_lo]...
        val sb = StringBuilder("7E8 ")
        val byteLen = 1 + (codes.size * 2)
        sb.append(String.format("%02X 43 ", byteLen))
        for (c in codes) {
            sb.append(encodeDtcToHex(c)).append(" ")
        }
        return sb.toString().trim() + "\r\r>"
    }

    private fun handleService07(): String {
        if (isDtcCleared) return "7E8 02 47 00\r\r>"
        // Simulate a pending code for Toyota/Isuzu (P0401 or P0101)
        return when (vehicleProfile.id) {
            "toyota_revo_28" -> "7E8 04 47 01 04 01\r\r>" // P0401 pending
            "isuzu_dmax_19" -> "7E8 04 47 01 01 01\r\r>" // P0101 pending
            else -> "7E8 02 47 00\r\r>"
        }
    }

    private fun handleService0A(): String {
        if (isDtcCleared) return "7E8 02 4A 00\r\r>"
        return when (vehicleProfile.id) {
            "toyota_revo_28" -> "7E8 04 4A 01 04 01\r\r>" // P0401 permanent
            "honda_civic_fc" -> "7E8 04 4A 01 01 71\r\r>" // P0171 permanent
            else -> "7E8 02 4A 00\r\r>"
        }
    }

    private fun handleService09(pid: String): String {
        return when (pid) {
            "02" -> {
                // VIN (17 chars) -> e.g. MHFBA3BB8N0123456
                val vin = vehicleProfile.vin
                val hexChars = vin.map { String.format("%02X", it.code) }.joinToString(" ")
                "7E8 10 14 49 02 01 $hexChars\r\r>"
            }
            "04" -> {
                // Calibration ID / CALID
                val calId = "33481000A"
                val hexChars = calId.map { String.format("%02X", it.code) }.joinToString(" ")
                "7E8 10 0D 49 04 01 $hexChars\r\r>"
            }
            "0A" -> {
                // ECU Name
                val name = "ECM-TOYOTA-GD"
                val hexChars = name.map { String.format("%02X", it.code) }.joinToString(" ")
                "7E8 10 11 49 0A 01 $hexChars\r\r>"
            }
            else -> "7E8 03 7F 09 12\r\r>"
        }
    }

    private fun encodeDtcToHex(code: String): String {
        // Example: P0171 -> P = 0, 0171 -> High byte 0x01, Low byte 0x71
        // Example: P0401 -> 04 01
        // Example: U0100 -> U = 3 -> 0xC1 0x00
        val prefix = code.first()
        val num = code.drop(1)
        val highNibble = when (prefix) {
            'P' -> 0
            'C' -> 1
            'B' -> 2
            'U' -> 3
            else -> 0
        }
        val firstDigit = num.take(1).toIntOrNull(16) ?: 0
        val byte1 = (highNibble shl 6) or (firstDigit shl 4) or ((num.drop(1).take(1).toIntOrNull(16) ?: 0))
        val byte2 = (num.drop(2).take(2).toIntOrNull(16) ?: 0)
        return String.format("%02X %02X", byte1, byte2)
    }
}
