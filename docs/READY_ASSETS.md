# 🎬 الأسبوع الأول جاهز كملفات فعلية

صار عندك **5 فيديوهات MP4 حقيقية قابلة للرفع**، مش سكربتات بس. كل واحد: 1080×1920 (9:16)،
H.264 + AAC، كابتشنز محروقة بالفيديو، فويس أوفر إنجليزي، ثيمبنيل، وملف SRT.

| الحلقة | المدة | الفيديو | الثيمبنيل | SRT |
|---|---:|---|---|---|
| `ep-001` — Can't tickle yourself | 46.2 s | `content/videos/ep-001.mp4` | `assets/thumbnails/ep-001.jpg` | `content/videos/ep-001.srt` |
| `ep-002` — Venus day > year | 51.8 s | `content/videos/ep-002.mp4` | `assets/thumbnails/ep-002.jpg` | `content/videos/ep-002.srt` |
| `ep-003` — Why the sky is dark | 44.1 s | `content/videos/ep-003.mp4` | `assets/thumbnails/ep-003.jpg` | `content/videos/ep-003.srt` |
| `ep-004` — 3,000-year-old honey | 41.7 s | `content/videos/ep-004.mp4` | `assets/thumbnails/ep-004.jpg` | `content/videos/ep-004.srt` |
| `ep-005` — Baker / baker | 48.2 s | `content/videos/ep-005.mp4` | `assets/thumbnails/ep-005.jpg` | `content/videos/ep-005.srt` |

كلهم تحت 60 ثانية ≈ حد الـ Shorts. المجموع ~11 MB.

**وشو اللي انعمل بالضبط:** 5 خلفيات مولّدة بالذكاء الاصطناعي (بدون نص)، 5 فويس أوفر بنفس الصوت
(`voice-00`)، ومحرّك `tools/build_short.py` اللي بيقرأ تايم لاين الحلقات من الـ JSON، بيوسّعه على
المدة الحقيقية للصوت، بيرسم كابتشن كل بيت مع بطاقة السلسلة وشريط تقدّم، وبيعمل push-in بطيء
+ بان خفيف كل بيت حتى ما تكون الخلفية جامدة.

## ⚠️ تقييم صريح للمستوى
هاي **نسخة قابلة للنشر فوراً**، بس هي «خلفية ثابتة + كابتشن متغيّر كل ~5–6 ثواني + زوم بطيء»،
**مش مونتاج بحركة كل 1.5–2.5 ثانية** زي ما بيطلب `docs/PLAYBOOK.md` لأعلى retention. إذا بدك
النسخة الأقوى: أضف B-roll/زوم سريع/صوت hit على كل pattern interrupt — المحرّك جاهز، بس لازم
مقاطع حركة إضافية. ما رح أدّعي إنها MrBeast-level.

## الملفات اللي معاها
```
assets/backgrounds/ep-00X.png   خلفيات مولّدة (بدون نص)
assets/audio/ep-00X.mp3         فويس أوفر
assets/thumbnails/ep-00X.jpg    ثيمبنيل بـ 3 كلمات
content/videos/ep-00X.mp4       الفيديو الجاهز
content/videos/ep-00X.srt       ترجمة كاملة للـ VO
assets/previews/                لقطتان للمراجعة البصرية من ep-001
```

> كل الوسائط مستثناة من Git بـ `.gitignore` (حوالي 22 MB). التوليد موثّق بـ `assets/ASSETS.md`
> والأمر `python tools/build_short.py --all` يعيد بناءها متى ما بدك.

## كيف تحطها بالطابور (بعد التفويض + توثيق المصادر)

`content/publish_queue.ready.json` فيه المدخلات الخمسة جاهزة بـ `enabled: false`. انسخها لـ
`content/publish_queue.json` وغيّر `false` → `true`:

```bash
python -c "import json,pathlib; p=pathlib.Path('content/publish_queue.json'); d=json.loads(pathlib.Path('content/publish_queue.ready.json').read_text()); [i.update(enabled=True) for i in d['items']]; p.write_text(json.dumps(d,indent=2,ensure_ascii=False)+'\n')"
python automation/run_channel.py                # معاينة
python automation/run_channel.py --send --yes   # نشر فعلي
```

**للـ GitHub Actions:** الـ `video_path` محلي فقط؛ ارفع الـ MP4 عندك (S3 / Drive direct link /
Release asset) واستبدلها بـ `video_url` HTTPS. الريبو ما بيخزّن فيديوهات.

## البوابات لسه شغّالة (وما لازم تعطّلها)
- `fact_check.verified = false` في الحلقات الخمسة → الناشر **بيرفض** الرفع لحد ما تتحقق من المصادر.
- الطابور `enabled: false` → ما بينشر شي لحاله.
- الـ OAuth والـ Secrets لسه عندك أنت → أنا ما عندي صلاحية ولا بطلب الـ token.

يعني: **الملفات جاهزة، النشر لسه موقوف عند خط التفويض + التحقق من الحقائق** — مش عندي.
