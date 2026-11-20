package com.daraavpn.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daraavpn.app.ui.theme.GoldAccent
import com.daraavpn.app.ui.theme.GoldSoft
import com.daraavpn.app.ui.theme.NavyNight
import com.daraavpn.app.ui.theme.TextPrimary
import com.daraavpn.app.ui.theme.TextSecondary

private data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            "خصوصية حقيقية بلا سجلات",
            "تشفير قوي لحماية بياناتك على الشبكات العامة والخاصة دون تتبع نشاطك.",
            Icons.Default.Lock
        ),
        OnboardingPage(
            "سرعة فائقة وثبات دائم",
            "بروتوكول WireGuard مع اختيار تلقائي لأسرع خادم في الدول العربية والأوروبية.",
            Icons.Default.Bolt
        ),
        OnboardingPage(
            "سهولة بلمسة واحدة",
            "واجهة عربية فخمة وزر اتصال واحد يمنحك الحماية الكاملة خلال ثوانٍ.",
            Icons.Default.TouchApp
        )
    )

    var currentPage by remember { mutableIntStateOf(0) }
    val page = pages[currentPage]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyNight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onFinish) {
                Text("تخطي", color = TextSecondary, fontSize = 15.sp)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(GoldSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = page.title,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = page.description,
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { idx ->
                    Box(
                        modifier = Modifier
                            .size(if (idx == currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(if (idx == currentPage) GoldAccent else TextSecondary.copy(alpha = 0.4f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (currentPage < pages.lastIndex) currentPage++ else onFinish()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = NavyNight
                )
            ) {
                Text(
                    text = if (currentPage == pages.lastIndex) "ابدأ" else "التالي",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
