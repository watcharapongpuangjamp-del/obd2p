package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DataQuality
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
    accentColor: Color = CyberCyan
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = telemetry.name,
                color = TextMutedDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            QualityBadge(quality = telemetry.quality)
        }

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
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PID: ${telemetry.sourcePid}",
                color = TextMutedDark.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
            Text(
                text = "${telemetry.latencyMs}ms",
                color = TextMutedDark.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

// Helper to provide a skeleton placeholder when data is missing
@Composable
fun TelemetryCardSkeleton(
    title: String,
    unit: String,
    pid: String,
    modifier: Modifier = Modifier
) {
    TelemetryCard(
        telemetry = TelemetryPoint(
            id = "dummy",
            name = title,
            thaiName = title,
            value = 0.0,
            displayValue = "---",
            unit = unit,
            sourcePid = pid,
            quality = DataQuality.UNAVAILABLE
        ),
        modifier = modifier
    )
}
