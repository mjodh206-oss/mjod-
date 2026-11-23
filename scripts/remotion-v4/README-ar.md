# موشن غرافيك ماري سيليست V4

نسخة Remotion مبنية برسوم SVG متحركة أصلية داخل React: سفينة شراعية وطاقمها، بحر وطقس، خط رحلة وخريطة، سجلّ السفينة، قارب النجاة، المضخة، قرائن التحقيق، وبطاقات الفرضيات. لا تعتمد المشاهد على تحريك بطيء لصور ثابتة. التكوين العمودي للشورت مستقل عن الأفقي، والترجمة العربية تُوقّت بإطارات التايملاين.

## الإصدارات الناتجة

- `deliverables/episode-01-main-motion-v4.mp4` — أفقي 1280×720، 24fps، 5433 إطارًا (3:46.375).
- `deliverables/episode-01-short-motion-v4.mp4` — عمودي 720×1280، 24fps، 1100 إطار (45.833 ثانية).
- كلا الملفين H.264/AAC. التعليق الصوتي من مكس V3، مع مؤثرات Foley إجرائية أصلية منخفضة المستوى تحت التعليق.

## إعادة البناء

من جذر المستودع:

```bash
npm install --prefix scripts/remotion-v4
node scripts/remotion-v4/render.mjs
node scripts/remotion-v4/render.mjs --short
python -m pip install -r scripts/requirements-motion.txt
node scripts/remotion-v4/finalize.mjs
```

اختبار صورة مفردة: `node scripts/remotion-v4/render.mjs --still`، وللشورت أضف `--short`. يمكن اختيار الإطار عبر متغير `FRAME`.

يقرأ المشروع التايملاين والترجمات من `scripts/remotion-v3/timeline-v3.json`، ويأخذ مساري التعليق من `audio/episode-01/v3-final-mix.mp3` و`v3-short-final-mix.mp3`. يُنشئ `finalize.mjs` مؤثرات Foley عند مدد التايملاين الدقيقة، يمزجها بهدوء تحت التعليق، ويحوّل الصورة إلى H.264 بتدرج BT.709. لا يغيّر ذلك ملفات V2/V3 المنشورة.
