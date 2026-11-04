package com.example.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted per-application firewall preferences.
 *
 * BLOCK/VPN/DIRECT are consumed by PacketPolicy and the native UID route table.
 * Speed-exception and screen-aware values are persisted for the next enforcement
 * layer and are not active traffic controls yet.
 */
data class AppFirewallRule(
    val packageName: String,
    val mode: FirewallMode = FirewallMode.VPN,
    val speedException: String = "Default",
    val screenAwareRule: Boolean = false
)

class AppFirewallRuleRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val lock = Any()

    private val _rules = MutableStateFlow(loadRules())
    val rules: StateFlow<Map<String, AppFirewallRule>> = _rules.asStateFlow()

    fun save(rule: AppFirewallRule) {
        if (rule.packageName.isBlank()) return
        synchronized(lock) {
            preferences.edit()
                .putString(key(rule.packageName, MODE_SUFFIX), rule.mode.name)
                .putString(key(rule.packageName, SPEED_SUFFIX), rule.speedException)
                .putBoolean(key(rule.packageName, SCREEN_SUFFIX), rule.screenAwareRule)
                .apply()

            _rules.value = _rules.value + (rule.packageName to rule)
        }
    }

    fun saveAll(rules: Collection<AppFirewallRule>) {
        if (rules.isEmpty()) return
        synchronized(lock) {
            val editor = preferences.edit()
            val current = _rules.value.toMutableMap()
            rules.filter { it.packageName.isNotBlank() }.forEach { rule ->
                editor.putString(key(rule.packageName, MODE_SUFFIX), rule.mode.name)
                editor.putString(key(rule.packageName, SPEED_SUFFIX), rule.speedException)
                editor.putBoolean(key(rule.packageName, SCREEN_SUFFIX), rule.screenAwareRule)
                current[rule.packageName] = rule
            }
            editor.apply()
            _rules.value = current
        }
    }

    fun getRule(packageName: String): AppFirewallRule? = _rules.value[packageName]

    /** Refresh the live policy snapshot after another repository instance saved rules. */
    fun reload() {
        synchronized(lock) {
            _rules.value = loadRules()
        }
    }

    private fun loadRules(): Map<String, AppFirewallRule> {
        val packageNames = preferences.all.keys
            .filter { it.startsWith(RULE_PREFIX) && it.endsWith(MODE_SUFFIX) }
            .map { it.removePrefix(RULE_PREFIX).removeSuffix(MODE_SUFFIX) }
            .filter { it.isNotBlank() }
            .toSet()

        return packageNames.mapNotNull { packageName ->
            val mode = preferences.getString(key(packageName, MODE_SUFFIX), null)
                ?.let { stored -> FirewallMode.values().firstOrNull { it.name == stored } }
                ?: FirewallMode.VPN
            val speed = preferences.getString(key(packageName, SPEED_SUFFIX), "Default") ?: "Default"
            val screenAware = preferences.getBoolean(key(packageName, SCREEN_SUFFIX), false)
            packageName to AppFirewallRule(packageName, mode, speed, screenAware)
        }.toMap()
    }

    private fun key(packageName: String, suffix: String) = "$RULE_PREFIX$packageName$suffix"

    companion object {
        private const val PREFS_NAME = "vianshield_firewall_rules_v1"
        private const val RULE_PREFIX = "rule:"
        private const val MODE_SUFFIX = ":mode"
        private const val SPEED_SUFFIX = ":speed"
        private const val SCREEN_SUFFIX = ":screen"
    }
}
