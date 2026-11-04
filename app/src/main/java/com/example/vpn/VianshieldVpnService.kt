package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.config.WireGuardConfigParser
import com.example.config.WireGuardProfile
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import com.example.ui.AppFirewallRuleRepository
import com.example.ui.FirewallSettingsRepository
import com.example.ui.DnsResolverCatalog
import eu.faircode.netguard.ServiceSinkhole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kollnig.missioncontrol.wg.WgEgress
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Sole owner of Vianshield's Android VPN TUN and packet-engine lifecycle.
 *
 * ServiceSinkhole is a plain JNI compatibility facade, not an Android
 * Service. WgEgress owns the optional userspace WireGuard tunnel behind this
 * same TUN. A blank profile starts local firewall-only protection.
 */
class VianshieldVpnService : VpnService() {

    companion object {
        const val ACTION_START_VPN = "com.example.vpn.ACTION_START_VPN"
        const val ACTION_STOP_VPN = "com.example.vpn.ACTION_STOP_VPN"
        const val EXTRA_CONFIG_STRING = "com.example.vpn.EXTRA_CONFIG_STRING"

        private const val NOTIFICATION_CHANNEL_ID = "vianshield_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val FIREWALL_ONLY_ENDPOINT = "Local firewall"
        private const val DEFAULT_FIREWALL_MTU = 1500

        private val _vpnState = MutableStateFlow<VpnState>(VpnState.Stopped)
        val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

        private val _nativeEngineAvailable = MutableStateFlow(false)
        val nativeEngineAvailable: StateFlow<Boolean> = _nativeEngineAvailable.asStateFlow()

        @Volatile
        private var activeInstance: VianshieldVpnService? = null

        fun startIntent(context: Context, configString: String): Intent =
            Intent(context, VianshieldVpnService::class.java).apply {
                action = ACTION_START_VPN
                putExtra(EXTRA_CONFIG_STRING, configString)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, VianshieldVpnService::class.java).apply {
                action = ACTION_STOP_VPN
            }

        /** Refresh native per-UID routing after the UI persists firewall rules. */
        fun notifyFirewallRulesChanged() {
            activeInstance?.refreshFirewallRouting()
        }

        /** Restart the single active TUN so Android's DNS server list is updated. */
        fun notifyDnsSettingsChanged() {
            activeInstance?.refreshDnsSettings()
        }
    }

