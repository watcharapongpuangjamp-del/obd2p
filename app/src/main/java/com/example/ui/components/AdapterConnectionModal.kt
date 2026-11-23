package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.core.elm327.AdapterDeviceInfo
import com.example.core.elm327.AdapterType
import com.example.core.transport.TransportState
import com.example.ui.DiagnosticUiState
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HighEnergyAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark

@Composable
fun AdapterConnectionBar(
    uiState: DiagnosticUiState,
    onOpenAdapterManager: () -> Unit,
    onTogglePolling: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = uiState.transportState
    val (statusLabel, statusColor) = when (state) {
        is TransportState.Connected -> {
            val modePrefix = when (uiState.adapterType) {
                AdapterType.BLUETOOTH_CLASSIC -> "BT"
                AdapterType.USB_SERIAL -> "USB"
                AdapterType.REPLAY_SIMULATOR -> "SIM"
            }
            "$modePrefix • ${state.deviceName}" to NeonEmerald
        }
        is TransportState.Connecting -> "CONNECTING • ${state.deviceName}" to HighEnergyAmber
        is TransportState.Reconnecting -> "RECONNECTING (${state.attempt}/${state.maxAttempts})" to HighEnergyAmber
        is TransportState.PermissionRequired -> "USB PERMIT • ${state.deviceName}" to HighEnergyAmber
        is TransportState.Timeout -> "TIMEOUT • ${state.commandTx}" to HighEnergyAmber
        is TransportState.Error -> "ERROR • ${state.failureKind.name}" to CrimsonAlert
        TransportState.Disconnected -> "DISCONNECTED" to TextMutedDark
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(10.dp))
            .clickable { onOpenAdapterManager() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("adapter_status_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = statusLabel,
                    color = TextPrimaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${uiState.activeProtocol} • ${uiState.batteryVoltage} • ${uiState.averageLatencyMs}ms",
                    color = TextMutedDark,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (uiState.isPolling) "LIVE" else "PAUSED",
                color = if (uiState.isPolling) NeonEmerald else HighEnergyAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        (if (uiState.isPolling) NeonEmerald else HighEnergyAmber).copy(alpha = 0.15f)
                    )
                    .clickable { onTogglePolling() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("toggle_polling_chip")
            )
            Text(
                text = "ADAPTER",
                color = CyberCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyberCyan.copy(alpha = 0.15f))
                    .clickable { onOpenAdapterManager() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("open_adapter_manager_button")
            )
        }
    }
}

