package com.example.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DataProvenance
import com.example.core.model.DataQuality
import com.example.ui.DiagnosticUiState
import com.example.ui.components.AdapterConnectionBar
import com.example.ui.components.CircularGauge
import com.example.ui.components.ProvenanceBadge
import com.example.ui.components.QualityBadge
import com.example.ui.components.RealTimeTelemetryChart
import com.example.ui.components.TelemetryCard
import com.example.ui.components.TelemetryCardSkeleton
import com.example.ui.components.formatTelemetryTimestamp
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import java.util.Locale

@Composable
fun DashboardTab(
    uiState: DiagnosticUiState,
    onOpenAdapterManager: () -> Unit = {},
    onTogglePolling: () -> Unit = {},
    onSelectChartPid: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snapshot = uiState.validatedSnapshot
    val channels = snapshot.channels
    val telemetry = uiState.telemetryMap

    val priorityPids = listOf("04", "0B", "06", "07", "11", "10", "42", "2F", "0F", "5C")
    val labels = listOf(
        "Engine Load", "Intake MAP", "STFT B1", "LTFT B1",
        "Throttle", "MAF Flow", "Control Voltage", "Fuel Level",
        "Intake Air Temp", "Engine Oil Temp"
    )
    val units = listOf("%", "kPa", "%", "%", "%", "g/s", "V", "%", "°C", "°C")

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        AdapterConnectionBar(
            uiState = uiState,
            onOpenAdapterManager = onOpenAdapterManager,
            onTogglePolling = onTogglePolling
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Top Hero Gauges (Speed 0D, Engine RPM 0C, Coolant 05) sourced from Validated Diagnostic State
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val speedPt = channels["0D"]?.latest ?: telemetry["0D"]
                    BoxGaugeWithLabel(
                        title = "SPEED (0D)",
                        value = speedPt?.displayValue ?: "---",
                        unit = speedPt?.unit ?: "km/h",
                        progress = speedPt?.value?.toFloat() ?: 0f,
                        max = 220f,
                        color = CyberBlue,
                        quality = speedPt?.quality ?: DataQuality.UNAVAILABLE,
                        provenance = speedPt?.provenance ?: snapshot.provenance,
                        timestampMs = speedPt?.timestamp ?: 0L,
                        onClick = { onSelectChartPid("0D") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gauge_speed_0D")
                    )

                    val rpmPt = channels["0C"]?.latest ?: telemetry["0C"]
                    BoxGaugeWithLabel(
                        title = "ENGINE RPM (0C)",
                        value = rpmPt?.displayValue ?: "---",
                        unit = rpmPt?.unit ?: "RPM",
                        progress = rpmPt?.value?.toFloat() ?: 0f,
                        max = 8000f,
                        color = NeonEmerald,
                        quality = rpmPt?.quality ?: DataQuality.UNAVAILABLE,
                        provenance = rpmPt?.provenance ?: snapshot.provenance,
                        timestampMs = rpmPt?.timestamp ?: 0L,
                        onClick = { onSelectChartPid("0C") },
                        modifier = Modifier
                            .weight(1.2f)
                            .padding(horizontal = 6.dp)
                            .testTag("gauge_rpm_0C")
                    )

                    val ectPt = channels["05"]?.latest ?: telemetry["05"]
                    BoxGaugeWithLabel(
                        title = "COOLANT (05)",
                        value = ectPt?.displayValue ?: "---",
                        unit = ectPt?.unit ?: "°C",
                        progress = ectPt?.value?.toFloat() ?: 0f,
                        min = -20f,
                        max = 140f,
                        color = CyberCyan,
                        quality = ectPt?.quality ?: DataQuality.UNAVAILABLE,
                        provenance = ectPt?.provenance ?: snapshot.provenance,
                        timestampMs = ectPt?.timestamp ?: 0L,
                        onClick = { onSelectChartPid("05") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gauge_coolant_05")
                    )
                }
            }

            // Dynamic Real-Time Telemetry Waveform Chart
            item(span = { GridItemSpan(maxLineSpan) }) {
                RealTimeTelemetryChart(
                    snapshot = snapshot,
                    selectedPidHex = uiState.selectedChartPid,
                    onSelectPid = onSelectChartPid
                )
            }

            // Section Header with Live Scheduler Status
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VALIDATED PID CHANNELS",
                        color = TextMutedDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Rate: ${
                            String.format(Locale.US, "%.1f", snapshot.overallPollingHz)
                        } Hz • Supported: ${
                            if (snapshot.supportedPids.isEmpty()) "ALL (AUTO)" else "${snapshot.supportedPids.size} PIDs"
                        }",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Adaptive Telemetry Grid Tiles
            items(priorityPids.size) { index ->
                val pid = priorityPids[index]
                val channel = channels[pid]
                val point = channel?.latest ?: telemetry[pid]
                if (point != null) {
                    TelemetryCard(
                        telemetry = point,
                        history = channel?.history.orEmpty(),
                        tier = channel?.tier,
                        isSelected = uiState.selectedChartPid == pid,
                        onClick = { onSelectChartPid(pid) }
                    )
                } else {
                    TelemetryCardSkeleton(
                        title = labels[index],
                        unit = units[index],
                        pid = pid
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxGaugeWithLabel(
    title: String,
    value: String,
    unit: String,
    progress: Float,
    min: Float = 0f,
    max: Float,
    color: Color,
    quality: DataQuality,
    provenance: DataProvenance,
    timestampMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable { onClick() }
    ) {
        CircularGauge(
            value = progress,
            min = min,
            max = max,
            color = color,
            quality = quality,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    color = TextPrimaryDark,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = unit,
                    color = TextMutedDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = TextMutedDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QualityBadge(quality = quality)
            ProvenanceBadge(provenance = provenance)
        }
        Text(
            text = formatTelemetryTimestamp(timestampMs),
            color = TextMutedDark.copy(alpha = 0.75f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
