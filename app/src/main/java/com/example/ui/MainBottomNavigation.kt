package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMutedDark

@Composable
fun MainBottomNavigation(
    currentTab: DiagnosticTab,
    onTabSelected: (DiagnosticTab) -> Unit,
    faultCount: Int
) {
    NavigationBar(
        containerColor = CarbonDark,
        contentColor = CyberCyan,
        tonalElevation = 8.dp
    ) {
        val tabs = listOf(
            DiagnosticTab.DASHBOARD to "DASHBOARD",
            DiagnosticTab.DTC_SCAN to "DTC SCAN",
            DiagnosticTab.AI_MECHANIC to "AI MECHANIC",
            DiagnosticTab.EVIDENCE_LOG to "EVIDENCE",
            DiagnosticTab.ECU_DISCOVERY to "MODULES",
            DiagnosticTab.HISTORY_EXPORT to "HISTORY"
        )
        
        tabs.forEach { (tab, label) ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Box {
                        Text(
                            text = label.take(2),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) CarbonDark else TextMutedDark
                        )
                        if (tab == DiagnosticTab.DTC_SCAN && faultCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(start = 12.dp, bottom = 12.dp)
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CrimsonAlert)
                            )
                        }
                    }
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (selected) CyberCyan else TextMutedDark
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CarbonDark,
                    unselectedIconColor = TextMutedDark,
                    selectedTextColor = CyberCyan,
                    unselectedTextColor = TextMutedDark,
                    indicatorColor = CyberCyan
                )
            )
        }
    }
}
