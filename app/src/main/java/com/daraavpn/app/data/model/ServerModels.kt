package com.daraavpn.app.data.model

enum class RegionTab { ARAB, EUROPE }

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

enum class ServerStatus { AVAILABLE, BUSY, MAINTENANCE }

enum class VpnProtocol(val labelAr: String, val labelEn: String) {
    AUTO("تلقائي (ذكي)", "Auto (Smart)"),
    WIREGUARD("WireGuard", "WireGuard"),
    OPENVPN("OpenVPN (احتياطي)", "OpenVPN (Fallback)")
}

enum class PerformanceMode(val labelAr: String, val labelEn: String) {
    STANDARD("قياسي", "Standard"),
    TURBO("وضع Turbo", "Turbo Mode"),
    GAMING("وضع الألعاب", "Gaming Mode"),
    STREAMING("وضع البث", "Streaming Mode")
}

data class VpnServer(
    val id: String,
    val countryNameAr: String,
    val countryNameEn: String,
    val flagEmoji: String,
    val region: RegionTab,
    val pingMs: Int,
    val loadPercent: Int,
    val status: ServerStatus,
    val ipAddress: String,
    val isFavorite: Boolean = false,
    val isPrimarySyria: Boolean = false,
    val fallbackServerId: String? = null
)

data class VpnSettings(
    val killSwitch: Boolean = true,
    val autoConnectOnBoot: Boolean = false,
    val protocol: VpnProtocol = VpnProtocol.AUTO,
    val dnsProtection: Boolean = true,
    val ipv6LeakProtection: Boolean = true,
    val splitTunnelingEnabled: Boolean = false,
    val performanceMode: PerformanceMode = PerformanceMode.TURBO,
    val isArabic: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = true
)
