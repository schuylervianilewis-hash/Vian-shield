package com.example.config

import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher

data class WireGuardProfile(
    val name: String,
    val privateKey: String,
    val address: String,
    val addresses: List<String> = listOf(address),
    val dns: String = "1.1.1.1",
    val dnsServers: List<String> = listOf(dns),
    val mtu: Int = WireGuardConfigParser.DEFAULT_MTU,
    val peerPublicKey: String,
    val presharedKey: String? = null,
    val endpointHost: String,
    val endpointPort: Int,
    val allowedIps: List<String> = listOf("0.0.0.0/0"),
    val persistentKeepalive: Int? = null
) {
    fun toRedactedSummary(): String {
        return "Profile '$name': Endpoint=$endpointHost:$endpointPort, Addresses=${addresses.joinToString(",")}, MTU=$mtu, DNS=${dnsServers.joinToString(",")}, Keepalive=${persistentKeepalive ?: "none"}, AllowedIPs=${allowedIps.joinToString(",")}, Keys=[REDACTED]"
    }

    /**
     * Produces safe, non-secret diagnostic map for UI preview before import confirmation.
     */
    fun toDisplaySummary(): Map<String, String> {
        val pubKeySnippet = if (peerPublicKey.length >= 10) {
            "${peerPublicKey.take(6)}...${peerPublicKey.takeLast(6)}"
        } else {
            "[REDACTED]"
        }
        return linkedMapOf(
            "Profile Name" to name,
            "Server Endpoint" to "$endpointHost:$endpointPort",
            "Tunnel IP (Address)" to addresses.joinToString(", "),
            "DNS Servers" to dnsServers.joinToString(", "),
            "Tunnel MTU" to mtu.toString(),
            "Persistent Keepalive" to (persistentKeepalive?.let { "${it}s" } ?: "Disabled"),
            "Allowed IPs" to allowedIps.joinToString(", "),
            "Peer Public Key" to pubKeySnippet,
            "Private Key" to "[REDACTED - 44 chars validated]",
            "Preshared Key" to if (presharedKey != null) "[REDACTED - 44 chars validated]" else "None"
        )
    }
}

object WireGuardConfigParser {

    const val DEFAULT_MTU = 1280
    const val MIN_MTU = 1280
    const val MAX_MTU = 1420

