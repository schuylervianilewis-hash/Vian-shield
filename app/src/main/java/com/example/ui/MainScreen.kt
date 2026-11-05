package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SkyBlueBackground
import com.example.ui.theme.SkyBlueOnPrimaryContainer
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SkyBluePrimaryContainer
import com.example.ui.theme.SkyBlueSurface
import com.example.ui.theme.SkyBlueSurfaceBorder
import com.example.ui.theme.SkyBlueSurfaceVariant
import com.example.ui.theme.SkyBlueTextPrimary
import com.example.ui.theme.SkyBlueTextSecondary
import com.example.ui.theme.SkyBlueTextTertiary
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusStarting
import com.example.ui.theme.StatusStopped
import com.example.vpn.VpnState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: DiagnosticViewModel,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val vpnState by viewModel.vpnState.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SkyBlueBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Vianshield",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = SkyBlueTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SkyBluePrimaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "Runtime Pending",
                                color = SkyBlueOnPrimaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = SkyBlueSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ================================================================
            // ORDER MANDATED BY SPECIFICATION:
            // 1. Master protection switch
            // 2. Speed-throttling preset
            // 3. DNS changer
            // 4. Ad filter
            // 5. Ad-filter allow-list
            // 6. Actual protection status and relevant errors
            // ================================================================

            // 1. Master Protection Switch
            PrimaryActionButton(
                vpnState = vpnState,
                activeProfile = activeProfile,
                onUnavailable = {
                    Toast.makeText(
                        context,
                        "Firewall protection is unavailable until the VPN/firewall runtime is integrated. No protection is active.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )

            // 2. Speed-Throttling Preset — visible, not actionable until enforcement exists.
            DashboardControlRow(
                icon = Icons.Default.Speed,
                title = "Speed-Throttling Preset",
                value = "Not available until rate-limiting enforcement is implemented",
                statusTag = "Unavailable",
                onClick = {
                    Toast.makeText(
                        context,
                        "Speed presets are not applied yet. Rate-limiting enforcement is not implemented.",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                testTag = "main_speed_preset_row"
            )

            // 3. DNS Changer — selecting a provider cannot change system/engine DNS yet.
            DashboardControlRow(
                icon = Icons.Default.Dns,
                title = "DNS Changer",
                value = "DNS selection is not active yet",
                statusTag = "Unavailable",
                onClick = {
                    Toast.makeText(
                        context,
                        "DNS selection is not applied yet. DNS runtime integration is pending.",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                testTag = "main_dns_changer_row"
            )

            // 4. Ad Filter — keep the control visible, but do not allow a false enabled state.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Toast.makeText(
                            context,
                            "Ad and tracker filtering is not active. Filtering rules and runtime enforcement are not implemented.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurfaceVariant.copy(alpha = 0.55f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SkyBlueSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = SkyBlueTextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Ad & Tracker Filter",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = SkyBlueTextSecondary
                            )
                            Text(
                                text = "Filtering is not active — runtime not integrated",
                                fontSize = 11.sp,
                                color = SkyBlueTextTertiary
                            )
                        }
                    }

                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        enabled = false,
                        colors = SwitchDefaults.colors(
                            disabledCheckedThumbColor = Color.White,
                            disabledCheckedTrackColor = SkyBlueSurfaceBorder,
                            disabledUncheckedThumbColor = Color.White,
                            disabledUncheckedTrackColor = SkyBlueSurfaceBorder
                        ),
                        modifier = Modifier.testTag("main_ad_filter_switch")
                    )
                }
            }

            // 5. Ad-Filter Allow-List — policy UI is staged until the filter exists.
            DashboardControlRow(
                icon = Icons.Default.Security,
                title = "Ad-Filter Allow-List",
                value = "Domain exemptions are not available yet",
                statusTag = "Unavailable",
                onClick = {
                    Toast.makeText(
                        context,
                        "The ad-filter allow-list is not active yet because the filtering runtime is not integrated.",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                testTag = "main_ad_allowlist_row"
            )

            // 6. Blocked-domain activity is a required entry point, pending its data pipeline.
            DashboardControlRow(
                icon = Icons.Default.Block,
                title = "Blocked domains",
                value = "Blocked-domain activity is not available yet",
                statusTag = "Unavailable",
                onClick = {
                    Toast.makeText(
                        context,
                        "Blocked-domain activity is not available yet. DNS/ad-filter event collection is not integrated.",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                testTag = "main_blocked_domains_row"
            )

            // 6. Actual Protection Status and Relevant Errors
            ConnectionStatusCard(
                vpnState = vpnState,
                activeProfile = activeProfile
            )

            // Error Banner (shown if VPN is in Error state)
            AnimatedVisibility(visible = vpnState is VpnState.Error) {
                if (vpnState is VpnState.Error) {
                    val errorState = vpnState as VpnState.Error
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "VPN Startup Failed",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B),
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errorState.message,
                                fontSize = 12.sp,
                                color = Color(0xFF7F1D1D)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onNavigate(AppScreen.DIAGNOSTICS) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusError),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("View Diagnostics", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DashboardControlRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    statusTag: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Looks disabled, but remains tappable solely to explain why it is unavailable.
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = SkyBlueSurfaceVariant.copy(alpha = 0.55f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SkyBlueSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SkyBlueTextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = SkyBlueTextSecondary
                    )
                    Text(
                        text = value,
                        fontSize = 11.sp,
                        color = SkyBlueTextTertiary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SkyBlueSurface
            ) {
                Text(
                    text = statusTag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = SkyBlueTextTertiary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    vpnState: VpnState,
    activeProfile: com.example.config.WireGuardProfile?,
    onUnavailable: () -> Unit
) {
    val isConnected = vpnState is VpnState.Connected
    val isTransitioning = vpnState is VpnState.Starting || vpnState is VpnState.Stopping

    val iconColor = when {
        isConnected -> StatusConnected
        isTransitioning -> StatusStarting
        vpnState is VpnState.Error -> StatusError
        else -> Color(0xFF94A3B8)
    }

    val (statusLabel, statusTag) = when {
        vpnState is VpnState.Starting -> "CONNECTING" to "Negotiating"
        vpnState is VpnState.Stopping -> "DISCONNECTING" to "Teardown"
        isConnected -> "FIREWALL ACTIVE" to "Protected"
        vpnState is VpnState.Error -> "PROTECTION ERROR" to "Failed"
        vpnState is VpnState.RuntimePending -> "FIREWALL PENDING" to "Integration pending"
        else -> "FIREWALL OFF" to "Standby"
    }

    val subtitle = when {
        isConnected -> {
            val endpoint = (vpnState as? VpnState.Connected)?.endpoint
                ?: activeProfile?.let { "${it.endpointHost}:${it.endpointPort}" }
                ?: "WireGuard Tunnel"
            "Tunneling through $endpoint"
        }
        vpnState is VpnState.Starting -> "Establishing secure UDP tunnel..."
        vpnState is VpnState.Stopping -> "Stopping VPN tunnel..."
        vpnState is VpnState.Error -> "Failed to establish tunnel"
        vpnState is VpnState.RuntimePending -> "Firewall runtime integration is pending. No protection is active."
        else -> "Firewall runtime integration is pending. No protection is active."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConnected) StatusConnected.copy(alpha = 0.4f) else SkyBlueSurfaceBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status Tag Header
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isConnected -> Color(0xFFDCFCE7)
                    isTransitioning -> Color(0xFFFEF3C7)
                    vpnState is VpnState.Error -> Color(0xFFFEE2E2)
                    else -> SkyBlueSurfaceVariant
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isConnected -> StatusConnected
                                    isTransitioning -> StatusStarting
                                    vpnState is VpnState.Error -> StatusError
                                    else -> SkyBlueTextTertiary
                                }
                            )
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isConnected -> StatusConnected
                            isTransitioning -> StatusStarting
                            vpnState is VpnState.Error -> StatusError
                            else -> SkyBlueTextSecondary
                        },
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Big Hollow PC Power Button (~150dp diameter, Option B)
            val buttonBg = when {
                isConnected -> Color(0xFFF0FDF4)
                isTransitioning -> Color(0xFFFFFBEB)
                vpnState is VpnState.Error -> Color(0xFFFEF2F2)
                else -> SkyBlueSurfaceVariant.copy(alpha = 0.45f)
            }
            val buttonBorderColor = when {
                isConnected -> StatusConnected
                isTransitioning -> StatusStarting
                vpnState is VpnState.Error -> StatusError
                else -> Color(0xFFCBD5E1)
            }

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .border(
                        width = if (isConnected || isTransitioning) 3.dp else 2.dp,
                        color = buttonBorderColor,
                        shape = CircleShape
                    )
                    .clickable(onClick = onUnavailable)
                    .testTag("vpn_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                // Classic Hollow PC Power Symbol (IEC 5009)
                Canvas(modifier = Modifier.size(76.dp)) {
                    val strokeWidth = 6.dp.toPx()
                    val r = size.minDimension * 0.40f
                    val arcCenter = Offset(size.width / 2f, size.height / 2f + r * 0.08f)

                    // Broken circle arc with 60-degree gap at 12 o'clock (-120° to -60°)
                    drawArc(
                        color = iconColor,
                        startAngle = -60f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(arcCenter.x - r, arcCenter.y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Vertical line through the top gap down to center
                    drawLine(
                        color = iconColor,
                        start = Offset(arcCenter.x, arcCenter.y - r * 1.25f),
                        end = Offset(arcCenter.x, arcCenter.y - r * 0.12f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Subtitle information
            Text(
                text = subtitle,
                fontSize = 12.sp,
                fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isConnected) StatusConnected else SkyBlueTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    vpnState: VpnState,
    activeProfile: com.example.config.WireGuardProfile?
) {
    val (statusColor, statusText, statusIcon) = when (vpnState) {
        is VpnState.Connected -> Triple(StatusConnected, "Protection Active", Icons.Default.CheckCircle)
        is VpnState.Starting -> Triple(StatusStarting, "Establishing Tunnel...", Icons.Default.HourglassEmpty)
        is VpnState.Stopping -> Triple(StatusStarting, "Stopping Tunnel...", Icons.Default.HourglassEmpty)
        is VpnState.Error -> Triple(StatusError, "Protection Error", Icons.Default.Error)
        is VpnState.Stopped -> Triple(StatusStopped, "Protection Inactive", Icons.Default.Stop)
        is VpnState.RuntimePending -> Triple(StatusStarting, "Firewall Runtime Pending", Icons.Default.HourglassEmpty)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(2.dp, statusColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusText,
                    tint = statusColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = statusText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = SkyBlueTextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            val subtitle = when (vpnState) {
                is VpnState.Connected -> "Firewall runtime reported connected"
                is VpnState.Starting -> "Starting VPN runtime"
                is VpnState.Stopping -> "Stopping VPN runtime"
                is VpnState.Error -> vpnState.message
                is VpnState.Stopped -> "Firewall runtime is stopped"
                is VpnState.RuntimePending -> "Firewall runtime integration is pending. No protection is active."
            }

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SkyBlueTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (activeProfile != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SkyBlueSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile: ${activeProfile.name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SkyBlueTextPrimary
                    )
                    Text(
                        text = "${activeProfile.endpointHost}:${activeProfile.endpointPort}",
                        fontSize = 11.sp,
                        color = SkyBlueTextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Native Core Status Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SkyBlueSurfaceVariant
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StatusStarting)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Firewall runtime integration pending",
                        fontSize = 10.sp,
                        color = SkyBlueTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
