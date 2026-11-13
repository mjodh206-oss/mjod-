#!/usr/bin/env node
/**
 * Builds the built-in IR code database (assets/www/js/db.js) from the
 * open-source Flipper-IRDB collection (CC0-1.0).
 *
 * Usage:
 *   node tools/build-db.mjs /path/to/irdb /path/to/db.js
 *
 * The irdb dump is expected to have the original folder layout:
 *   TVs/, Audio_and_Video_Receivers/, ACs/, Fans/, LED_Lighting/ ...
 * (download the source linked in NOTICE.md)
 */
import fs from 'fs';
import path from 'path';

const [,, SRC, OUT] = process.argv;
if (!SRC || !OUT) { console.error('usage: build-db.mjs <irdb-dir> <out.js>'); process.exit(1); }

/* ---------------- protocol support ---------------- */
const SUPPORTED_PARSED = new Set(['NEC', 'NECext', 'Samsung32', 'SIRC', 'SIRC15', 'SIRC20']);

/* ---------------- category definitions ---------------- */
// popularity rank inside the Arab market (lower = more popular). Fallback: 100.
const rank = (obj) => (b) => {
  const k = b.toLowerCase().replace(/[_\-\s]/g, '');
  return obj[k] ?? 100;
};
const TV_RANK = rank({ samsung:1, lg:2, sony:3, tcl:4, hisense:5, panasonic:6, philips:7, toshiba:8, sharp:9, thomson:10, grundig:11, vizio:12, skyworth:13, strong:14, hitachi:15, sanyo:16, rca:17, jvc:18, conrad:19, goldstar:20, continentaledison:21 });
const AC_RANK = rank({ lg:1, samsung:2, midea:3, carrier:4, daikin:5, gree:6, haier:7, toshiba:8, panasonic:9, sharp:10, hitachi:11, tcl:12, hisense:13, whirlpool:14, york:15, chigo:16, delonghi:17, kross:18, artel:19 });
const FAN_RANK = rank({ woozoo:1, rowenta:2, toshiba:3, tcp:4, silvercrest:5, honeywell:6, mi:7, xiaomi:8, orbegozo:9, orbegoz:9, solis:10, mycarbon:11, shark:12 });
const LIGHT_RANK = rank({ govee:1, unknown:2, briloner:3, sylvania:4, idual:5, tuffenough:6, tweenlights:7, olliyn:8, gosund:9, tuya:10, beamz:11, warmoon:12, veezio:13 });
const RCVR_RANK = rank({ humax:1, sony:2, yamaha:3, onkyo:4, pioneer:5, denon:6, marantz:7, lumia:50, harmankardon:8, philips:9, lg:10, samsung:11, humaxcable:1, humaxus:1, skyq:3, xfinity:40, humaxhd:1 });
const OLD_RANK = rank({ sony:1, panasonic:2, philips:3, sharp:4, hitachi:5, grundig:6, telefunken:7, toshiba:8, sanyo:9, samsung:10, lg:11, rca:12, jvc:13, orion:14, goldstar:15, thompson:16, thomson:16 });

const CATS = [
  { id: 'tv',       dirs: ['TVs', 'Universal_TV_Remotes'], cap: 150, rawBudget: 260 * 1024, brandRank: TV_RANK,  protos: null, legacyOnly: false },
  { id: 'receiver', dirs: ['Audio_and_Video_Receivers', 'DVB-T', 'Cable_Boxes', 'TV_Tuner', 'SoundBars'], cap: 90, rawBudget: 60 * 1024, brandRank: RCVR_RANK, protos: null, legacyOnly: false },
  { id: 'ac',       dirs: ['ACs'], cap: 80, rawBudget: 850 * 1024, brandRank: AC_RANK, protos: null, legacyOnly: false },
  { id: 'fan',      dirs: ['Fans'], cap: 50, rawBudget: 320 * 1024, brandRank: FAN_RANK, protos: null, legacyOnly: false },
  { id: 'oldtv',    dirs: ['TVs'], cap: 60, rawBudget: 120 * 1024, brandRank: OLD_RANK, protos: ['SIRC', 'SIRC15', 'SIRC20'], legacyOnly: true },
  { id: 'light',    dirs: ['LED_Lighting'], cap: 90, rawBudget: 160 * 1024, brandRank: LIGHT_RANK, protos: null, legacyOnly: false },
];
const CAP_TOTAL = 450; // global safety cap

