package com.example.core.scheduler

import com.example.core.elm327.IElm327Driver
import com.example.core.model.DataQuality
import com.example.core.model.PidPollingTier
import com.example.core.model.TelemetryChannelState
import com.example.core.model.TelemetryPoint
import com.example.core.model.ValidatedDiagnosticSnapshot
import com.example.core.protocol.PidDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface IPidScheduler {
    val telemetryMap: StateFlow<Map<String, TelemetryPoint>>
    val telemetryHistory: StateFlow<Map<String, List<TelemetryPoint>>>
    val validatedSnapshot: StateFlow<ValidatedDiagnosticSnapshot>
    val isRunning: StateFlow<Boolean>
    val averageLatencyMs: StateFlow<Long>
    val pollingFrequencyHz: StateFlow<Double>

    fun startPolling(
        driver: IElm327Driver,
        scope: CoroutineScope,
        supportedPids: Set<String> = emptySet()
    )
    fun stopPolling()
    fun recordManualTelemetryPoint(point: TelemetryPoint)
}

class AdaptivePidScheduler(
    private val maxHistorySamples: Int = 40
) : IPidScheduler {

    private val _telemetryMap = MutableStateFlow<Map<String, TelemetryPoint>>(emptyMap())
    override val telemetryMap: StateFlow<Map<String, TelemetryPoint>> = _telemetryMap.asStateFlow()

    private val _telemetryHistory = MutableStateFlow<Map<String, List<TelemetryPoint>>>(emptyMap())
    override val telemetryHistory: StateFlow<Map<String, List<TelemetryPoint>>> = _telemetryHistory.asStateFlow()

    private val _validatedSnapshot = MutableStateFlow(ValidatedDiagnosticSnapshot())
    override val validatedSnapshot: StateFlow<ValidatedDiagnosticSnapshot> = _validatedSnapshot.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _averageLatencyMs = MutableStateFlow(35L)
    override val averageLatencyMs: StateFlow<Long> = _averageLatencyMs.asStateFlow()

    private val _pollingFrequencyHz = MutableStateFlow(0.0)
    override val pollingFrequencyHz: StateFlow<Double> = _pollingFrequencyHz.asStateFlow()

    private var pollingJob: Job? = null

    // Adaptive Scheduling Tiers
    private val fastPids = listOf("0C", "0D", "11") // RPM, Speed, Throttle
    private val mediumPids = listOf("04", "0B", "05", "06", "10") // Load, MAP, Coolant, STFT, MAF
    private val slowPids = listOf("07", "0E", "0F", "1F", "2F", "33", "42", "5C") // LTFT, Timing, IAT, RunTime, Fuel, Baro, Volt, Oil

    private var loopCounter = 0

    fun tierForPid(pidHex: String): PidPollingTier {
        return when (pidHex.uppercase()) {
            in fastPids -> PidPollingTier.FAST
            in mediumPids -> PidPollingTier.MEDIUM
            else -> PidPollingTier.SLOW
        }
    }

    override fun startPolling(
        driver: IElm327Driver,
        scope: CoroutineScope,
        supportedPids: Set<String>
    ) {
        if (_isRunning.value) {
            stopPolling()
        }
        // Polling is a hardware operation: never start against an uninitialized
        // adapter or a transport whose provenance is unknown/disconnected.
        if (!driver.isInitialized.value || driver.getProvenance() == com.example.core.model.DataProvenance.UNKNOWN) {
            _isRunning.value = false
            return
        }
        _isRunning.value = true
        loopCounter = 0

        // Seed UNSUPPORTED markers for known PIDs explicitly excluded by ECU bitmask
        if (supportedPids.isNotEmpty()) {
            seedUnsupportedPids(driver, supportedPids)
        }

        pollingJob = scope.launch {
            val latencyHistory = mutableListOf<Long>()
            val activeFast = filterSupported(fastPids, supportedPids)
            val activeMedium = filterSupported(mediumPids, supportedPids)
            val activeSlow = filterSupported(slowPids, supportedPids)

            while (isActive && _isRunning.value) {
                if (!driver.isInitialized.value || driver.getProvenance() == com.example.core.model.DataProvenance.UNKNOWN) {
                    stopPolling()
                    break
                }
                val cycleStart = System.currentTimeMillis()
                val pidsToPollThisTurn = mutableListOf<String>()

                // Fast PIDs polled every single cycle
                pidsToPollThisTurn.addAll(activeFast)

                // Medium PIDs polled every 2nd cycle
                if (loopCounter % 2 == 0 && activeMedium.isNotEmpty()) {
                    val idx = (loopCounter / 2) % activeMedium.size
                    pidsToPollThisTurn.add(activeMedium[idx])
                }

                // Slow PIDs polled every 4th cycle
                if (loopCounter % 4 == 0 && activeSlow.isNotEmpty()) {
                    val idx = (loopCounter / 4) % activeSlow.size
                    pidsToPollThisTurn.add(activeSlow[idx])
                }

                for (pid in pidsToPollThisTurn) {
                    if (!isActive || !_isRunning.value) break
                    val resp = driver.queryPid("01", pid)
                    latencyHistory.add(resp.latencyMs)
                    if (latencyHistory.size > 20) latencyHistory.removeAt(0)
                    _averageLatencyMs.value = latencyHistory.average().toLong().coerceAtLeast(1L)

                    val telemetryPoint = PidDecoder.decodeTelemetryPoint(pid, resp)
                    if (telemetryPoint != null) {
                        ingestTelemetryPoint(telemetryPoint, driver, supportedPids)
                    }
                }

                // Mark aged cached samples as STALE if not refreshed within their tier window
                validateStaleness(driver, supportedPids)

                loopCounter++
                // Adaptive delay: throttle down when bus latency rises to avoid adapter congestion
                val delayTime = when {
                    _averageLatencyMs.value > 150L -> 120L
                    _averageLatencyMs.value > 80L -> 70L
                    else -> 30L
                }
                delay(delayTime)

                val cycleDurationMs = (System.currentTimeMillis() - cycleStart).coerceAtLeast(1L)
                _pollingFrequencyHz.value = 1000.0 / cycleDurationMs.toDouble()
                publishSnapshot(driver, supportedPids)
            }
        }
    }

    override fun recordManualTelemetryPoint(point: TelemetryPoint) {
        val currentMap = _telemetryMap.value.toMutableMap()
        currentMap[point.sourcePid] = point
        _telemetryMap.value = currentMap

        if (point.quality == DataQuality.VALID) {
            val currentHist = _telemetryHistory.value.toMutableMap()
            val list = (currentHist[point.sourcePid].orEmpty() + point).takeLast(maxHistorySamples)
            currentHist[point.sourcePid] = list
            _telemetryHistory.value = currentHist
        }

        val currentSnap = _validatedSnapshot.value
        val updatedChannels = currentSnap.channels.toMutableMap()
        val tier = tierForPid(point.sourcePid)
        updatedChannels[point.sourcePid] = TelemetryChannelState(
            pidHex = point.sourcePid,
            latest = point,
            history = _telemetryHistory.value[point.sourcePid].orEmpty(),
            tier = tier,
            isSupportedByEcu = point.quality != DataQuality.UNSUPPORTED,
            effectiveHz = _pollingFrequencyHz.value / tier.cycleModulo
        )
        _validatedSnapshot.value = currentSnap.copy(
            timestamp = System.currentTimeMillis(),
            channels = updatedChannels
        )
    }

    override fun stopPolling() {
        _isRunning.value = false
        _pollingFrequencyHz.value = 0.0
        pollingJob?.cancel()
        pollingJob = null
        _validatedSnapshot.value = _validatedSnapshot.value.copy(
            isPolling = false,
            overallPollingHz = 0.0
        )
    }

    private fun filterSupported(candidates: List<String>, supportedPids: Set<String>): List<String> {
        if (supportedPids.isEmpty()) return candidates
        val intersection = candidates.filter { supportedPids.contains(it) }
        return intersection
    }

    private fun seedUnsupportedPids(driver: IElm327Driver, supportedPids: Set<String>) {
        val allKnown = fastPids + mediumPids + slowPids
        val currentMap = _telemetryMap.value.toMutableMap()
        for (pid in allKnown) {
            if (!supportedPids.contains(pid)) {
                val def = PidDecoder.PID_DEFINITIONS[pid] ?: continue
                currentMap[pid] = TelemetryPoint(
                    id = "PID_$pid",
                    name = def.name,
                    thaiName = def.thaiName,
                    value = 0.0,
                    displayValue = "UNSUP",
                    unit = def.unit,
                    timestamp = System.currentTimeMillis(),
                    quality = DataQuality.UNSUPPORTED,
                    provenance = driver.getProvenance(),
                    sourcePid = pid
                )
            }
        }
        _telemetryMap.value = currentMap
        publishSnapshot(driver, supportedPids)
    }

    private fun ingestTelemetryPoint(
        point: TelemetryPoint,
        driver: IElm327Driver,
        supportedPids: Set<String>
    ) {
        val currentMap = _telemetryMap.value.toMutableMap()
        currentMap[point.sourcePid] = point
        _telemetryMap.value = currentMap

        if (point.quality == DataQuality.VALID) {
            val currentHist = _telemetryHistory.value.toMutableMap()
            val updatedSeries = (currentHist[point.sourcePid].orEmpty() + point).takeLast(maxHistorySamples)
            currentHist[point.sourcePid] = updatedSeries
            _telemetryHistory.value = currentHist
        }
    }

    private fun validateStaleness(driver: IElm327Driver, supportedPids: Set<String>) {
        val now = System.currentTimeMillis()
        val currentMap = _telemetryMap.value.toMutableMap()
        var changed = false
        for ((pid, pt) in currentMap) {
            if (pt.quality == DataQuality.VALID) {
                val tier = tierForPid(pid)
                if (now - pt.timestamp > tier.staleAfterMs) {
                    currentMap[pid] = pt.copy(quality = DataQuality.STALE)
                    changed = true
                }
            }
        }
        if (changed) {
            _telemetryMap.value = currentMap
            publishSnapshot(driver, supportedPids)
        }
    }

    private fun publishSnapshot(driver: IElm327Driver, supportedPids: Set<String>) {
        val currentMap = _telemetryMap.value
        val currentHist = _telemetryHistory.value
        val hz = _pollingFrequencyHz.value
        val channels = currentMap.mapValues { (pid, pt) ->
            val tier = tierForPid(pid)
            TelemetryChannelState(
                pidHex = pid,
                latest = pt,
                history = currentHist[pid].orEmpty(),
                tier = tier,
                isSupportedByEcu = supportedPids.isEmpty() || supportedPids.contains(pid),
                effectiveHz = if (_isRunning.value) hz / tier.cycleModulo else 0.0
            )
        }
        _validatedSnapshot.value = ValidatedDiagnosticSnapshot(
            timestamp = System.currentTimeMillis(),
            provenance = driver.getProvenance(),
            protocol = driver.detectedProtocol.value,
            isPolling = _isRunning.value,
            averageLatencyMs = _averageLatencyMs.value,
            overallPollingHz = hz,
            supportedPids = supportedPids,
            channels = channels
        )
    }
}
