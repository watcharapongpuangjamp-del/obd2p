package com.example.core.scheduler

import com.example.core.elm327.IElm327Driver
import com.example.core.model.TelemetryPoint
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
    val isRunning: StateFlow<Boolean>
    val averageLatencyMs: StateFlow<Long>

    fun startPolling(driver: IElm327Driver, scope: CoroutineScope)
    fun stopPolling()
}

class AdaptivePidScheduler : IPidScheduler {

    private val _telemetryMap = MutableStateFlow<Map<String, TelemetryPoint>>(emptyMap())
    override val telemetryMap: StateFlow<Map<String, TelemetryPoint>> = _telemetryMap.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _averageLatencyMs = MutableStateFlow(35L)
    override val averageLatencyMs: StateFlow<Long> = _averageLatencyMs.asStateFlow()

    private var pollingJob: Job? = null

    // Adaptive Scheduling Tiers
    private val fastPids = listOf("0C", "0D", "11") // RPM, Speed, Throttle
    private val mediumPids = listOf("04", "0B", "05", "06", "10") // Load, MAP, Coolant, STFT, MAF
    private val slowPids = listOf("07", "0E", "0F", "1F", "2F", "33", "42", "5C") // LTFT, Timing, IAT, RunTime, Fuel, Baro, Volt, Oil

    private var loopCounter = 0

    override fun startPolling(driver: IElm327Driver, scope: CoroutineScope) {
        if (_isRunning.value) return
        _isRunning.value = true

        pollingJob = scope.launch {
            val latencyHistory = mutableListOf<Long>()

            while (isActive && _isRunning.value) {
                val pidsToPollThisTurn = mutableListOf<String>()

                // Fast PIDs polled every single cycle
                pidsToPollThisTurn.addAll(fastPids)

                // Medium PIDs polled every 2nd cycle
                if (loopCounter % 2 == 0) {
                    val idx = (loopCounter / 2) % mediumPids.size
                    pidsToPollThisTurn.add(mediumPids[idx])
                }

                // Slow PIDs polled every 4th cycle
                if (loopCounter % 4 == 0) {
                    val idx = (loopCounter / 4) % slowPids.size
                    pidsToPollThisTurn.add(slowPids[idx])
                }

                for (pid in pidsToPollThisTurn) {
                    if (!isActive || !_isRunning.value) break
                    val resp = driver.queryPid("01", pid)
                    latencyHistory.add(resp.latencyMs)
                    if (latencyHistory.size > 20) latencyHistory.removeAt(0)
                    _averageLatencyMs.value = latencyHistory.average().toLong().coerceAtLeast(1L)

                    val telemetryPoint = PidDecoder.decodeTelemetryPoint(pid, resp)
                    if (telemetryPoint != null) {
                        val currentMap = _telemetryMap.value.toMutableMap()
                        currentMap[pid] = telemetryPoint
                        _telemetryMap.value = currentMap
                    }
                }

                loopCounter++
                // Adaptive delay: if latency is high, throttle down slightly to prevent bus congestion
                val delayTime = if (_averageLatencyMs.value > 100) 80L else 30L
                delay(delayTime)
            }
        }
    }

    override fun stopPolling() {
        _isRunning.value = false
        pollingJob?.cancel()
        pollingJob = null
    }
}
