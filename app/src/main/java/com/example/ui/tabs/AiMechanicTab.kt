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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DtcSeverity
import com.example.ui.DiagnosticUiState
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HighEnergyAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.PurpleProvenance
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark

@Composable
fun AiMechanicTab(
    uiState: DiagnosticUiState,
    onGenerateReportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val report = uiState.aiReport

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
                    text = "AI MECHANIC",
                    color = PurpleProvenance,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Powered by Gemini AI",
                    color = TextMutedDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Button(
                onClick = onGenerateReportClick,
                enabled = !uiState.isAiGenerating,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleProvenance)
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "ANALYZE")
            }
        }

        if (uiState.isAiGenerating) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PurpleProvenance)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Analyzing Diagnostic Telemetry...", color = PurpleProvenance, fontWeight = FontWeight.Bold)
                    Text(text = "Generating root cause and repair plan", color = TextMutedDark, fontSize = 12.sp)
                }
            }
        } else if (report == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI", tint = TextMutedDark, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "No AI Analysis Available", color = TextMutedDark, fontSize = 16.sp)
                    Text(text = "Run a DTC Scan or tap ANALYZE to generate", color = TextMutedDark, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Summary Block
                item {
                    val severityColor = when (report.severity) {
                        DtcSeverity.CRITICAL, DtcSeverity.HIGH -> CrimsonAlert
                        DtcSeverity.MEDIUM -> HighEnergyAmber
                        else -> NeonEmerald
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, severityColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .background(severityColor.copy(alpha = 0.1f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (report.severity == DtcSeverity.INFO || report.severity == DtcSeverity.LOW) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = severityColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SYSTEM SUMMARY",
                                color = severityColor,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${report.confidencePercentage}% Confidence",
                                color = TextMutedDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = report.summaryThai, color = TextPrimaryDark, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = report.summaryEnglish, color = TextMutedDark, fontSize = 12.sp)
                    }
                }

                // Safety Warning
                if (report.safetyWarningThai != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CrimsonAlert.copy(alpha = 0.2f))
                                .border(1.dp, CrimsonAlert, RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = CrimsonAlert)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = report.safetyWarningThai, color = CrimsonAlert, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Root Cause Analysis
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .padding(16.dp)
                    ) {
                        Text("ROOT CAUSE ANALYSIS", color = PurpleProvenance, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = report.rootCauseAnalysisThai, color = TextPrimaryDark, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = report.rootCauseAnalysisEnglish, color = TextMutedDark, fontSize = 13.sp)
                    }
                }

                // Action Plan
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .padding(16.dp)
                    ) {
                        Text("RECOMMENDED ACTION PLAN", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        report.actionPlanThai.forEachIndexed { index, action ->
                            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                                Text("${index + 1}.", color = CyberCyan, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                Text(action, color = TextPrimaryDark, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Build, contentDescription = "Cost", tint = HighEnergyAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Estimated Cost: ${report.estimatedRepairCostThb}", color = HighEnergyAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