/* ---------------- brand arabic labels ---------------- */
const AR_BRAND = {
  samsung: 'سامسونج', lg: 'إل جي', sony: 'سوني', tcl: 'تي سي إل', hisense: 'هايسنس', panasonic: 'باناسونيك',
  philips: 'فيليبس', toshiba: 'توشيبا', sharp: 'شارب', hitachi: 'هيتاشي', grundig: 'جرونديج', telefunken: 'تيليفونكن',
  skyworth: 'سكاي ورث', vizio: 'فيزيو', rca: 'RCA', jvc: 'JVC', sanyo: 'سانيو', thompson: 'تومسون', thomson: 'تومسون',
  strong: 'سترينج', goldstar: 'جولد ستار', continentaledison: 'كونتيننتال إديسون', conrad: 'كونراد',
  gree: 'جري', midea: 'ميديا', haier: 'هاير', carrier: 'كاريير', daikin: 'دايكن', mitsubishi: 'ميتسوبيشي',
  delonghi: 'ديلونجي', whirlpool: 'ويرلبول', york: 'يورك', chigo: 'شيجو', artel: 'أرتل',
  yamaha: 'ياماها', onkyo: 'أونكيو', pioneer: 'بايونير', denon: 'دينون', marantz: 'مارانتز', harman_kardon: 'هارمن كاردن',
  harmankardon: 'هارمن كاردن', humax: 'هيوماكس', skyq: 'سكاي', xfinity: 'إكسفينيتي', edifier: 'إديفاير', naim: 'نايم',
  woozoo: 'ووزو', rowenta: 'روفينتا', tcp: 'تي سي بي', silvercrest: 'سيلفر كريست', honeywell: 'هنيويل',
  govee: 'جوفي', sylvania: 'سيلفانيا', idual: 'آي ديوال', tuffenough: 'تاف إناف', tween_lights: 'توين لايتس',
  tweenlights: 'توين لايتس', olliyn: 'أوليين', briloner: 'بريلونر', beamz: 'بيمز',
};
const prettyBrand = (b) => {
  const k = b.toLowerCase().replace(/[\-\s]/g, '_');
  if (AR_BRAND[k]) return AR_BRAND[k];
  return b.replace(/_/g, ' ').replace(/\s+/g, ' ').trim();
};

/* ---------------- button name → canonical ---------------- */
const norm = (n) => n.toLowerCase().trim().replace(/[^a-z0-9+]+/g, '_').replace(/^_+|_+$/g, '');

