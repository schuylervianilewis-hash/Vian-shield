package com.example.ui

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.StatusError

data class AppFirewallItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isConfigured: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSystemApps by remember { mutableStateOf(false) }
    var expandedAppPackage by remember { mutableStateOf<String?>(null) }

    var installedApps by remember { mutableStateOf<List<AppFirewallItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    // Use only packages Android actually reports; never substitute sample/demo apps.
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val installed = try {
            pm.getInstalledApplications(0)
        } catch (_: Exception) {
            emptyList<ApplicationInfo>()
        }

        installedApps = installed.map { appInfo ->
            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                appInfo.packageName
            }
            AppFirewallItem(
                packageName = appInfo.packageName,
                appName = if (label.isNotBlank()) label else appInfo.packageName,
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isConfigured = false
            )
        }
        isLoadingApps = false
    }

    // Search and system-app filtering operate only on the actual visible package list.
    val displayedApps = installedApps
        .filter { item ->
            if (!showSystemApps && item.isSystemApp) false
            else searchQuery.isBlank() ||
                    item.appName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true)
        }
        .sortedWith(
            compareByDescending<AppFirewallItem> { it.isConfigured }
                .thenBy { it.appName.lowercase() }
        )

    val totalCount = installedApps.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SkyBlueBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Firewall",
                            fontWeight = FontWeight.Bold,
                            color = SkyBlueTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SkyBluePrimaryContainer
                        ) {
                            Text(
                                text = "$totalCount apps",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SkyBlueOnPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchVisible || searchQuery.isNotEmpty()) SkyBluePrimary else SkyBlueTextPrimary
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = SkyBlueTextPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = SkyBlueSurface
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (showSystemApps) "Hide System Applications" else "Show System Applications",
                                    color = SkyBlueTextPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                showSystemApps = !showSystemApps
                                showMenu = false
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkyBlueSurface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search Bar (shown when toggled or non-empty)
            if (isSearchVisible || searchQuery.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter applications by name or package...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = SkyBlueTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = SkyBlueTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SkyBlueSurface,
                            unfocusedContainerColor = SkyBlueSurface,
                            focusedBorderColor = SkyBluePrimary,
                            unfocusedBorderColor = SkyBlueSurfaceBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("firewall_search_input")
                    )
                }
            }

            // 2. List Header with "Set All At Once" controls
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "APPLICATIONS (${displayedApps.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SkyBlueTextTertiary,
                        letterSpacing = 0.5.sp
                    )

                    // Bulk policy shortcuts remain visible but cannot change rules yet.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Set All:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SkyBlueTextTertiary
                        )
                        MasterModeChip("Block") {
                            showFirewallUnavailableToast(context, "Bulk Block rules")
                        }
                        MasterModeChip("VPN") {
                            showFirewallUnavailableToast(context, "Bulk VPN routing")
                        }
                        MasterModeChip("Direct") {
                            showFirewallUnavailableToast(context, "Bulk Direct routing")
                        }
                    }
                }
            }

            // 3. List of Apps (Compact One-Line Rows)
            if (displayedApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "No applications found matching '$searchQuery'"
                                isLoadingApps -> "Loading installed applications..."
                                else -> "No visible installed applications. Android package visibility may limit this list."
                            },
                            color = SkyBlueTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(
                    items = displayedApps,
                    key = { it.packageName }
                ) { item ->
                    val isExpanded = expandedAppPackage == item.packageName

                    AppFirewallRow(
                        item = item,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedAppPackage = if (isExpanded) null else item.packageName
                        },
                        onUnavailable = { feature ->
                            showFirewallUnavailableToast(context, feature)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun showFirewallUnavailableToast(context: android.content.Context, feature: String) {
    Toast.makeText(
        context,
        "$feature is not available yet. Firewall rule storage and runtime enforcement are not integrated.",
        Toast.LENGTH_SHORT
    ).show()
}

@Composable
private fun AppFirewallRow(
    item: AppFirewallItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUnavailable: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isConfigured) SkyBluePrimary.copy(alpha = 0.4f) else SkyBlueSurfaceBorder
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon + Name (Clickable to expand)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggleExpand),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SkyBlueSurfaceVariant.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.appName.firstOrNull()?.uppercase() ?: "A",
                            fontWeight = FontWeight.Bold,
                            color = SkyBlueTextTertiary,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.appName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = SkyBlueTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (item.isSystemApp) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SkyBlueSurfaceVariant
                                ) {
                                    Text(
                                        text = "SYS",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SkyBlueTextTertiary,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.packageName,
                            fontSize = 10.sp,
                            color = SkyBlueTextTertiary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Compact Option B Trio Mode Checkbox / Segment
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactModeButton(
                        text = "Block",
                        isSelected = false,
                        activeColor = StatusError,
                        activeBg = Color(0xFFFEE2E2),
                        testTag = "firewall_mode_block",
                        onClick = { onUnavailable("Per-app Block rule") }
                    )
                    CompactModeButton(
                        text = "VPN",
                        isSelected = false,
                        activeColor = SkyBluePrimary,
                        activeBg = SkyBluePrimaryContainer,
                        testTag = "firewall_mode_vpn",
                        onClick = { onUnavailable("Per-app VPN routing") }
                    )
                    CompactModeButton(
                        text = "Direct",
                        isSelected = false,
                        activeColor = Color(0xFF64748B),
                        activeBg = Color(0xFFE2E8F0),
                        testTag = "firewall_mode_direct",
                        onClick = { onUnavailable("Per-app Direct routing") }
                    )
                }
            }

            // Expandable secondary details (speed exception & screen-aware rule)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUnavailable("Per-app speed exception") },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Speed Exception", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SkyBlueTextSecondary)
                            Text("Not configured", fontSize = 9.sp, color = SkyBlueTextTertiary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Default", "Fast", "Throttled").forEach { option ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SkyBlueSurfaceVariant.copy(alpha = 0.55f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder),
                                    modifier = Modifier.clickable { onUnavailable("Per-app speed exception") }
                                ) {
                                    Text(
                                        option,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SkyBlueTextTertiary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onUnavailable("Per-app ad-filter exemption") },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Ad-Filter Exemption", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SkyBlueTextSecondary)
                            Text("Separate from app blocking and routing · Not configured", fontSize = 9.sp, color = SkyBlueTextTertiary)
                        }
                        Switch(
                            checked = false,
                            onCheckedChange = {},
                            enabled = false,
                            colors = SwitchDefaults.colors(
                                disabledUncheckedThumbColor = Color.White,
                                disabledUncheckedTrackColor = SkyBlueSurfaceBorder
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onUnavailable("Screen-aware policy") },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Screen-Aware Policy", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SkyBlueTextSecondary)
                            Text("Block traffic when screen turns off · Not configured", fontSize = 9.sp, color = SkyBlueTextTertiary)
                        }
                        Switch(
                            checked = false,
                            onCheckedChange = {},
                            enabled = false,
                            colors = SwitchDefaults.colors(
                                disabledUncheckedThumbColor = Color.White,
                                disabledUncheckedTrackColor = SkyBlueSurfaceBorder
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactModeButton(
    text: String,
    isSelected: Boolean,
    activeColor: Color,
    activeBg: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) activeBg else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) activeColor else SkyBlueSurfaceBorder
        ),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeColor else SkyBlueTextTertiary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MasterModeChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = SkyBlueSurfaceVariant.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = SkyBlueTextTertiary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
