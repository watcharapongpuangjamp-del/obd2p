package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.DataQuality
import com.example.core.model.TelemetryChannelState
import com.example.core.model.ValidatedDiagnosticSnapshot
import com.example.core.protocol.PidDecoder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HighEnergyAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import java.util.Locale

private val CHART_FEATURED_PIDS = listOf("0C", "0D", "04", "05", "0B", "11", "10", "42")

@Composable
fun RealTimeTelemetryChart(
    snapshot: ValidatedDiagnosticSnapshot,
    selectedPidHex: String,
    onSelectPid: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val channel: TelemetryChannelState? = snapshot.channels[selectedPidHex]
    val pidDef = PidDecoder.PID_DEFINITIONS[selectedPidHex]
    val title = channel?.latest?.name ?: pidDef?.name ?: "PID $selectedPidHex"
    val thaiTitle = channel?.latest?.thaiName ?: pidDef?.thaiName ?: ""
    val unit = channel?.latest?.unit ?: pidDef?.unit ?: ""
    val history = channel?.history.orEmpty()

    val accentColor = when (selectedPidHex) {
        "0C" -> NeonEmerald
        "0D" -> CyberBlue
        "05" -> HighEnergyAmber
        else -> CyberCyan
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("realtime_telemetry_chart")
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header: Validated Diagnostic State & Adaptive Polling Rate
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.realtime_chart_title),
                    color = TextPrimaryDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "${snapshot.protocol} • ${
                        String.format(Locale.US, "%.1f Hz", snapshot.overallPollingHz)
                    } • ${snapshot.averageLatencyMs}ms",
                    color = TextMutedDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            ProvenanceBadge(provenance = snapshot.provenance)
        }

        // PID Selector Row (respecting ECU supportedPids from AdaptivePidScheduler)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(CHART_FEATURED_PIDS) { pid ->
                val isSelected = pid == selectedPidHex
                val supportKnown = snapshot.supportedPids.isNotEmpty()
                val isSupported = snapshot.supportedPids.contains(pid)
                val shortLabel = when (pid) {
                    "0C" -> "RPM (0C)"
                    "0D" -> "SPEED (0D)"
                    "04" -> "LOAD (04)"
                    "05" -> "COOLANT (05)"
                    "0B" -> "MAP (0B)"
                    "11" -> "TPS (11)"
                    "10" -> "MAF (10)"
                    "42" -> "VOLT (42)"
                    else -> pid
                }

                val chipBg = when {
                    isSelected -> CyberCyan.copy(alpha = 0.22f)
                    supportKnown && !isSupported -> CarbonDark.copy(alpha = 0.45f)
                    else -> CarbonDark.copy(alpha = 0.75f)
                }
                val chipBorder = when {
                    isSelected -> CyberCyan
                    else -> SurfaceBorderDark
                }

                Box(
                    modifier = Modifier
                        .testTag("chart_pid_chip_$pid")
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipBg)
                        .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelectPid(pid) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            !supportKnown -> "$shortLabel (?)"
                            isSupported -> shortLabel
                            else -> "$shortLabel (UNSUP)"
                        },
                        color = when {
                            isSelected -> CyberCyan
                            supportKnown && !isSupported -> TextMutedDark.copy(alpha = 0.55f)
                            else -> TextPrimaryDark
                        },
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Selected Channel Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$title ($selectedPidHex)",
                    color = TextPrimaryDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (thaiTitle.isNotBlank()) {
                    Text(
                        text = thaiTitle,
                        color = TextMutedDark,
                        fontSize = 11.sp
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${channel?.latest?.displayValue ?: "---"} $unit",
                    color = accentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                QualityBadge(quality = channel?.latest?.quality ?: DataQuality.UNAVAILABLE)
            }
        }

        // Dynamic Waveform Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CarbonDark.copy(alpha = 0.85f))
                .border(1.dp, SurfaceBorderDark.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                channel != null && channel.supportKnown && !channel.isSupportedByEcu -> {
                    Text(
                        text = stringResource(R.string.realtime_chart_unsupported, selectedPidHex),
                        color = TextMutedDark,
                        fontSize = 12.sp
                    )
                }
                history.size < 2 -> {
                    Text(
                        text = if (snapshot.isPolling) {
                            stringResource(R.string.realtime_chart_collecting)
                        } else {
                            stringResource(R.string.realtime_chart_paused)
                        },
                        color = TextMutedDark,
                        fontSize = 12.sp
                    )
                }
                else -> {
                    TelemetryWaveformCanvas(
                        values = history.map { it.value },
                        accentColor = if (channel?.latest?.quality == DataQuality.STALE) {
                            HighEnergyAmber
                        } else {
                            accentColor
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Bottom Telemetry Model Traceability Bar (Min/Max, Tier, Effective Hz, Timestamp, ECU)
        val minVal = history.minOfOrNull { it.value }
        val maxVal = history.maxOfOrNull { it.value }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (minVal != null && maxVal != null) {
                    "Min: ${String.format(Locale.US, "%.1f", minVal)} • Max: ${
                        String.format(Locale.US, "%.1f", maxVal)
                    } $unit"
                } else {
                    "Samples: ${history.size}"
                },
                color = TextMutedDark,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${channel?.tier?.label ?: "AUTO"} (${
                    String.format(Locale.US, "%.1f", channel?.effectiveHz ?: 0.0)
                } Hz)",
                color = CyberCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(1.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ECU: ${channel?.latest?.sourceEcuId?.ifBlank { "UNKNOWN" } ?: "---"} • ${
                    formatTelemetryTimestamp(channel?.latest?.timestamp ?: snapshot.timestamp)
                }",
                color = TextMutedDark.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Samples: ${history.size} • ${channel?.latest?.latencyMs ?: snapshot.averageLatencyMs}ms",
                color = TextMutedDark.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TelemetryWaveformCanvas(
    values: List<Double>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas

        val rawMin = values.minOrNull() ?: 0.0
        val rawMax = values.maxOrNull() ?: 1.0
        val pad = ((rawMax - rawMin) * 0.12).coerceAtLeast(1.0)
        val minVal = rawMin - pad
        val maxVal = rawMax + pad
        val range = (maxVal - minVal).coerceAtLeast(0.001)

        // Subtle horizontal grid lines
        val gridRows = 3
        for (r in 0..gridRows) {
            val y = (size.height / gridRows) * r
            drawLine(
                color = SurfaceBorderDark.copy(alpha = 0.45f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val strokePath = Path()
        val fillPath = Path()
        var lastPoint = Offset.Zero

        values.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
            val y = size.height - (normalized * (size.height - 8f)) - 4f
            val pt = Offset(x, y)
            if (index == 0) {
                strokePath.moveTo(pt.x, pt.y)
                fillPath.moveTo(pt.x, size.height)
                fillPath.lineTo(pt.x, pt.y)
            } else {
                strokePath.lineTo(pt.x, pt.y)
                fillPath.lineTo(pt.x, pt.y)
            }
            lastPoint = pt
        }
        fillPath.lineTo(lastPoint.x, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.30f),
                    accentColor.copy(alpha = 0.02f)
                ),
                startY = 0f,
                endY = size.height
            )
        )

        drawPath(
            path = strokePath,
            color = accentColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Live endpoint indicator
        drawCircle(
            color = accentColor.copy(alpha = 0.25f),
            radius = 8.dp.toPx(),
            center = lastPoint
        )
        drawCircle(
            color = accentColor,
            radius = 4.dp.toPx(),
            center = lastPoint
        )
    }
}