// ordered rules per category: [canon, regex]  — first match wins
const COMMON_RULES = [
  ['power', /^(power|pwr|toggle|standby|master|on\/?off|on_off|onoff|off_on|powertoggle|power_toggle|poweronoff|power_on|poweroff)$/],
  ['mute', /^mut(e|ing|ed)?$/],
  ['volUp', /^(vol(ume)?[-_ ]?(up|plus)|vol[-_ ]?up|volumeup|volume_up|v[-_ ]?up|v\+)$/],
  ['volDown', /^(vol(ume)?[-_ ]?(down|minus)|vol[-_ ]?down|volumedown|volume_down|v[-_ ]?down|v\-)$/],
  ['chUp', /^(ch(annel)?[-_ ]?(up|plus)|channel[-_ ]?up|channelup|prg[-_ ]?up|prog[-_ ]?up|program[-_ ]?up|c[-_ ]?up|c\+|page[-_ ]?up)$/],
  ['chDown', /^(ch(annel)?[-_ ]?(down|minus)|channel[-_ ]?down|channeldown|prg[-_ ]?down|prog[-_ ]?down|program[-_ ]?down|c[-_ ]?down|c\-|page[-_ ]?down)$/],
  ['menu', /^menu/],
  ['back', /^(back|return|exit|recall|last|back_?space)$/],
  ['ok', /^(ok|enter|select|confirm|apply|set|sel)$/],
  ['up', /^(up|arrow[-_ ]?up|cursor[-_ ]?up|nav[-_ ]?up|navigate[-_ ]?up|direction[-_ ]?up)$/],
  ['down', /^(down|arrow[-_ ]?down|cursor[-_ ]?down|nav[-_ ]?down|navigate[-_ ]?down|direction[-_ ]?down)$/],
  ['left', /^(left|arrow[-_ ]?left|cursor[-_ ]?left|nav[-_ ]?left|navigate[-_ ]?left|direction[-_ ]?left)$/],
  ['right', /^(right|arrow[-_ ]?right|cursor[-_ ]?right|nav[-_ ]?right|navigate[-_ ]?right|direction[-_ ]?right)$/],
  ['source', /^(source|input|av|tv[-_ ]?av|hdmi\d*|input[-_ ]?select|tv[-_ ]?input|video[-_ ]?select|scart|component|s[-_ ]?video|antenna)/],
  ['guide', /^(guide|epg|tv[-_ ]?guide|p[-_ ]?guide|program[-_ ]?guide|listings|index)/],
  ['red', /^(red|colou?r[-_ ]?red|key[-_ ]?red)$/],
  ['green', /^(green|colou?r[-_ ]?green|key[-_ ]?green)$/],
  ['yellow', /^(yellow|colou?r[-_ ]?yellow|key[-_ ]?yellow)$/],
  ['blue', /^(blue|colou?r[-_ ]?blue|key[-_ ]?blue)$/],
  ['play', /^(play|resume)$/],
  ['pause', /^(pause)$/],
  ['stop', /^(stop)$/],
  ['rw', /^(rw|rewind|rwd|rev|fast[-_ ]?back|prev(ious)?[-_ ]?track)$/],
  ['ff', /^(ff|fwrd|fast[-_ ]?forward|fwd|next[-_ ]?track|ffwd)$/],
];
const NUM_RULE = [/^(?:(?:num|digit|key|d|number|btn)[-_ ]?)?([0-9])$/, (m) => 'num' + m[1]];

