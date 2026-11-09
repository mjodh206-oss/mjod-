# الروابط المباشرة للتفويض — اقرأ أول سطر قبل ما تضغط

> **ما فيه "رابط تفويض" واحد أقدر أعطيك ياه.** وأي رابط بيوعد بـ "اربط قناتك بضغطة" بهالمحادثة هو رابط مزيّف.
> السبب التقني: رابط موافقة Google لازم يحتوي `client_id` تابع **لمشروع Google Cloud بتاعك أنت**،
> وهالمشروع لسه ما انعمل. كمان تحققت: ما فيه connector يوتيوب ولا Google متاح بهالجلسة،
> يعني ما فيه زر "Connect" أصلاً.

الرابط الحقيقي بيطلع **على جهازك أنت**، من `client_secret.json` تبعك، بسطر واحد:

```bash
python automation/auth_setup.py --client-secret /path/to/client_secret.json --no-browser
```

السكربت بيطبع سطر مثل:

```
Please visit this URL to authorize this application:
https://accounts.google.com/o/oauth2/v2/auth?client_id=XXXX.apps.googleusercontent.com&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2F&response_type=code&scope=...&access_type=offline&prompt=consent
```

**هذا هو الرابط المباشر.** افتحه والسكربت شغّال (بيستنى الـ localhost callback). الرابط دايماً على
`accounts.google.com`، وفيه `client_id` العام فقط — **ما فيه ولا لازم يكون فيه `client_secret` أو token**.
إذا بدك رابط بمنفذ ثابت: أضف `--port 8080`.

⚠️ النتيجة (الـ token) **ما تبعتها بالشات ولا تحطها بالريبو**. خزّنها كـ GitHub Secret.

---

## 1. روابط Google Cloud — هدول اللي بتضغط عليهم اليوم

| # | الرابط | شو بتعمل فيه |
|---|---|---|
| 1 | [تفعيل YouTube Data API v3](https://console.cloud.google.com/apis/library/youtube.googleapis.com) | اضغط **ENABLE** |
| 2 | [تفعيل YouTube Analytics API](https://console.cloud.google.com/apis/library/youtubeanalytics.googleapis.com) | اضغط **ENABLE** (لنسب الاحتفاظ) |
| 3 | [شاشة موافقة OAuth](https://console.cloud.google.com/apis/credentials/consent) | External + ضيف حساب القناة كـ **Test user** |
| 4 | [ال credentials](https://console.cloud.google.com/apis/credentials) | CREATE CREDENTIALS → OAuth client ID → **Desktop app** → نزّل الـ JSON |
| 5 | [حصّة الـ API](https://console.cloud.google.com/apis/api/youtube.googleapis.com/quotas) | 10,000 وحدة/يوم ≈ 6 رفعات |

> بعد إنشاء العميل: طلب **مراجعة compliance** من صفحة الـ API نفسها — بدونها الفيديوهات بتطلع Private.
> ووضع Testing بينتهي بعد 7 أيام. التفاصيل: [docs/ACTIVATION.md](ACTIVATION.md).

## 2. روابط هذا الريبو — بعد ما تطلع الـ token

| الرابط | شو بتحط / بتعمل |
|---|---|
| [GitHub Secrets للريبو](https://github.com/mjodh206-oss/mjod-/settings/secrets/actions) | `YT_CLIENT_SECRET_JSON` + `YT_TOKEN_JSON` |
| [تشغيل حلقة القناة](https://github.com/mjodh206-oss/mjod-/actions/workflows/channel-loop.yml) | زر **Run workflow** (ينشر الطابور + يرد + تقرير) |
| [طابور النشر](https://github.com/mjodh206-oss/mjod-/blob/arena/16677b1e-mjod/content/publish_queue.json) | حط رابط الـ MP4 و `enabled: true` |
| [قائمة التفعيل الكاملة](https://github.com/mjodh206-oss/mjod-/blob/arena/16677b1e-mjod/docs/ACTIVATION.md) | 15 دقيقة بالترتيب |
| [الأداة الخارجية على GitHub](https://github.com/darkzOGx/youtube-automation-agent) | اختيارية، بتشتغل على جهازك `npm start` → `localhost:3456` |

## 3. أوامر بعد الروابط (على جهازك)

```bash
python3 -m pip install -r requirements.txt
python automation/auth_setup.py --client-secret /path/to/client_secret.json --no-browser
cp .env.example .env                     # GOOGLE_TOKEN_JSON=/absolute/path/token.json
python tools/validate_content.py         # وثّق المصادر وخلّي verified=true
python automation/run_channel.py         # معاينة، بعدين --send --yes
```

## 4. قاعدة أمان

أي رابط موافقة لازم:
- يكون على `accounts.google.com` (مش دومين شبيه)،
- يكون مولّد من `client_secret.json` تبعك،
- ما يطلب منك كلمة مرور يوتيوب أبداً،
- وما يتبع بعده طلب "ابعثلي الـ token" — هالطلب ما بقدر أعمله ولا رح أعمله.

التفويض = OAuth بتعمله أنت بمتصفحك + Secret بتحطه أنت بإعدادات الريبو. أنا ما بشوفه، بس النظام بيقرأه ويشتغل.
