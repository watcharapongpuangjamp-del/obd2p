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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DtcClearResult
import com.example.core.model.DtcClearStatus
import com.example.core.model.DtcCode
import com.example.ui.DiagnosticUiState
import com.example.ui.components.DtcLookupPanel
import com.example.ui.components.ExpandableDtcCard
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HighEnergyAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark

@Composable
fun DtcScanTab(
    uiState: DiagnosticUiState,
    onScanClick: () -> Unit,
    onClearClick: () -> Unit,
    onConfirmClear: () -> Unit = {},
    onDismissConfirmDialog: () -> Unit = {},
    onDismissClearResult: () -> Unit = {},
    onLookupQueryChange: (String) -> Unit = {},
    onLookupSubmit: () -> Unit = {},
    onLookupSuggestionClick: (String) -> Unit = {},
    onClearLookup: () -> Unit = {},
    onInspectDtcEvidence: (DtcCode) -> Unit = {},
    onEvidenceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val result = uiState.dtcScanResult

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status & Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DTC SCAN ENGINE",
                    color = TextPrimaryDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "MIL Status: ${if (result.confirmedCodes.isNotEmpty()) "ON (Check Engine)" else "OFF"}",
                    color = if (result.confirmedCodes.isNotEmpty()) CrimsonAlert else NeonEmerald,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onScanClick,
                    enabled = !uiState.isDtcScanning && !uiState.isDtcClearing,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dtc_scan_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Scan DTCs", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "SCAN", color = CarbonDark, fontWeight = FontWeight.Bold)
                }

                if (result.totalFaults > 0 || uiState.lastClearResult != null) {
                    Button(
                        onClick = onClearClick,
                        enabled = !uiState.isDtcScanning && !uiState.isDtcClearing,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dtc_clear_button")
                    ) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear DTCs", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "CLEAR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Service 04 Clearing Progress Indicator
        if (uiState.isDtcClearing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, CrimsonAlert.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = CrimsonAlert,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    Column {
                        Text(
                            text = "Transmitting OBD-II Service 04 (04\\r)...",
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Awaiting positive response (44) or ECU status from target modules.",
                            color = TextMutedDark,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Service 04 Result / Evidence Card
        if (uiState.lastClearResult != null && !uiState.isDtcClearing) {
            ClearResultEvidenceCard(
                result = uiState.lastClearResult,
                onDismiss = onDismissClearResult,
                onEvidenceClick = onEvidenceClick
            )
        }

        if (uiState.isDtcScanning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = CyberCyan)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Querying All Modules via ISO 15765-4...",
                    color = TextMutedDark,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    color = CyberCyan,
                    trackColor = SurfaceVariantDark,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }
        }

        // Scrollable Fault Overview + DTC Lookup Knowledge Base + Scanned Expandable Fault Cards
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Fault Overview Summary
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    FaultSummaryBox(title = "CONFIRMED", count = result.confirmedCodes.size, color = CrimsonAlert)
                    FaultSummaryBox(title = "PENDING", count = result.pendingCodes.size, color = HighEnergyAmber)
                    FaultSummaryBox(title = "PERMANENT", count = result.permanentCodes.size, color = CyberCyan)
                }
            }

            // Local DTC Lookup & Knowledge Base Input
            item {
                DtcLookupPanel(
                    query = uiState.dtcLookupQuery,
                    suggestions = uiState.dtcSuggestions,
                    lookupResult = uiState.dtcLookupResult,
                    lookupError = uiState.dtcLookupError,
                    onQueryChange = onLookupQueryChange,
                    onLookupSubmit = onLookupSubmit,
                    onSuggestionClick = onLookupSuggestionClick,
                    onClearLookup = onClearLookup,
                    onInspectEvidence = onInspectDtcEvidence
                )
            }

            if (!uiState.isDtcScanning && result.totalFaults == 0 && result.timestamp > 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "No Faults",
                                tint = NeonEmerald,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "0 FAULTS DETECTED",
                                color = NeonEmerald,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "DTC memory clear. Diagnostic monitors verified.",
                                color = TextMutedDark,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Confirmed Codes
            if (result.confirmedCodes.isNotEmpty()) {
                item { SectionHeader("CONFIRMED FAULTS (Service 03 • MIL Active)") }
                items(result.confirmedCodes, key = { "CONFIRMED_${it.code}_${it.ecuId}" }) { dtc ->
                    ExpandableDtcCard(
                        dtc = dtc,
                        onInspectEvidence = { onInspectDtcEvidence(dtc) }
                    )
                }
            }

            // Pending Codes
            if (result.pendingCodes.isNotEmpty()) {
                item { SectionHeader("PENDING FAULTS (Service 07 • Awaiting Cycle)") }
                items(result.pendingCodes, key = { "PENDING_${it.code}_${it.ecuId}" }) { dtc ->
                    ExpandableDtcCard(
                        dtc = dtc,
                        onInspectEvidence = { onInspectDtcEvidence(dtc) }
                    )
                }
            }

            // Permanent Codes
            if (result.permanentCodes.isNotEmpty()) {
                item { SectionHeader("PERMANENT FAULTS (Service 0A • Drive Cycle Lock)") }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(HighEnergyAmber.copy(alpha = 0.1f))
                            .border(1.dp, HighEnergyAmber.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Permanent DTC Notice",
                                tint = HighEnergyAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "รหัสถาวร (Permanent DTCs) เป็นไปตามข้อบังคับ SAE J1979 ไม่สามารถลบได้ด้วยคำสั่ง Service 04 กล่อง ECU จะลบรหัสออกเองโดยอัตโนมัติเมื่อขับขี่ครบ Drive Cycle และไม่พบความผิดปกติซ้ำ",
                                color = TextPrimaryDark,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                items(result.permanentCodes, key = { "PERMANENT_${it.code}_${it.ecuId}" }) { dtc ->
                    ExpandableDtcCard(
                        dtc = dtc,
                        onInspectEvidence = { onInspectDtcEvidence(dtc) }
                    )
                }
            }
        }
    }

    // Confirmation Dialog before executing Service 04 Clear DTCs
    if (uiState.isClearConfirmDialogVisible) {
        AlertDialog(
            onDismissRequest = onDismissConfirmDialog,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Clear DTC warning",
                    tint = CrimsonAlert,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Clear DTCs (Service 04)?",
                    color = TextPrimaryDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "คำสั่งนี้จะส่ง OBD-II Service 04 (04\\r) ไปยังกล่อง ECU ของรถยนต์:",
                        color = TextPrimaryDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• ลบรหัสข้อผิดพลาดทั้งหมดในหน่วยความจำชั่วคราว\n• รีเซ็ตไฟเตือนรูปเครื่องยนต์ (Check Engine / MIL) ให้ดับลง\n• ลบสแนปช็อตสภาวะการทำงานของเครื่องยนต์ (Freeze Frame Data)\n• รีเซ็ตสถานะระบบตรวจวัดมลพิษ (I/M Readiness Monitors)",
                        color = TextMutedDark,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ข้อแนะนำ: ควรทำขณะเปิดสวิตช์กุญแจ ON และดับเครื่องยนต์ (Key-On Engine-Off / KOEO) รถบางรุ่นจะปฏิเสธคำสั่ง (NRC 22) หากเครื่องยนต์ยังติดอยู่",
                        color = HighEnergyAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmClear,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    modifier = Modifier.testTag("confirm_clear_dtc_button")
                ) {
                    Text("CLEAR CODES", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissConfirmDialog,
                    modifier = Modifier.testTag("cancel_clear_dtc_button")
                ) {
                    Text("CANCEL", color = TextMutedDark)
                }
            },
            containerColor = SurfaceVariantDark,
            tonalElevation = 6.dp
        )
    }
}

