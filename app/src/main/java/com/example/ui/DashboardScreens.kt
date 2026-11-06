package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SpeedPresetScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    FeaturePage("Speed Preset", "Preset controls are UI-only until rate limiting is implemented.", onBack, "speed_preset_screen") {
        OptionCard(Icons.Default.Speed, "Unlimited (Full Speed)", "Not applied yet.", "speed_unlimited") {
            Toast.makeText(context, "Speed presets are not applied yet.", Toast.LENGTH_SHORT).show()
        }
        OptionCard(Icons.Default.Speed, "Gaming / Low Latency", "Not applied yet.", "speed_gaming") {
            Toast.makeText(context, "Speed presets are not applied yet.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun DnsChangerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    FeaturePage("DNS Changer", "DNS selection is not active until runtime integration.", onBack, "dns_changer_screen") {
        listOf("Profile Default", "Cloudflare DNS · 1.1.1.1", "Google DNS · 8.8.8.8").forEachIndexed { i, label ->
            OptionCard(Icons.Default.Dns, label, "Provider selection is unavailable.", "dns_provider_$i") {
                Toast.makeText(context, "DNS selection is not applied yet.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun AdFilterScreen(onBack: () -> Unit, onOpenWhitelist: () -> Unit) {
    val context = LocalContext.current
    FeaturePage("Ad Filter", "Filtering is not active. This is a UI shell only.", onBack, "ad_filter_screen") {
        Text("Ad Filter", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SkyBlueTextPrimary)
        OptionCard(Icons.Default.Block, "Filtering controls", "Filter status and blocklist controls.", "ad_filter_controls") {
            Toast.makeText(context, "Ad filtering is not implemented yet.", Toast.LENGTH_SHORT).show()
        }
        OptionCard(Icons.Default.Block, "Blocklist management", "Manage blocklist sources when filtering is integrated.", "ad_filter_lists") {
            Toast.makeText(context, "Blocklist management is not available yet.", Toast.LENGTH_SHORT).show()
        }
        Text("Whitelist", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SkyBlueTextPrimary)
        OptionCard(Icons.Default.Security, "Manage domain and link whitelist", "Domains or links only; no app exemptions.", "ad_filter_open_whitelist", onOpenWhitelist)
    }
}

@Composable
fun AdFilterWhitelistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    FeaturePage("Whitelist", "Only domain or link entries are allowed. Apps cannot be added.", onBack, "ad_filter_whitelist_screen") {
        Text(
            "DNS filtering normally matches hostnames, not complete HTTPS URL paths.",
            color = SkyBlueTextSecondary,
            fontSize = 12.sp
        )
        OptionCard(Icons.Default.Security, "Add domain or link", "Entry management is not implemented yet.", "whitelist_add") {
            Toast.makeText(context, "Whitelist editing is not available yet.", Toast.LENGTH_SHORT).show()
        }
        Text("No saved whitelist entries", color = SkyBlueTextSecondary, modifier = Modifier.padding(18.dp))
    }
}

@Composable
fun BlockedDomainsScreen(onBack: () -> Unit) {
    FeaturePage("Blocked Domains", "Blocked-domain history will appear when DNS/filter events are collected.", onBack, "blocked_domains_screen") {
        Text("No blocked-domain events", fontWeight = FontWeight.SemiBold, color = SkyBlueTextPrimary)
        Text("The event pipeline is not connected yet.", color = SkyBlueTextSecondary, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeaturePage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(tag),
        containerColor = SkyBlueBackground,
        topBar = {
            TopAppBar(
                title = { Text(title, color = SkyBlueTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkyBlueSurface)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(subtitle, color = SkyBlueTextSecondary, fontSize = 13.sp)
            content()
        }
    }
}

@Composable
private fun OptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurfaceVariant),
        border = BorderStroke(1.dp, SkyBlueSurfaceBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SkyBlueTextTertiary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = SkyBlueTextSecondary, fontWeight = FontWeight.SemiBold)
                Text(description, color = SkyBlueTextTertiary, fontSize = 11.sp)
            }
        }
    }
}
