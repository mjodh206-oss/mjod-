# إخراج موشن غرافيك «ماري سيليست»

النسختان الناتجتان موجودتان في `deliverables/episode-01-main-motion.mp4` و`deliverables/episode-01-short-motion.mp4`. يرسم `render-motion.mjs` السفينة والأمواج والخرائط والطاقم وقارب النجاة والقرائن والفرضيات كعناصر SVG متحركة إطارًا بإطار، ثم يركّب التعليق الصوتي والترجمة العربية والعناوين الذهبية. تُضاف مؤثرات Foley أصلية مولّدة إجرائيًا ومتزامنة مع انتقالات الخريطة والطقس والقارب والسلم والورق والفرضيات؛ لا موسيقى مضافة.

## متطلبات إعادة الإخراج
- Node.js 20 أو أحدث، Python 3 وNumPy.
- `sharp` لتحويل رسوم SVG إلى إطارات.
- FFmpeg يدعم `libx264` و`libass` ومرمّز AAC.

إعادة بناء النسختين النظيفة ثم توليد المؤثرات ومزجها تحت التعليق (استخدم متغيرات البيئة إذا لم تكن الأدوات على `PATH`):

```sh
npm install --prefix /tmp/video-tools --no-save sharp @ffmpeg-installer/linux-x64
python -m pip install --target /tmp/video-python numpy
PYTHONPATH=/tmp/video-python \
SHARP_MODULE=/tmp/video-tools/node_modules/sharp \
FFMPEG_PATH=/tmp/video-tools/node_modules/@ffmpeg-installer/linux-x64/ffmpeg \
python video/add-motion-sfx.py
```

`add-motion-sfx.py` يعيد توليد الصورة والصوت الإجرائي قبل المزج، لذلك تكرار تشغيله لا يضاعف المؤثرات. `synthesize-motion-sfx.py` يصنع الموج والريح والحبال والورق والانتقالات من دون عينات خارجية؛ ويستخدم المازج ضغطًا جانبيًا ليبقى التعليق واضحًا ومهيمنًا. المصدر الصوتي والترجمة: `audio/episode-01/episode-01-full-voiceover.mp3` و`deliverables/episode-01-subtitles-ar.srt` للحلقة، و`audio/episode-01/04-short.mp3` و`deliverables/episode-01-short-ar.srt` للشورت.

الإخراج 24fps مع ترجمة مدمجة. النسخة الأفقية 1280×720 ومدتها 3:45 تقريبًا؛ العمودية 720×1280 ومدتها 45.5 ثانية. لا توجد صور أرشيفية أو موسيقى في هذه النسخ. وُصفَت الشحنة وفق السجل التاريخي، وبقيت الفرضيات موسومة بوضوح على أنها غير مثبتة.
