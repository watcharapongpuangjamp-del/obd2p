package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DiagnosticTab
import com.example.ui.DiagnosticViewModel
import com.example.ui.MainBottomNavigation
import com.example.ui.components.AdapterConnectionDialog
import com.example.ui.components.EvidenceDetailDialog
import com.example.ui.tabs.AiMechanicTab
import com.example.ui.tabs.DashboardTab
import com.example.ui.tabs.DtcScanTab
import com.example.ui.tabs.EcuDiscoveryTab
import com.example.ui.tabs.EvidenceLogTab
import com.example.ui.tabs.HistoryExportTab
import com.example.ui.theme.ThaiCarTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ThaiCarTheme {
        val viewModel: DiagnosticViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsState()

        val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
          viewModel.onBluetoothPermissionUpdated()
        }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            MainBottomNavigation(
              currentTab = uiState.currentTab,
              onTabSelected = { viewModel.selectTab(it) },
              faultCount = uiState.dtcScanResult.totalFaults
            )
          }
        ) { innerPadding ->
          when (uiState.currentTab) {
            DiagnosticTab.DASHBOARD -> {
              DashboardTab(
                uiState = uiState,
                onOpenAdapterManager = { viewModel.setAdapterSheetVisible(true) },
                onTogglePolling = { viewModel.toggleTelemetryPolling() },
                onSelectChartPid = { pidHex -> viewModel.selectChartPid(pidHex) },
                modifier = Modifier.padding(innerPadding)
              )
            }
            DiagnosticTab.DTC_SCAN -> {
              DtcScanTab(
                uiState = uiState,
                onScanClick = { viewModel.scanDtcs() },
                onClearClick = { viewModel.requestClearTroubleCodes() },
                onConfirmClear = { viewModel.executeClearTroubleCodes() },
                onDismissConfirmDialog = { viewModel.dismissClearConfirmDialog() },
                onDismissClearResult = { viewModel.dismissClearResult() },
                onLookupQueryChange = { query -> viewModel.updateDtcLookupQuery(query) },
                onLookupSubmit = { viewModel.lookupDtc() },
                onLookupSuggestionClick = { code -> viewModel.lookupDtc(code) },
                onClearLookup = { viewModel.clearDtcLookup() },
                onInspectDtcEvidence = { dtc ->
                  viewModel.inspectEvidenceByRef(
                    evidenceRef = dtc.evidenceRef,
                    fallbackCode = dtc.code,
                    fallbackEcuId = dtc.ecuId,
                    fallbackRawHex = dtc.rawHex
                  )
                },
                onEvidenceClick = { evidenceRef ->
                  viewModel.inspectEvidenceByRef(evidenceRef = evidenceRef)
                },
                modifier = Modifier.padding(innerPadding)
              )
            }
            DiagnosticTab.AI_MECHANIC -> {
              AiMechanicTab(
                uiState = uiState,
                onGenerateReportClick = { viewModel.generateAiReport() },
                modifier = Modifier.padding(innerPadding)
              )
            }
            DiagnosticTab.EVIDENCE_LOG -> {
              EvidenceLogTab(
                uiState = uiState,
                onEvidenceClick = { viewModel.showEvidenceDetail(it) },
                modifier = Modifier.padding(innerPadding)
              )
            }
            DiagnosticTab.ECU_DISCOVERY -> {
              EcuDiscoveryTab(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding)
              )
            }
            DiagnosticTab.HISTORY_EXPORT -> {
              HistoryExportTab(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding)
              )
            }
          }

          if (uiState.isAdapterSheetVisible) {
            AdapterConnectionDialog(
              uiState = uiState,
              onDismiss = { viewModel.setAdapterSheetVisible(false) },
              onRefreshAdapters = { viewModel.refreshAdapters() },
              onRequestBluetoothPermissions = {
                bluetoothPermissionLauncher.launch(viewModel.requiredBluetoothPermissions())
              },
              onRequestUsbPermission = { deviceId ->
                viewModel.requestUsbPermission(deviceId)
              },
              onConnectAdapter = { type, targetId ->
                viewModel.connectAdapter(type, targetId)
              },
              onReconnect = { viewModel.reconnectAdapter() },
              onDisconnect = { viewModel.disconnect() },
              onRequestStandardPid = { pidHex ->
                viewModel.requestStandardPid(pidHex)
              }
            )
          }

          val activeEvidence = uiState.selectedEvidence
          if (uiState.isEvidenceDialogVisible && activeEvidence != null) {
            EvidenceDetailDialog(
              evidence = activeEvidence,
              onDismiss = { viewModel.dismissEvidenceDialog() },
              onOpenEvidenceTab = { viewModel.selectTab(DiagnosticTab.EVIDENCE_LOG) }
            )
          }
        }
      }
    }
  }
}
