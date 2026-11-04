package com.example.ui

import android.content.Context
import java.net.IDN

/**
 * Small, app-private settings store for firewall controls that must survive
 * process death. No traffic data, host history, or profile keys are stored here.
 */
class FirewallSettingsRepository(context: Context) {
    data class Snapshot(
        val speedPreset: String = DEFAULT_SPEED_PRESET,
        val dnsProvider: String = DnsResolverCatalog.PROFILE_DEFAULT,
        val adFilterEnabled: Boolean = false,
        val allowListDomains: List<String> = DEFAULT_ALLOWLIST
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): Snapshot {
        val allowlist = preferences.getStringSet(ALLOWLIST_KEY, null)
            ?.mapNotNull(::normalizeDomain)
            ?.distinct()
            ?.sorted()
            ?: DEFAULT_ALLOWLIST
        val provider = preferences.getString(DNS_PROVIDER_KEY, DnsResolverCatalog.PROFILE_DEFAULT)
            ?.takeIf { DnsResolverCatalog.isKnownProvider(it) }
            ?: DnsResolverCatalog.PROFILE_DEFAULT
        val preset = preferences.getString(SPEED_PRESET_KEY, DEFAULT_SPEED_PRESET)
            ?.takeIf { it in SPEED_PRESETS }
            ?: DEFAULT_SPEED_PRESET
        return Snapshot(
            speedPreset = preset,
            dnsProvider = provider,
            adFilterEnabled = preferences.getBoolean(AD_FILTER_ENABLED_KEY, false),
            allowListDomains = allowlist
        )
    }

    @Synchronized
    fun saveSpeedPreset(preset: String): Boolean {
        if (preset !in SPEED_PRESETS) return false
        return preferences.edit().putString(SPEED_PRESET_KEY, preset).commit()
    }

    @Synchronized
    fun saveDnsProvider(provider: String): Boolean {
        if (!DnsResolverCatalog.isKnownProvider(provider)) return false
        return preferences.edit().putString(DNS_PROVIDER_KEY, provider).commit()
    }

    @Synchronized
    fun saveAdFilterEnabled(enabled: Boolean): Boolean =
        preferences.edit().putBoolean(AD_FILTER_ENABLED_KEY, enabled).commit()

    @Synchronized
    fun addAllowListDomain(input: String): Snapshot? {
        val domain = normalizeDomain(input) ?: return null
        val current = load()
        if (domain in current.allowListDomains) return current
        val updated = (current.allowListDomains + domain).distinct().sorted()
        val saved = preferences.edit().putStringSet(ALLOWLIST_KEY, updated.toSet()).commit()
        return if (saved) current.copy(allowListDomains = updated) else null
    }

    @Synchronized
    fun removeAllowListDomain(input: String): Snapshot? {
        val domain = normalizeDomain(input) ?: return null
        val current = load()
        val updated = current.allowListDomains.filterNot { it == domain }
        if (updated == current.allowListDomains) return current
        val saved = preferences.edit().putStringSet(ALLOWLIST_KEY, updated.toSet()).commit()
        return if (saved) current.copy(allowListDomains = updated) else null
    }

    fun isAllowlisted(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        return load().allowListDomains.any { normalized == it || normalized.endsWith(".$it") }
    }

    private fun normalizeDomain(input: String): String? {
        val trimmed = input.trim().trimEnd('.').removePrefix("*.").lowercase()
        if (trimmed.isBlank() || trimmed.contains('/') || trimmed.contains(':') ||
            trimmed.contains('@') || trimmed.contains(' ')) return null
        val ascii = runCatching { IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase() }
            .getOrNull() ?: return null
        if (ascii.length > 253) return null
        val labels = ascii.split('.')
        if (labels.size < 2 || labels.any { it.isBlank() || it.length > 63 }) return null
        if (labels.any { !it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) }) return null
        return ascii
    }

    companion object {
        const val DEFAULT_SPEED_PRESET = "Unlimited (Full Speed)"
        private const val PREFERENCES_NAME = "vianshield_firewall_settings_v1"
        private const val SPEED_PRESET_KEY = "speed_preset"
        private const val DNS_PROVIDER_KEY = "dns_provider"
        private const val AD_FILTER_ENABLED_KEY = "ad_filter_enabled"
        private const val ALLOWLIST_KEY = "ad_filter_allowlist"

        val SPEED_PRESETS = setOf(
            DEFAULT_SPEED_PRESET,
            "Gaming / Low Latency",
            "Battery Saver (5 Mbps)",
            "Extreme Data Saver (1 Mbps)"
        )
        val DEFAULT_ALLOWLIST = listOf("api.github.com", "android.googleapis.com")
    }
}

/** DNS providers shown in the Main dashboard's DNS selector. */
object DnsResolverCatalog {
    const val PROFILE_DEFAULT = "Profile Default (from .conf)"
    const val CLOUDFLARE = "Cloudflare DNS (1.1.1.1)"
    const val QUAD9 = "Quad9 Security (9.9.9.9)"
    const val GOOGLE = "Google Public DNS (8.8.8.8)"
    const val ADGUARD = "AdGuard DNS (94.140.14.14)"

    fun isKnownProvider(value: String): Boolean =
        value in setOf(PROFILE_DEFAULT, CLOUDFLARE, QUAD9, GOOGLE, ADGUARD)

    fun resolve(provider: String, profileDns: List<String>): List<String> =
        when (provider) {
            QUAD9 -> listOf("9.9.9.9", "149.112.112.112")
            GOOGLE -> listOf("8.8.8.8", "8.8.4.4")
            ADGUARD -> listOf("94.140.14.14", "94.140.15.15")
            CLOUDFLARE -> listOf("1.1.1.1", "1.0.0.1")
            PROFILE_DEFAULT -> profileDns.ifEmpty { listOf("1.1.1.1", "1.0.0.1") }
            else -> profileDns.ifEmpty { listOf("1.1.1.1", "1.0.0.1") }
        }
}