const AC_RULES = [
  ['power', /^(power|on|off|on\/?off|standby|powertoggle|power_on|power_off)$/],
  ['tempUp', /^(temp[-_ ]?(up|plus|\+|increase)|tempup|temp_increase|t[-_ ]?\+|temp_\+|heat_?up|warmer|degree[-_ ]?up)$/],
  ['tempDown', /^(temp[-_ ]?(down|minus|\-|decrease)|tempdn|tempdown|temp_decrease|t[-_ ]?\-|temp_-|cool_?down|cooler|degree[-_ ]?down)$/],
  ['mode', /^(mode|operation|air[-_ ]?mode|op(eration)?[-_ ]?mode|cool[-_ ]?heat|heat[-_ ]?cool)$/],
  ['fanSpeed', /^(fan([-_ ]?speed)?|speed|fan_?speed|airflow|fanrate|wind|fan[-_ ]?only)$/],
  ['swing', /^(swing|oscillate|louver|swing[-_ ]?mode|air[-_ ]?swing|vertical|vane)/],
  ['timer', /^(timer|sleep[-_ ]?timer|on[-_ ]?timer|off[-_ ]?timer|clock|hour)/],
  ['sleep', /^(sleep|night)$/],
  ['turbo', /^(turbo|power[-_ ]?full|jet)$/],
  ['eco', /^(eco|save|energy|low)$/],
  ['quiet', /^(quiet|silent|mute[-_ ]?fan)$/],
  ['modeCool', /^cool(_\d+)?$/],
  ['modeHeat', /^heat(_\d+)?$/],
];
const FAN_RULES = [
  ['power', /^(power|on|off|standby|toggle)$/],
  ['speedUp', /^(speed[-_ ]?up|fan[-_ ]?up|faster|high|turbo|speed\+|stronger|sp[-_ ]?up)$/],
  ['speedDown', /^(speed[-_ ]?down|fan[-_ ]?down|slower|low|speed\-|quiet|sp[-_ ]?down)$/],
  ['fanSpeed', /^(speed|fan([-_ ]?speed)?|fan_?speed|wind|level)$/],
  ['speed1', /^(1|speed[-_ ]?1|fan[-_ ]?1|low[-_ ]?speed|speed[-_ ]?low)$/],
  ['speed2', /^(2|speed[-_ ]?2|fan[-_ ]?2|medium|mid[-_ ]?speed|speed[-_ ]?mid)$/],
  ['speed3', /^(3|speed[-_ ]?3|fan[-_ ]?3|high[-_ ]?speed|speed[-_ ]?high)$/],
  ['oscillate', /^(osc(illate|illation)?|rotate|rotation|swing|turn|pivot|swirl)$/],
  ['timer', /^(timer|sleep[-_ ]?timer|hour)$/],
  ['night', /^(night|sleep|natural|breeze|nature)$/],
  ['auto', /^(auto)$/],
];
const LIGHT_RULES = [
  ['power', /^(power|on|off|light|standby|toggle|power_?on|power_?off|master)$/],
  ['brightUp', /^(brighter|bright(ness)?[-_ ]?up|bri\+?|bri_up|dim[-_ ]?\+|up|more|plus|b\+)$/],
  ['brightDown', /^(dim|dimmer|bright(ness)?[-_ ]?down|bri\-?|bri_down|dim[-_ ]?\-|down|less|minus|b\-)$/],
  ['colorRed', /^r(ed)?$/], ['colorGreen', /^g(reen)?$/], ['colorBlue', /^b(lue)?$/],
  ['colorWhite', /^(w|white)$/], ['colorYellow', /^yellow$/], ['colorOrange', /^orange$/],
  ['colorPink', /^(pink|magenta)$/], ['colorPurple', /^purple$/], ['colorCyan', /^(cyan|aqua)$/],
  ['warm', /^(warm|ww|warm[-_ ]?white|warmwhite|color[-_ ]?warm|ct[-_ ]?warm)$/],
  ['cold', /^(cold|cool|cw|cool[-_ ]?white|coolwhite|color[-_ ]?cool|ct[-_ ]?cool)$/],
  ['effect', /^(flash|strobe|fade|smooth|jump|effect|diy|demo|disco|music|mic|mode|scene|color[-_ ]?mode|jump3|cycle)\d*$/],
  ['timer', /^(timer|1h|2h|4h|8h|4hrs|1hrs)$/],
  ['night', /^(night|sleep|reading)$/],
];
const COMMON_WHITELIST = {
  tv: new Set(['power','mute','volUp','volDown','chUp','chDown','up','down','left','right','ok','menu','back','source','guide','red','green','yellow','blue','num0','num1','num2','num3','num4','num5','num6','num7','num8','num9']),
  oldtv: new Set(['power','mute','volUp','volDown','chUp','chDown','up','down','left','right','ok','menu','back','num0','num1','num2','num3','num4','num5','num6','num7','num8','num9']),
  receiver: new Set(['power','mute','volUp','volDown','up','down','left','right','ok','menu','back','source','guide','red','green','yellow','blue','num0','num1','num2','num3','num4','num5','num6','num7','num8','num9','play','pause','stop','rw','ff']),
  ac: new Set(['power','tempUp','tempDown','mode','fanSpeed','swing','timer','sleep','turbo','eco','quiet','modeCool','modeHeat']),
  fan: new Set(['power','speedUp','speedDown','fanSpeed','speed1','speed2','speed3','oscillate','timer','night','auto']),
  light: new Set(['power','brightUp','brightDown','colorRed','colorGreen','colorBlue','colorWhite','colorYellow','colorOrange','colorPink','colorPurple','colorCyan','warm','cold','effect','timer','night']),
};