@Composable
fun AdapterConnectionDialog(
    uiState: DiagnosticUiState,
    onDismiss: () -> Unit,
    onRefreshAdapters: () -> Unit,
    onRequestBluetoothPermissions: () -> Unit,
    onRequestUsbPermission: (Int) -> Unit,
    onConnectAdapter: (AdapterType, String?) -> Unit,
    onReconnect: () -> Unit = {},
    onDisconnect: () -> Unit,
    onRequestStandardPid: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .heightIn(max = 640.dp)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.adapter_manager_title),
                            color = CyberCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Bluetooth SPP / USB-Serial OTG / Replay",
                            color = TextMutedDark,
                            fontSize = 11.sp
                        )
                    }
                    Row {
                        IconButton(
                            onClick = onRefreshAdapters,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.adapter_refresh),
                                tint = CyberCyan
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMutedDark
                            )
                        }
                    }
                }

                // Transport Status Banner
                val statusBannerColor = when (uiState.transportState) {
                    is TransportState.Connected -> NeonEmerald
                    is TransportState.Connecting,
                    is TransportState.Reconnecting,
                    is TransportState.PermissionRequired,
                    is TransportState.Timeout -> HighEnergyAmber
                    is TransportState.Error -> CrimsonAlert
                    TransportState.Disconnected -> TextMutedDark
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBannerColor.copy(alpha = 0.12f))
                        .border(1.dp, statusBannerColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val title = when (val st = uiState.transportState) {
                                is TransportState.Connected -> "Connected: ${st.deviceName} (${st.baudRate} bps)"
                                is TransportState.Connecting -> "Connecting to ${st.deviceName}..."
                                is TransportState.Reconnecting -> "Reconnecting ${st.deviceName} (${st.attempt}/${st.maxAttempts})..."
                                is TransportState.PermissionRequired -> st.message
                                is TransportState.Timeout -> st.message
                                is TransportState.Error -> "${st.failureKind.name}: ${st.message}"
                                TransportState.Disconnected -> "No active ELM327 session"
                            }
                            Text(
                                text = title,
                                color = statusBannerColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "FW: ${uiState.adapterVersion} | Proto: ${uiState.activeProtocol}",
                                color = TextPrimaryDark,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        when (val st = uiState.transportState) {
                            is TransportState.Connected -> {
                                OutlinedButton(
                                    onClick = onDisconnect,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("disconnect_adapter_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.adapter_disconnect),
                                        color = CrimsonAlert,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            is TransportState.PermissionRequired -> {
                                if (st.deviceId != null) {
                                    Button(
                                        onClick = { onRequestUsbPermission(st.deviceId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = HighEnergyAmber),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("grant_usb_permission_button")
                                    ) {
                                        Text(
                                            text = "PERMIT USB",
                                            color = CarbonDark,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            is TransportState.Error, is TransportState.Timeout -> {
                                OutlinedButton(
                                    onClick = onReconnect,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("reconnect_adapter_button")
                                ) {
                                    Text(
                                        text = "RECONNECT",
                                        color = CyberCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            is TransportState.Connecting, is TransportState.Reconnecting -> {
                                CircularProgressIndicator(
                                    color = HighEnergyAmber,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            TransportState.Disconnected -> Unit
                        }
                    }
                }

                // Bluetooth Permission Notice if missing
                if (!uiState.hasBluetoothPermission) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberBlue.copy(alpha = 0.12f))
                            .border(1.dp, CyberBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bluetooth Permission Needed",
                                color = TextPrimaryDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Allow Bluetooth Connect & Scan to discover paired ELM327 adapters.",
                                color = TextMutedDark,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestBluetoothPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("request_bt_permission_button")
                        ) {
                            Text("ALLOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Available Adapters List
                Text(
                    text = "AVAILABLE ADAPTERS (${uiState.availableAdapters.size})",
                    color = TextMutedDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.availableAdapters, key = { "${it.type}_${it.id}" }) { device ->
                        val isActive = uiState.activeDevice?.id == device.id &&
                            uiState.transportState is TransportState.Connected
                        AdapterDeviceRow(
                            device = device,
                            isActive = isActive,
                            onConnectClick = {
                                if (device.type == AdapterType.USB_SERIAL && !device.hasPermission) {
                                    device.id.toIntOrNull()?.let { onRequestUsbPermission(it) }
                                } else {
                                    onConnectAdapter(device.type, device.id)
                                }
                            }
                        )
                    }
                }

                // Quick Standard PID Probe
                Text(
                    text = stringResource(R.string.standard_pid_probe),
                    color = TextMutedDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                val quickPids = listOf(
                    "0C" to "0C RPM",
                    "05" to "05 ECT",
                    "0D" to "0D SPD",
                    "04" to "04 LOAD",
                    "0B" to "0B MAP",
                    "11" to "11 TPS",
                    "42" to "42 VOLT"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPids.forEach { (pidHex, label) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(6.dp))
                                .clickable { onRequestStandardPid(pidHex) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("pid_quick_query_chip_$pidHex")
                        ) {
                            Text(
                                text = label,
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Display latest Standard PID raw TX/RX result if present
                uiState.lastStandardPidResult?.let { pidRes ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CarbonDark)
                            .border(
                                1.dp,
                                if (pidRes.isSuccess) NeonEmerald.copy(alpha = 0.45f) else CrimsonAlert.copy(alpha = 0.45f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TX: ${pidRes.commandTx}  •  ECU: ${pidRes.elmResponse.ecuHeader}",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${pidRes.elmResponse.latencyMs}ms",
                                color = TextMutedDark,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "RX: ${pidRes.elmResponse.rawResponse.replace("\r", " ").trim()}",
                            color = TextPrimaryDark,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        val pt = pidRes.telemetryPoint
                        if (pidRes.isSuccess && pt != null) {
                            Text(
                                text = "Decoded: ${pt.name} = ${pt.displayValue} ${pt.unit} [${pt.provenance}]",
                                color = NeonEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Status: ${pidRes.errorMessage ?: "Unavailable"}",
                                color = CrimsonAlert,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (uiState.supportedPids.isNotEmpty()) {
                    Text(
                        text = "ECU Supported PIDs (${uiState.supportedPids.size}): ${uiState.supportedPids.take(12).joinToString(", ")}${if (uiState.supportedPids.size > 12) "..." else ""}",
                        color = TextMutedDark,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun AdapterDeviceRow(
    device: AdapterDeviceInfo,
    isActive: Boolean,
    onConnectClick: () -> Unit
) {
    val icon = when (device.type) {
        AdapterType.BLUETOOTH_CLASSIC -> Icons.Default.Bluetooth
        AdapterType.USB_SERIAL -> Icons.Default.Usb
        AdapterType.REPLAY_SIMULATOR -> Icons.Default.Memory
    }
    val tintColor = when {
        isActive -> NeonEmerald
        device.isLikelyObdAdapter -> CyberCyan
        else -> TextMutedDark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariantDark)
            .border(
                1.dp,
                if (isActive) NeonEmerald else SurfaceBorderDark,
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = device.type.label,
                tint = tintColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = device.name,
                    color = TextPrimaryDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${device.addressOrPort} • ${device.detail}",
                    color = TextMutedDark,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onConnectClick,
            enabled = !isActive,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) NeonEmerald.copy(alpha = 0.2f) else CyberCyan,
                contentColor = if (isActive) NeonEmerald else CarbonDark
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("connect_adapter_button_${device.id}")
        ) {
            val btnText = when {
                isActive -> "ACTIVE"
                device.type == AdapterType.USB_SERIAL && !device.hasPermission -> "PERMIT"
                else -> stringResource(R.string.adapter_connect)
            }
            Text(text = btnText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
