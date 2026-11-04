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
        return if (shouldRouteDirect(packageNames, rules)) Decision.ALLOW_DIRECT else Decision.ALLOW_VPN
    }

    /**
     * Android may assign multiple packages to one UID. A UID is routed direct
     * only when every package sharing it explicitly selects Direct. One
     * blocked package is still handled by decide() before routing.
     */
    fun shouldRouteDirect(
        packageNames: Array<String>?,
        rules: AppFirewallRuleRepository
    ): Boolean = !packageNames.isNullOrEmpty() && packageNames.all { packageName ->
        rules.getRule(packageName)?.mode == FirewallMode.DIRECT
    }
}
