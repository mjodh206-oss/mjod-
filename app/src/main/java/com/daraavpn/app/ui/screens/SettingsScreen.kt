package com.daraavpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daraavpn.app.data.model.PerformanceMode
import com.daraavpn.app.data.model.VpnProtocol
import com.daraavpn.app.data.model.VpnSettings
import com.daraavpn.app.ui.theme.GlassBorder
import com.daraavpn.app.ui.theme.GoldAccent
import com.daraavpn.app.ui.theme.NavyGlass
import com.daraavpn.app.ui.theme.NavyNight
import com.daraavpn.app.ui.theme.TextPrimary
import com.daraavpn.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    settings: VpnSettings,
    onUpdateSettings: ((VpnSettings) -> VpnSettings) -> Unit,
    onSelectProtocol: (VpnProtocol) -> Unit,
    onSelectMode: (PerformanceMode) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyNight)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldAccent)
            }
            Text("الإعدادات", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        SettingSwitchRow(
            title = "Kill Switch (قاطع الإنترنت)",
            subtitle = "قطع الإنترنت تلقائياً إذا انقطع الـ VPN",
            checked = settings.killSwitch,
            onCheckedChange = { v -> onUpdateSettings { it.copy(killSwitch = v) } }
        )

        SettingSwitchRow(
            title = "اتصال تلقائي عند تشغيل الجهاز",
            subtitle = "استئناف الحماية فور إقلاع الهاتف",
            checked = settings.autoConnectOnBoot,
            onCheckedChange = { v -> onUpdateSettings { it.copy(autoConnectOnBoot = v) } }
        )

        SettingSwitchRow(
            title = "حماية DNS و IPv6",
            subtitle = "منع تسرب استعلامات DNS وعناوين IPv6",
            checked = settings.dnsProtection,
            onCheckedChange = { v -> onUpdateSettings { it.copy(dnsProtection = v, ipv6LeakProtection = v) } }
        )

        SettingSwitchRow(
            title = "Split Tunneling (تقسيم النفق)",
            subtitle = "اختيار تطبيقات تعمل خارج الـ VPN",
            checked = settings.splitTunnelingEnabled,
            onCheckedChange = { v -> onUpdateSettings { it.copy(splitTunnelingEnabled = v) } }
        )

        SettingSwitchRow(
            title = "الإشعارات",
            subtitle = "إشعار دائم أثناء الاتصال وتنبيه عند الانقطاع",
            checked = settings.notificationsEnabled,
            onCheckedChange = { v -> onUpdateSettings { it.copy(notificationsEnabled = v) } }
        )

        SettingSwitchRow(
            title = "الأصوات",
            subtitle = "نغمة قصيرة عند الاتصال والانقطاع",
            checked = settings.soundEffectsEnabled,
            onCheckedChange = { v -> onUpdateSettings { it.copy(soundEffectsEnabled = v) } }
        )

        Text("البروتوكول", color = GoldAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        VpnProtocol.entries.forEach { protocol ->
            SelectableOptionRow(
                label = protocol.labelAr,
                selected = settings.protocol == protocol,
                onClick = { onSelectProtocol(protocol) }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("وضع الأداء", color = GoldAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        PerformanceMode.entries.forEach { mode ->
            SelectableOptionRow(
                label = mode.labelAr,
                selected = settings.performanceMode == mode,
                onClick = { onSelectMode(mode) }
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavyGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NavyNight,
                checkedTrackColor = GoldAccent
            )
        )
    }
}

@Composable
private fun SelectableOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyGlass)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) GoldAccent else GlassBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (selected) {
            Text("مفعّل", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