@Composable
private fun ClearResultEvidenceCard(
    result: DtcClearResult,
    onDismiss: () -> Unit,
    onEvidenceClick: (String) -> Unit
) {
    val borderColor = if (result.isSuccess) NeonEmerald else CrimsonAlert
    val statusBg = if (result.isSuccess) NeonEmerald.copy(alpha = 0.12f) else CrimsonAlert.copy(alpha = 0.12f)
    val statusTitle = when (result.status) {
        DtcClearStatus.SUCCESS -> "SERVICE 04 SUCCESS (44 OK)"
        DtcClearStatus.ECU_REJECTED -> "ECU REJECTED (NRC ${result.negativeResponseCode ?: "7F"})"
        DtcClearStatus.NO_DATA -> "NO DATA RECEIVED"
        DtcClearStatus.COMMUNICATION_ERROR -> "COMMUNICATION ERROR"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(statusBg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Clear result status",
                        tint = borderColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusTitle,
                        color = borderColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss clear result", tint = TextMutedDark, modifier = Modifier.size(16.dp))
                }
            }

            Text(
                text = if (result.isSuccess) result.confirmationMessageThai else (result.errorMessageThai ?: "การล้างรหัสล้มเหลว"),
                color = TextPrimaryDark,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonDark.copy(alpha = 0.7f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Command TX: ${result.commandTx}", color = CyberCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Target ECU: ${result.ecuHeader}", color = TextMutedDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Response RX: ${result.responseRx.replace("\r", " ").trim()}", color = TextPrimaryDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Latency: ${result.latencyMs}ms", color = TextMutedDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SHA-256: ${result.sha256Hash.take(12)}...", color = TextMutedDark, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("Source: ${result.provenance}", color = NeonEmerald, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Inspect Evidence →",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag("clear_result_inspect_evidence")
                        .minimumInteractiveComponentSize()
                        .clickable { onEvidenceClick(result.evidenceRef) }
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FaultSummaryBox(title: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            color = if (count > 0) color else TextMutedDark,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = TextMutedDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextMutedDark,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}
