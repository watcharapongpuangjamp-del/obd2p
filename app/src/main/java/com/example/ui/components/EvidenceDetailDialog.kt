package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.EvidenceLog
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark

@Composable
fun EvidenceDetailDialog(
    evidence: EvidenceLog,
    onDismiss: () -> Unit,
    onOpenEvidenceTab: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.evidence_detail_title),
                    color = TextPrimaryDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                QualityBadge(quality = evidence.quality)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("evidence_detail_dialog"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProvenanceBadge(provenance = evidence.provenance)
                    Text(
                        text = "${formatTelemetryTimestamp(evidence.timestamp)} • ${evidence.latencyMs}ms",
                        color = TextMutedDark,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CarbonDark)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EvidenceField("Evidence ID", evidence.id)
                    EvidenceField("ECU Header", evidence.ecuHeader.ifBlank { "7E8" })
                    EvidenceField("Direction", evidence.direction.name)
                    EvidenceField("TX Command", evidence.commandText.ifBlank { "---" })
                    EvidenceField("RX Response", evidence.responseText.replace("\r", " ").trim().ifBlank { "---" })
                    EvidenceField("Raw Hex", evidence.rawBytesHex.replace("\r", " ").trim().ifBlank { "---" })
                    if (evidence.parsedMeaning.isNotBlank()) {
                        EvidenceField("Meaning", evidence.parsedMeaning, highlight = true)
                    }
                    if (evidence.sha256Hash.isNotBlank()) {
                        EvidenceField("SHA-256", evidence.sha256Hash.take(24) + "...")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onOpenEvidenceTab()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier.testTag("evidence_dialog_open_log_button")
            ) {
                Text(
                    text = stringResource(R.string.evidence_view_all_tab),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("evidence_dialog_close_button")
            ) {
                Text("CLOSE", color = TextMutedDark)
            }
        },
        containerColor = SurfaceVariantDark,
        tonalElevation = 6.dp
    )
}

@Composable
private fun EvidenceField(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            color = TextMutedDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = if (highlight) NeonEmerald else TextPrimaryDark,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
