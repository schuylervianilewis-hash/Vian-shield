package com.example.vpn

import com.example.ui.AppFirewallRuleRepository
import com.example.ui.FirewallMode

/**
 * Packet allow/block policy for the native callback.
 *
 * Known applications are allowed through the local firewall by default.
 * BLOCK is enforced before per-UID routing. DIRECT is only selected for a
 * shared UID when every package attached to that UID explicitly requests it.
 * Unknown UID attribution remains fail-closed.
 */
internal object PacketPolicy {
    enum class Decision { ALLOW_VPN, ALLOW_DIRECT, BLOCK, UNKNOWN_UID, SELF }

    fun decide(
        uid: Int,
        ownUid: Int,
        packageNames: Array<String>?,
        rules: AppFirewallRuleRepository
    ): Decision {
        if (uid == ownUid) return Decision.SELF
        if (uid < 0 || packageNames.isNullOrEmpty()) return Decision.UNKNOWN_UID

        val appRules = packageNames.mapNotNull(rules::getRule)
        if (appRules.any { it.mode == FirewallMode.BLOCK }) return Decision.BLOCK

        val everyPackageExplicitlyDirect = appRules.isNotEmpty() &&
            packageNames.all { packageName ->
                rules.getRule(packageName)?.mode == FirewallMode.DIRECT
            }
        if (everyPackageExplicitlyDirect) return Decision.ALLOW_DIRECT

        return Decision.ALLOW_VPN
    }
}