    private val lifecycleExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Vianshield-VpnLifecycle").apply { isDaemon = true }
    }
    private val appFirewallRules by lazy { AppFirewallRuleRepository(applicationContext) }
    private val firewallSettings by lazy { FirewallSettingsRepository(applicationContext) }

    @Volatile
    private var packetEngine: ServiceSinkhole? = null

    @Volatile
    private var tunDescriptor: ParcelFileDescriptor? = null

    @Volatile
    private var activeRawConfig: String = ""

    @Volatile
    private var activeProfile: WireGuardProfile? = null

    @Volatile
    private var activeStartId: Int = 0

    @Volatile
    private var activeWireGuard: Boolean = false

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val wireGuardStateListener = Runnable {
        lifecycleExecutor.execute { refreshWireGuardHandshakeState() }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
        WgEgress.setRecoveryCallbacks(
            Runnable { requestWireGuardRecovery() },
            Runnable {
                OperationalLogCatcher.warn(
                    LogCategory.NETWORK_STATE,
                    "WgEgress",
                    "WireGuard connectivity monitor requested recovery"
                )
                updateNotification("WireGuard path degraded; recovering")
                if (vpnState.value is VpnState.Connected) {
                    _vpnState.value = VpnState.Starting("Recovering WireGuard connection")
                }
            }
        )
        registerUnderlyingNetworkCallback()
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "Vianshield VPN service created; one service owns the TUN and packet engine"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> handleStartRequest(
                intent.getStringExtra(EXTRA_CONFIG_STRING).orEmpty(),
                startId
            )
            ACTION_STOP_VPN -> handleStopRequest(startId)
            else -> stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleStartRequest(rawConfig: String, startId: Int) {
        when (vpnState.value) {
            is VpnState.Connected, is VpnState.Starting, is VpnState.Stopping -> {
                OperationalLogCatcher.warn(
                    LogCategory.VPN_LIFECYCLE,
                    "VpnService",
                    "Duplicate VPN start request ignored while a runtime transition is active"
                )
                return
            }
            else -> Unit
        }

        val profile = if (rawConfig.isBlank()) null else {
            val result = WireGuardConfigParser.parse(rawConfig)
            if (result.isFailure) {
                val message = result.exceptionOrNull()?.message ?: "WireGuard profile validation failed"
                _vpnState.value = VpnState.Error("ERR_CONFIG_INVALID", message)
                OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "VpnService", message)
                stopSelfResult(startId)
                return
            }
            result.getOrThrow()
        }

        activeRawConfig = rawConfig
        activeProfile = profile
        activeStartId = startId

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                if (profile == null) "Starting local firewall protection" else "Starting WireGuard firewall"
            )
        )
        _vpnState.value = VpnState.Starting(
            if (profile == null) "Initializing local firewall" else "Preparing WireGuard and firewall"
        )
        lifecycleExecutor.execute { startRuntime(rawConfig, profile, startId) }
    }

    private fun startRuntime(rawConfig: String, profile: WireGuardProfile?, startId: Int) {
        try {
            // Re-entry (for example a retry after a previous failed start) must
            // never leave an old native context or TUN descriptor behind.
            stopRuntime()

            appFirewallRules.reload()
            val engine = ServiceSinkhole(VianshieldPacketCallbacks(this, appFirewallRules))
            packetEngine = engine
            engine.initialize(Build.VERSION.SDK_INT, Log.WARN, false)

            val useWireGuard = profile != null
            activeWireGuard = useWireGuard
            engine.setWireGuardRequired(useWireGuard)
            refreshFirewallRoutingNow(engine, useWireGuard)

            _vpnState.value = VpnState.Starting("Establishing Android TUN interface")
            val tun = establishTun(profile)
            tunDescriptor = tun

            // Start the packet loop before WireGuard resolves its peer endpoint.
            // DNS used for a hostname endpoint can then pass through the native
            // DNS path; non-DNS traffic remains fail-closed until WG is ready.
            _vpnState.value = VpnState.Starting("Starting native firewall packet loop")
            engine.runTunnel(tun.fd, true, 3)
            if (!engine.isTunnelRunning()) {
                throw IllegalStateException("Native packet loop did not remain active after startup")
            }
            _nativeEngineAvailable.value = true

            if (profile == null) {
                WgEgress.stop { engine.stopWireGuardSocketpair() }
                engine.stopWireGuardSocketpair()
                engine.setWireGuardRequired(false)
                _vpnState.value = VpnState.Connected(
                    FIREWALL_ONLY_ENDPOINT,
                    "10.1.10.1/32, fd00:1::1/128",
                    DEFAULT_FIREWALL_MTU
                )
                updateNotification("Local firewall protection active")
                OperationalLogCatcher.info(
                    LogCategory.VPN_LIFECYCLE,
                    "VpnService",
                    "Native firewall packet loop active without a WireGuard profile"
                )
                return
            }

            WgEgress.addStateListener(wireGuardStateListener)
            _vpnState.value = VpnState.Starting("Starting userspace WireGuard tunnel")
            val wgStarted = WgEgress.startOrUpdate(
                true,
                rawConfig,
                this,
                tun,
                true,
                false,
                { engine.startWireGuardSocketpair() },
                { engine.stopWireGuardSocketpair() },
                { qname, aname, resource, ttl ->
                    engine.wireGuardDnsResolved(qname, aname, resource, ttl)
                }
            )
            if (!wgStarted) {
                throw IllegalStateException(
                    WgEgress.getLastError() ?: "Userspace WireGuard tunnel failed to start"
                )
            }

            // startOrUpdate means the Rust tunnel was created, not that the
            // peer handshake completed. Keep the UI in Connecting until the
            // WgEgress watchdog reports a real handshake.
            refreshWireGuardHandshakeState()
            updateNotification("Waiting for WireGuard handshake")
        } catch (error: Throwable) {
            OperationalLogCatcher.error(
                LogCategory.ENGINE_INIT,
                "VpnService",
                "VPN runtime startup failed: ${error.message ?: error.javaClass.simpleName}"
            )
            stopRuntime()
            if (vpnState.value !is VpnState.Stopping) {
                _vpnState.value = VpnState.Error(
                    "ERR_ENGINE_START",
                    error.message ?: "Vianshield could not start its packet engine"
                )
                stopForegroundSafely()
                stopSelfResult(startId)
            }
        }
    }

    private fun establishTun(profile: WireGuardProfile?): ParcelFileDescriptor {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(profile?.mtu ?: DEFAULT_FIREWALL_MTU)
            .setBlocking(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        var hasIpv4 = false
        var hasIpv6 = false
        profile?.addresses.orEmpty().forEach { cidr ->
            val parts = cidr.split("/", limit = 2)
            require(parts.size == 2) { "Invalid tunnel interface address: $cidr" }
            val address = parseNumericAddress(parts[0])
                ?: throw IllegalArgumentException("Invalid numeric tunnel address: ${parts[0]}")
            val prefix = parts[1].toIntOrNull()
                ?: throw IllegalArgumentException("Invalid tunnel prefix length: $cidr")
            val maxPrefix = if (address is Inet4Address) 32 else 128
            require(prefix in 0..maxPrefix) { "Tunnel prefix length out of range: $cidr" }
            builder.addAddress(address, prefix)
            if (address is Inet4Address) hasIpv4 = true else if (address is Inet6Address) hasIpv6 = true
        }

        if (!hasIpv4) builder.addAddress(InetAddress.getByName("10.1.10.1"), 32)
        if (!hasIpv6) builder.addAddress(InetAddress.getByName("fd00:1::1"), 128)

        // One Android TUN catches both families. The native packet engine
        // applies app policy and routes direct vs WireGuard per UID.
        builder.addRoute(InetAddress.getByName("0.0.0.0"), 0)
        builder.addRoute(InetAddress.getByName("::"), 0)

        val dnsServers = DnsResolverCatalog.resolve(
            firewallSettings.load().dnsProvider,
            profile?.dnsServers.orEmpty()
        ).mapNotNull(::parseNumericAddress).ifEmpty {
            listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
        }
        dnsServers.distinctBy { it.hostAddress }.forEach(builder::addDnsServer)

        return builder.establish()
            ?: throw IllegalStateException("Android did not establish the Vianshield TUN interface")
    }

    private fun parseNumericAddress(value: String): InetAddress? {
        val candidate = value.trim().removePrefix("[").removeSuffix("]")
        val validIpv4 = candidate.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) &&
            candidate.split(".").all { (it.toIntOrNull() ?: 256) in 0..255 }
        val validIpv6 = candidate.contains(":") &&
            candidate.all { it.isDigit() || it in "abcdefABCDEF:." }
        if (!validIpv4 && !validIpv6) return null
        return runCatching { InetAddress.getByName(candidate) }.getOrNull()
    }

    private fun currentDirectUids(): IntArray {
        if (appFirewallRules.rules.value.values.none { it.mode == com.example.ui.FirewallMode.DIRECT }) {
            return intArrayOf()
        }

        return try {
            packageManager.getInstalledApplications(0)
                .asSequence()
                .map { it.uid }
                .distinct()
                .filter { uid ->
                    val packages = packageManager.getPackagesForUid(uid)
                    PacketPolicy.shouldRouteDirect(packages, appFirewallRules)
                }
                .toList()
                .toIntArray()
        } catch (error: Exception) {
            OperationalLogCatcher.warn(
                LogCategory.VPN_LIFECYCLE,
                "VpnService",
                "Could not resolve Direct-mode app UIDs; WireGuard remains the default route"
            )
            intArrayOf()
        }
    }

    private fun refreshFirewallRouting() {
        lifecycleExecutor.execute {
            val engine = packetEngine ?: return@execute
            if (!engine.isTunnelRunning()) return@execute
            appFirewallRules.reload()
            refreshFirewallRoutingNow(engine, activeWireGuard)
        }
    }

    private fun refreshDnsSettings() {
        val state = vpnState.value
        if (state !is VpnState.Connected && state !is VpnState.Starting) return
        lifecycleExecutor.execute {
            if (packetEngine == null || tunDescriptor == null) return@execute
            _vpnState.value = VpnState.Starting("Applying DNS provider")
            val raw = activeRawConfig
            val profile = activeProfile
            val startId = activeStartId
            stopRuntime()
            startRuntime(raw, profile, startId)
        }
    }

    private fun refreshFirewallRoutingNow(engine: ServiceSinkhole, useWireGuard: Boolean) {
        val directUids = currentDirectUids()
        engine.setWireGuardRoute(
            directUids,
            useWireGuard,
            directUids.isNotEmpty()
        )
    }

    private fun refreshWireGuardHandshakeState() {
        if (!activeWireGuard || packetEngine == null || tunDescriptor == null) return
        val handshake = WgEgress.latestHandshakeMillisOrNull() ?: 0L
        if (handshake > 0L) {
            val profile = activeProfile
            _vpnState.value = VpnState.Connected(
                profile?.let { "${it.endpointHost}:${it.endpointPort}" } ?: FIREWALL_ONLY_ENDPOINT,
                profile?.addresses?.joinToString(", ") ?: "",
                profile?.mtu ?: DEFAULT_FIREWALL_MTU
            )
            updateNotification("WireGuard firewall active")
        } else if (vpnState.value !is VpnState.Stopping && vpnState.value !is VpnState.Error) {
            _vpnState.value = VpnState.Starting("Waiting for WireGuard handshake")
        }
    }

    internal fun onNativePacketFailure(error: Int, message: String) {
        val current = vpnState.value
        if (current !is VpnState.Connected && current !is VpnState.Starting) return
        lifecycleExecutor.execute {
            val latest = vpnState.value
            if (latest !is VpnState.Connected && latest !is VpnState.Starting) return@execute
            stopRuntime()
            _vpnState.value = VpnState.Error(
                "ERR_NATIVE_PACKET_LOOP",
                "Native packet engine stopped ($error): $message"
            )
            stopForegroundSafely()
            stopSelf()
        }
    }

    private fun requestWireGuardRecovery() {
        if (!activeWireGuard) return
        lifecycleExecutor.execute {
            val engine = packetEngine ?: return@execute
            val tun = tunDescriptor ?: return@execute
            if (!engine.isTunnelRunning()) return@execute
            _vpnState.value = VpnState.Starting("Recovering WireGuard connection")
            try {
                val restarted = WgEgress.startOrUpdate(
                    true,
                    activeRawConfig,
                    this,
                    tun,
                    true,
                    false,
                    { engine.startWireGuardSocketpair() },
                    { engine.stopWireGuardSocketpair() },
                    { qname, aname, resource, ttl ->
                        engine.wireGuardDnsResolved(qname, aname, resource, ttl)
                    }
                )
                if (!restarted) {
                    throw IllegalStateException(
                        WgEgress.getLastError() ?: "WireGuard tunnel restart failed"
                    )
                }
                refreshWireGuardHandshakeState()
            } catch (error: Throwable) {
                stopRuntime()
                _vpnState.value = VpnState.Error(
                    "ERR_WIREGUARD_RECOVERY",
                    error.message ?: "WireGuard recovery failed"
                )
                stopForegroundSafely()
                stopSelf()
            }
        }
    }

    private fun handleStopRequest(startId: Int) {
        _vpnState.value = VpnState.Stopping
        lifecycleExecutor.execute {
            stopRuntime()
            _vpnState.value = VpnState.Stopped
            OperationalLogCatcher.info(
                LogCategory.VPN_LIFECYCLE,
                "VpnService",
                "Vianshield firewall and VPN runtime stopped"
            )
            stopForegroundSafely()
            stopSelfResult(startId)
        }
    }

    /**
     * Stop ordering is deliberate: the native loop no longer reads or writes
     * the TUN before the WireGuard bridge is stopped and the TUN descriptor
     * is closed.
     */
    private fun stopRuntime() {
        val oldEngine = packetEngine
        val oldTun = tunDescriptor
        packetEngine = null
        tunDescriptor = null
        activeWireGuard = false
        _nativeEngineAvailable.value = false

        WgEgress.removeStateListener(wireGuardStateListener)
        if (oldEngine != null) {
            runCatching { oldEngine.stopTunnel() }
            runCatching { WgEgress.stop { oldEngine.stopWireGuardSocketpair() } }
            runCatching { oldEngine.stopWireGuardSocketpair() }
            runCatching { oldEngine.setWireGuardRequired(false) }
            runCatching { oldEngine.close() }
        } else {
            runCatching { WgEgress.stop { } }
        }
        runCatching { oldTun?.close() }
    }

    private fun registerUnderlyingNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (activeWireGuard && WgEgress.isRunning()) {
                    WgEgress.onUnderlyingNetworkChanged()
                }
            }
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (error: Exception) {
            OperationalLogCatcher.warn(
                LogCategory.NETWORK_STATE,
                "VpnService",
                "Underlying network monitor could not be registered: ${error.message}"
            )
        }
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_vpn_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_vpn_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, VianshieldVpnService::class.java).apply {
            action = ACTION_STOP_VPN
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.btn_stop_vpn), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopForegroundSafely() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Service may already have exited foreground state.
        }
    }

    override fun onDestroy() {
        runCatching { stopRuntime() }
        networkCallback?.let { callback ->
            runCatching {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
            }
        }
        WgEgress.setRecoveryCallbacks(Runnable {}, Runnable {}, null)
        if (activeInstance === this) activeInstance = null
        lifecycleExecutor.shutdownNow()
        if (vpnState.value !is VpnState.Error) _vpnState.value = VpnState.Stopped
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "Vianshield VPN service destroyed"
        )
        super.onDestroy()
    }

    override fun onRevoke() {
        OperationalLogCatcher.warn(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VPN permission revoked by system or another VPN application started"
        )
        handleStopRequest(activeStartId)
        super.onRevoke()
    }
}
