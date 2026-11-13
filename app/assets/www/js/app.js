/* ══════════════════════════════════════════════════════════════
   ريموتي الذكي — منطق التطبيق (RTL · يعمل دون إنترنت)
   ══════════════════════════════════════════════════════════════ */
'use strict';

/* ───────────────── الأقسام ───────────────── */
const CATEGORIES = [
  { id: 'tv',       name: 'الشاشات',              emoji: '📺', glow: 'rgba(34,211,238,.40)', c1: '#0e2e4d', c2: '#060f22' },
  { id: 'receiver', name: 'الرسيفرات',            emoji: '📡', glow: 'rgba(167,139,250,.42)', c1: '#241a4e', c2: '#0d0621' },
  { id: 'ac',       name: 'المكيفات',             emoji: '❄️', glow: 'rgba(96,165,250,.40)',  c1: '#0e2a4e', c2: '#050f24' },
  { id: 'fan',      name: 'المراوح',              emoji: '🌀', glow: 'rgba(52,211,153,.38)',  c1: '#0d3325', c2: '#041a12' },
  { id: 'oldtv',    name: 'التلفزيونات القديمة',  emoji: '📼', glow: 'rgba(251,191,36,.38)',  c1: '#34260b', c2: '#1a1004' },
  { id: 'light',    name: 'الإضاءات',             emoji: '💡', glow: 'rgba(253,224,71,.42)',  c1: '#352d07', c2: '#1a1603' },
];
const catById = (id) => CATEGORIES.find((c) => c.id === id) || CATEGORIES[0];
const DB_CATEGORIES = () => (window.IR_DB && window.IR_DB.categories) || {};

/* ───────────────── الإعدادات ───────────────── */
const DEFAULT_SETTINGS = { theme: 'dark', sound: true, haptic: true, searchInterval: 450 };
let settings = Object.assign({}, DEFAULT_SETTINGS, IR.store.get('settings', {}));
const saveSettings = () => IR.store.set('settings', settings);

/* ───────────────── الأجهزة المحفوظة ───────────────── */
let devices = IR.store.get('devices', []);
const saveDevices = () => IR.store.set('devices', devices);

/* ───────────────── أدوات عامة ───────────────── */
const $ = (sel, root) => (root || document).querySelector(sel);
const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
const escapeHtml = (s) => String(s).replace(/[&<>"']/g, (m) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]));
const haptic = (ms) => { if (settings.haptic) IR.vibrate(ms == null ? 18 : ms); };
const sound = (kind) => { if (settings.sound) IR.beep(kind || 'tick'); };

let toastTimer = null;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 2400);
}

let sendFlashTimer = null;
function flashSend() {
  const f = $('#send-flash');
  f.classList.add('show');
  clearTimeout(sendFlashTimer);
  sendFlashTimer = setTimeout(() => f.classList.remove('show'), 950);
}

/* مودال معلوماتي + تأكيد عام */
let infoOkCallback = null;
function showInfo({ icon, title, text, okLabel, cancelLabel, onOk }) {
  $('#info-modal-icon').textContent = icon || 'ℹ';
  $('#info-modal-title').textContent = title || 'تنبيه';
  $('#info-modal-text').innerHTML = text || '';
  $('#info-modal-ok').textContent = okLabel || 'حسناً';
  const cancel = $('#info-modal-cancel');
  if (cancelLabel) { cancel.hidden = false; cancel.textContent = cancelLabel; } else { cancel.hidden = true; }
  infoOkCallback = typeof onOk === 'function' ? onOk : null;
  $('#info-modal').hidden = false;
}
function closeInfo() { $('#info-modal').hidden = true; infoOkCallback = null; }

/* مودال تسمية عام */
let namePromptCallback = null;
function namePrompt(title, defaultValue, okLabel, cb) {
  namePromptCallback = cb;
  $('#save-modal h4').textContent = title || 'أدخل الاسم';
  const input = $('#device-name-input');
  input.value = defaultValue || '';
  $('#save-device-confirm').textContent = okLabel || 'حفظ';
  $('#save-modal').hidden = false;
  setTimeout(() => { try { input.focus(); input.select(); } catch (e) {} }, 80);
}
function closeNamePrompt() { $('#save-modal').hidden = true; namePromptCallback = null; }

/* ═══════════════ التوجيه (Routing) ═══════════════ */
const SCREENS = ['screen-home', 'screen-search', 'screen-remote', 'screen-settings', 'screen-about'];
let currentRemoteId = null;

