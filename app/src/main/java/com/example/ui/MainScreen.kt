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
import androidx.compose.foundation.layout.heightIn
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
                    Text(
                        text = "Vianshield",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = SkyBlueTextPrimary
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = SkyBlueSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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

            // Keep the main protection action prominent; feature shortcuts follow in a two-column grid.
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

            Text(
                text = "Quick Settings",
                modifier = Modifier.fillMaxWidth().padding(top = 0.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = SkyBlueTextPrimary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardActionCard(
                        icon = Icons.Default.Speed,
                        title = "Speed Preset",
                        description = "Throttling options",
                        testTag = "main_action_speed",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.SPEED_PRESET) }
                    )
                    DashboardActionCard(
                        icon = Icons.Default.Dns,
                        title = "DNS Changer",
                        description = "Choose DNS provider",
                        testTag = "main_action_dns",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.DNS_CHANGER) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardActionCard(
                        icon = Icons.Default.Block,
                        title = "Ad Filter",
                        description = "Filtering and whitelist",
                        testTag = "main_action_ad_filter",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.AD_FILTER) }
                    )
                    DashboardActionCard(
                        icon = Icons.Default.Security,
                        title = "Blocked Domains",
                        description = "Blocked-domain history",
                        testTag = "main_action_blocked_domains",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppScreen.BLOCKED_DOMAINS) }
                    )
                }
            }

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
                                onClick = { onNavigate(AppScreen.LOG_KEEPER) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusError),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("View Log Keeper", fontSize = 12.sp)
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
private fun DashboardActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(72.dp).clickable(onClick = onClick).testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(SkyBluePrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = SkyBluePrimary, modifier = Modifier.size(17.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SkyBlueTextPrimary)
                Text(description, fontSize = 10.sp, color = SkyBlueTextSecondary, maxLines = 1)
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

    val statusLabel = when {
        vpnState is VpnState.Starting -> "CONNECTING"
        vpnState is VpnState.Stopping -> "DISCONNECTING"
        isConnected -> "FIREWALL ACTIVE"
        vpnState is VpnState.Error -> "PROTECTION ERROR"
        vpnState is VpnState.RuntimePending -> "FIREWALL PENDING"
        else -> "FIREWALL OFF"
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
        vpnState is VpnState.RuntimePending -> "Runtime pending · No protection active"
        else -> "No protection active"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConnected) StatusConnected.copy(alpha = 0.4f) else SkyBlueSurfaceBorder
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape).background(
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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isConnected -> StatusConnected
                            isTransitioning -> StatusStarting
                            vpnState is VpnState.Error -> StatusError
                            else -> SkyBlueTextSecondary
                        },
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    .size(92.dp)
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
                Canvas(modifier = Modifier.size(48.dp)) {
                    val strokeWidth = 4.dp.toPx()
                    val r = size.minDimension * 0.40f
                    val arcCenter = Offset(size.width / 2f, size.height / 2f + r * 0.08f)
                    drawArc(
                        color = iconColor,
                        startAngle = -60f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(arcCenter.x - r, arcCenter.y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(arcCenter.x, arcCenter.y - r * 1.25f),
                        end = Offset(arcCenter.x, arcCenter.y - r * 0.12f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isConnected) StatusConnected else SkyBlueTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

