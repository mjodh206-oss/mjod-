package com.daraavpn.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daraavpn.app.data.model.ServerStatus
import com.daraavpn.app.data.model.VpnServer
import com.daraavpn.app.ui.theme.DangerRed
import com.daraavpn.app.ui.theme.EmeraldConnected
import com.daraavpn.app.ui.theme.GlassBorder
import com.daraavpn.app.ui.theme.GoldAccent
import com.daraavpn.app.ui.theme.GoldSoft
import com.daraavpn.app.ui.theme.NavyGlass
import com.daraavpn.app.ui.theme.NavyNight
import com.daraavpn.app.ui.theme.NavySurface
import com.daraavpn.app.ui.theme.TextPrimary
import com.daraavpn.app.ui.theme.TextSecondary
import com.daraavpn.app.ui.theme.WarningAmber
import com.daraavpn.app.ui.viewmodel.MainUiState

@Composable
fun ServersScreen(
    uiState: MainUiState,
    onSearchChange: (String) -> Unit,
    onSelectBest: () -> Unit,
    onSelectServer: (VpnServer) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBack: () -> Unit
) {
    val filteredServers = uiState.servers
        .filter { it.region == uiState.selectedTab }
        .filter {
            uiState.searchQuery.isBlank() ||
                it.countryNameAr.contains(uiState.searchQuery, ignoreCase = true) ||
                it.countryNameEn.contains(uiState.searchQuery, ignoreCase = true)
        }
        .sortedWith(compareByDescending<VpnServer> { it.isPrimarySyria }.thenByDescending { it.isFavorite })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyNight)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldAccent)
            }
            Text(
                text = "قائمة الدول والخوادم",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("ابحث عن دولة...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldAccent) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldAccent,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = NavySurface,
                unfocusedContainerColor = NavySurface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Best Server Auto Option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GoldSoft)
                .border(1.dp, GoldAccent, RoundedCornerShape(16.dp))
                .clickable {
                    onSelectBest()
                    onBack()
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldAccent)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("أفضل سيرفر تلقائي", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("اختيار أسرع خادم حسب Ping والحمل", color = GoldAccent, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredServers, key = { it.id }) { server ->
                val isSelected = server.id == uiState.selectedServer.id
                val signalColor = when {
                    server.pingMs < 45 -> EmeraldConnected
                    server.pingMs < 65 -> WarningAmber
                    else -> DangerRed
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NavyGlass)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) GoldAccent else GlassBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSelectServer(server)
                            onBack()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = server.flagEmoji, fontSize = 26.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = server.countryNameAr,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val statusText = when (server.status) {
                                ServerStatus.AVAILABLE -> "متاح"
                                ServerStatus.BUSY -> "مشغول"
                                ServerStatus.MAINTENANCE -> "صيانة"
                            }
                            Text(
                                text = "${server.pingMs} ms • الحمل ${server.loadPercent}% • $statusText",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(signalColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { onToggleFavorite(server.id) }) {
                            Icon(
                                imageVector = if (server.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "مفضلة",
                                tint = if (server.isFavorite) GoldAccent else TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
