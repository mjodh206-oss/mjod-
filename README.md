# ريموتي الذكي — Smart Remote

تطبيق أندرويد عربي بالكامل يحوّل الهاتف المزوّد بمرسل أشعة تحت حمراء (IR Blaster) إلى ريموت كنترول شامل.

**تم تطوير وتنفيذ هذا التطبيق بواسطة أمجد حبيب**

## المميزات

- 📺 الشاشات · 📡 الرسيفرات · ❄️ المكيفات · 🌀 المراوح · 📼 التلفزيونات القديمة · 💡 الإضاءات (Ring Lights)
- بحث تلقائي ذكي: وجّه الهاتف نحو الجهاز واضغط «ابدأ البحث» — التطبيق يجرّب الأكواد من الأكثر شيوعاً في المنطقة العربية حتى يستجيب الجهاز، ثم يُخصَّص ويُحفظ فوراً **بدون أي إدخال يدوي**.
- واجهة عربية RTL كاملة بخط Cairo، مع وضعين داكن وفاتح.
- قاعدة بيانات أكواد محلية بالكامل (offline) مُولّدة من Flipper-IRDB (CC0) — لا حاجة للإنترنت إطلاقاً.
- حجم صغير (~0.4 MB) وإذن واحد فقط: `TRANSMIT_IR`.

## البنية

Android WebView app:
- `app/assets/www/` — الواجهة (HTML/CSS/JS) وقواعد الأكواد `js/db.js` والخطوط والأيقونة.
- `app/smali/` — `MainActivity` (نافذة كاملة + WebView) و `IrBridge` (`@JavascriptInterface`: الإرسال عبر `ConsumerIrManager` + اهتزاز/نبرات/تخزين محلي).
- `app/AndroidManifest.xml` + `app/apktool.yml` — مشروع **apktool** (minSdk 21 / targetSdk 34).
- `tools/build-db.mjs` — إعادة توليد `db.js` من IRDB · `tools/sign.py` — توقيع حتمي v1+v2+v3 عبر openssl.

## البناء والتوقيع

```bash
# 1) تجميع APK غير موقّع
java -jar apktool.jar b -f app -o SmartRemote-unsigned.apk

# 2) توليد مفتاح (مرة واحدة — لا تُودعه في المستودع)
openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 10950 -nodes \
  -subj "/CN=Smart IR Remote/O=Amjad Habib/C=NL"

# 3) توقيع v1+v2+v3 والتحقق المستقل
python3 tools/sign.py SmartRemote-unsigned.apk SmartRemote-release.apk <مجلد-المفتاح>
```

الحد الأدنى أندرويد 5.0 (API 21)، الهدف أندرويد 14 (API 34). أجهزة **بدون** مرسل IR لن تتمكن من الإرسال (تظهر رسالة ودّية داخل التطبيق).

الدعم والملاحظات: https://github.com/mjodh206-oss/mjod-/issues
