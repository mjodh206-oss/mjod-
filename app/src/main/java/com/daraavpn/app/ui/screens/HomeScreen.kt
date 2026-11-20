package com.daraavpn.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daraavpn.app.data.model.ConnectionState
import com.daraavpn.app.data.model.RegionTab
import com.daraavpn.app.ui.theme.DisconnectedGray
import com.daraavpn.app.ui.theme.EmeraldConnected
import com.daraavpn.app.ui.theme.EmeraldGlow
import com.daraavpn.app.ui.theme.GlassBorder
import com.daraavpn.app.ui.theme.GoldAccent
import com.daraavpn.app.ui.theme.NavyGlass
import com.daraavpn.app.ui.theme.NavyNight
import com.daraavpn.app.ui.theme.NavySurface
import com.daraavpn.app.ui.theme.TextPrimary
import com.daraavpn.app.ui.theme.TextSecondary
import com.daraavpn.app.ui.theme.WarningAmber
import com.daraavpn.app.ui.viewmodel.MainUiState
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: MainUiState,
    onTabSelected: (RegionTab) -> Unit,
    onToggleConnect: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val isConnected = uiState.connectionState == ConnectionState.CONNECTED
    val isConnecting = uiState.connectionState == ConnectionState.CONNECTING

    val buttonColor by animateColorAsState(
        targetValue = when (uiState.connectionState) {
            ConnectionState.DISCONNECTED -> DisconnectedGray
            ConnectionState.CONNECTING -> WarningAmber
            ConnectionState.CONNECTED -> EmeraldConnected
        },
        label = "buttonColor"
    )

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyNight)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = GoldAccent)
            }
            Text(
                text = "درع VPN",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onOpenAbout) {
                Icon(Icons.Default.Info, contentDescription = "حول التطبيق", tint = GoldAccent)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Region Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NavySurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            RegionTabButton(
                title = "دول عربية",
                selected = uiState.selectedTab == RegionTab.ARAB,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(RegionTab.ARAB) }
            )
            RegionTabButton(
                title = "دول أوروبية",
                selected = uiState.selectedTab == RegionTab.EUROPE,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(RegionTab.EUROPE) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Connect Button with Pulse Glow
        Box(contentAlignment = Alignment.Center) {
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(EmeraldGlow)
                )
            }
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .clip(CircleShape)
                    .background(NavyGlass)
                    .border(4.dp, buttonColor, CircleShape)
                    .clickable { onToggleConnect() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "اتصال",
                        tint = buttonColor,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (uiState.connectionState) {
                            ConnectionState.DISCONNECTED -> "اتصال"
                            ConnectionState.CONNECTING -> "جاري..."
                            ConnectionState.CONNECTED -> "قطع"
                        },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Status & Timer
        Text(
            text = when (uiState.connectionState) {
                ConnectionState.DISCONNECTED -> "غير متصل"
                ConnectionState.CONNECTING -> "جاري الاتصال..."
                ConnectionState.CONNECTED -> "متصل • ${formatDuration(uiState.connectedSeconds)}"
            },
            color = if (isConnected) EmeraldConnected else if (isConnecting) WarningAmber else TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isConnected) "IP الجديد: ${uiState.selectedServer.ipAddress}" else "اضغط للاتصال الآمن",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // Selected Country Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(NavyGlass)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .clickable { onOpenServers() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = uiState.selectedServer.flagEmoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = uiState.selectedServer.countryNameAr,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ping: ${uiState.selectedServer.pingMs} ms • ${uiState.settings.performanceMode.labelAr}",
                        color = GoldAccent,
                        fontSize = 12.sp
                    )
                }
            }
            Icon(Icons.Default.Public, contentDescription = "تغيير الدولة", tint = GoldAccent)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live Counters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                label = "التحميل",
                value = String.format(Locale.US, "%.1f Mbps", uiState.downloadMbps),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "الرفع",
                value = String.format(Locale.US, "%.1f Mbps", uiState.uploadMbps),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "البيانات",
                value = String.format(Locale.US, "%.1f MB", uiState.dataUsedMb),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RegionTabButton(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GoldAccent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) NavyNight else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NavyGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}
