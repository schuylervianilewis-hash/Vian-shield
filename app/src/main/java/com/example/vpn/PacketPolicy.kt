package com.example.vpn

import com.example.ui.AppFirewallRuleRepository
import com.example.ui.FirewallMode

/**
 * Conservative packet policy for the TrackerControl JNI callback.
 *
 * Unknown UIDs and unsupported routing modes are denied rather than silently
 * inheriting an allow rule. DIRECT is intentionally denied until a protected
 * direct-egress route is implemented by the runtime.
 */
internal object PacketPolicy {
    enum class Decision { ALLOW_VPN, BLOCK, UNKNOWN_UID, DIRECT_NOT_READY, SELF }

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
        if (appRules.any { it.mode == FirewallMode.DIRECT }) return Decision.DIRECT_NOT_READY
        return if (appRules.isNotEmpty()) Decision.ALLOW_VPN else Decision.UNKNOWN_UID
    }
}
