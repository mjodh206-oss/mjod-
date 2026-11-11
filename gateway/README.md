# مجودة Gateway — البوابة المجانية

هذه البوابة تجعل مجودة تشتغل **بدون أي مفتاح مدفوع**: تستقبل طلبات مجودة بصيغة Anthropic Messages API، تترجمها إلى صيغة OpenAI-compatible، وتتصل بمزود مجاني — الافتراضي هو **Google AI Studio (Gemini Flash)** — ثم ترجع الإجابة إلى مجودة بصيغة Anthropic، بما في ذلك **استدعاء الأدوات** (tool calling) والبث المتدفق (SSE streaming).

> ⚠️ **ملاحظة صادقة:** عبر البوابة، المساعد **ما عاد Claude بالضبط** — هو Gemini (أو أي مزود تختاره). جودة العربية ممتازة مع Gemini Flash لكنها ليست صيغة كلود، والأدوات المعقدة قد تخفق أحيانًا. الحصة المجانية في AI Studio سخية لكنها **محدودة يوميًا** — عند نفادها ترجع 429.

## المتطلبات (كلها مجانية، بدون بطاقة ائتمان)

1. حساب **Cloudflare** (مجاني): <https://dash.cloudflare.com/sign-up>
2. حساب **Google AI Studio** ومفتاح API (مجاني): <https://aistudio.google.com/> ← **Get API key** — الصق مفتاحًا يبدأ بـ `AIza…`
3. Node.js 20+ على جهازك (للنشر فقط)

## النشر خطوة بخطوة (من الكمبيوتر)

```bash
cd gateway

# 1) سجّل الدخول إلى Cloudflare (يفتح المتصفح مرة واحدة)
npx wrangler@latest login

# 2) احفظ مفتاح المزود (الصقه عندما يُطلب)
npx wrangler secret put PROVIDER_API_KEY

# 3) (موصى به بقوة) ضع سرًا مشتركًا تختاره بنفسك، مثال: majwuda-honors-2026
#    هذا يمنع أي شخص من سرقة حصتك المجانية عبر رابط البوابة العام:
npx wrangler secret put APP_TOKEN

# 4) النشر
npx wrangler deploy
```

بعد النشر تطبع الأداة رابطًا مثل:

```
https://majwuda-gateway.<subdomain>.workers.dev
```

## اربط مجودة بالبوابة (على الهاتف)

1. مجودة ← **⋮ ← الإعدادات ← الاتصال بكلود**
2. خانة **«بوابة اختيارية (Gateway URL)»**: الصق رابط الـWorker أعلاه
3. خانة **مفتاح API**: الصق نفس `APP_TOKEN` الذي اخترته (أو اتركه فارغًا إذا لم تفعل `APP_TOKEN` — حينها أرسل التطبيق `x-api-key: gateway` والبوابة مفتوحة للجميع، **لا أنصح بذلك**)
4. خانة **النموذج**: أي شيء — البوابة تتجاهلها دائمًا وتستعمل `PROVIDER_MODEL` من `wrangler.toml`
5. ارجع وجرّب «احسب 15 بالمية من 240»

## بدائل المزودين

غيّر `PROVIDER_BASE_URL` و`PROVIDER_MODEL` في `wrangler.toml` ثم `npx wrangler deploy` مجددًا (مثال Groq وOpenRouter معلّقة داخل الملف). مفتاح المزود الجديد يُحدَّث بـ`npx wrangler secret put PROVIDER_API_KEY`.

## ماذا تفعل البوابة / لا تفعل

- ✅ ترجمة نظام (system prompt)، محادثات كاملة مع صور، أدوات (`tools`/`tool_use`/`tool_result`)
- ✅ بث SSE كامل بأحداث Anthropic المتوقعة من مجودة (message_start … message_stop)
- ✅ حماية بسر مشترك (`APP_TOKEN`)، وحد أقصى `max_tokens = 2048`، ومهلة 110 ثانية
- ❌ لا تخزّن أي طلب/محادثة — لا يوجد أي storage أو قاعدة بيانات
- ❌ لا تحطّ مفتاح Anthropic مدفوع هنا؛ هي مخصصة للمزودين المجانيين
- ❌ `thinking` (تفكير كلود الممتد) يُتجاهل بصمت

## التطوير والاختبار

```bash
cd gateway
npm test        # 19 اختبار وحدة (ترجمة الطلبات/الاستجابات/البث/المصادقة) — بلا شبكة
```

## التكلفة

- Cloudflare Workers (خطة Free): ~100 ألف طلب/يوم — أكثر من كافٍ.
- Google AI Studio (خطة Free): حصة يومية سخية على Gemini Flash (تغيّر بين الحين والآخر — راجع صفحة AI Studio).
- المجموع: **0 دولار**.