function canonFor(catId, rawName) {
  const n = norm(rawName);
  if (!n) return null;
  const rules = catId === 'ac' ? AC_RULES : catId === 'fan' ? FAN_RULES : catId === 'light' ? LIGHT_RULES : COMMON_RULES;
  for (const [canon, re] of rules) if (re.test(n)) return canon;
  // digits (not for ac / fan / light — except light timer digits excluded deliberately)
  if (catId === 'tv' || catId === 'oldtv' || catId === 'receiver') {
    const m = n.match(/^(?:(?:num|digit|key|d|number|btn)[-_ ]?)?([0-9])$/);
    if (m) return 'num' + m[1];
    const m2 = n.match(/^([0-9])$/);
    if (m2) return 'num' + m2[1];
  }
  return null;
}

/* ---------------- .ir file parsing ---------------- */
function parseIrFile(filePath) {
  let text;
  try { text = fs.readFileSync(filePath, 'utf8'); } catch { return []; }
  const out = [];
  const blocks = text.split(/^#/m);
  for (const block of blocks) {
    const nameM = block.match(/^name:\s*(.+)$/m);
    const typeM = block.match(/^type:\s*(\w+)$/m);
    if (!nameM || !typeM) continue;
    const name = nameM[1].trim();
    const type = typeM[1].trim();
    if (type === 'parsed') {
      const protoM = block.match(/^protocol:\s*(\S+)$/m);
      const addrM = block.match(/^address:\s*([0-9a-fA-F\s]+)$/m);
      const cmdM = block.match(/^command:\s*([0-9a-fA-F\s]+)$/m);
      if (!protoM || !addrM || !cmdM) continue;
      const proto = protoM[1].trim();
      if (!SUPPORTED_PARSED.has(proto)) continue;
      const bytes = (s) => s.trim().split(/\s+/).map((h) => parseInt(h, 16));
      const addr = bytes(addrM[1]), cmd = bytes(cmdM[1]);
      if (addr.some(Number.isNaN) || cmd.some(Number.isNaN)) continue;
      out.push({ name, p: proto.toLowerCase(), a: addr.slice(0, 4), c: cmd[0] });
    } else if (type === 'raw') {
      const freqM = block.match(/^frequency:\s*(\d+)$/m);
      const dataM = block.match(/^data:\s*([0-9\s]+)$/m);
      if (!dataM) continue;
      const data = dataM[1].trim().split(/\s+/).map(Number);
      if (data.some((v) => !Number.isFinite(v) || v <= 0 || v > 32000)) continue;
      if (data.length < 4 || data.length > 1024) continue;
      out.push({ name, p: 'raw', f: freqM ? parseInt(freqM[1], 10) : 38000, raw: data });
    }
  }
  return out;
}

function walk(dir) {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walk(p));
    else if (e.name.endsWith('.ir')) out.push(p);
  }
  return out;
}

/* ---------------- main build ---------------- */
const result = {
  v: 1,
  src: 'Flipper-IRDB (CC0-1.0) + NEC/Samsung/SIRC protocol generators',
  categories: {},
  meta: { totalProfiles: 0, rawBytes: 0 },
};

