# مشغل الحركة التحريرية V3 — ماري سيليست

استُخدم **Remotion (React/Chromium)** بدل مسار الإطارات الثابتة السابق لبناء العناوين والخرائط والبيانات والمؤثرات الحركية إطارًا بإطار. وقت الفحص: Remotion قرابة 74.8 ألف نجمة على GitHub، وMotion Canvas قرابة 20.1 ألفًا؛ اخترنا Remotion لأن مشاهد React قابلة للتوقيت والقياس وإعادة الاستخدام. هذه الأدوات محرك موشن غرافيك برمجي، وليست مولّد فيديو بالذكاء الاصطناعي.

## ما تغيّر في المعالجة
- ستة فصول بانتقالات/تحركات كاميرا، وتدرج لوني وعناصر تحريرية عربية بخطّي Noto Sans Arabic وNoto Naskh Arabic.
- خط رحلة يُرسم تدريجيًا، نقاط جغرافية وتواريخ، ولوحات أدلة وفرضيات تظهر بإيقاع مرتبط بمقاطع التعليق الفعلية.
- تصميم عمودي مستقل للشورت 9:16، بدل قصّ النسخة الأفقية فقط.
- الصورة تستخدم أصولًا توضيحية من المشروع، وتُعرض كإعادة بناء بصرية لا كأرشيف تاريخي. لا يمكن ضمان أن كل أصل فوتوغرافي التكوين لن يبدو توليديًا؛ النسخة تخفف ذلك باستخدام تصميم معلوماتي تحريري بدل تمثيل واقعي زائف.
- مكساج V3 يطبّع التعليق إلى قرابة ‎-16 LUFS، يخفض سرير البحر/الريح، ويستخدم side-chain ducking حتى تتراجع المؤثرات تحت الكلام وتظهر في الفواصل.

## إعادة البناء
من جذر المستودع:

```bash
python -m pip install -r scripts/requirements-motion.txt
npm install --prefix scripts/remotion-v3
python scripts/remotion-v3/prepare-assets.py
python scripts/build_mary_celeste_v3_audio.py
node scripts/remotion-v3/render.mjs
node scripts/remotion-v3/render.mjs --short
python scripts/mux_mary_celeste_v3.py
```

يُنتج Remotion ملفين صامتين مؤقتين في الجذر، ثم يدمجهما سكربت mux مع `v3-final-mix.mp3` و`v3-short-final-mix.mp3`. ملفات Node dependencies لا تُضمّن في Git. يلزم تثبيت Python packages وNode.js.
