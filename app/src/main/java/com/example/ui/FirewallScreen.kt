package com.example.ui

import android.content.pm.ApplicationInfo
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Android
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

enum class FirewallMode(val label: String) {
    BLOCK("Block"),
    VPN("VPN"),
    DIRECT("Direct")
}

data class AppFirewallItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val mode: FirewallMode = FirewallMode.VPN,
    val isConfigured: Boolean = false,
    val speedException: String = "Default",
    val screenAwareRule: Boolean = false
)

private object AppIconBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(128)

    @Synchronized
    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    @Synchronized
    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }
}

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
    var showSystemApps by remember { mutableStateOf(false) }
    var expandedAppPackage by remember { mutableStateOf<String?>(null) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var appLoadError by remember { mutableStateOf<String?>(null) }

    // UI cache for installed apps; selected settings are persisted by the ViewModel.
    var appRules by remember {
        mutableStateOf<Map<String, AppFirewallItem>>(emptyMap())
    }

    // Load the real installed-app inventory. Never substitute demo rows on failure.
    LaunchedEffect(Unit) {
        isLoadingApps = true
        appLoadError = null
        try {
            val pm = context.packageManager
            val installed = withContext(Dispatchers.IO) {
                pm.getInstalledApplications(0)
            }
            val rules = viewModel.appFirewallRules.value
            val items = installed.map { appInfo ->
                val label = runCatching {
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrDefault(appInfo.packageName)
                val isSystem = (appInfo.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                val saved = rules[appInfo.packageName]
                AppFirewallItem(
                    packageName = appInfo.packageName,
                    appName = label.ifBlank { appInfo.packageName },
                    isSystemApp = isSystem,
                    mode = saved?.mode ?: FirewallMode.VPN,
                    isConfigured = saved != null,
                    speedException = saved?.speedException ?: "Default",
                    screenAwareRule = saved?.screenAwareRule ?: false
                )
            }
            appRules = items.associateBy { it.packageName }
        } catch (error: Exception) {
            appLoadError = error.message ?: "Android could not return the installed-app inventory."
        } finally {
            isLoadingApps = false
        }
    }

    // User and system applications are separate, exclusive views.
    val displayedApps = appRules.values
        .filter { item ->
            item.isSystemApp == showSystemApps &&
                (searchQuery.isBlank() ||
                    item.appName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true))
        }
        .sortedWith(
            compareByDescending<AppFirewallItem> { it.isConfigured }
                .thenBy { it.appName.lowercase() }
        )

    val configuredCount = appRules.values.count { it.isConfigured }
    val totalCount = appRules.size

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
                                text = "$configuredCount/$totalCount Configured",
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
                            contentDescription = "Search applications",
                            tint = if (isSearchVisible || searchQuery.isNotEmpty()) SkyBluePrimary else SkyBlueTextPrimary
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppCategoryChip(
                        label = "User Apps (${appRules.values.count { !it.isSystemApp }})",
                        selected = !showSystemApps,
                        testTag = "firewall_user_apps_tab",
                        onClick = { showSystemApps = false }
                    )
                    AppCategoryChip(
                        label = "System Apps (${appRules.values.count { it.isSystemApp }})",
                        selected = showSystemApps,
                        testTag = "firewall_system_apps_tab",
                        onClick = { showSystemApps = true }
                    )
                }
            }

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

                    // Option B: Master selection to apply mode to all apps at once
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
                        MasterModeChip(
                            label = "Block",
                            color = StatusError,
                            onClick = {
                                val updated = appRules.toMutableMap()
                                displayedApps.forEach { app ->
                                    updated[app.packageName] = app.copy(mode = FirewallMode.BLOCK, isConfigured = true)
                                }
                                appRules = updated
                                viewModel.saveAppFirewallRules(displayedApps.map { app ->
                                    app.copy(mode = FirewallMode.BLOCK, isConfigured = true).toPersistedRule()
                                })
                            }
                        )
                        MasterModeChip(
                            label = "VPN",
                            color = SkyBluePrimary,
                            onClick = {
                                val updated = appRules.toMutableMap()
                                displayedApps.forEach { app ->
                                    updated[app.packageName] = app.copy(mode = FirewallMode.VPN, isConfigured = true)
                                }
                                appRules = updated
                                viewModel.saveAppFirewallRules(displayedApps.map { app ->
                                    app.copy(mode = FirewallMode.VPN, isConfigured = true).toPersistedRule()
                                })
                            }
                        )
                        MasterModeChip(
                            label = "Direct",
                            color = Color(0xFF64748B),
                            onClick = {
                                val updated = appRules.toMutableMap()
                                displayedApps.forEach { app ->
                                    updated[app.packageName] = app.copy(mode = FirewallMode.DIRECT, isConfigured = true)
                                }
                                appRules = updated
                                viewModel.saveAppFirewallRules(displayedApps.map { app ->
                                    app.copy(mode = FirewallMode.DIRECT, isConfigured = true).toPersistedRule()
                                })
                            }
                        )
                    }
                }
            }

            // 3. List of Apps (Compact One-Line Rows)
            when {
                isLoadingApps -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading installed applications…", color = SkyBlueTextSecondary, fontSize = 13.sp)
                    }
                }
                appLoadError != null -> item {
                    Text(
                        text = "Unable to load installed applications: ${appLoadError}",
                        color = StatusError,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
                displayedApps.isEmpty() -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No applications found matching '${searchQuery}'"
                            } else if (showSystemApps) {
                                "No system applications are visible to Vianshield."
                            } else {
                                "No user applications are visible to Vianshield."
                            },
                            color = SkyBlueTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> items(items = displayedApps, key = { it.packageName }) { item ->
                    val isExpanded = expandedAppPackage == item.packageName
                    AppFirewallRow(
                        item = item,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedAppPackage = if (isExpanded) null else item.packageName
                        },
                        onModeSelected = { newMode ->
                            val updated = item.copy(mode = newMode, isConfigured = true)
                            appRules = appRules + (item.packageName to updated)
                            viewModel.saveAppFirewallRule(updated.toPersistedRule())
                        },
                        onSpeedExceptionSelected = { exception ->
                            val updated = item.copy(speedException = exception, isConfigured = true)
                            appRules = appRules + (item.packageName to updated)
                            viewModel.saveAppFirewallRule(updated.toPersistedRule())
                        },
                        onScreenAwareToggled = { enabled ->
                            val updated = item.copy(screenAwareRule = enabled, isConfigured = true)
                            appRules = appRules + (item.packageName to updated)
                            viewModel.saveAppFirewallRule(updated.toPersistedRule())
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

@Composable
private fun AppFirewallRow(
    item: AppFirewallItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onModeSelected: (FirewallMode) -> Unit,
    onSpeedExceptionSelected: (String) -> Unit,
    onScreenAwareToggled: (Boolean) -> Unit
) {

    val context = LocalContext.current
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
                    val appIcon by produceState<ImageBitmap?>(
                        initialValue = AppIconBitmapCache.get(item.packageName),
                        key1 = item.packageName
                    ) {
                        if (value == null) {
                            val loaded = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.packageManager
                                        .getApplicationIcon(item.packageName)
                                        .toBitmap(width = 96, height = 96)
                                        .asImageBitmap()
                                }.getOrNull()
                            }
                            if (loaded != null) {
                                AppIconBitmapCache.put(item.packageName, loaded)
                                value = loaded
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SkyBlueSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon!!,
                                contentDescription = "${item.appName} icon",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(2.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = "Application icon unavailable",
                                tint = SkyBlueTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
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
                        isSelected = item.mode == FirewallMode.BLOCK,
                        activeColor = StatusError,
                        activeBg = Color(0xFFFEE2E2),
                        testTag = "firewall_mode_block",
                        onClick = { onModeSelected(FirewallMode.BLOCK) }
                    )
                    CompactModeButton(
                        text = "VPN",
                        isSelected = item.mode == FirewallMode.VPN,
                        activeColor = SkyBluePrimary,
                        activeBg = SkyBluePrimaryContainer,
                        testTag = "firewall_mode_vpn",
                        onClick = { onModeSelected(FirewallMode.VPN) }
                    )
                    CompactModeButton(
                        text = "Direct",
                        isSelected = item.mode == FirewallMode.DIRECT,
                        activeColor = Color(0xFF64748B),
                        activeBg = Color(0xFFE2E8F0),
                        testTag = "firewall_mode_direct",
                        onClick = { onModeSelected(FirewallMode.DIRECT) }
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speed Exception",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SkyBlueTextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Default", "Fast", "Throttled").forEach { opt ->
                                val sel = item.speedException == opt
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (sel) SkyBluePrimaryContainer else SkyBlueSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (sel) SkyBluePrimary else SkyBlueSurfaceBorder
                                    ),
                                    modifier = Modifier.clickable { onSpeedExceptionSelected(opt) }
                                ) {
                                    Text(
                                        text = opt,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (sel) SkyBluePrimary else SkyBlueTextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Screen-Aware Policy",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = SkyBlueTextPrimary
                            )
                            Text(
                                text = "Block traffic when screen turns off",
                                fontSize = 9.sp,
                                color = SkyBlueTextSecondary
                            )
                        }
                        Switch(
                            checked = item.screenAwareRule,
                            onCheckedChange = onScreenAwareToggled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SkyBluePrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = SkyBlueSurfaceBorder
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
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}


private fun AppFirewallItem.toPersistedRule(): AppFirewallRule = AppFirewallRule(
    packageName = packageName,
    mode = mode,
    speedException = speedException,
    screenAwareRule = screenAwareRule
)


@Composable
private fun AppCategoryChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) SkyBluePrimaryContainer else SkyBlueSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) SkyBluePrimary else SkyBlueSurfaceBorder
        ),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) SkyBlueOnPrimaryContainer else SkyBlueTextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
