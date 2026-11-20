package com.daraavpn.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DaraaApp : Application() {

    companion object {
        const val VPN_CHANNEL_ID = "daraa_vpn_status_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VPN_CHANNEL_ID,
                "حالة اتصال درع VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار دائم لحالة الاتصال بالشبكة الافتراضية الخاصة"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
