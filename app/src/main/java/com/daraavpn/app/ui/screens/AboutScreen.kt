package com.daraavpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daraavpn.app.ui.theme.GlassBorder
import com.daraavpn.app.ui.theme.GoldAccent
import com.daraavpn.app.ui.theme.NavyGlass
import com.daraavpn.app.ui.theme.NavyNight
import com.daraavpn.app.ui.theme.TextPrimary
import com.daraavpn.app.ui.theme.TextSecondary

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyNight)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldAccent)
            }
            Text("حول التطبيق", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = GoldAccent,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("درع VPN (Daraa VPN)", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("الإصدار 1.0.0", color = GoldAccent, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(NavyGlass)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AboutInfoItem("سياسة الخصوصية", "لا يتم تسجيل أو تتبع نشاط التصفح الخاص بك (No-Logs).")
            AboutInfoItem("شروط الاستخدام", "يُمنع استخدام التطبيق في أي نشاط مخالف للقانون.")
            AboutInfoItem("التقنية", "WireGuard + Kotlin + Jetpack Compose + VpnService الرسمي")
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "تم تنفيذ وتطوير هذا التطبيق بواسطة أمجد حبيب",
            color = GoldAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )
    }
}

@Composable
private fun AboutInfoItem(title: String, body: String) {
    Column {
        Text(title, color = GoldAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(body, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}
