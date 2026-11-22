package com.example.ui.tabs

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.EvidenceDirection
import com.example.core.model.EvidenceLog
import com.example.ui.DiagnosticUiState
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HighEnergyAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.PurpleProvenance
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EvidenceLogTab(
    uiState: DiagnosticUiState,
    onEvidenceClick: (EvidenceLog) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "EVIDENCE LOG",
                    color = TextPrimaryDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${uiState.evidenceLogs.size} Packets Recorded",
                    color = TextMutedDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (uiState.evidenceLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.List, contentDescription = "Log", tint = TextMutedDark, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "No evidence recorded yet", color = TextMutedDark, fontSize = 16.sp)
                    Text(text = "Connect to an OBD adapter to begin", color = TextMutedDark, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.evidenceLogs) { log ->
                    EvidenceLogItem(log = log, onClick = { onEvidenceClick(log) })
                }
            }
        }
    }
}

@Composable
private fun EvidenceLogItem(log: EvidenceLog, onClick: () -> Unit) {
    val isTx = log.direction == EvidenceDirection.TX
    val dirColor = if (isTx) HighEnergyAmber else CyberCyan
    val dirIcon = if (isTx) Icons.Default.Send else Icons.Default.Code
    val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    val timeStr = timeFormatter.format(Date(log.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = dirIcon, contentDescription = null, tint = dirColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = if (isTx) "TX" else "RX", color = dirColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = timeStr, color = TextMutedDark, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isTx) log.commandText else log.responseText,
                color = TextPrimaryDark,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            if (log.parsedMeaning.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = log.parsedMeaning, color = NeonEmerald, fontSize = 11.sp, maxLines = 1)
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "${log.latencyMs}ms", color = TextMutedDark, fontSize = 10.sp)
            Text(text = log.ecuHeader.takeIf { it.isNotBlank() } ?: "---", color = TextMutedDark, fontSize = 10.sp)
        }
    }
}
