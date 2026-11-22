package com.example.ui.tabs

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.TelemetryPoint
import com.example.ui.DiagnosticUiState
import com.example.ui.components.AdapterConnectionBar
import com.example.ui.components.CircularGauge
import com.example.ui.components.QualityBadge
import com.example.ui.components.TelemetryCard
import com.example.ui.components.TelemetryCardSkeleton
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark

@Composable
fun DashboardTab(
    uiState: DiagnosticUiState,
    onOpenAdapterManager: () -> Unit = {},
    onTogglePolling: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetryMap

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        AdapterConnectionBar(
            uiState = uiState,
            onOpenAdapterManager = onOpenAdapterManager,
            onTogglePolling = onTogglePolling
        )

        // Top Hero Gauges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed Gauge
            val speedPt = telemetry["0D"]
            val speedVal = speedPt?.value?.toFloat() ?: 0f
            BoxGaugeWithLabel(
                title = "SPEED",
                value = speedPt?.displayValue ?: "---",
                unit = speedPt?.unit ?: "km/h",
                progress = speedVal,
                max = 220f,
                color = CyberBlue,
                quality = speedPt?.quality,
                modifier = Modifier.weight(1f)
            )

            // RPM Gauge (Center, Larger)
            val rpmPt = telemetry["0C"]
            val rpmVal = rpmPt?.value?.toFloat() ?: 0f
            BoxGaugeWithLabel(
                title = "ENGINE RPM",
                value = rpmPt?.displayValue ?: "---",
                unit = rpmPt?.unit ?: "RPM",
                progress = rpmVal,
                max = 8000f,
                color = NeonEmerald,
                quality = rpmPt?.quality,
                modifier = Modifier
                    .weight(1.2f)
                    .padding(horizontal = 8.dp)
            )

            // Coolant Temp Gauge
            val ectPt = telemetry["05"]
            val ectVal = ectPt?.value?.toFloat() ?: 0f
            BoxGaugeWithLabel(
                title = "COOLANT",
                value = ectPt?.displayValue ?: "---",
                unit = ectPt?.unit ?: "°C",
                progress = ectVal,
                min = -20f,
                max = 140f,
                color = CyberCyan,
                quality = ectPt?.quality,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "LIVE TELEMETRY",
            color = TextMutedDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Telemetry Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Priority PIDs to show on dashboard
            val priorityPids = listOf("04", "0B", "06", "07", "11", "10", "42", "2F")
            val labels = listOf("Engine Load", "Intake MAP", "STFT B1", "LTFT B1", "Throttle", "MAF", "Control Voltage", "Fuel Level")
            val units = listOf("%", "kPa", "%", "%", "%", "g/s", "V", "%")

            items(priorityPids.size) { index ->
                val pid = priorityPids[index]
                val point = telemetry[pid]
                if (point != null) {
                    TelemetryCard(telemetry = point)
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
    color: androidx.compose.ui.graphics.Color,
    quality: com.example.core.model.DataQuality?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        CircularGauge(
            value = progress,
            min = min,
            max = max,
            color = color,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    color = TextPrimaryDark,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = unit,
                    color = TextMutedDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = TextMutedDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        if (quality != null) {
            Spacer(modifier = Modifier.height(4.dp))
            QualityBadge(quality = quality)
        }
    }
}
