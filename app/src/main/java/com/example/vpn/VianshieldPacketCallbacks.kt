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
 * Minimal, fail-closed callbacks for the imported NetGuard native packet loop.
 * This is not activated until the TUN/service lifecycle is integrated.
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
            PacketPolicy.Decision.ALLOW_VPN, PacketPolicy.Decision.SELF -> {
                packet.allowed = true
                Allowed()
            }
            PacketPolicy.Decision.BLOCK,
            PacketPolicy.Decision.UNKNOWN_UID,
            PacketPolicy.Decision.DIRECT_NOT_READY -> {
                packet.allowed = false
                null
            }
        }
    }

    override fun isDomainBlocked(domain: String): Boolean = false

    override fun logPacket(packet: Packet) {
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "PacketPolicy",
            "Packet decision: uid=${packet.uid}, allowed=${packet.allowed}, protocol=${packet.protocol}, destination=${packet.daddr}:${packet.dport}"
        )
    }

    override fun dnsResolved(record: ResourceRecord) {
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "PacketPolicy",
            "DNS response observed for ${record.QName}"
        )
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
    }

    override fun onNativeError(error: Int, message: String) {
        OperationalLogCatcher.error(
            LogCategory.VPN_LIFECYCLE,
            "PacketPolicy",
            "Native packet engine error ($error): $message"
        )
    }
}