    private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/=]{44}$")

    fun parse(confContent: String, profileName: String = "Default Profile"): Result<WireGuardProfile> {
        val lines = confContent.lines()

        var currentSection = ""
        var peerCount = 0
        var privateKey: String? = null
        val rawAddresses = mutableListOf<String>()
        val rawDnsServers = mutableListOf<String>()
        var mtu: Int = DEFAULT_MTU
        var peerPublicKey: String? = null
        var presharedKey: String? = null
        var endpoint: String? = null
        var persistentKeepalive: Int? = null
        val allowedIps = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue

            if (line.startsWith("[") && line.contains("]")) {
                val endIdx = line.indexOf("]")
                currentSection = line.substring(1, endIdx).trim().lowercase()
                if (currentSection == "peer") {
                    peerCount++
                    if (peerCount > 1) {
                        val err = "Multiple [Peer] sections are not supported in single-tunnel mode (found $peerCount peers)"
                        OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
                        return Result.failure(IllegalArgumentException(err))
                    }
                } else if (currentSection != "interface") {
                    val err = "Unsupported section '[$currentSection]' in WireGuard configuration"
                    OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
                    return Result.failure(IllegalArgumentException(err))
                }
                continue
            }

            val parts = line.split("=", limit = 2)
            if (parts.size != 2) continue
            val key = parts[0].trim().lowercase()
            // Strip inline comments starting with '#' or ';'
            val rawValue = parts[1].split(Regex("[#;]"), limit = 2)[0].trim()
            if (rawValue.isEmpty()) continue

            when (currentSection) {
                "interface" -> {
                    when (key) {
                        "privatekey" -> privateKey = rawValue
                        "address" -> {
                            val splitAddresses = rawValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            rawAddresses.addAll(splitAddresses)
                        }
                        "dns" -> {
                            val splitDns = rawValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            rawDnsServers.addAll(splitDns)
                        }
                        "mtu" -> {
                            val parsed = rawValue.toIntOrNull()
                            mtu = if (parsed != null) parsed.coerceIn(MIN_MTU, MAX_MTU) else DEFAULT_MTU
                        }
                    }
                }
                "peer" -> {
                    when (key) {
                        "publickey" -> peerPublicKey = rawValue
                        "presharedkey" -> presharedKey = rawValue
                        "endpoint" -> endpoint = rawValue
                        "persistentkeepalive" -> persistentKeepalive = rawValue.toIntOrNull()
                        "allowedips" -> allowedIps.addAll(rawValue.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    }
                }
            }
        }

        // Validation boundary
        if (privateKey.isNullOrBlank()) {
            val err = "Missing or empty PrivateKey in [Interface]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (!isValidBase64Key(privateKey)) {
            val err = "Invalid PrivateKey format. Must be standard 44-character Base64 key."
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (rawAddresses.isEmpty()) {
            val err = "Missing or empty Address in [Interface]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        // Normalize address CIDR notation (e.g. 10.0.0.2 -> 10.0.0.2/32, fd00::2 -> fd00::2/128)
        val normalizedAddresses = rawAddresses.map { addr ->
            if (!addr.contains("/")) {
                if (addr.contains(":")) "$addr/128" else "$addr/32"
            } else {
                addr
            }
        }
        val primaryAddress = normalizedAddresses.first()

        val finalDnsServers = if (rawDnsServers.isEmpty()) listOf("1.1.1.1") else rawDnsServers
        val primaryDns = finalDnsServers.first()

        if (peerPublicKey.isNullOrBlank()) {
            val err = "Missing or empty PublicKey in [Peer]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (!isValidBase64Key(peerPublicKey)) {
            val err = "Invalid PublicKey format in [Peer]. Must be standard 44-character Base64 key."
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (!presharedKey.isNullOrBlank() && !isValidBase64Key(presharedKey)) {
            val err = "Invalid PresharedKey format in [Peer]. Must be standard 44-character Base64 key."
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (endpoint.isNullOrBlank()) {
            val err = "Missing or empty Endpoint in [Peer]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        val lastColon = endpoint.lastIndexOf(':')
        if (lastColon <= 0 || lastColon == endpoint.length - 1) {
            val err = "Invalid Endpoint format '$endpoint'. Expected host:port"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        val host = endpoint.substring(0, lastColon).trim().removePrefix("[").removeSuffix("]")
        val portStr = endpoint.substring(lastColon + 1).trim()
        val port = portStr.toIntOrNull()
        if (port == null || port !in 1..65535) {
            val err = "Invalid Endpoint port '$portStr' in '$endpoint'"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        val finalAllowedIps = if (allowedIps.isEmpty()) listOf("0.0.0.0/0") else allowedIps

        val profile = WireGuardProfile(
            name = profileName,
            privateKey = privateKey,
            address = primaryAddress,
            addresses = normalizedAddresses,
            dns = primaryDns,
            dnsServers = finalDnsServers,
            mtu = mtu,
            peerPublicKey = peerPublicKey,
            presharedKey = presharedKey,
            endpointHost = host,
            endpointPort = port,
            allowedIps = finalAllowedIps,
            persistentKeepalive = persistentKeepalive
        )

        OperationalLogCatcher.info(
            LogCategory.CONFIG_VALIDATION,
            "ConfigParser",
            "WireGuard profile successfully validated: ${profile.toRedactedSummary()}"
        )

        return Result.success(profile)
    }

    fun parseFromInputStream(inputStream: java.io.InputStream, profileName: String = "Imported Profile"): Result<WireGuardProfile> {
        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            parse(content, profileName)
        } catch (e: Exception) {
            val err = "Failed to read configuration stream: ${e.message}"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            Result.failure(e)
        }
    }

    private fun isValidBase64Key(key: String): Boolean {
        return key.length == 44 && BASE64_PATTERN.matches(key)
    }

    /**
     * Default verified minimal testing profile definition (documentation only, not connected blindly).
     * Uses valid 44-character Base64 keys.
     */
    fun createDefaultTestConfig(): String {
        return """
            [Interface]
            # Client WireGuard Private Key (32 bytes Base64)
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 172.19.0.2/30
            DNS = 1.1.1.1
            MTU = 1280

            [Peer]
            # Server Public Key (32 bytes Base64)
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 198.51.100.1:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
    }
}
