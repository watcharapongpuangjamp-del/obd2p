package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DataProvenance
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HighEnergyAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.PurpleProvenance
import com.example.ui.theme.TextMutedDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProvenanceBadge(
    provenance: DataProvenance,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (provenance) {
        DataProvenance.REAL_HARDWARE -> NeonEmerald to "REAL HW"
        DataProvenance.SIMULATOR -> PurpleProvenance to "SIMULATOR"
        DataProvenance.USER_ENTERED -> CyberCyan to "MANUAL"
        DataProvenance.HISTORICAL -> TextMutedDark to "CACHED"
        DataProvenance.ESTIMATED -> HighEnergyAmber to "EST"
        DataProvenance.AI_INFERRED -> CyberCyan to "AI"
        DataProvenance.UNKNOWN -> TextMutedDark to "UNKNOWN"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}

fun formatTelemetryTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0L) return "--:--:--"
    return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMs))
}
