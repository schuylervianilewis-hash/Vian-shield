package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SkyBlueBackground
import com.example.ui.theme.SkyBlueOnPrimaryContainer
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SkyBluePrimaryContainer
import com.example.ui.theme.SkyBlueSurface
import com.example.ui.theme.SkyBlueSurfaceBorder
import com.example.ui.theme.SkyBlueTextPrimary
import com.example.ui.theme.SkyBlueTextSecondary
import com.example.ui.theme.SkyBlueTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit = {},
    onOpenLogKeeper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val isLoggingEnabled by viewModel.isLoggingEnabled.collectAsState()
    val logEvents by viewModel.rawLogEvents.collectAsState()
    val diskSizeBytes = viewModel.getDiskLogSizeBytes()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SkyBlueBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        color = SkyBlueTextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkyBlueSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLogKeeper)
                    .testTag("settings_open_log_keeper_button"),
                shape = RoundedCornerShape(14.dp),
                color = SkyBlueSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(10.dp), color = SkyBluePrimaryContainer) {
                            Icon(
                                imageVector = Icons.Default.ListAlt,
                                contentDescription = null,
                                tint = SkyBlueOnPrimaryContainer,
                                modifier = Modifier.padding(9.dp).size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Logs & Diagnostics",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = SkyBlueTextPrimary
                        )
                        Text(
                            text = "${if (isLoggingEnabled) "Logging on" else "Logging off"} · ${logEvents.size} recent events · ${diskSizeBytes / 1024} KB stored",
                            fontSize = 11.sp,
                            color = SkyBlueTextSecondary
                        )
                    }
                    Text("›", fontSize = 24.sp, color = SkyBluePrimary)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SkyBlueTextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "About Vianshield",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = SkyBlueTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsInfoRow("Version", "1.0")
                    HorizontalDivider(color = SkyBlueSurfaceBorder, modifier = Modifier.padding(vertical = 6.dp))
                    SettingsInfoRow("VPN engine", "TrackerControl / NetGuard + GotaTun")
                    HorizontalDivider(color = SkyBlueSurfaceBorder, modifier = Modifier.padding(vertical = 6.dp))
                    SettingsInfoRow("Architecture", "ARMv7 · ARM64")
                    HorizontalDivider(color = SkyBlueSurfaceBorder, modifier = Modifier.padding(vertical = 6.dp))
                    SettingsInfoRow("Privacy", "On-device processing · no browsing telemetry")
                }
            }
            Text(
                text = "Preferences and backup actions will appear here when connected to real behaviour.",
                fontSize = 11.sp,
                color = SkyBlueTextTertiary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = SkyBlueTextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SkyBlueTextPrimary)
    }
}
