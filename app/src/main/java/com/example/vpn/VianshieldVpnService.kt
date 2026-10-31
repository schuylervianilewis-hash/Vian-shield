package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.config.WireGuardConfigParser
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android VPN service host.
 *
 * The Android service/lifecycle shell is intentionally engine-neutral. Until the
 * TrackerControl runtime is integrated, start requests fail closed and never
 * establish a TUN or report the VPN as connected.
 */
class VianshieldVpnService : VpnService() {

    companion object {
        const val ACTION_START_VPN = "com.example.vpn.ACTION_START_VPN"
        const val ACTION_STOP_VPN = "com.example.vpn.ACTION_STOP_VPN"
        const val EXTRA_CONFIG_STRING = "com.example.vpn.EXTRA_CONFIG_STRING"

        private const val NOTIFICATION_CHANNEL_ID = "vianshield_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        private val _vpnState = MutableStateFlow<VpnState>(VpnState.Stopped)
        val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

        fun startIntent(context: Context, configString: String): Intent =
            Intent(context, VianshieldVpnService::class.java).apply {
                action = ACTION_START_VPN
                putExtra(EXTRA_CONFIG_STRING, configString)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, VianshieldVpnService::class.java).apply {
                action = ACTION_STOP_VPN
            }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "Engine-neutral VianshieldVpnService created"
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
        if (_vpnState.value is VpnState.Connected || _vpnState.value is VpnState.Starting) {
            OperationalLogCatcher.warn(
                LogCategory.VPN_LIFECYCLE,
                "VpnService",
                "Duplicate VPN start request ignored"
            )
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification("VPN engine integration pending"))
        _vpnState.value = VpnState.Starting("Checking WireGuard profile")

        val parseResult = if (rawConfig.isBlank()) {
            Result.failure(IllegalArgumentException("Import a WireGuard profile before starting the VPN"))
        } else {
            WireGuardConfigParser.parse(rawConfig)
        }

        if (parseResult.isFailure) {
            val message = parseResult.exceptionOrNull()?.message ?: "WireGuard profile validation failed"
            _vpnState.value = VpnState.Error("ERR_CONFIG_INVALID", message)
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "VpnService", message)
        } else {
            val message = "VPN engine is not installed in this cleanup build. Protection has not started."
            _vpnState.value = VpnState.Error("ERR_ENGINE_NOT_CONFIGURED", message)
            OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "VpnService", message)
        }

        stopForegroundSafely()
        stopSelfResult(startId)
    }

    private fun handleStopRequest(startId: Int) {
        _vpnState.value = VpnState.Stopping
        stopForegroundSafely()
        _vpnState.value = VpnState.Stopped
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VPN service stop request completed; no engine was active"
        )
        stopSelfResult(startId)
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
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, VianshieldVpnService::class.java).apply {
            action = ACTION_STOP_VPN
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
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
            // The service may already have left the foreground state.
        }
    }

    override fun onDestroy() {
        if (_vpnState.value !is VpnState.Error) {
            _vpnState.value = VpnState.Stopped
        }
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VianshieldVpnService destroyed"
        )
        super.onDestroy()
    }

    override fun onRevoke() {
        OperationalLogCatcher.warn(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VPN permission revoked by system or another VPN application started"
        )
        _vpnState.value = VpnState.Stopped
        stopForegroundSafely()
        super.onRevoke()
    }
}
