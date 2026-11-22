# أدوات الحلقة الثانية «ماري سيليست» — وراء الحكاية

## اختيار أدوات GitHub
بحثت في مستودعات توليد الفيديو والمؤثرات الصوتية الأعلى شعبية/الأقرب للمهمة وقت التنفيذ:

- [Wan2.1](https://github.com/Wan-Video/Wan2.1) — نحو **17.5 ألف نجمة**، ويدعم توليد الفيديو من النص والصورة وتحريره. README الرسمي يذكر أن نسخة T2V-1.3B تحتاج قرابة 8.19 GB VRAM، وأن مقطع 480p لخمس ثوانٍ يستغرق قرابة أربع دقائق على RTX 4090.
- [CogVideo](https://github.com/zai-org/CogVideo) — نحو **13.2 ألف نجمة**، لتوليد الفيديو من النص والصورة.
- [AudioCraft من Meta](https://github.com/facebookresearch/audiocraft) — نحو **23.9 ألف نجمة**؛ المكتبة الصوتية الأعلى شعبية في البحث، وتضم نماذج توليد صوت/موسيقى مثل AudioGen وMusicGen.
- [FoleyCrafter من OpenMMLab](https://github.com/open-mmlab/FoleyCrafter) — نحو **665 نجمة**؛ أقل شعبية من AudioCraft، لكنه أقرب للمهمة تحديدًا: توليد أصوات متزامنة مع مشاهد الفيديو.

الأعداد تقريبية وتتغير على GitHub. بيئة التنفيذ هنا لا تملك GPU أو أوزان تلك النماذج؛ لذلك لم أزعم استخدام Wan أو AudioCraft أو FoleyCrafter فعليًا. أنشأت بدلًا منها مسارًا محليًا قابلًا لإعادة التشغيل، بلا عينات صوتية محمية:

- `render_mary_celeste_v2.py`: موشن غرافيك 720p/20fps، والسفينة تتمايل وتتقدم/تبتعد، والموج والرغوة يتحركان، ومسار الخريطة يُكشف مع مؤشر متحرك، ومشاهد سطح السفينة والملف فيها حركة كاميرا/مطر/ورق، والتوقيت مربوط بمقاطع التعليق الصوتي الفعلية.
- `render_mary_celeste_short_v2.py`: يقتطع لقطات متحركة من الفيديو الرئيسي ويركبها بصيغة عمودية 9:16 مع ترجمة الشورت.
- `synthesize_mary_celeste_sfx.py`: يصنع مؤثرات أصلية إجرائية للموج والريح وصرير الخشب والحبال وتقليب الورق والمسح الصوتي؛ ثم تُخفض تلقائيًا تحت التعليق وتُمزج به.

**ملاحظة:** هذه لقطات تمثيلية ورسوم/موشن غرافيك، وليست تصويرًا حيًا أو لقطات مولدة بالكامل بنموذج فيديو. لمرحلة أكثر واقعية، نحتاج تشغيل Wan2.1 أو FoleyCrafter على جهاز يملك GPU مناسبًا وأوزان النماذج.

## إعادة الإنتاج

```bash
python -m pip install -r scripts/requirements-motion.txt
python scripts/synthesize_mary_celeste_sfx.py
python scripts/render_mary_celeste_v2.py --output Mary-Celeste-main.mp4
python scripts/render_mary_celeste_short_v2.py
```

الأصول المحلية في `brand/motion/` و`brand/episode-01-visuals/`، والتعليق والصوت الممزوج والترجمة في `audio/episode-01/` و`deliverables/`.
