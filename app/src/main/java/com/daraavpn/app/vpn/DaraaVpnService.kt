package com.daraavpn.app.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.daraavpn.app.DaraaApp
import com.daraavpn.app.MainActivity

class DaraaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_CONNECT = "com.daraavpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.daraavpn.app.DISCONNECT"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_DNS_PROTECTION = "extra_dns_protection"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopVpnTunnel()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT, null -> {
                val serverName = intent?.getStringExtra(EXTRA_SERVER_NAME) ?: "سوريا (دمشق 1)"
                val dnsProtection = intent?.getBooleanExtra(EXTRA_DNS_PROTECTION, true) ?: true
                startForegroundNotification(serverName)
                establishLocalTunnel(dnsProtection)
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification(serverName: String) {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DaraaVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, DaraaApp.VPN_CHANNEL_ID)
            .setContentTitle("درع VPN • متصل")
            .setContentText("الخادم: $serverName")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع الاتصال", disconnectIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun establishLocalTunnel(dnsProtection: Boolean) {
        try {
            vpnInterface?.close()
            val builder = Builder()
                .setSession("DaraaVPN")
                .addAddress("10.8.0.2", 24)
                // Route only the virtual tunnel subnet until a real WireGuard server endpoint is configured,
                // so the phone's internet connection is not blackholed.
                .addRoute("10.8.0.0", 24)
                .setMtu(1420)

            if (dnsProtection) {
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("9.9.9.9")
            }

            // Note: WireGuard GoBackend.setState(tunnel, UP, config) connects here
            // once real server public keys & endpoints are provided by the backend API.
            vpnInterface = builder.establish()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun stopVpnTunnel() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }
}