let grandTotal = 0;
for (const cat of CATS) {
  const profiles = [];
  for (const d of cat.dirs) {
    const dirPath = path.join(SRC, d);
    if (!fs.existsSync(dirPath)) continue;
    for (const file of walk(dirPath)) {
      const entries = parseIrFile(file);
      if (!entries.length) continue;
      const rel = path.relative(SRC, file);
      const brand = rel.split(path.sep)[1] || rel.split(path.sep)[0];
      const model = path.basename(file, '.ir');
      // legacy filter for oldtv (SIRC family in TVs, or crt-ish name)
      if (cat.legacyOnly) {
        const isSirc = entries.some((e) => ['sirc', 'sirc15', 'sirc20'].includes(e.p));
        const legacyName = /kv|trinitron|wega|crt|tr|flat|51cm|k700|s99|29"|28"|21"/i.test(model);
        if (!isSirc && !legacyName) continue;
      }
      // map buttons
      const buttons = {};
      let rawBytes = 0;
      const rawName = {};
      for (const e of entries) {
        const canon = canonFor(cat.id, e.name);
        if (!canon) continue;
        if (!COMMON_WHITELIST[cat.id].has(canon)) continue;
        const mk = (e) => e.p === 'raw'
          ? { p: 'raw', f: e.f, raw: e.raw }
          : { p: e.p, a: e.a, c: e.c };
        if (buttons[canon]) {
          // for direct-temperature AC frames prefer the 24-degree preset
          if (canon === 'modeCool' || canon === 'modeHeat') {
            const score = (n) => (/_24$/i.test(n) ? 2 : /_23$/i.test(n) ? 1 : 0);
            if (score(e.name) > score(rawName[canon])) { buttons[canon] = mk(e); rawName[canon] = e.name; }
          }
          continue; // otherwise first wins
        }
        buttons[canon] = mk(e);
        rawName[canon] = e.name;
        if (e.p === 'raw') rawBytes += e.raw.length * 2 + 8;
      }
      if (!buttons.power) continue;
      const nbtn = Object.keys(buttons).length;
      profiles.push({ brand, model, buttons, nbtn, rawBytes, rank: cat.brandRank(brand) });
    }
  }
  // dedupe identical (brand+model) keeping first
  profiles.sort((a, b) => a.rank - b.rank || b.nbtn - a.nbtn || a.brand.localeCompare(b.brand));
  // apply caps: raw budget, profile cap
  let rawUsed = 0; const picked = []; const seen = new Set();
  for (const p of profiles) {
    if (picked.length >= cat.cap) break;
    const key = (p.brand + '|' + p.model).toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    const isHeavyRaw = p.rawBytes > 120 * 1024;
    if (isHeavyRaw && rawUsed + p.rawBytes > cat.rawBudget) {
      // degrade heavy raw profiles: keep power only
      const pb = p.buttons.power;
      const est = pb.p === 'raw' ? pb.raw.length * 2 + 8 : 20;
      if (rawUsed + est <= cat.rawBudget) {
        picked.push({ ...p, buttons: { power: pb }, nbtn: 1, rawBytes: est });
        rawUsed += est;
      }
      continue;
    }
    picked.push(p); rawUsed += p.rawBytes;
  }
  grandTotal += picked.length;
  result.categories[cat.id] = picked.map((p, i) => ({
    id: cat.id + i,
    brand: prettyBrand(p.brand),
    model: p.model.replace(/_/g, ' '),
    buttons: p.buttons,
  }));
  console.log(`${cat.id}: picked ${picked.length} profiles (raw bytes ${(rawUsed / 1024).toFixed(0)} KB)`);
}
result.meta.totalProfiles = grandTotal;

const js = '/* Auto-generated from Flipper-IRDB (CC0-1.0). Do not edit by hand.\n'
  + '   Regenerate: node tools/build-db.mjs <irdb-dir> <out.js> */\n'
  + 'window.IR_DB = ' + JSON.stringify(result) + ';\n';
fs.writeFileSync(OUT, js);
console.log('total profiles:', grandTotal, '->', OUT, (js.length / 1024).toFixed(0) + ' KB');
