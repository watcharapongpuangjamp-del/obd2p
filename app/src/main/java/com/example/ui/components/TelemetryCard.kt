package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DataQuality
import com.example.core.model.PidPollingTier
import com.example.core.model.TelemetryPoint
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark

@Composable
fun TelemetryCard(
    telemetry: TelemetryPoint,
    modifier: Modifier = Modifier,
    accentColor: Color = CyberCyan,
    history: List<TelemetryPoint> = emptyList(),
    tier: PidPollingTier? = null,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val borderColor = if (isSelected) accentColor else SurfaceBorderDark
    val cardModifier = modifier
        .testTag("telemetry_card_${telemetry.sourcePid}")
        .clip(RoundedCornerShape(12.dp))
        .background(SurfaceVariantDark)
        .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
        .then(
            if (onClick != null) {
                Modifier
                    .minimumInteractiveComponentSize()
                    .clickable { onClick() }
            } else {
                Modifier
            }
        )
        .padding(14.dp)

    Column(
        modifier = cardModifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = telemetry.name,
                    color = TextPrimaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = telemetry.thaiName,
                    color = TextMutedDark,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            QualityBadge(quality = telemetry.quality)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = telemetry.displayValue,
                    color = if (telemetry.quality == DataQuality.VALID) TextPrimaryDark else TextMutedDark,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = telemetry.unit,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            if (history.size >= 2) {
                MiniSparkline(
                    points = history.map { it.value },
                    strokeColor = accentColor,
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(24.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProvenanceBadge(provenance = telemetry.provenance)
            if (tier != null) {
                Text(
                    text = tier.label,
                    color = accentColor.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PID ${telemetry.sourcePid} • ${telemetry.sourceEcuId.ifBlank { "UNKNOWN" }}",
                color = TextMutedDark.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${formatTelemetryTimestamp(telemetry.timestamp)} • ${telemetry.latencyMs}ms",
                color = TextMutedDark.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MiniSparkline(
    points: List<Double>,
    strokeColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas
        val minVal = points.minOrNull() ?: 0.0
        val maxVal = points.maxOrNull() ?: 1.0
        val span = (maxVal - minVal).takeIf { it > 0.0001 } ?: 1.0
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)

        val path = Path()
        points.forEachIndexed { idx, value ->
            val x = idx * stepX
            val normalized = ((value - minVal) / span).toFloat().coerceIn(0f, 1f)
            val y = size.height - (normalized * (size.height - 4f)) - 2f
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// Helper to provide an explicit UNAVAILABLE placeholder before the first ECU sample arrives
@Composable
fun TelemetryCardSkeleton(
    title: String,
    unit: String,
    pid: String,
    modifier: Modifier = Modifier
) {
    TelemetryCard(
        telemetry = TelemetryPoint(
            id = "pending_$pid",
            name = title,
            thaiName = "รอสัญญาณจากกล่อง ECU",
            value = 0.0,
            displayValue = "---",
            unit = unit,
            timestamp = 0L,
            sourcePid = pid,
            quality = DataQuality.UNAVAILABLE
        ),
        modifier = modifier
    )
}
