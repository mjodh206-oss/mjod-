package dev.lasq.fawri.ui

import android.app.KeyguardManager
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import dev.lasq.fawri.*
import dev.lasq.fawri.core.*
import dev.lasq.fawri.platform.*

class MainActivity : FragmentActivity() {
    val vm: AppModel by viewModels()
    var epoch by mutableIntStateOf(0)
        private set

    private var unlocked by mutableStateOf(false)
    private var foreground by mutableStateOf(false)
    private var shared by mutableStateOf<String?>(null)
    private var transfer by mutableStateOf<Pair<Uri, Boolean>?>(null)
    private var authDone: (() -> Unit)? = null
    private val credential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) authenticated() else authDone = null
        }
    private val create =
        registerForActivityResult(
            object : ActivityResultContracts.CreateDocument("application/octet-stream") {
                override fun createIntent(context: Context, input: String) =
                    super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            },
        ) {
            if (it != null) transfer = it to true
        }
    private val doc =
        registerForActivityResult(
            object : ActivityResultContracts.OpenDocument() {
                override fun createIntent(context: Context, input: Array<String>) =
                    super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            },
        ) {
            if (it != null) transfer = it to false
        }

    fun pickFile(export: Boolean) {
        if (export) create.launch("سجل-لصق-فوري.lasq")
        else doc.launch(arrayOf("application/octet-stream", "*/*"))
    }

    fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    fun external(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("لم نجد شاشة نظام مناسبة على هذا الجهاز.")
        }
    }

    fun chooseBoard() {
        val im = system<InputMethodManager>()
        if (im.enabledInputMethodList.any { it.packageName == packageName }) im.showInputMethodPicker()
        else external(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun authenticated() {
        unlocked = true
        syncWindow()
        authDone?.invoke()
        authDone = null
    }

    @Suppress("DEPRECATION")
    private fun deviceCredential() {
        val i =
            system<KeyguardManager>()
                .createConfirmDeviceCredentialIntent("لصق فوري", "أكّد هويتك لفتح مساحتك الخاصة.")
        if (i != null) credential.launch(i)
        else {
            authDone = null
            toast("عيّن قفل شاشة من إعدادات الجهاز أولاً.")
        }
    }

    fun authenticate(done: () -> Unit = {}) {
        if (!system<KeyguardManager>().isDeviceSecure) {
            toast("عيّن رمز PIN أو قفل شاشة آمن على جهازك أولاً.")
            return
        }
        authDone = done
        val supported =
            if (Build.VERSION.SDK_INT >= 30)
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (
            BiometricManager.from(this).canAuthenticate(supported) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            deviceCredential()
            return
        }
        val prompt =
            BiometricPrompt(
                this,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        authenticated()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                Build.VERSION.SDK_INT < 30
                        )
                            deviceCredential()
                        else {
                            authDone = null
                            if (
                                errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                    errorCode != BiometricPrompt.ERROR_CANCELED
                            )
                                toast("تعذّر التحقق من الهوية. أعد المحاولة بقفل الجهاز.")
                        }
                    }
                },
            )
        val info =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("فتح لصق فوري")
                .setSubtitle("تحقق محلي ببصمة أو بقفل الجهاز")
                .setAllowedAuthenticators(supported)
        if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("استخدام قفل الجهاز")
        prompt.authenticate(info.build())
    }

    override fun attachBaseContext(newBase: Context) {
        val config = android.content.res.Configuration(newBase.resources.configuration)
        val ar = java.util.Locale.forLanguageTag("ar")
        config.setLocale(ar)
        config.setLayoutDirection(ar)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent {
            val lock by app.prefs.locked.collectAsState()
            LasqTheme {
                val light = Colors.current.canvas.luminance() > .5
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
                LaunchedEffect(lock, unlocked, foreground) { syncWindow() }
                Box(
                    Modifier.fillMaxSize()
                        .background(Colors.current.canvas)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        !app.integrity.first ->
                            Empty(G.SHIELD, "هذه النسخة غير معتمدة", "لم نفتح السجل أو نشغّل الحفظ.")
                        lock && !unlocked ->
                            Column(
                                Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Logo(60)
                                Spacer(Modifier.height(14.dp))
                                Txt("سجلك مقفل", 24, bold = true)
                                Spacer(Modifier.height(16.dp))
                                Primary("فتح السجل", { authenticate() }, icon = G.LOCK)
                            }
                        else -> {
                            ArchivePage(this@MainActivity, vm, lock)
                            shared?.let { body ->
                                Confirm(
                                    "إضافة النص المُشارك؟",
                                    body.take(200),
                                    "حفظ",
                                    { shared = null },
                                ) {
                                    vm.shared(body)
                                    shared = null
                                }
                            }
                            transfer?.let { (uri, export) ->
                                Passphrase(export, { transfer = null }) { pass ->
                                    transfer = null
                                    vm.transfer(uri, export, pass)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun changeRecording(value: Boolean) {
        if (!vm.visible.value) return
        app.recorder.setEnabled(value)
        if (value) LegacyMonitor.ensure(this)
    }

    private fun syncWindow() {
        val allowed = foreground && (!app.prefs.locked.value || unlocked) && app.integrity.first
        vm.visible.value = allowed
        app.recorder.focus(allowed && hasWindowFocus())
    }

    private fun readIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            shared =
                runCatching {
                    if (
                        i.type != "text/plain" ||
                            i.clipData
                                ?.description
                                ?.extras
                                ?.getBoolean("android.content.extra.IS_SENSITIVE") == true
                    )
                        null
                    else
                        i.getCharSequenceExtra(Intent.EXTRA_TEXT)
                            ?.takeIf { it.length in 1..MAX_TEXT && it.isNotEmpty() }
                            ?.toString()
                }
                    .getOrNull()
            if (shared == null) toast("نقبل نصاً غير حساس فقط، ضمن حجم الحافظة المتاح.")
            setIntent(Intent(this, MainActivity::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        syncWindow()
    }

    override fun onResume() {
        super.onResume()
        epoch++
        syncWindow()
        LegacyMonitor.ensure(this)
    }

    override fun onStop() {
        foreground = false
        unlocked = false
        syncWindow()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        epoch++
        syncWindow()
    }
}

@Composable
private fun Passphrase(export: Boolean, close: () -> Unit, ok: (CharArray) -> Unit) {
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Txt(if (export) "تشفير النسخة" else "فتح النسخة", 21, bold = true) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Txt(
                    if (export) "اختر عبارة مرور من ١٠ أحرف على الأقل. لا يمكننا استعادتها."
                    else "أدخل عبارة مرور الملف لإضافة نصوصه إلى سجلك، مهما كان عمرها.",
                    14,
                    Colors.current.muted,
                )
                OutlinedTextField(
                    pass,
                    { pass = it.take(256) },
                    singleLine = true,
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        ),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Txt("عبارة المرور", 13) },
                )
                if (export)
                    OutlinedTextField(
                        again,
                        { again = it.take(256) },
                        singleLine = true,
                        keyboardOptions =
                            androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            ),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Txt("تأكيد العبارة", 13) },
                    )
            }
        },
        confirmButton = {
            TextButton(
                {
                    ok(pass.toCharArray())
                    pass = ""
                    again = ""
                },
                enabled = pass.length >= 10 && (!export || pass == again),
            ) {
                Txt(if (export) "تصدير" else "استيراد", color = LocalContentColor.current)
            }
        },
        dismissButton = { TextButton(close) { Txt("إلغاء") } },
    )
}
