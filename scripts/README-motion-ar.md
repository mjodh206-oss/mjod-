# حركة «ماري سيليست» — الإصدار الحالي V3

## محرك الحركة
بحثت في أدوات موشن غرافيك مفتوحة المصدر على GitHub، واخترت [Remotion](https://github.com/remotion-dev/remotion) لبناء الإصدار الحالي: مكتبة لإنشاء الفيديو برمجيًا باستخدام React/Chromium. وقت الفحص كان للمستودع نحو 74.8 ألف نجمة. قارنتها أيضًا بـ[Motion Canvas](https://github.com/motion-canvas/motion-canvas)، نحو 20.1 ألف نجمة، وبـ[Natron](https://github.com/NatronGitHub/Natron)، برنامج تركيب عقدي أقرب إلى After Effects/Nuke. Remotion هو الأنسب هنا لتوقيت عناصر عربية وخرائط ومعلومات متحركة مع مقاطع التعليق ومقاسات 16:9 و9:16.

**تنويه:** هذا محرك تحريك ومونتاج برمجي، وليس مولد فيديو بالذكاء الاصطناعي، ولم تُشغّل نماذج توليد فيديو ثقيلة. أصول الصور في المشروع توضيحية وليست أرشيفًا تاريخيًا؛ لذلك لا نعرضها على أنها صور حقيقية من 1872.

## تغييرات V3
- فصول مترابطة بتحريك كاميرا وطبقات، وخط رحلة يُرسم تدريجيًا، وبطاقات أدلة/فرضيات وعناوين عربية بخط Noto.
- تصميم شورت عمودي مستقل؛ لا يقتصر على تدوير/قصّ الحلقة الأفقية.
- نص تحرير جديد أكثر تحفظًا: تاريخ العثور، تفاصيل الرحلة والحمولة، حالة السفينة، التحقيق، والفرضيات مع وسمها بوضوح بأنها غير مثبتة.
- تخفيض صوت البحر، رفع أولوية التعليق، side-chain ducking للمؤثرات، وموازنة المزيج إلى قرابة ‎-16 LUFS.

## إعادة الإنتاج
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

ينتج Remotion فيديوهين صامتين مؤقتين، ثم يضيف سكربت mux المكساج النهائي. أما `render_mary_celeste_motion.py` و`render_mary_celeste_v2.py` فهما مساران أقدم محفوظان لإعادة إنتاج النسخ السابقة.
