package com.daraavpn.app.data.repository

import com.daraavpn.app.data.model.RegionTab
import com.daraavpn.app.data.model.ServerStatus
import com.daraavpn.app.data.model.VpnServer

object ServerRepository {

    val initialServers: List<VpnServer> = listOf(
        // Arab Countries (Syria first)
        VpnServer("sy-1", "سوريا (دمشق 1)", "Syria (Damascus 1)", "🇸🇾", RegionTab.ARAB, 24, 32, ServerStatus.AVAILABLE, "185.107.56.12", isFavorite = true, isPrimarySyria = true, fallbackServerId = "lb-1"),
        VpnServer("sy-2", "سوريا (دمشق 2)", "Syria (Damascus 2)", "🇸🇾", RegionTab.ARAB, 29, 41, ServerStatus.AVAILABLE, "185.107.56.44", isPrimarySyria = true, fallbackServerId = "jo-1"),
        VpnServer("sa-1", "السعودية", "Saudi Arabia", "🇸🇦", RegionTab.ARAB, 38, 45, ServerStatus.AVAILABLE, "94.124.18.90"),
        VpnServer("ae-1", "الإمارات", "UAE", "🇦🇪", RegionTab.ARAB, 42, 36, ServerStatus.AVAILABLE, "185.220.12.77"),
        VpnServer("qa-1", "قطر", "Qatar", "🇶🇦", RegionTab.ARAB, 46, 28, ServerStatus.AVAILABLE, "176.202.40.15"),
        VpnServer("kw-1", "الكويت", "Kuwait", "🇰🇼", RegionTab.ARAB, 49, 52, ServerStatus.AVAILABLE, "185.80.24.31"),
        VpnServer("bh-1", "البحرين", "Bahrain", "🇧🇭", RegionTab.ARAB, 51, 30, ServerStatus.AVAILABLE, "185.134.10.22"),
        VpnServer("om-1", "عُمان", "Oman", "🇴🇲", RegionTab.ARAB, 55, 25, ServerStatus.AVAILABLE, "185.115.32.19"),
        VpnServer("jo-1", "الأردن", "Jordan", "🇯🇴", RegionTab.ARAB, 31, 38, ServerStatus.AVAILABLE, "176.29.14.88"),
        VpnServer("lb-1", "لبنان", "Lebanon", "🇱🇧", RegionTab.ARAB, 27, 34, ServerStatus.AVAILABLE, "185.104.12.63"),
        VpnServer("iq-1", "العراق", "Iraq", "🇮🇶", RegionTab.ARAB, 58, 64, ServerStatus.BUSY, "185.99.45.10"),
        VpnServer("eg-1", "مصر", "Egypt", "🇪🇬", RegionTab.ARAB, 44, 48, ServerStatus.AVAILABLE, "197.45.120.5"),
        VpnServer("ma-1", "المغرب", "Morocco", "🇲🇦", RegionTab.ARAB, 68, 35, ServerStatus.AVAILABLE, "196.200.14.2"),
        VpnServer("tn-1", "تونس", "Tunisia", "🇹🇳", RegionTab.ARAB, 64, 29, ServerStatus.AVAILABLE, "197.14.88.11"),

        // European Countries
        VpnServer("de-1", "ألمانيا", "Germany", "🇩🇪", RegionTab.EUROPE, 48, 28, ServerStatus.AVAILABLE, "185.230.11.40", isFavorite = true),
        VpnServer("nl-1", "هولندا", "Netherlands", "🇳🇱", RegionTab.EUROPE, 52, 33, ServerStatus.AVAILABLE, "185.156.72.18"),
        VpnServer("fr-1", "فرنسا", "France", "🇫🇷", RegionTab.EUROPE, 56, 40, ServerStatus.AVAILABLE, "185.190.44.92"),
        VpnServer("gb-1", "بريطانيا", "United Kingdom", "🇬🇧", RegionTab.EUROPE, 59, 44, ServerStatus.AVAILABLE, "185.182.90.14"),
        VpnServer("it-1", "إيطاليا", "Italy", "🇮🇹", RegionTab.EUROPE, 54, 37, ServerStatus.AVAILABLE, "185.140.22.51"),
        VpnServer("es-1", "إسبانيا", "Spain", "🇪🇸", RegionTab.EUROPE, 63, 31, ServerStatus.AVAILABLE, "185.160.18.73"),
        VpnServer("se-1", "السويد", "Sweden", "🇸🇪", RegionTab.EUROPE, 65, 22, ServerStatus.AVAILABLE, "185.210.33.66"),
        VpnServer("ch-1", "سويسرا", "Switzerland", "🇨🇭", RegionTab.EUROPE, 50, 26, ServerStatus.AVAILABLE, "185.158.14.99"),
        VpnServer("ro-1", "رومانيا", "Romania", "🇷🇴", RegionTab.EUROPE, 45, 39, ServerStatus.AVAILABLE, "185.170.52.27"),
        VpnServer("pl-1", "بولندا", "Poland", "🇵🇱", RegionTab.EUROPE, 53, 30, ServerStatus.AVAILABLE, "185.188.61.84")
    )
}
