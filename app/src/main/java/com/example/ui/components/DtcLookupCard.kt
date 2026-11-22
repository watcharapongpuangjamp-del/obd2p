package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.DtcCode
import com.example.core.model.DtcLookupResult
import com.example.core.model.DtcOnlineResource
import com.example.core.model.DtcSeverity
import com.example.core.model.DtcType
import com.example.core.protocol.DtcKnowledgeEntry
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
fun DtcLookupPanel(
    query: String,
    suggestions: List<DtcKnowledgeEntry>,
    lookupResult: DtcLookupResult?,
    lookupError: String?,
    onQueryChange: (String) -> Unit,
    onLookupSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onClearLookup: () -> Unit,
    onInspectEvidence: (DtcCode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.dtc_lookup_title),
            color = CyberCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.dtc_lookup_hint),
                        color = TextMutedDark,
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search DTC",
                        tint = CyberCyan
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank() || lookupResult != null) {
                        IconButton(
                            onClick = onClearLookup,
                            modifier = Modifier.testTag("dtc_lookup_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear DTC lookup",
                                tint = TextMutedDark
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onLookupSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = SurfaceBorderDark,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    cursorColor = CyberCyan
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("dtc_lookup_input")
            )

            Button(
                onClick = onLookupSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dtc_lookup_button")
            ) {
                Text(
                    text = stringResource(R.string.dtc_lookup_action),
                    color = CarbonDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(suggestions) { entry ->
                    Box(
                        modifier = Modifier
                            .testTag("dtc_suggestion_${entry.code}")
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonDark.copy(alpha = 0.75f))
                            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(6.dp))
                            .clickable { onSuggestionClick(entry.code) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = entry.code,
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (lookupError != null) {
            Text(
                text = lookupError,
                color = CrimsonAlert,
                fontSize = 12.sp,
                modifier = Modifier.testTag("dtc_lookup_error")
            )
        }

        if (lookupResult != null) {
            DtcLookupResultCard(
                result = lookupResult,
                onInspectEvidence = onInspectEvidence
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DtcLookupResultCard(
    result: DtcLookupResult,
    onInspectEvidence: (DtcCode) -> Unit
) {
    val severityColor = severityColor(result.severity)
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dtc_lookup_result_card")
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonDark.copy(alpha = 0.9f))
            .border(1.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.code,
                    color = severityColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                StatusPill(
                    label = if (result.isExactLocalMatch) "LOCAL DB" else "SAE J2012",
                    color = if (result.isExactLocalMatch) NeonEmerald else PurpleProvenance
                )
            }
            StatusPill(
                label = result.severity.name,
                color = severityColor
            )
        }

        // Evidence Chain & ECU Scan Status Linkage Banner
        if (result.matchedScannedDtcs.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CrimsonAlert.copy(alpha = 0.12f))
                    .border(1.dp, CrimsonAlert.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ACTIVE IN VEHICLE DTC ENGINE",
                    color = CrimsonAlert,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                result.matchedScannedDtcs.forEach { matched ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DtcTypeBadge(type = matched.type)
                            Text(
                                text = "ECU ${matched.ecuId} • ${matched.rawHex}",
                                color = TextPrimaryDark,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = stringResource(R.string.dtc_inspect_evidence),
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable { onInspectEvidence(matched) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            StatusPill(
                label = "STATUS: LOCAL REFERENCE (NOT DETECTED IN LAST SCAN)",
                color = TextMutedDark
            )
        }

        Text(
            text = result.thaiTitle,
            color = TextPrimaryDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${result.englishDescription} • ${result.system}",
            color = TextMutedDark,
            fontSize = 12.sp
        )

        DtcSectionBlock(
            title = "คำอธิบาย (Explanation)",
            body = result.thaiExplanation,
            accent = TextPrimaryDark
        )

        DtcSectionBlock(
            title = "ผลกระทบและความเสี่ยง (Implications)",
            body = result.implicationsThai,
            accent = HighEnergyAmber
        )

        DtcBulletList(
            title = "สาเหตุที่เป็นไปได้ (Potential Causes)",
            items = result.potentialCausesThai,
            titleColor = CyberCyan
        )

        DtcBulletList(
            title = "แนวทางตรวจสอบเบื้องต้น (Inspection Steps)",
            items = result.inspectionStepsThai,
            titleColor = NeonEmerald
        )

        if (result.onlineResources.isNotEmpty()) {
            OnlineResourceButtons(
                resources = result.onlineResources,
                onOpenUrl = { url -> uriHandler.openUri(url) }
            )
        }
    }
}

@Composable
fun ExpandableDtcCard(
    dtc: DtcCode,
    onInspectEvidence: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable(dtc.code, dtc.type.name) { mutableStateOf(false) }
    val severityColor = severityColor(dtc.severity)
    val icon = when (dtc.severity) {
        DtcSeverity.CRITICAL, DtcSeverity.HIGH -> Icons.Default.Warning
        else -> Icons.Default.Info
    }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dtc_card_${dtc.type.name.lowercase()}_${dtc.code}")
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(10.dp))
            .clickable { isExpanded = !isExpanded }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "DTC severity icon",
                    tint = severityColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = dtc.code,
                    color = TextPrimaryDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                DtcTypeBadge(type = dtc.type)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ECU ${dtc.ecuId}",
                    color = TextMutedDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse DTC details" else "Expand DTC details",
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = dtc.thaiTitle,
            color = TextPrimaryDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = dtc.englishDescription,
            color = TextMutedDark,
            fontSize = 13.sp
        )

        DtcBulletList(
            title = "สาเหตุที่เป็นไปได้:",
            items = if (isExpanded) dtc.potentialCausesThai else dtc.potentialCausesThai.take(2),
            titleColor = CyberCyan
        )

        AnimatedVisibility(visible = isExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                DtcSectionBlock(
                    title = "คำอธิบายการทำงาน:",
                    body = dtc.thaiExplanation,
                    accent = TextPrimaryDark
                )

                if (dtc.implicationsThai.isNotBlank()) {
                    DtcSectionBlock(
                        title = "ผลกระทบและความเสี่ยง:",
                        body = dtc.implicationsThai,
                        accent = HighEnergyAmber
                    )
                }

                if (dtc.symptomsThai.isNotEmpty()) {
                    DtcBulletList(
                        title = "อาการที่พบได้บ่อย:",
                        items = dtc.symptomsThai,
                        titleColor = TextPrimaryDark
                    )
                }

                if (dtc.inspectionStepsThai.isNotEmpty()) {
                    DtcBulletList(
                        title = "แนวทางตรวจสอบ:",
                        items = dtc.inspectionStepsThai,
                        titleColor = NeonEmerald
                    )
                }

                if (dtc.onlineResources.isNotEmpty()) {
                    OnlineResourceButtons(
                        resources = dtc.onlineResources,
                        onOpenUrl = { url -> uriHandler.openUri(url) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Raw: ${dtc.rawHex.ifBlank { "---" }} • ${dtc.evidenceRef.take(18)}",
                color = TextMutedDark.copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${stringResource(R.string.dtc_inspect_evidence)} →",
                color = CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .testTag("dtc_inspect_evidence_${dtc.code}")
                    .minimumInteractiveComponentSize()
                    .clickable { onInspectEvidence() }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineResourceButtons(
    resources: List<DtcOnlineResource>,
    onOpenUrl: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "แหล่งข้อมูลออนไลน์เพิ่มเติม (Online Resources):",
            color = CyberCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            resources.forEach { res ->
                OutlinedButton(
                    onClick = { onOpenUrl(res.url) },
                    modifier = Modifier
                        .testTag("dtc_resource_link_${res.label}")
                        .minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open external guide ${res.label}",
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = res.label,
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DtcSectionBlock(
    title: String,
    body: String,
    accent: Color
) {
    if (body.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = CyberCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            color = accent,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun DtcBulletList(
    title: String,
    items: List<String>,
    titleColor: Color
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        items.forEach { item ->
            Text(
                text = "• $item",
                color = TextMutedDark,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
fun DtcTypeBadge(type: DtcType) {
    val (color, label) = when (type) {
        DtcType.CONFIRMED -> CrimsonAlert to "CONFIRMED"
        DtcType.PENDING -> HighEnergyAmber to "PENDING"
        DtcType.PERMANENT -> CyberCyan to "PERMANENT"
    }
    StatusPill(label = label, color = color)
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}

private fun severityColor(severity: DtcSeverity): Color = when (severity) {
    DtcSeverity.CRITICAL, DtcSeverity.HIGH -> CrimsonAlert
    DtcSeverity.MEDIUM -> HighEnergyAmber
    DtcSeverity.LOW -> CyberCyan
    DtcSeverity.INFO -> TextMutedDark
}
