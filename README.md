# MJOD — Vertical Shorts Studio 🎬

إنتاج فيديوهات **YouTube Shorts** عمودية (1080×1920) بجودة إعلانية، بالكامل من السكربت:
تصوير ثابت (AI stills) + حركة كاميرا + طباعة عربية مشكّلة بـ HarfBuzz + تعليق صوتي + ساوند ديزاين.

### المخرجات الحالية
| الملف | الوصف |
|---|---|
| `output/money_shorts_v2.mp4` | **النسخة القوية** — موشن جرافيكس حقيقية عبر Chromium (عدّاد، طابع، بطاقات، جزيئات، كارةوكي) |
| `output/money_shorts.mp4` | نسخة v1 — ffmpeg + libass (أخف وأبسط) |
| `output/cover.jpg` | غلاف 1080×1920 |
| `output/money_shorts.srt` | ترجمة جاهزة للرفع |
| `output/publish_kit.md` | عناوين + وصف + هاشتاجات + خطة نشر |

### مختبر اللوك (lab) — عينات 3D / UI
`build/lab/` فيه ٣ محرّكات لوك جاهزة (تُصوَّر بنفس Chromium):
- `tunnel.html` — WebGL shader: نفق جزيئات ذهبية + حلقات warp (three.js r180).
- `fintech.html` — واجهة زجاجية: عدّاد أصول + sparkline + بارات (CSS/SVG).
- `ktype.html` — تايبوغرافي حركي: كلمات ضخمة + streaks + فلاشات.
```bash
node build/lab/render_lab.mjs --page=build/lab/tunnel.html --out=out.mp4 --dur=3
```
المخرجات: `output/samples_reel.mp4` + `output/samples/*`.

### محرّكان

### 1) `build/motion/` — Chromium (v2، الأقوى)
HTML/CSS/Canvas + Chromium 153 headless: كل فريم بيتحسب كدالة نقية في الزمن `window.__seek(t)`
ثم `page.screenshot()` → pipe إلى ffmpeg. النتيجة: Spring animation، تدرّجات، غبار ذهبي،
عدّادات أرقام، طوابع، بطاقات مقارنة، وحركة تايبوغرافي بنفس توقيت التعليق الصوتي.

```bash
node build/motion/render.mjs --stills=1.5,22,42          # معاينة سريعة
node build/motion/render.mjs --out=output/money_shorts_v2.mp4   # ≈15 دقيقة
```

> التشغيل بيحتاج مكتبات NSS: بتتاخد من `al2023.tar.br` جوه حزمة `@sparticuz/chromium`
> (`LD_LIBRARY_PATH=/tmp/al/lib`). متصفح كامل + تشكيل عربي صحيح عبر HarfBuzz.

### 2) `build/build_video.py` — ffmpeg + libass (v1)
```
build/build_video.py
 ├─ 1. السكربت (Egyptian Arabic) + خط الزمن حسب طول كل جملة صوتية
 ├─ 2. تسريع الصوت (atempo) + قياس الكلام + حساب توقيت المشاهد
 ├─ 3. ترجمة ASS (نصوص حركية، تدرّجات، توهج) — libass + HarfBuzz = تشكيل عربي صحيح
 ├─ 4. Ken Burns لكل صورة (zoompan) + تدرّج لوني سينمائي + جْراين + فينييت
 ├─ 5. Cross-fades + سكريم إضاءة + شريط تقدّم ذهبي
 └─ 6. مزج الصوت: تعليق + موسيقى مُصنّعة (درون/نبض/Whoosh) + Loudness −14 LUFS
```

### إعادة الرندر
```bash
pip install imageio-ffmpeg pillow numpy
python3 build/build_video.py     # ≈ 4.5 دقيقة على CPU
```

### هيكل المشروع
- `assets/` صور المشاهد (9:16) + خطوط Cairo / Almarai (OFL).
- `build/vo/` مقاطع التعليق الصوتي الأصلية.
- `build/build_video.py` كل منطق الإنتاج (نصوص، توقيتات، حركة، صوت).

> كل النصوص والحركة والصور مُنتجة لهذا المشروع — مفيش موسيقى أو فيديو محمي بحقوق.

## 3) النسخة الكينيتيك (v4) — `output/kt_short.mp4`
تايبوغرافي حركي + شرح بصري · 1080×1920 · 36.2 ث · 30fps · AAC 48kHz · −14 LUFS.
8 مشاهد: هيدر ثابت (قاعدة الفلوس + 01/08) + شريط تقدّم + رسوم (عملة، أعداد، راية، مقارنة) + لافتة CTA.

```
build/kt/
 ├─ vo/n1..n8.mp3      تعليق صوتي مصري (مقطع لكل جملة)
 ├─ audio.py           المزج: معالجة الصوت + بيت 92 BPM + مؤثرات + Ducking + timeline.json
 ├─ timeline.json      توقيت كل كلمة (t/d) — هو اللي بيحرّك النص
 ├─ index.html         محرك الكينيتيك (window.__seek(t))
 └─ stills.mjs         لقطات مراجعة سريعة
```

```bash
python3 build/kt/audio.py                                     # mix.wav + timeline.json
node build/lab/render_lab.mjs --page=build/kt/index.html \
  --out=output/kt_short.mp4 --dur=36.2 --audio=build/kt/mix.wav    # ≈10 دقائق
```
قواعد مهمة: النص العربي لازم يكون HTML (مش Canvas)، وكل سطر بيتقاس ويعاد تحجيمه أوتوماتيك قبل الرندر
(عشان ما يقصّش على 1080px)، ولازم مساحة آمنة ~90px من كل جنب.
