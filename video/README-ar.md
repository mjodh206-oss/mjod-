# إخراج موشن غرافيك «ماري سيليست»

النسختان الناتجتان موجودتان في `deliverables/episode-01-main-motion.mp4` و`deliverables/episode-01-short-motion.mp4`. يرسم `render-motion.mjs` السفينة والأمواج والخرائط والطاقم وقارب النجاة والقرائن والفرضيات كعناصر SVG متحركة إطارًا بإطار، ثم يركّب التعليق الصوتي والترجمة العربية والعناوين الذهبية.

## متطلبات إعادة الإخراج
- Node.js 20 أو أحدث.
- `sharp` لتحويل رسوم SVG إلى إطارات.
- FFmpeg يدعم `libx264` و`libass` ومرمّز AAC.

مثال تثبيت الأدوات المؤقتة عبر npm:

```sh
npm install --prefix /tmp/video-tools --no-save sharp @ffmpeg-installer/linux-x64
SHARP_MODULE=/tmp/video-tools/node_modules/sharp \
FFMPEG_PATH=/tmp/video-tools/node_modules/@ffmpeg-installer/linux-x64/ffmpeg \
node video/render-motion.mjs all
```

لاستهداف صيغة واحدة استخدم `main` أو `short` بدل `all`. المصدر الصوتي والترجمة: `audio/episode-01/episode-01-full-voiceover.mp3` و`deliverables/episode-01-subtitles-ar.srt` للحلقة، و`audio/episode-01/04-short.mp3` و`deliverables/episode-01-short-ar.srt` للشورت.

الإخراج 24fps مع ترجمة مدمجة. النسخة الأفقية 1280×720 ومدتها 3:45 تقريبًا؛ العمودية 720×1280 ومدتها 45.5 ثانية. لا توجد صور أرشيفية أو موسيقى في هذه النسخ. التعليق هو المسار الصوتي المهيمن. وُصفَت الشحنة وفق السجل التاريخي، وبقيت الفرضيات موسومة بوضوح على أنها غير مثبتة.
