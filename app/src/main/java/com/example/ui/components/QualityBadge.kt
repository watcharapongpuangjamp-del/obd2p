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
import com.example.core.model.DataQuality
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.HighEnergyAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMutedDark

@Composable
fun QualityBadge(quality: DataQuality, modifier: Modifier = Modifier) {
    val (color, text) = when (quality) {
        DataQuality.VALID -> NeonEmerald to "VALID"
        DataQuality.STALE -> HighEnergyAmber to "STALE"
        DataQuality.TIMEOUT -> CrimsonAlert to "TIMEOUT"
        DataQuality.INVALID -> CrimsonAlert to "ERR"
        DataQuality.UNAVAILABLE -> TextMutedDark to "N/A"
        DataQuality.UNSUPPORTED -> TextMutedDark to "UNSUP"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
