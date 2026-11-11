package app.majwuda.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.majwuda.MajwudaApp
import app.majwuda.BuildConfig
import app.majwuda.R
import app.majwuda.data.AssistantSettings
import app.majwuda.data.ModelOption
import app.majwuda.data.ThemeMode
import app.majwuda.services.WakeWordService
import app.majwuda.speech.SpeechManager
import app.majwuda.ui.theme.MajwudaPalette
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = MajwudaApp.from(context)
    val settings by app.settings.settingsFlow.collectAsState(initial = app.settings.state.value)
    val scope = rememberCoroutineScope()

    var apiKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var gateway by remember(settings.gatewayUrl) { mutableStateOf(settings.gatewayUrl) }
    var assistantName by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var userName by remember(settings.userName) { mutableStateOf(settings.userName) }
    var customInstructions by remember(settings.customInstructions) { mutableStateOf(settings.customInstructions) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var savingKey by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("الإعدادات", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // ------------------------------------------------ connection
            Section(title = "الاتصال بكلود") {
                Text(
                    "مجودة يعمل بقلب Claude (Anthropic). الصق مفتاح API الخاص بك — يُخزَّن مشفّرًا على جهازك عبر AndroidKeyStore ولا يغادر الهاتف.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MajwudaPalette.TextSecondary,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; keySaved = false },
                    label = { Text("مفتاح Claude API") },
                    placeholder = { Text("sk-ant-...") },
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Text(if (apiKeyVisible) "إخفاء" else "إظهار")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        savingKey = true
                        scope.launch {
                            app.settings.setApiKey(apiKey)
                            app.settings.setGatewayUrl(gateway)
                            savingKey = false
                            keySaved = true
                        }
                    },
                    enabled = !savingKey,
                ) {
                    if (savingKey) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (keySaved) "حُفظت ✓" else "حفظ المفتاح والبوابة")
                    }
                }
                OutlinedTextField(
                    value = gateway,
                    onValueChange = { gateway = it },
                    label = { Text("بوابة اختيارية (Gateway URL)") },
                    placeholder = { Text("https://gateway.example.com (فارغ = اتصال مباشر)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                Text("النموذج", style = MaterialTheme.typography.labelMedium, color = MajwudaPalette.TextSecondary)
                Button(onClick = { modelExpanded = true }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MajwudaPalette.SurfaceHigh, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(AssistantSettings.MODEL_OPTIONS.firstOrNull { it.id == settings.model }?.title ?: settings.model)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.ExpandMore, null)
                }
                DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                    AssistantSettings.MODEL_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.title)
                                    Text(option.subtitle, style = MaterialTheme.typography.labelSmall, color = MajwudaPalette.TextSecondary)
                                }
                            },
                            onClick = {
                                modelExpanded = false
                                scope.launch { app.settings.setModel(option.id) }
                            },
                        )
                    }
                }
                Text(
                    "الحد الأقصى للتوكنز في الرد: ${settings.maxTokens}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MajwudaPalette.TextSecondary,
                )
                Slider(
                    value = settings.maxTokens.toFloat(),
                    onValueChange = { scope.launch { app.settings.setMaxTokens(it.toInt()) } },
                    valueRange = 256f..4096f,
                    steps = 15,
                )
            }

            // ------------------------------------------------ assistant identity
            Section(title = "شخصية المساعد") {
                OutlinedTextField(
                    value = assistantName,
                    onValueChange = { assistantName = it },
                    label = { Text("اسم المساعد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("اسمك") },
                    placeholder = { Text("اختياري — لتتعرف مجودة عليك") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = customInstructions,
                    onValueChange = { customInstructions = it },
                    label = { Text("تعليمات إضافية لك") },
                    placeholder = { Text("مثال: كن موجزًا جدًا، استخدم أرقامًا هندية…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                Button(onClick = {
                    scope.launch {
                        app.settings.setAssistantName(assistantName)
                        app.settings.setUserName(userName)
                        app.settings.setCustomInstructions(customInstructions)
                    }
                }) {
                    Text("حفظ الشخصية")
                }
            }

            // ------------------------------------------------ voice
            Section(title = "الصوت") {
                SwitchRow(
                    icon = Icons.Filled.VolumeUp,
                    title = "تحدث مجودة بصوت",
                    subtitle = "قراءة الإجابات بصوت عربي طبيعي",
                    checked = settings.voiceEnabled,
                    onChange = { scope.launch { app.settings.setVoiceEnabled(it) } },
                )
                Text(
                    "سرعة الكلام: %.1fx".format(settings.speechRate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MajwudaPalette.TextSecondary,
                )
                Slider(
                    value = settings.speechRate,
                    onValueChange = { scope.launch { app.settings.setSpeechRate(it) } },
                    valueRange = 0.6f..1.8f,
                )
                TextButton(onClick = {
                    app.let {
                        SpeechManager.get(context).speak("أهلًا! أنا مجودة، مساعدتك الذكية. هذا صوتي.")
                    }
                }) {
                    Icon(Icons.Filled.RecordVoiceOver, null)
                    Spacer(Modifier.width(6.dp))
                    Text("جرّب الصوت")
                }
            }

            // ------------------------------------------------ system integration
            Section(title = "النظام والتكامل") {
                SwitchRow(
                    icon = Icons.Filled.Mic,
                    title = "الاستماع لكلمة التنبيه «يا مجودة»",
                    subtitle = "يعمل في الخلفية باستمرار — يستهلك بطارية أكثر. يتطلب إذن الميكروفون.",
                    checked = settings.wakePhraseEnabled,
                    onChange = { enabled ->
                        scope.launch {
                            app.settings.setWakePhraseEnabled(enabled)
                            val intent = Intent(context, WakeWordService::class.java)
                            if (enabled) context.startForegroundService(intent) else context.stopService(intent)
                        }
                    },
                )
                SwitchRow(
                    icon = Icons.Filled.Screenshot,
                    title = "التحكم بالشاشة",
                    subtitle = "قراءة الشاشة والضغط والكتابة — يتطلب تفعيل خدمة إمكانية الوصول من النظام",
                    checked = settings.screenControlEnabled,
                    onChange = { enabled ->
                        scope.launch { app.settings.setScreenControlEnabled(enabled) }
                        if (enabled) {
                            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    },
                )
                SwitchRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "قراءة الإشعارات",
                    subtitle = "لتلخيص إشعاراتك والرد على الرسائل بطلبك — يتطلب إذن الوصول للإشعارات",
                    checked = settings.notificationReadEnabled,
                    onChange = { enabled ->
                        scope.launch { app.settings.setNotificationReadEnabled(enabled) }
                        if (enabled) {
                            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    },
                )
                SwitchRow(
                    icon = Icons.Filled.Web,
                    title = "الوصول المباشر للإنترنت",
                    subtitle = "بحث الويب والطقس وأسعار الصرف من جهازك مباشرة",
                    checked = settings.webAccessEnabled,
                    onChange = { scope.launch { app.settings.setWebAccessEnabled(it) } },
                )
                SwitchRow(
                    icon = Icons.Filled.WifiTethering,
                    title = "اتصال تلقائي دون تأكيد",
                    subtitle = "تنفيذ الاتصالات والرسائل النصية دون سؤالك كل مرة (غير مفعّل افتراضيًا)",
                    checked = settings.autoCall,
                    onChange = { scope.launch { app.settings.setAutoCall(it) } },
                )
                SwitchRow(
                    icon = Icons.Filled.Language,
                    title = "رسائل نصية تلقائية دون تأكيد",
                    subtitle = "إرسال SMS دون سؤالك كل مرة (غير مفعّل افتراضيًا)",
                    checked = settings.autoSendSms,
                    onChange = { scope.launch { app.settings.setAutoSendSms(it) } },
                )
                SwitchRow(
                    icon = Icons.Filled.Shield,
                    title = "اهتزاز عند التنفيذ",
                    subtitle = "نبضة خفيفة عند تنفيذ الأوامر",
                    checked = settings.hapticsEnabled,
                    onChange = { scope.launch { app.settings.setHapticsEnabled(it) } },
                )
            }

            // ------------------------------------------------ appearance
            Section(title = "المظهر واللغة") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Palette, null, tint = MajwudaPalette.VioletSoft)
                    Spacer(Modifier.width(12.dp))
                    Text("السمة", modifier = Modifier.weight(1f))
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "النظام"
                            ThemeMode.LIGHT -> "فاتحة"
                            ThemeMode.DARK -> "داكنة"
                        }
                        TextButton(onClick = { scope.launch { app.settings.setTheme(mode) } }) {
                            Text(if (settings.theme == mode) "✓ $label" else label)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, null, tint = MajwudaPalette.Teal)
                    Spacer(Modifier.width(12.dp))
                    Text("لغة الواجهة", modifier = Modifier.weight(1f))
                    listOf("auto" to "تلقائي", "ar" to "العربية", "en" to "English").forEach { (tag, label) ->
                        TextButton(onClick = { scope.launch { app.settings.setLanguage(tag) } }) {
                            Text(if (settings.language == tag) "✓ $label" else label)
                        }
                    }
                }
            }

            // ------------------------------------------------ about
            Section(title = "عن مجودة") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "مجودة",
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("مجودة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            app.getString(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MajwudaPalette.TextSecondary,
                        )
                        Text(
                            "تنفيذ وتطوير أمجد حبيب",
                            style = MaterialTheme.typography.titleSmall,
                            color = MajwudaPalette.Teal,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "الإصدار ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MajwudaPalette.TextTertiary,
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    "تعمل مجودة بنموذج Claude من Anthropic. رسائلك ومحادثاتك تُرسل فقط إلى واجهة Claude API لمعالجتها، وبقية بياناتك (الملاحظات، التذكيرات، الذاكرة، جهات الاتصال) تبقى على جهازك. الميكروفون يُستخدم فقط عند الضغط عليه أو عند تفعيل كلمة التنبيه اختياريًا وأنت تعلم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MajwudaPalette.TextSecondary,
                )
                Text(
                    "© 2026 أمجد حبيب. جميع الحقوق محفوظة.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MajwudaPalette.TextTertiary,
                )
                Spacer(Modifier.height(4.dp))
                if (confirmWipe) {
                    Text(
                        "سيتم مسح كل المحادثات والملاحظات والتذكيرات والذاكرة والإعدادات. لا يمكن التراجع!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MajwudaPalette.Red,
                    )
                    Row {
                        Button(
                            onClick = {
                                scope.launch {
                                    app.database.messageDao().clearAll()
                                    app.database.conversationDao().clearAll()
                                    app.database.memoryDao().clearAll()
                                    app.database.noteDao().clearAll()
                                    app.database.reminderDao().clearAll()
                                    app.settings.resetAll()
                                    confirmWipe = false
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MajwudaPalette.Red),
                        ) {
                            Text("امسح كل شيء")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { confirmWipe = false }) { Text("تراجع") }
                    }
                } else {
                    TextButton(onClick = { confirmWipe = true }) {
                        Icon(Icons.Filled.DeleteForever, null, tint = MajwudaPalette.Red)
                        Spacer(Modifier.width(6.dp))
                        Text("مسح كل بيانات التطبيق", color = MajwudaPalette.Red)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MajwudaPalette.Surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MajwudaPalette.VioletSoft)
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = MajwudaPalette.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MajwudaPalette.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