function showOnly(id) {
  for (const s of SCREENS) {
    const el = document.getElementById(s);
    el.classList.toggle('visible', s === id);
  }
}
function go(path, replace) {
  const target = '#/' + (path || 'home');
  if (replace) history.replaceState(null, '', target);
  else if ((location.hash || '') !== target) location.hash = target;
  else renderRoute();
}
function currentRoute() {
  const hash = (location.hash || '').replace(/^#\/?/, '');
  const [route, param] = hash.split('/');
  return { route: route || 'home', param: param ? decodeURIComponent(param) : '' };
}
function renderRoute() {
  const { route, param } = currentRoute();
  stopSearch();
  switch (route) {
    case 'search':   openSearchScreen(param); break;
    case 'remote':   openRemoteScreen(param); break;
    case 'settings': showOnly('screen-settings'); syncSettingsUI(); break;
    case 'about':    showOnly('screen-about'); break;
    default:         renderHome(); showOnly('screen-home'); break;
  }
}
window.addEventListener('hashchange', renderRoute);

/* ═══════════════ الرئيسية ═══════════════ */
function renderHome() {
  // شبكة الأقسام
  const grid = $('#cats-grid');
  grid.innerHTML = CATEGORIES.map((cat) => {
    const count = (DB_CATEGORIES()[cat.id] || []).length;
    const saved = devices.filter((d) => d.cat === cat.id).length;
    return `<button class="cat-card" data-cat="${cat.id}" style="--cat-glow:${cat.glow};--cat-1:${cat.c1};--cat-2:${cat.c2}" aria-label="${escapeHtml(cat.name)}">
      <span class="cat-emoji">${cat.emoji}</span>
      <span>
        <span class="cat-name">${escapeHtml(cat.name)}</span>
        <span class="cat-meta">
          <span class="cat-count">${count} كود ماركة</span>
          <span class="cat-saved" ${saved ? '' : 'hidden'}>★ ${saved} محفوظ</span>
        </span>
      </span>
    </button>`;
  }).join('');

  // أجهزتي
  const wrap = $('#mydevices-wrap');
  const list = $('#mydevices');
  $('#mydevices-count').textContent = devices.length ? `${devices.length} جهاز` : '';
  if (!devices.length) {
    wrap.hidden = true;
  } else {
    wrap.hidden = false;
    list.innerHTML = devices.map((d) => {
      const cat = catById(d.cat);
      return `<button class="device-chip" data-open-device="${escapeHtml(d.id)}" aria-label="${escapeHtml(d.name)}">
        <span class="dc-ico">${cat.emoji}</span>
        <span class="dc-name">${escapeHtml(d.name)}</span>
        <span class="dc-brand">${escapeHtml(d.brand)}</span>
      </button>`;
    }).join('');
  }
}

/* ═══════════════ البحث التلقائي ═══════════════ */
const searchState = {
  catId: null,
  list: [],
  pointer: 0,   // فهرس الكود التالي للإرسال
  attempts: 0,
  timer: null,
  playing: false,
  filterBrand: null,
};
const SEARCH_TICK_LIMIT = 400;

function openSearchScreen(catId) {
  const cat = catById(catId);
  const full = (DB_CATEGORIES()[cat.id] || []);
  searchState.catId = cat.id;
  searchState.list = full.slice();
  searchState.pointer = 0;
  searchState.attempts = 0;
  searchState.filterBrand = null;
  searchState.playing = false;

  $('#search-cat-icon').textContent = cat.emoji;
  $('#search-cat-name').textContent = cat.name;
  $('#search-total').textContent = full.length;
  $('#search-fallback').hidden = true;
  renderSearchProgress();
  showOnly('screen-search');

  if (!full.length) {
    showInfo({ icon: '⚠️', title: 'لا توجد أكواد لهذا القسم', text: 'حدث خطأ في قاعدة الأكواد المدمجة. حاول إعادة تثبيت التطبيق.', onOk: () => go('home') });
    return;
  }
  // يبدأ البحث فوراً بدون أي زر — حسب المواصفات
  setTimeout(() => { if (currentRoute().route === 'search' && !searchState.playing) startSearch(); }, 650);
}

function renderSearchProgress() {
  const { list, pointer } = searchState;
  const total = list.length;
  const current = total ? Math.min(pointer + 1, total) : 0;
  $('#search-step').textContent = current;
  $('#search-total').textContent = total;
  $('#search-bar-fill').style.width = total ? `${(pointer / total) * 100}%` : '0%';
  const p = list[Math.min(pointer, total - 1)];
  $('#search-brand-name').textContent = p ? p.brand : '…';
}

function transmitPower(profile) {
  if (!profile || !profile.buttons || !profile.buttons.power) return false;
  IR.transmitButton(profile.buttons.power);
  haptic(14);
  return true;
}

function tick() {
  const s = searchState;
  if (s.pointer >= s.list.length) { exhaustSearch(); return; }
  const profile = s.list[s.pointer];
  transmitPower(profile);
  s.pointer += 1;
  s.attempts += 1;
  renderSearchProgress();
  // في وضع المحاكاة: نجعل «الجهاز» يستجيب بعد 4 محاولات لعرض التدفق كاملاً
  if (IR.isDemo() && s.attempts === 4 && s.pointer < s.list.length) {
    pauseSearch();
    openConfirmModal(s.list[s.pointer - 1]);
  }
  if (s.attempts > SEARCH_TICK_LIMIT) exhaustSearch();
}

function startSearch() {
  const s = searchState;
  stopSearch();
  if (s.pointer >= s.list.length) s.pointer = 0;
  s.playing = true;
  $('#btn-pause').classList.remove('paused');
  $('#pause-label').textContent = 'إيقاف مؤقت';
  $('#radar').classList.remove('paused');
  tick();
  s.timer = setInterval(tick, settings.searchInterval || 450);
}
function pauseSearch() {
  const s = searchState;
  s.playing = false;
  clearInterval(s.timer);
  s.timer = null;
  $('#btn-pause').classList.add('paused');
  $('#pause-label').textContent = 'متابعة';
  $('#radar').classList.add('paused');
}
function stopSearch() {
  const s = searchState;
  s.playing = false;
  clearInterval(s.timer);
  s.timer = null;
  $('#radar').classList.remove('sending', 'paused');
}
function togglePause() { searchState.playing ? pauseSearch() : startSearch(); }
function backOneStep() {
  const s = searchState;
  if (!s.list.length) return;
  const wasPlaying = s.playing;
  if (wasPlaying) pauseSearch();
  s.pointer = Math.max(0, s.pointer - 1);
  transmitPower(s.list[s.pointer]);
  haptic(30);
  renderSearchProgress();
  toast('أُعيد إرسال الكود السابق');
}
function skipBrand() {
  const s = searchState;
  if (!s.list.length) return;
  const currentBrand = s.list[Math.min(s.pointer, s.list.length - 1)].brand;
  let j = s.pointer + 1;
  while (j < s.list.length && s.list[j].brand === currentBrand) j++;
  if (j >= s.list.length) { toast('لا توجد ماركة أخرى — انتهت القائمة'); exhaustSearch(); return; }
  s.pointer = j;
  renderSearchProgress();
  haptic(24);
  toast(`تخطينا «${currentBrand}»`);
}
function exhaustSearch() {
  stopSearch();
  $('#search-fallback').hidden = false;
  $('#radar').classList.remove('sending');
  haptic(60);
}

function openConfirmModal(profile) {
  $('#confirm-code-desc').textContent = profile ? `«${profile.brand}» — ${profile.model}` : '—';
  $('#confirm-modal').hidden = false;
  haptic(80);
  sound('ok');
}
function closeConfirmModal() { $('#confirm-modal').hidden = true; }

function openBrandSheet() {
  const s = searchState;
  const seen = [];
  for (const p of DB_CATEGORIES()[s.catId] || []) if (!seen.includes(p.brand)) seen.push(p.brand);
  $('#brand-grid').innerHTML = seen.map((b) => `<button class="brand-btn" data-brand="${escapeHtml(b)}">${escapeHtml(b)}</button>`).join('') || '<p class="settings-desc">لا توجد ماركات</p>';
  $('#brand-sheet').hidden = false;
}
function closeBrandSheet() { $('#brand-sheet').hidden = true; }

/* حفظ جهاز بعد التعرف */
function buildSavedDevice(profile, name) {
  return {
    id: 'dev_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
    name: name,
    cat: searchState.catId || 'tv',
    brand: profile.brand,
    model: profile.model,
    addedAt: Date.now(),
    buttons: profile.buttons,
    ac: { temp: 24, mode: 'cool', fan: 1, on: true },
  };
}

/* ═══════════════ الريموت الكامل ═══════════════ */
function findDevice(id) { return devices.find((d) => d.id === id) || null; }

const SVG = {
  power: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M12 3v9"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M6.3 6.5a8 8 0 1 0 11.4 0"/></svg>',
  mute: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M4 9.5v5h3.5L12 18.6V5.4L7.5 9.5H4z"/><path fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" d="M16 9.5l5 5m0-5l-5 5"/></svg>',
  source: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="13" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M9 21h6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  menu: '<svg viewBox="0 0 24 24"><path stroke="currentColor" stroke-width="1.9" stroke-linecap="round" d="M5 7h14M5 12h14M5 17h9"/></svg>',
  back: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" d="M15 6l-6 6 6 6"/></svg>',
  guide: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="15" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  up: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round" d="M6 14l6-6 6 6"/></svg>',
  down: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round" d="M6 10l6 6 6-6"/></svg>',
  left: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round" d="M14 6l-6 6 6 6"/></svg>',
  right: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round" d="M10 6l6 6-6 6"/></svg>',
  volUp: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M4 10v4h3.4L12 17.8V6.2L7.4 10H4z"/><path stroke="currentColor" stroke-width="1.8" stroke-linecap="round" d="M16 10v4M14 12h4"/></svg>',
  volDown: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M4 10v4h3.4L12 17.8V6.2L7.4 10H4z"/><path stroke="currentColor" stroke-width="1.8" stroke-linecap="round" d="M14.5 12h5"/></svg>',
  chUp: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" d="M7 15l5-5 5 5"/></svg>',
  chDown: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" d="M7 9l5 5 5-5"/></svg>',
  play: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M8.5 6v12l10-6z"/></svg>',
  pause: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M8 6h3v12H8zM13.5 6H17v12h-3.5z"/></svg>',
  stop: '<svg viewBox="0 0 24 24"><rect x="7" y="7" width="10" height="10" rx="1.5" fill="currentColor"/></svg>',
  rw: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M11.5 6v12L4 12zM19 6v12l-7-6z"/></svg>',
  ff: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12.5 6v12L20 12zM5 6v12l7-6z"/></svg>',
  swing: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" d="M4 10c5.3 0 5.3 4 10.7 4S20 10 20 10M4 15h.01M20 15h.01"/><path fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" d="M7 7c3 0 3 2.4 6 2.4S16 7 17 7" opacity=".6"/></svg>',
  fan: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="2" fill="currentColor"/><path fill="none" stroke="currentColor" stroke-width="1.7" d="M12 10c0-3 1-5 4-5.5 1 2.5-.5 4.5-4 5.5zM14 12c3 0 5 1 5.5 4-2.5 1-4.5-.5-5.5-4zM12 14c0 3-1 5-4 5.5-1-2.5.5-4.5 4-5.5zM10 12c-3 0-5-1-5.5-4 2.5-1 4.5.5 5.5 4z"/></svg>',
  timer: '<svg viewBox="0 0 24 24"><circle cx="12" cy="13" r="8" fill="none" stroke="currentColor" stroke-width="1.9"/><path d="M12 9.5V13l2.3 1.6M9.5 2.5h5" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/></svg>',
  moon: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M20 13.2A8.5 8.5 0 1 1 10.8 4a6.8 6.8 0 1 0 9.2 9.2z"/></svg>',
  turbo: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" d="M13 2 5 13h6l-1 9 8-11h-6z"/></svg>',
  leaf: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M5 19C5 9 11 4 20 4c0 10-5 15-14 15M5 19c3-6 7-10 12-12"/></svg>',
  droplet: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" d="M12 3s6.5 7 6.5 12a6.5 6.5 0 0 1-13 0C5.5 10 12 3 12 3z"/></svg>',
  snow: '<svg viewBox="0 0 24 24"><path stroke="currentColor" stroke-width="1.8" stroke-linecap="round" d="M12 2v20M4 6l16 12M20 6L4 18M2 12h20"/></svg>',
  sun: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4.6" fill="none" stroke="currentColor" stroke-width="1.9"/><path stroke="currentColor" stroke-width="1.9" stroke-linecap="round" d="M12 2.5V5M12 19v2.5M2.5 12H5M19 12h2.5M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8"/></svg>',
  refresh: '<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" d="M20 12a8 8 0 1 1-2.3-5.6M20 3v5h-5"/></svg>',
  brightness: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4" fill="currentColor"/><path stroke="currentColor" stroke-width="1.8" stroke-linecap="round" d="M12 2v2.6M12 19.4V22M2 12h2.6M19.4 12H22M4.2 4.2l1.9 1.9M18 18l1.8 1.8M4.2 19.8 6.1 17.9M18 6l1.8-1.8"/></svg>',
  sparkle: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 2l2.1 6.4L20.5 10l-6.4 1.6L12 18l-2.1-6.4L3.5 10l6.4-1.6z"/></svg>',
};

function rbtn(canon, label, iconSvg, cls, dataExtra) {
  return `<button class="rbtn ${cls || ''}" data-btn="${canon}" ${dataExtra || ''}>${iconSvg || ''}<span>${label}</span></button>`;
}

function renderRemoteBody(device) {
  const b = device.buttons || {};
  const has = (k) => !!b[k];
  const ghost = (k) => (has(k) ? '' : ' ghosted');
  const cat = device.cat;

  if (cat === 'tv' || cat === 'oldtv' || cat === 'receiver') {
    const isReceiver = cat === 'receiver';
    const isOld = cat === 'oldtv';
    let html = '';
    html += `<div class="r-row">
      ${rbtn('power', 'تشغيل / إطفاء', SVG.power, 'power' + ghost('power'))}
      ${rbtn('mute', 'كتم الصوت', SVG.mute, ghost('mute'))}
      ${isOld ? '' : rbtn('source', 'المدخل', SVG.source, ghost('source')) + (isReceiver ? '' : rbtn('guide', 'الدليل', SVG.guide, ghost('guide')))}
    </div>`;
    // الصوت والقنوات (أذرع عمودية)
    html += `<div class="r-row">
      <div class="rocker">
        <button class="rocker-btn${ghost('volUp')}" data-btn="volUp">＋<small>مستوى الصوت</small></button>
        <button class="rocker-btn${ghost('volDown')}" data-btn="volDown">－<small>&nbsp;</small></button>
      </div>
      ${isReceiver ? `<div class="rocker"><button class="rocker-btn${ghost('rw')}" data-btn="rw">◀◀<small>رجوع</small></button><button class="rocker-btn${ghost('ff')}" data-btn="ff">▶▶<small>تقديم</small></button></div>`
        : `<div class="rocker">
        <button class="rocker-btn${ghost('chUp')}" data-btn="chUp">${SVG.chUp.replace('width="2"', 'width="2.4"')}<small>القناة</small></button>
        <button class="rocker-btn${ghost('chDown')}" data-btn="chDown">${SVG.chDown.replace('width="2"', 'width="2.4"')}<small>&nbsp;</small></button>
      </div>`}
    </div>`;
    if (!isOld) {
      // لوحة الأسهم + OK
      html += `<div class="dpad">
        <span></span><button class="dbtn${ghost('up')}" data-btn="up" aria-label="أعلى">${SVG.up}</button><span></span>
        <button class="dbtn${ghost('left')}" data-btn="left" aria-label="يسار">${SVG.left}</button>
        <button class="dbtn dpad-ok${ghost('ok')}" data-btn="ok">OK</button>
        <button class="dbtn${ghost('right')}" data-btn="right" aria-label="يمين">${SVG.right}</button>
        <span></span><button class="dbtn${ghost('down')}" data-btn="down" aria-label="أسفل">${SVG.down}</button><span></span>
      </div>
      <div class="r-row">
        ${rbtn('menu', 'القائمة', SVG.menu, ghost('menu'))}
        ${rbtn('back', 'رجوع', SVG.back, ghost('back'))}
        ${isReceiver ? rbtn('guide', 'الدليل', SVG.guide, ghost('guide')) : ''}
      </div>`;
    } else {
      html += `<div class="r-row">${rbtn('menu', 'القائمة', SVG.menu, ghost('menu'))}</div>`;
    }
    // الأرقام
    let digits = '';
    for (const n of ['1','2','3','4','5','6','7','8','9']) digits += `<button class="digit${ghost('num' + n)}" data-btn="num${n}">${n}</button>`;
    digits += `<button class="digit zero${ghost('num0')}" data-btn="num0">0</button>`;
    html += `<div class="digits">${digits}</div>`;
    // الألوان
    if (!isOld) {
      html += `<div class="colors-row">
        <button class="color-btn c-red${ghost('red')}" data-btn="red" aria-label="أحمر"></button>
        <button class="color-btn c-green${ghost('green')}" data-btn="green" aria-label="أخضر"></button>
        <button class="color-btn c-yellow${ghost('yellow')}" data-btn="yellow" aria-label="أصفر"></button>
        <button class="color-btn c-blue${ghost('blue')}" data-btn="blue" aria-label="أزرق"></button>
      </div>`;
    }
    if (isReceiver) {
      html += `<div class="r-row">
        ${rbtn('stop', 'إيقاف', SVG.stop, ghost('stop'))}
        ${rbtn('pause', 'إيقاف مؤقت', SVG.pause, ghost('pause'))}
        ${rbtn('play', 'تشغيل', SVG.play, ghost('play'))}
      </div>`;
    }
    return html;
  }

  if (cat === 'ac') {
    const ac = device.ac || { temp: 24, mode: 'cool', fan: 1, on: true };
    const hasModeBtn = has('mode');
    const modeEnabled = {
      cool: has('modeCool') || hasModeBtn, heat: has('modeHeat') || hasModeBtn,
      fan: hasModeBtn, dry: hasModeBtn, auto: hasModeBtn,
    };
    const modeBtn = (mode, label, ico) => `<button class="ac-mode-btn${ac.mode === mode ? ' active' : ''}${modeEnabled[mode] ? '' : ' ghosted'}" data-ac-mode="${mode}"><span class="m-ico">${ico}</span>${label}</button>`;
    const fanLabel = ['', 'سرعة منخفضة', 'سرعة متوسطة', 'سرعة عالية'][ac.fan] || 'تلقائي';
    return `
      <div class="ac-lcd">
        <div class="ac-lcd-top"><span class="ac-lcd-mode"><span class="ac-lcd-power-dot${ac.on ? '' : ' off'}"></span>مكيّف الهواء</span><span>IR · ${escapeHtml(device.brand)}</span></div>
        <div class="ac-lcd-temp"><span id="ac-temp-value">${ac.temp}</span><small>°</small></div>
        <div class="ac-lcd-sub"><span id="ac-mode-label">${modeLabel(ac.mode)}</span><span>·</span><span id="ac-fan-label">${fanLabel}</span><span id="ac-swing-label"></span></div>
      </div>
      <div class="ac-temp-ctrl">
        <button class="ac-temp-btn${ghost('tempDown')}" data-btn="tempDown" aria-label="خفض الحرارة">−</button>
        <button class="rbtn power${ghost('power')}" data-btn="power" style="min-width:150px">${SVG.power}<span>${ac.on ? 'إطفاء' : 'تشغيل'}</span></button>
        <button class="ac-temp-btn${ghost('tempUp')}" data-btn="tempUp" aria-label="رفع الحرارة">＋</button>
      </div>
      <div class="ac-modes">
        ${modeBtn('cool', 'تبريد', '❄️')}${modeBtn('heat', 'تدفئة', '🔥')}${modeBtn('fan', 'مروحة', '🌀')}${modeBtn('dry', 'تجفيف', '💧')}${modeBtn('auto', 'تلقائي', '🔄')}
      </div>
      <div class="ac-aux">
        <button class="rbtn${ghost('fanSpeed')}" data-btn="fanSpeed" data-ac-fan="1">${SVG.fan}<span>سرعة المروحة</span></button>
        <button class="rbtn${ghost('swing')}" data-btn="swing">${SVG.swing}<span>التأرجح</span></button>
        <button class="rbtn${ghost('timer')}" data-btn="timer">${SVG.timer}<span>المؤقّت</span></button>
        <button class="rbtn${ghost('sleep')}" data-btn="sleep">${SVG.moon}<span>وضع النوم</span></button>
        <button class="rbtn${ghost('turbo')}" data-btn="turbo">${SVG.turbo}<span>تربو</span></button>
        <button class="rbtn${ghost('eco')}" data-btn="eco">${SVG.leaf}<span>موفّر</span></button>
        <button class="rbtn${ghost('quiet')}" data-btn="quiet"><svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" d="M4 12h16M7 8.5h10M9.5 15.5h5"/></svg><span>هادئ</span></button>
      </div>`;
  }

  if (cat === 'fan') {
    return `
      <div class="r-row">
        <button class="rbtn power${ghost('power')}" data-btn="power" style="min-width:180px;min-height:74px">${SVG.power}<span>تشغيل / إطفاء</span></button>
      </div>
      <div class="fan-speeds" id="fan-speed-dots">
        ${[1, 2, 3].map((n) => `<button class="fan-dot${ghost('speed' + n)}" data-btn="speed${n}" data-fan-level="${n}">${n}</button>`).join('')}
      </div>
      <div class="r-row">
        <button class="rbtn${ghost('speedDown')}" data-btn="speedDown"><span style="font-size:22px">−</span><span>أهدأ</span></button>
        <button class="rbtn${ghost('fanSpeed')}" data-btn="fanSpeed">${SVG.refresh}<span>دور السرعات</span></button>
        <button class="rbtn${ghost('speedUp')}" data-btn="speedUp"><span style="font-size:22px">＋</span><span>أسرع</span></button>
      </div>
      <div class="r-row">
        <button class="rbtn${ghost('oscillate')}" data-btn="oscillate">${SVG.swing}<span>دوران</span></button>
        <button class="rbtn${ghost('timer')}" data-btn="timer">${SVG.timer}<span>المؤقّت</span></button>
        <button class="rbtn${ghost('night')}" data-btn="night">${SVG.moon}<span>الليل</span></button>
        <button class="rbtn${ghost('auto')}" data-btn="auto"><span style="font-size:15px;font-weight:900">AUTO</span><span>تلقائي</span></button>
      </div>`;
  }

  if (cat === 'light') {
    const colorDefs = [
      ['colorRed', '#ef4444', 'أحمر'], ['colorGreen', '#22c55e', 'أخضر'], ['colorBlue', '#3b82f6', 'أزرق'],
      ['colorWhite', '#f8fafc', 'أبيض'], ['colorYellow', '#eab308', 'أصفر'], ['colorOrange', '#fb923c', 'برتقالي'],
      ['colorPink', '#ec4899', 'وردي'], ['colorPurple', '#a855f7', 'بنفسجي'], ['colorCyan', '#22d3ee', 'سماوي'],
    ];
    const swatches = colorDefs.filter(([k]) => has(k))
      .map(([k, hex, ar]) => `<button class="light-swatch" data-btn="${k}" style="background:${hex}" aria-label="${ar}"></button>`).join('');
    const segs = Array.from({ length: 10 }, (_, i) => `<span class="light-seg${i < 5 ? ' on' : ''}" data-seg="${i}"></span>`).join('');
    return `
      <div class="light-power"><button class="light-power-btn${ghost('power')}" data-btn="power" aria-label="تشغيل / إطفاء">${SVG.power}</button></div>
      <div class="r-row" style="align-items:center">
        <button class="ac-temp-btn${ghost('brightDown')}" data-btn="brightDown" style="width:56px;height:56px;font-size:26px" aria-label="خفض السطوع">−</button>
        <div class="light-bar" id="light-bar">${segs}</div>
        <button class="ac-temp-btn${ghost('brightUp')}" data-btn="brightUp" style="width:56px;height:56px;font-size:26px" aria-label="رفع السطوع">＋</button>
      </div>
      <div class="r-row">
        <button class="rbtn${ghost('warm')}" data-btn="warm">${SVG.sun}<span>أبيض دافئ</span></button>
        <button class="rbtn${ghost('cold')}" data-btn="cold"><svg viewBox="0 0 24 24"><path stroke="currentColor" stroke-width="1.9" stroke-linecap="round" d="M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9"/></svg><span>أبيض بارد</span></button>
      </div>
      ${swatches ? `<div class="light-colors">${swatches}</div>` : ''}
      <div class="r-row">
        <button class="rbtn${ghost('effect')}" data-btn="effect" style="min-width:170px">${SVG.sparkle}<span>مؤثرات ضوئية</span></button>
        <button class="rbtn${ghost('night')}" data-btn="night">${SVG.moon}<span>وضع الليل</span></button>
      </div>`;
  }

  // احتياطي عام (أي نوع آخر)
  return `<div class="r-row">${rbtn('power', 'تشغيل / إطفاء', SVG.power, 'power' + ghost('power'))}</div>`;
}

function modeLabel(m) {
  return { cool: 'تبريد ❄️', heat: 'تدفئة 🔥', fan: 'مروحة 🌀', dry: 'تجفيف 💧', auto: 'تلقائي 🔄' }[m] || m;
}

function openRemoteScreen(deviceId) {
  const device = findDevice(deviceId);
  if (!device) { toast('لم يُعثر على هذا الجهاز'); go('home', true); return; }
  currentRemoteId = device.id;
  const cat = catById(device.cat);
  $('#remote-cat-icon').textContent = cat.emoji;
  $('#remote-device-name').textContent = device.name;
  $('#remote-brand-line').textContent = `${device.brand} · ${device.model}`;
  $('#remote-body').innerHTML = renderRemoteBody(device);
  showOnly('screen-remote');
}

function refreshOpenRemote() { if (currentRemoteId) openRemoteScreen(currentRemoteId); }

function sendIrButton(btnKey, device) {
  const btn = device.buttons && device.buttons[btnKey];
  if (!btn) {
    toast('هذا الزر غير مدعوم في هذا الموديل');
    haptic(40);
    return false;
  }
  IR.transmitButton(btn);
  haptic(18);
  sound('tick');
  flashSend();
  return true;
}

function persistDeviceState(device) {
  saveDevices();
}

/* ═══════════════ الإعدادات ═══════════════ */
function applyTheme(theme) {
  document.documentElement.dataset.theme = theme === 'light' ? 'light' : 'dark';
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.content = theme === 'light' ? '#eef1ff' : '#05071a';
}
function syncSettingsUI() {
  $$('#theme-segment .seg-btn').forEach((b) => b.classList.toggle('active', b.dataset.themeVal === settings.theme));
  $$('#interval-segment .seg-btn').forEach((b) => b.classList.toggle('active', Number(b.dataset.interval) === Number(settings.searchInterval)));
  $('#toggle-sound').setAttribute('aria-checked', String(!!settings.sound));
  $('#toggle-haptic').setAttribute('aria-checked', String(!!settings.haptic));
}

/* ═══════════════ التهيئة وربط الأحداث ═══════════════ */
function init() {
  applyTheme(settings.theme);
  renderHome();
  syncSettingsUI();

  /* الافتتاحية */
  let splashDone = false;
  const finishSplash = () => {
    if (splashDone) return;
    splashDone = true;
    $('#screen-splash').classList.remove('visible');
    history.replaceState(null, '', '#/home');
    showOnly('screen-home');
    checkInfrared(false);
  };
  $('#screen-splash').addEventListener('pointerdown', finishSplash);
  setTimeout(finishSplash, 2900);
  setTimeout(() => { if (settings.sound) IR.beep(0); }, 350);

  /* الفحص عند أول تشغيل — إن وُجدت أجهزة محفوظة لا نزعج المستخدم */
  function checkInfrared(force) {
    const dismissed = sessionStorage.getItem('ir_noir_dismissed') === '1';
    if (!IR.isNative) {
      if (!dismissed || force) $('#noir-modal').hidden = false;
      return;
    }
    if (!IR.hasIrEmitter() && (!dismissed || force) && devices.length === 0) {
      $('#noir-modal').hidden = false;
    }
  }
  $('#noir-continue').addEventListener('click', () => {
    sessionStorage.setItem('ir_noir_dismissed', '1');
    $('#noir-modal').hidden = true;
    toast('وضع تجريبي — لن تُرسل أشعة فعلية');
  });
  $('#noir-recheck').addEventListener('click', () => {
    if (IR.hasIrEmitter()) { $('#noir-modal').hidden = true; toast('ممتاز! رُصد ناشر IR ✓'); }
    else toast('ما زال ناشر IR غير موجود على هذا الهاتف');
  });

  /* الرئيسية */
  $('#cats-grid').addEventListener('click', (e) => {
    const card = e.target.closest('[data-cat]');
    if (!card) return;
    haptic(16);
    sound('tick');
    go('search/' + card.dataset.cat);
  });
  $('#mydevices').addEventListener('click', (e) => {
    const chip = e.target.closest('[data-open-device]');
    if (chip) { haptic(16); go('remote/' + chip.dataset.openDevice); }
  });
  $('#btn-settings').addEventListener('click', () => { haptic(12); go('settings'); });
  $('#btn-about').addEventListener('click', () => { haptic(12); go('about'); });
  $('#btn-theme').addEventListener('click', () => {
    settings.theme = settings.theme === 'light' ? 'dark' : 'light';
    saveSettings(); applyTheme(settings.theme); syncSettingsUI(); haptic(12);
  });

  /* البحث */
  $('#search-back').addEventListener('click', () => history.back());
  $('#btn-pause').addEventListener('click', () => { haptic(12); togglePause(); });
  $('#btn-back-step').addEventListener('click', backOneStep);
  $('#btn-skip-brand').addEventListener('click', skipBrand);
  $('#btn-worked').addEventListener('click', () => {
    if (!searchState.list.length) return;
    pauseSearch();
    const idx = Math.max(0, Math.min(searchState.pointer - 1, searchState.list.length - 1));
    openConfirmModal(searchState.list[idx]);
  });
  $('#confirm-yes').addEventListener('click', () => {
    closeConfirmModal();
    const idx = Math.max(0, Math.min(searchState.pointer - 1, searchState.list.length - 1));
    const profile = searchState.list[idx];
    const cat = catById(profile.id.replace(/\d+$/, '')) || catById(searchState.catId);
    namePrompt('ممتاز! تم التعرّف على الجهاز', `${cat.name.replace('ال', '')} ${profile.brand}`, 'حفظ وفتح الريموت', (name) => {
      const device = buildSavedDevice(profile, name || `ريموت ${profile.brand}`);
      devices.unshift(device);
      saveDevices();
      renderHome();
      toast('تم حفظ الجهاز في «أجهزتي» ✓');
      sound('save');
      go('remote/' + device.id);
    });
  });
  $('#confirm-no').addEventListener('click', () => { closeConfirmModal(); startSearch(); });
  $('#btn-retry-search').addEventListener('click', () => { $('#search-fallback').hidden = true; searchState.pointer = 0; searchState.attempts = 0; startSearch(); });
  $('#btn-manual-brand').addEventListener('click', openBrandSheet);
  $('#btn-learn-note').addEventListener('click', () => {
    showInfo({
      icon: '🎓', title: 'التعلّم من ريموت أصلي',
      text: 'للأسف، نظام Android لا يتيح للتطبيقات <b>استقبال</b> إشارات الأشعة تحت الحمراء — فقط <b>إرسالها</b> عبر <code>TRANSMIT_IR</code>.<br><br>لذا فإن التعلّم المباشر غير ممكن تقنياً. البدائل:<br>• اختر الماركة يدوياً من القائمة أعلاه.<br>• جرّب «رجوع خطوة» إذا أخطأت الجهاز بالاستجابة.<br>• بعض الملحقات العتادية (USB-C IR) تدعم الاستقبال وتتعلّم عبر تطبيقها الرسمي.',
    });
  });
  $('#brand-grid').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-brand]');
    if (!btn) return;
    const brand = btn.dataset.brand;
    const all = DB_CATEGORIES()[searchState.catId] || [];
    searchState.list = all.filter((p) => p.brand === brand);
    if (!searchState.list.length) searchState.list = all.slice();
    searchState.pointer = 0;
    searchState.attempts = 0;
    $('#search-fallback').hidden = true;
    closeBrandSheet();
    renderSearchProgress();
    toast(`البحث يدور حول ماركة «${brand}»`);
    startSearch();
  });

  /* الريموت */
  $('#remote-back').addEventListener('click', () => history.back());
  $('#remote-menu').addEventListener('click', () => { $('#device-sheet').hidden = false; haptic(10); });
  $$('#device-sheet [data-close-sheet]').forEach((el) => el.addEventListener('click', () => { $('#device-sheet').hidden = true; }));
  $('#device-rename').addEventListener('click', () => {
    $('#device-sheet').hidden = true;
    const device = findDevice(currentRemoteId);
    if (!device) return;
    namePrompt('إعادة تسمية الجهاز', device.name, 'حفظ', (name) => {
      if (!name) return;
      device.name = name;
      saveDevices(); renderHome(); refreshOpenRemote();
      toast('تم تغيير الاسم');
    });
  });
  $('#device-delete').addEventListener('click', () => {
    $('#device-sheet').hidden = true;
    const device = findDevice(currentRemoteId);
    if (!device) return;
    showInfo({
      icon: '🗑️', title: 'حذف الجهاز؟', text: `سيتم حذف «<b>${escapeHtml(device.name)}</b>» مع أكواده المحفوظة نهائياً.`,
      okLabel: 'حذف نهائياً', cancelLabel: 'إلغاء',
      onOk: () => {
        devices = devices.filter((d) => d.id !== device.id);
        saveDevices(); renderHome();
        toast('تم حذف الجهاز');
        go('home', true);
      },
    });
  });
  $('#remote-body').addEventListener('click', (e) => {
    const device = findDevice(currentRemoteId);
    if (!device) return;

    // أزرار AC الخاصة بالوضع (تبريد/تدفئة/…)
    const modeEl = e.target.closest('[data-ac-mode]');
    if (modeEl && !modeEl.classList.contains('ghosted')) {
      const mode = modeEl.dataset.acMode;
      device.ac = device.ac || { temp: 24, mode: 'cool', fan: 1, on: true };
      device.ac.mode = mode;
      const btnKey = mode === 'cool' ? 'modeCool' : mode === 'heat' ? 'modeHeat' : 'mode';
      if (device.buttons[btnKey]) sendIrButton(btnKey, device);
      else if (device.buttons.mode) sendIrButton('mode', device);
      persistDeviceState(device);
      refreshOpenRemote();
      return;
    }
    // التحكم بالحرارة المعروض (لا يُرسل وحده)
    const acFanEl = e.target.closest('[data-ac-fan]');
    if (acFanEl && !acFanEl.classList.contains('ghosted')) {
      device.ac = device.ac || { temp: 24, mode: 'cool', fan: 1, on: true };
      device.ac.fan = device.ac.fan % 3 + 1;
      sendIrButton('fanSpeed', device);
      persistDeviceState(device);
      refreshOpenRemote();
      return;
    }
    // أزرار المروحة المستوى المباشر
    const fanLevelEl = e.target.closest('[data-fan-level]');
    if (fanLevelEl && !fanLevelEl.classList.contains('ghosted')) {
      sendIrButton(fanLevelEl.dataset.btn, device);
      return;
    }
    // الأزرار العادية
    const btnEl = e.target.closest('[data-btn]');
    if (!btnEl) return;
    if (btnEl.classList.contains('ghosted')) { toast('هذا الزر غير مدعوم في هذا الموديل'); haptic(40); return; }
    const key = btnEl.dataset.btn;
    const sent = sendIrButton(key, device);
    if (!sent) return;
    // تحديث حالة المكيف المعروضة
    if (device.cat === 'ac') {
      device.ac = device.ac || { temp: 24, mode: 'cool', fan: 1, on: true };
      if (key === 'tempUp') device.ac.temp = Math.min(30, device.ac.temp + 1);
      else if (key === 'tempDown') device.ac.temp = Math.max(16, device.ac.temp - 1);
      else if (key === 'power') device.ac.on = !device.ac.on;
      persistDeviceState(device);
      refreshOpenRemote();
      return;
    }
    // أشرطة السطوع الصفراء
    if (device.cat === 'light' && (key === 'brightUp' || key === 'brightDown')) {
      const bar = $('#light-bar');
      if (bar) {
        const segs = $$('.light-seg', bar);
        let on = segs.filter((s) => s.classList.contains('on')).length;
        on = Math.max(1, Math.min(10, on + (key === 'brightUp' ? 1 : -1)));
        segs.forEach((s, i) => s.classList.toggle('on', i < on));
      }
    }
  });

  /* الإعدادات */
  $('#settings-back').addEventListener('click', () => history.back());
  $('#about-back').addEventListener('click', () => history.back());
  $('#theme-segment').addEventListener('click', (e) => {
    const b = e.target.closest('[data-theme-val]'); if (!b) return;
    settings.theme = b.dataset.themeVal; saveSettings(); applyTheme(settings.theme); syncSettingsUI(); haptic(10);
  });
  $('#interval-segment').addEventListener('click', (e) => {
    const b = e.target.closest('[data-interval]'); if (!b) return;
    settings.searchInterval = Number(b.dataset.interval); saveSettings(); syncSettingsUI(); haptic(10);
    toast(`سرعة البحث: ${settings.searchInterval} مللي ثانية`);
  });
  $('#toggle-sound').addEventListener('click', () => { settings.sound = !settings.sound; saveSettings(); syncSettingsUI(); if (settings.sound) sound('tick'); });
  $('#toggle-haptic').addEventListener('click', () => { settings.haptic = !settings.haptic; saveSettings(); syncSettingsUI(); haptic(20); });
  $('#clear-devices').addEventListener('click', () => {
    if (!devices.length) { toast('لا توجد أجهزة محفوظة'); return; }
    showInfo({
      icon: '🗑️', title: 'مسح الأجهزة؟', text: `سيتم حذف جميع الأجهزة المحفوظة (${devices.length}).`,
      okLabel: 'مسح الكل', cancelLabel: 'إلغاء',
      onOk: () => { devices = []; saveDevices(); renderHome(); toast('تم مسح جميع الأجهزة'); },
    });
  });
  $('#reset-settings').addEventListener('click', () => {
    settings = Object.assign({}, DEFAULT_SETTINGS);
    saveSettings(); applyTheme(settings.theme); syncSettingsUI();
    toast('تمت إعادة ضبط الإعدادات');
  });

  /* مودالات مشتركة */
  $('#info-modal-ok').addEventListener('click', () => { const cb = infoOkCallback; closeInfo(); if (cb) cb(); });
  $('#info-modal-cancel').addEventListener('click', closeInfo);
  $('#save-modal').addEventListener('submit', (e) => e.preventDefault());
  $('#save-device-confirm').addEventListener('click', () => {
    const v = $('#device-name-input').value.trim();
    const cb = namePromptCallback;
    closeNamePrompt();
    if (cb) cb(v);
  });
  $('#save-device-cancel').addEventListener('click', closeNamePrompt);
  $$('.modal [data-close-sheet], #device-sheet [data-close-sheet], .sheet [data-close-sheet]').forEach((el) => el.addEventListener('click', () => { $('#brand-sheet').hidden = true; }));

  /* إغلاق الشيتات والمودالات بالنقر على الخلفية */
  $$('#brand-sheet .sheet-backdrop').forEach((el) => el.addEventListener('click', closeBrandSheet));
  $('#confirm-modal .modal-backdrop').addEventListener('click', () => { closeConfirmModal(); startSearch(); });
  $('#info-modal .modal-backdrop').addEventListener('click', closeInfo);
  $('#noir-modal .modal-backdrop').addEventListener('click', () => { sessionStorage.setItem('ir_noir_dismissed', '1'); $('#noir-modal').hidden = true; });

  renderRoute();
}

document.addEventListener('DOMContentLoaded', init);
