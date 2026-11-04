package com.example.vpn

import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import eu.faircode.netguard.Allowed
import eu.faircode.netguard.Packet
import eu.faircode.netguard.ResourceRecord
import eu.faircode.netguard.ServiceSinkhole
import eu.faircode.netguard.Usage
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import com.example.ui.AppFirewallRuleRepository
import java.net.InetSocketAddress

/**
 * Callback adapter for the imported NetGuard native packet loop.
 * VianshieldVpnService owns and starts the sole Android VPN/TUN runtime.
 */
internal class VianshieldPacketCallbacks(
    private val service: VianshieldVpnService,
    private val rules: AppFirewallRuleRepository
) : ServiceSinkhole.Callbacks {

    override fun protect(socketFd: Int): Boolean = service.protect(socketFd)

    override fun getUidQ(
        version: Int,
        protocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Process.INVALID_UID
        val manager = service.getSystemService(ConnectivityManager::class.java)
            ?: return Process.INVALID_UID
        return try {
            manager.getConnectionOwnerUid(
                protocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort)
            )
        } catch (_: RuntimeException) {
            Process.INVALID_UID
        }
    }

    override fun isAddressAllowed(packet: Packet): Allowed? {
        val packages = try {
            service.packageManager.getPackagesForUid(packet.uid)
        } catch (_: SecurityException) {
            null
        }
        return when (PacketPolicy.decide(packet.uid, Process.myUid(), packages, rules)) {
            PacketPolicy.Decision.ALLOW_VPN,
            PacketPolicy.Decision.ALLOW_DIRECT,
            PacketPolicy.Decision.ALLOW_SYSTEM,
            PacketPolicy.Decision.SELF -> {
                packet.allowed = true
                Allowed()
            }
            PacketPolicy.Decision.BLOCK,
            PacketPolicy.Decision.UNKNOWN_UID -> {
                packet.allowed = false
                null
            }
        }
    }

    override fun isDomainBlocked(domain: String): Boolean = false

    override fun logPacket(packet: Packet) {
        // Intentionally do not persist per-packet metadata. Destination IPs,
        // ports and per-app activity are sensitive browsing telemetry.
    }

    override fun dnsResolved(record: ResourceRecord) {
        // Intentionally do not persist queried domains or resolved records.
        // DNS counters can be aggregated without retaining domain history.
    }

    override fun accountUsage(usage: Usage) {
        // Usage persistence is wired in the tracker/data integration phase.
    }

    override fun onNativeExit(error: Int, reason: String) {
        OperationalLogCatcher.error(
            LogCategory.VPN_LIFECYCLE,
            "PacketPolicy",
            "Native packet loop exited ($error): $reason"
        )
        service.onNativePacketFailure(error, reason)
    }

    override fun onNativeError(error: Int, message: String) {
        OperationalLogCatcher.error(
            LogCategory.VPN_LIFECYCLE,
            "PacketPolicy",
            "Native packet engine error ($error): $message"
        )
        service.onNativePacketFailure(error, message)
    }
}
