/* QnS — تطبيق إدارة الإيداع والسحب
 * تطوير وتنفيذ: أمجد حبيب
 * يعمل بدون إنترنت بالكامل. كل البيانات مخزّنة محلياً على الجهاز. */
(function () {
'use strict';

/* ================= أدوات عامة ================= */
const $ = (s, r = document) => r.querySelector(s);
const cap = window.Capacitor;
const isNative = !!(cap && cap.isNativePlatform && cap.isNativePlatform());
const N = (plugin, method, opts) => isNative ? cap.nativePromise(plugin, method, opts || {}) : Promise.reject(new Error('web'));
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
const CUR = 'ل.س';
const num = n => Math.round(n).toLocaleString('en-US');
const money = n => num(n) + ' ' + CUR;
const moneyH = n => `<span class="num">${num(n)}</span> ${CUR}`;
const toLatin = s => String(s).replace(/[٠-٩]/g, d => '٠١٢٣٤٥٦٧٨٩'.indexOf(d)).replace(/[۰-۹]/g, d => '۰۱۲۳۴۵۶۷۸۹'.indexOf(d));
const parseAmount = s => { const d = toLatin(s).replace(/[^\d]/g, '').slice(0, 15); return d ? parseInt(d, 10) : 0; };
const parseDec = s => { const v = parseFloat(toLatin(s).replace(/,/g, '').replace(/[^\d.]/g, '')); return isFinite(v) ? v : 0; };
const pad2 = n => String(n).padStart(2, '0');
const iso = d => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
const today = () => iso(new Date());
const MONTHS = ['كانون الثاني', 'شباط', 'آذار', 'نيسان', 'أيار', 'حزيران', 'تموز', 'آب', 'أيلول', 'تشرين الأول', 'تشرين الثاني', 'كانون الأول'];
const MSHORT = ['ك2', 'شباط', 'آذار', 'نيسان', 'أيار', 'حزيران', 'تموز', 'آب', 'أيلول', 'ت1', 'ت2', 'ك1'];
const DAYS = ['الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
const pDate = s => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const dateAr = s => { const d = pDate(s); return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`; };
const dateDay = s => {
  if (s === today()) return 'اليوم';
  const y = new Date(); y.setDate(y.getDate() - 1);
  if (s === iso(y)) return 'أمس';
  return DAYS[pDate(s).getDay()] + '، ' + dateAr(s);
};
const stamp = t => { const d = new Date(t); return `${dateAr(iso(d))} — ${pad2(d.getHours())}:${pad2(d.getMinutes())}`; };
const TYPE = { dep: 'إيداع', wd: 'سحب' };

const I = {
  home: '<path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13"/><circle cx="3.5" cy="6" r="1"/><circle cx="3.5" cy="12" r="1"/><circle cx="3.5" cy="18" r="1"/>',
  chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-2.8 1.2V21a2 2 0 11-4 0v-.1a1.7 1.7 0 00-2.9-1.2l-.1.1a2 2 0 11-2.8-2.8l.1-.1A1.7 1.7 0 003 14H3a2 2 0 110-4h.1a1.7 1.7 0 001.2-2.9l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.9.3H9a1.7 1.7 0 001-1.5V3a2 2 0 114 0v.1a1.7 1.7 0 002.9 1.2l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.9V9a1.7 1.7 0 001.5 1H21a2 2 0 110 4h-.1a1.7 1.7 0 00-1.5 1z"/>',
  down: '<path d="M12 4v14M6 12l6 6 6-6"/>', up: '<path d="M12 20V6M6 12l6-6 6 6"/>',
  wallet: '<path d="M3 7a2 2 0 012-2h13v4"/><path d="M3 7v10a2 2 0 002 2h15V9H5a2 2 0 01-2-2z"/><circle cx="16.5" cy="14" r="1"/>',
  x: '<path d="M6 6l12 12M18 6L6 18"/>', search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.5-4.5"/>',
  warn: '<path d="M12 3l10 18H2z"/><path d="M12 10v5M12 18v.5"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/>', trash: '<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/>',
  plus: '<path d="M12 5v14M5 12h14"/>', check: '<path d="M5 12l5 5 9-10"/>',
  moon: '<path d="M21 13A9 9 0 1111 3a7 7 0 0010 10z"/>', sound: '<path d="M4 9v6h4l5 4V5L8 9z"/><path d="M16 9a4 4 0 010 6M19 6a8 8 0 010 12"/>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 018 0v4"/>',
  finger: '<path d="M12 11v4a6 6 0 01-1 3M8 18a10 10 0 001-4v-3a3 3 0 016 0v2a12 12 0 01-1 5M5 15a14 14 0 000-4 7 7 0 0114 0v3"/>',
  tag: '<path d="M3 12V3h9l9 9-9 9z"/><circle cx="8" cy="8" r="1.4"/>', target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
  dollar: '<path d="M12 2v20M17 6.5C17 4.6 14.8 4 12 4S7 5 7 7.5 9.5 11 12 12s5 1.5 5 4.5-2.2 3.5-5 3.5-5-.6-5-2.5"/>',
  bell: '<path d="M6 16V11a6 6 0 0112 0v5l2 2H4z"/><path d="M10 21h4"/>',
  save: '<path d="M12 3v12M7 10l5 5 5-5M4 21h16"/>', restore: '<path d="M12 21V9M7 14l5-5 5 5M4 3h16"/>',
  share: '<circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="5" r="2.5"/><circle cx="18" cy="19" r="2.5"/><path d="M8 11l8-5M8 13l8 5"/>',
  prev: '<path d="M9 5l7 7-7 7"/>', next: '<path d="M15 5l-7 7 7 7"/>', back: '<path d="M20 5H9l-6 7 6 7h11zM17 9l-5 6M12 9l5 6"/>',
  empty: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h4"/>', chev: '<path d="M6 9l6 6 6-6"/>',
  file: '<path d="M6 2h9l5 5v15H6z"/><path d="M14 2v6h6"/>', clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'
};
const ic = n => `<svg viewBox="0 0 24 24">${I[n] || ''}</svg>`;

/* ================= الحالة والتخزين ================= */
const KEY = 'qns.data.v1';
const fresh = () => ({
  v: 1,
  wallets: [{ id: 'w1', name: 'المحفظة الرئيسية', goal: null }],
  active: 'w1',
  tx: [],
  cats: { dep: ['مبيعات', 'راتب', 'تحويل وارد', 'أخرى'], wd: ['مصاريف', 'سلفة', 'مشتريات', 'فواتير', 'أخرى'] },
  settings: { theme: 'dark', sound: true, volume: 0.7, pin: null, bio: false, usd: false, rate: 0, remind: false, remindTime: '21:00' },
  log: []
});
let S = fresh();
function normalize(d) {
  const f = fresh();
  if (!d || typeof d !== 'object' || !Array.isArray(d.tx) || !Array.isArray(d.wallets) || !d.wallets.length) return null;
  const o = Object.assign(f, d);
  o.settings = Object.assign(fresh().settings, d.settings || {});
  o.cats = { dep: (d.cats && d.cats.dep) || f.cats.dep, wd: (d.cats && d.cats.wd) || f.cats.wd };
  o.tx = d.tx.filter(t => t && (t.type === 'dep' || t.type === 'wd') && t.amount > 0 && /^\d{4}-\d\d-\d\d$/.test(t.date || ''));
  if (!o.wallets.some(w => w.id === o.active)) o.active = o.wallets[0].id;
  o.log = Array.isArray(d.log) ? d.log : [];
  return o;
}
let mirrorT = 0;
function save() {
  const json = JSON.stringify(S);
  try { localStorage.setItem(KEY, json); } catch (e) { toast('تعذّر الحفظ: مساحة التخزين ممتلئة'); }
  if (isNative) { clearTimeout(mirrorT); mirrorT = setTimeout(() => N('Preferences', 'set', { key: KEY, value: json }).catch(() => {}), 400); }
}
async function load() {
  let raw = null;
  try { raw = localStorage.getItem(KEY); } catch (e) {}
  if (!raw && isNative) { try { raw = (await N('Preferences', 'get', { key: KEY })).value; } catch (e) {} }
  if (raw) { try { const n = normalize(JSON.parse(raw)); if (n) S = n; } catch (e) {} }
}
const addLog = text => { S.log.unshift({ at: Date.now(), text }); if (S.log.length > 300) S.log.length = 300; };

/* ================= الحسابات ================= */
const wallet = () => S.wallets.find(w => w.id === S.active) || S.wallets[0];
const wtx = (wid = S.active) => S.tx.filter(t => t.wallet === wid);
function totals(list) {
  let dep = 0, wd = 0;
  for (const t of list) t.type === 'dep' ? dep += t.amount : wd += t.amount;
  return { dep, wd, net: dep - wd };
}
const balance = (wid = S.active, exceptId) => totals(wtx(wid).filter(t => t.id !== exceptId)).net;
const sorted = list => list.slice().sort((a, b) => a.date < b.date ? 1 : a.date > b.date ? -1 : (b.at || 0) - (a.at || 0));

/* ================= الصوتيات ================= */
const Snd = {
  ctx: null,
  ac() {
    try {
      if (!this.ctx) { const C = window.AudioContext || window.webkitAudioContext; if (!C) return null; this.ctx = new C(); }
      if (this.ctx.state === 'suspended') this.ctx.resume();
      return this.ctx;
    } catch (e) { return null; }
  },
  tone(f, at, dur, type, gain, f2) {
    const c = this.ctx, o = c.createOscillator(), g = c.createGain(), t = c.currentTime + at;
    o.type = type || 'sine'; o.frequency.setValueAtTime(f, t);
    if (f2) o.frequency.exponentialRampToValueAtTime(f2, t + dur);
    const v = (gain || 0.2) * S.settings.volume;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(Math.max(v, 0.0002), t + Math.min(0.03, dur / 3));
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(c.destination); o.start(t); o.stop(t + dur + 0.05);
  },
  pad(f, at, dur, gain) {
    const c = this.ctx, o = c.createOscillator(), g = c.createGain(), t = c.currentTime + at;
    o.type = 'sine'; o.frequency.value = f;
    const v = gain * S.settings.volume;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.linearRampToValueAtTime(v, t + dur * 0.7);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(c.destination); o.start(t); o.stop(t + dur + 0.05);
  },
  play(name, force) {
    if ((!S.settings.sound && !force) || S.settings.volume <= 0) return;
    if (!this.ac()) return;
    try {
      if (name === 'dep') { // مبهجة وصاعدة
        [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => { this.tone(f, i * 0.085, 0.42, 'triangle', 0.26); this.tone(f * 2, i * 0.085, 0.3, 'sine', 0.07); });
        this.tone(1318.5, 0.36, 0.7, 'sine', 0.12);
      } else if (name === 'wd') { // هادئة ونازلة
        [659.25, 523.25, 392].forEach((f, i) => this.tone(f, i * 0.15, 0.55, 'sine', 0.24));
        this.tone(196, 0.3, 0.7, 'sine', 0.12);
      } else if (name === 'intro') { // افتتاحية الشعار
        [130.81, 196, 261.63, 329.63].forEach((f, i) => this.pad(f, 0.05 * i, 1.75, 0.09));
        this.tone(400, 0.2, 1.35, 'sine', 0.04, 1600);
        [1046.5, 1318.5, 1568, 2093].forEach((f, i) => this.tone(f, 1.55 + i * 0.07, 1.1, 'sine', 0.14));
        this.tone(523.25, 1.55, 1.3, 'triangle', 0.12);
      } else if (name === 'err') {
        this.tone(220, 0, 0.18, 'square', 0.07); this.tone(180, 0.12, 0.25, 'square', 0.07);
      } else if (name === 'tap') {
        this.tone(880, 0, 0.06, 'sine', 0.06);
      }
    } catch (e) {}
  }
};

/* ================= واجهات مساعدة ================= */
let toastT = 0;
function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.hidden = false;
  clearTimeout(toastT); toastT = setTimeout(() => t.hidden = true, 2600);
}
let dialogResolve = null;
function dialog({ title, msg, ok = 'موافق', cancel = 'إلغاء', danger, input }) {
  return new Promise(res => {
    dialogResolve = res;
    $('#dialog').innerHTML = `<h3>${esc(title)}</h3>${msg ? `<p>${esc(msg)}</p>` : '<p></p>'}
      ${input ? `<div class="field"><input class="input" id="dlgInput" maxlength="40" value="${esc(input.value || '')}" placeholder="${esc(input.placeholder || '')}"></div>` : ''}
      <div class="row">${cancel ? `<button class="btn sm ghost" data-act="dlg" data-v="0">${esc(cancel)}</button>` : ''}
      <button class="btn sm ${danger ? 'danger' : 'gold'}" data-act="dlg" data-v="1">${esc(ok)}</button></div>`;
    $('#dialogWrap').hidden = false;
    if (input) setTimeout(() => { const i = $('#dlgInput'); if (i) i.focus(); }, 50);
  });
}
function closeDialog(v) {
  const i = $('#dlgInput'), r = dialogResolve; dialogResolve = null;
  $('#dialogWrap').hidden = true;
  if (r) r(v ? (i ? i.value.trim() : true) : (i ? null : false));
}
const confirmDlg = (title, msg, ok, danger) => dialog({ title, msg, ok, danger });
const promptDlg = (title, value, placeholder) => dialog({ title, input: { value, placeholder }, ok: 'حفظ' });

let sheetName = null;
function openSheet(name, html) {
  sheetName = name;
  const s = $('#sheet'); s.innerHTML = html; s.scrollTop = 0; s.classList.remove('kb');
  $('#sheetWrap').hidden = false;
}
function closeSheet() { sheetName = null; $('#sheetWrap').hidden = true; $('#sheet').innerHTML = ''; }
const sheetTitle = t => `<div class="sheet-title"><span>${t}</span><button class="x" data-act="closeSheet" aria-label="إغلاق">${ic('x')}</button></div>`;

function applyTheme() {
  const th = S.settings.theme;
  document.documentElement.dataset.theme = th;
  const m = $('meta[name=theme-color]'); if (m) m.content = th === 'dark' ? '#070E22' : '#F3F5FB';
  N('SystemBars', 'setStyle', { style: th === 'dark' ? 'DARK' : 'LIGHT' }).catch(() => {});
}

/* ================= العرض ================= */
const UI = { view: 'home', period: 'all', ftype: 'all', q: '', month: today().slice(0, 7), scope: 'month', catType: 'wd' };

function txRow(t) {
  const title = t.note || t.cat || TYPE[t.type];
  const sub = [TYPE[t.type], t.note && t.cat ? t.cat : '', dateAr(t.date)].filter(Boolean).join(' • ');
  return `<button class="tx ${t.type}" data-act="showTx" data-id="${t.id}">
    <span class="ico">${ic(t.type === 'dep' ? 'down' : 'up')}</span>
    <span class="mid"><div class="t">${esc(title)}</div><div class="s">${esc(sub)}</div></span>
    <span class="a">${t.type === 'dep' ? '+' : '−'} ${moneyH(t.amount)}</span></button>`;
}
const emptyBox = msg => `<div class="empty">${ic('empty')}<div>${msg}</div></div>`;

function renderTop() {
  $('#topbar').innerHTML = `<span class="brand">QnS</span>
    <button class="wallet-btn" data-act="wallets">${ic('wallet')}<span>${esc(wallet().name)}</span>${ic('chev')}</button>`;
  $('#nav').innerHTML = [['home', 'الرئيسية', 'home'], ['history', 'السجل', 'list'], ['reports', 'التقارير', 'chart'], ['settings', 'الإعدادات', 'gear']]
    .map(([v, l, i]) => `<button class="${UI.view === v ? 'on' : ''}" data-act="nav" data-v="${v}">${ic(i)}<span>${l}</span></button>`).join('');
}

function viewHome() {
  const all = wtx(), t = totals(all), w = wallet(), st = S.settings;
  const usd = st.usd && st.rate > 0 ? `<div class="usd">≈ <span class="num">$${(t.net / st.rate).toLocaleString('en-US', { maximumFractionDigits: 2 })}</span> <span class="small">(سعر الصرف ${num(st.rate)})</span></div>` : '';
  let goal = '';
  if (w.goal && w.goal.target > 0) {
    const p = Math.max(0, Math.min(100, t.net / w.goal.target * 100));
    goal = `<div class="card goal" data-act="goal"><div class="top"><span>${ic('target')} ${esc(w.goal.name || 'هدف الادخار')}</span><span class="num">${p.toFixed(0)}%</span></div>
      <div class="bar"><i style="width:${p}%"></i></div>
      <div class="small muted">${p >= 100 ? '🎉 تم بلوغ الهدف!' : `المتبقي ${moneyH(w.goal.target - t.net)} من ${moneyH(w.goal.target)}`}</div></div>`;
  }
  const recent = sorted(all).slice(0, 5);
  return `<div class="balance"><div class="lbl">الرصيد الحالي • ${esc(w.name)}</div>
      <div class="amt"><span class="num">${num(t.net)}</span><span class="cur">${CUR}</span></div>${usd}</div>
    <div class="stats">
      <div class="stat dep"><div class="ico">${ic('down')}</div><div class="small muted">إجمالي الإيداع</div><div class="v">${moneyH(t.dep)}</div></div>
      <div class="stat wd"><div class="ico">${ic('up')}</div><div class="small muted">إجمالي السحب</div><div class="v">${moneyH(t.wd)}</div></div>
    </div>
    <div class="actions"><button class="btn dep" data-act="newTx" data-type="dep">${ic('down')} إيداع</button>
      <button class="btn wd" data-act="newTx" data-type="wd">${ic('up')} سحب</button></div>
    ${goal ? '<div style="height:14px"></div>' + goal : ''}
    <div class="h"><span>آخر العمليات</span>${all.length ? '<button data-act="nav" data-v="history">عرض الكل</button>' : ''}</div>
    ${recent.length ? recent.map(txRow).join('') : emptyBox('لا توجد عمليات بعد.<br>ابدأ بتسجيل أول إيداع.')}`;
}

function filtered() {
  const now = new Date(), td = today();
  const ws = new Date(now); ws.setDate(now.getDate() - ((now.getDay() + 1) % 7)); // الأسبوع يبدأ السبت
  const wk = iso(ws), mo = td.slice(0, 7), q = UI.q.trim().toLowerCase(), qn = toLatin(q).replace(/[,\s]/g, '');
  return sorted(wtx().filter(t => {
    if (UI.ftype !== 'all' && t.type !== UI.ftype) return false;
    if (UI.period === 'day' && t.date !== td) return false;
    if (UI.period === 'week' && (t.date < wk || t.date > td)) return false;
    if (UI.period === 'month' && t.date.slice(0, 7) !== mo) return false;
    if (q) {
      const hay = [t.note, t.cat, TYPE[t.type], dateAr(t.date), t.date].join(' ').toLowerCase();
      if (!hay.includes(q) && !(qn && /^\d+$/.test(qn) && String(t.amount).includes(qn))) return false;
    }
    return true;
  }));
}
function historyList() {
  const list = filtered();
  if (!list.length) return emptyBox('لا توجد عمليات مطابقة.');
  const t = totals(list);
  let out = `<div class="card small" style="display:flex;justify-content:space-between;padding:10px 14px">
    <span>${list.length} عملية</span><span style="color:var(--green)">+ ${moneyH(t.dep)}</span><span style="color:var(--red)">− ${moneyH(t.wd)}</span></div>`;
  let cur = '';
  for (const x of list) {
    if (x.date !== cur) { cur = x.date; out += `<div class="day"><span>${dateDay(cur)}</span></div>`; }
    out += txRow(x);
  }
  return out;
}
function viewHistory() {
  const chip = (g, v, l, cls = '') => `<button class="chip ${cls} ${UI[g] === v ? 'on' : ''}" data-act="filter" data-g="${g}" data-v="${v}">${l}</button>`;
  return `<div class="search">${ic('search')}<input class="input" id="q" type="search" placeholder="ابحث بالملاحظة، التصنيف أو المبلغ…" value="${esc(UI.q)}"></div>
    <div class="chips">${chip('period', 'all', 'الكل')}${chip('period', 'day', 'اليوم')}${chip('period', 'week', 'هذا الأسبوع')}${chip('period', 'month', 'هذا الشهر')}</div>
    <div class="chips">${chip('ftype', 'all', 'كل الأنواع')}${chip('ftype', 'dep', 'الإيداعات', 'g')}${chip('ftype', 'wd', 'المسحوبات', 'r')}</div>
    <div id="hlist">${historyList()}</div>`;
}

const monthLabel = m => { const [y, mm] = m.split('-').map(Number); return `${MONTHS[mm - 1]} ${y}`; };
const shiftMonth = (m, k) => { const [y, mm] = m.split('-').map(Number); const d = new Date(y, mm - 1 + k, 1); return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`; };
function viewReports() {
  const all = wtx();
  const months = []; for (let i = 5; i >= 0; i--) months.push(shiftMonth(UI.month, -i));
  const data = months.map(m => totals(all.filter(t => t.date.slice(0, 7) === m)));
  const max = Math.max(1, ...data.map(d => Math.max(d.dep, d.wd)));
  const cur = data[5];
  const chart = months.map((m, i) => `<div class="col ${i === 5 ? 'on' : ''}" data-act="setMonth" data-v="${m}"><div class="bars">
      <i class="d" style="height:${data[i].dep / max * 100}%"></i><i class="w" style="height:${data[i].wd / max * 100}%"></i></div>
      <div class="m">${MSHORT[+m.slice(5) - 1]}</div></div>`).join('');
  const mtx = all.filter(t => t.date.slice(0, 7) === UI.month && t.type === UI.catType);
  const byCat = {}; mtx.forEach(t => { const c = t.cat || 'بدون تصنيف'; byCat[c] = (byCat[c] || 0) + t.amount; });
  const catTotal = mtx.reduce((s, t) => s + t.amount, 0) || 1;
  const color = UI.catType === 'dep' ? 'var(--green)' : 'var(--red)';
  const cats = Object.entries(byCat).sort((a, b) => b[1] - a[1]).map(([c, v]) => `<div class="catrow"><div class="top"><span>${esc(c)}</span>
      <span>${moneyH(v)} <span class="muted small num">${(v / catTotal * 100).toFixed(0)}%</span></span></div>
      <div class="bar"><i style="width:${v / catTotal * 100}%;background:${color}"></i></div></div>`).join('');
  return `<div class="card"><div class="monthnav"><button data-act="month" data-k="-1" aria-label="السابق">${ic('prev')}</button><b>${monthLabel(UI.month)}</b>
      <button data-act="month" data-k="1" aria-label="التالي">${ic('next')}</button></div>
      <div class="chart">${chart}</div>
      <div class="legend"><span><i style="background:#1FB57A"></i>إيداع</span><span><i style="background:#E5484D"></i>سحب</span></div></div>
    <div class="stats">
      <div class="stat dep"><div class="small muted">إيداعات الشهر</div><div class="v">${moneyH(cur.dep)}</div></div>
      <div class="stat wd"><div class="small muted">مسحوبات الشهر</div><div class="v">${moneyH(cur.wd)}</div></div></div>
    <div class="card" style="display:flex;justify-content:space-between;font-weight:700"><span>صافي الشهر</span>
      <span style="color:${cur.net >= 0 ? 'var(--green)' : 'var(--red)'}">${cur.net < 0 ? '−' : '+'} ${moneyH(Math.abs(cur.net))}</span></div>
    <div class="h"><span>حسب التصنيف</span></div>
    <div class="card"><div class="seg"><button class="${UI.catType === 'wd' ? 'on' : ''}" data-act="catType" data-v="wd">المسحوبات</button>
      <button class="${UI.catType === 'dep' ? 'on' : ''}" data-act="catType" data-v="dep">الإيداعات</button></div>
      ${cats || '<div class="muted small" style="text-align:center;padding:10px">لا توجد بيانات لهذا الشهر</div>'}</div>
    <div class="h"><span>تصدير ومشاركة التقرير</span></div>
    <div class="card"><div class="seg"><button class="${UI.scope === 'month' ? 'on' : ''}" data-act="scope" data-v="month">${monthLabel(UI.month)}</button>
      <button class="${UI.scope === 'all' ? 'on' : ''}" data-act="scope" data-v="all">كل العمليات</button></div>
      <div class="row"><button class="btn sm gold" data-act="pdf">${ic('file')} PDF</button><button class="btn sm ghost" data-act="xlsx">${ic('share')} Excel</button></div>
      <div class="small muted" style="margin-top:8px;text-align:center">يمكنك مشاركة الملف مباشرةً عبر واتساب أو حفظه.</div></div>
    <div class="h"><span>سجل التعديلات</span></div>
    <div class="card">${S.log.length ? S.log.slice(0, 25).map(l => `<div class="logrow">${esc(l.text)}<time>${stamp(l.at)}</time></div>`).join('') : '<div class="muted small" style="text-align:center;padding:10px">لا توجد تعديلات مسجّلة</div>'}</div>`;
}

function viewSettings() {
  const st = S.settings, w = wallet();
  const sw = (on, act) => `<span class="switch ${on ? 'on' : ''}" data-act="${act}" role="switch" aria-checked="${!!on}"></span>`;
  const row = (icon, title, sub, right, act) => `<${act ? 'button' : 'div'} class="set" ${act ? `data-act="${act}"` : ''}><span class="ico">${ic(icon)}</span><span class="mid"><b>${title}</b><span>${sub}</span></span>${right || ''}</${act ? 'button' : 'div'}>`;
  return `<div class="h"><span>المظهر والصوت</span></div><div class="card">
      ${row('moon', 'الوضع الداكن', st.theme === 'dark' ? 'مفعّل' : 'الوضع الفاتح مفعّل', sw(st.theme === 'dark', 'theme'))}
      ${row('sound', 'الأصوات', st.sound ? 'نغمات الإيداع والسحب والافتتاح' : 'مكتومة', sw(st.sound, 'sound'))}
      <div class="set" style="display:block"><div class="small muted" style="margin-bottom:4px">مستوى الصوت: <span id="volLbl" class="num">${Math.round(st.volume * 100)}%</span></div>
        <input type="range" id="vol" min="0" max="100" value="${Math.round(st.volume * 100)}" ${st.sound ? '' : 'disabled'}>
        <div class="row" style="margin-top:8px"><button class="btn sm ghost" data-act="testSnd" data-v="dep">نغمة الإيداع</button><button class="btn sm ghost" data-act="testSnd" data-v="wd">نغمة السحب</button><button class="btn sm ghost" data-act="testSnd" data-v="intro">الافتتاحية</button></div></div>
    </div>
    <div class="h"><span>الأمان</span></div><div class="card">
      ${row('lock', 'قفل برقم سري', st.pin ? 'مفعّل — اضغط للتغيير أو الإلغاء' : 'غير مفعّل', sw(!!st.pin, 'pin'))}
      ${row('finger', 'فتح ببصمة الإصبع', st.pin ? (st.bio ? 'مفعّل' : 'غير مفعّل') : 'فعّل الرقم السري أولاً', sw(st.bio, 'bio'))}
    </div>
    <div class="h"><span>المحافظ والتنظيم</span></div><div class="card">
      ${row('wallet', 'المحافظ', `${S.wallets.length} محفظة — الحالية: ${esc(w.name)}`, ic('next'), 'wallets')}
      ${row('tag', 'التصنيفات', 'مبيعات، مصاريف، سلفة…', ic('next'), 'cats')}
      ${row('target', 'هدف الادخار', w.goal ? `${esc(w.goal.name || 'هدف')} — ${money(w.goal.target)}` : 'حدّد هدفاً لهذه المحفظة', ic('next'), 'goal')}
    </div>
    <div class="h"><span>العملة الثانوية</span></div><div class="card">
      ${row('dollar', 'إظهار المعادل بالدولار', st.usd ? 'مفعّل' : 'غير مفعّل', sw(st.usd, 'usd'))}
      ${row('edit', 'سعر الصرف', st.rate > 0 ? `1$ = ${money(st.rate)}` : 'لم يُحدَّد بعد', ic('next'), 'rate')}
    </div>
    <div class="h"><span>التذكير اليومي</span></div><div class="card">
      ${row('bell', 'تذكير بتسجيل العمليات', st.remind ? `يومياً الساعة ${st.remindTime}` : 'غير مفعّل', sw(st.remind, 'remind'))}
      <div class="set"><span class="ico">${ic('clock')}</span><span class="mid"><b>وقت التذكير</b><span>اختر الوقت المناسب</span></span>
        <input type="time" id="remTime" class="input" style="width:130px;min-height:42px" value="${esc(st.remindTime)}"></div>
    </div>
    <div class="h"><span>النسخ الاحتياطي</span></div><div class="card">
      ${row('save', 'حفظ نسخة على الهاتف', 'ملف في مجلد المستندات (Documents)', ic('next'), 'backupSave')}
      ${row('share', 'مشاركة نسخة احتياطية', 'Google Drive، واتساب، بريد…', ic('next'), 'backupShare')}
      ${row('restore', 'استعادة من ملف', 'استرجاع البيانات من نسخة سابقة', ic('next'), 'restore')}
    </div>
    <div class="about"><span class="brand">QnS</span>تطبيق إدارة الإيداع والسحب • الإصدار 1.0.0<br>تطوير وتنفيذ: <strong>أمجد حبيب</strong><br>
      <span class="small">يعمل بدون إنترنت — بياناتك محفوظة على جهازك فقط</span></div>`;
}

function render() {
  renderTop();
  const v = $('#view');
  const keep = v.dataset.v === UI.view ? v.scrollTop : 0;
  v.dataset.v = UI.view;
  v.innerHTML = ({ home: viewHome, history: viewHistory, reports: viewReports, settings: viewSettings }[UI.view])();
  v.scrollTop = keep;
}

/* ================= نموذج العملية ================= */
let form = null;
function txSheet(type, t) {
  form = { id: t ? t.id : null, type, amount: t ? t.amount : 0, cat: t ? (t.cat || '') : '', base: balance(S.active, t ? t.id : undefined) };
  const cats = S.cats[type];
  openSheet('tx', `${sheetTitle(`${t ? 'تعديل' : 'تسجيل'} ${TYPE[type]}`)}
    <div class="field"><label>المبلغ (${CUR})</label>
      <input class="input big" id="fAmount" inputmode="numeric" autocomplete="off" placeholder="0" value="${t ? num(t.amount) : ''}"></div>
    <div id="fPreview"></div>
    <div class="field"><label>التاريخ</label><input class="input" id="fDate" type="date" value="${t ? t.date : today()}" max="2099-12-31"></div>
    <div class="field"><label>التصنيف (اختياري)</label><div class="chips wrap" id="fCats">${cats.map(c => `<button class="chip ${form.cat === c ? 'on' : ''}" data-act="pickCat" data-v="${esc(c)}">${esc(c)}</button>`).join('')}</div></div>
    <div class="field"><label>ملاحظة (اختياري)</label><input class="input" id="fNote" maxlength="120" placeholder="مثال: مبيعات اليوم" value="${t ? esc(t.note || '') : ''}"></div>
    <button class="btn ${type}" id="fSave" data-act="saveTx">${ic('check')} ${t ? 'حفظ التعديل' : 'تأكيد ' + TYPE[type]}</button>`);
  updatePreview();
  if (!t) setTimeout(() => { const a = $('#fAmount'); if (a) a.focus(); }, 320);
}
function updatePreview() {
  if (!form) return;
  const after = form.base + (form.type === 'dep' ? form.amount : -form.amount);
  const bad = after < 0;
  $('#fPreview').innerHTML = `<div class="preview ${bad ? 'bad' : ''}">
      <div><span class="muted">الرصيد الحالي</span><span>${moneyH(form.base)}</span></div>
      <div><span>${form.type === 'wd' ? 'الصافي المتبقي بعد السحب' : 'الرصيد بعد الإيداع'}</span><span class="net">${after < 0 ? '−' : ''} ${moneyH(Math.abs(after))}</span></div>
      ${bad ? `<div class="warn">${ic('warn')}<span>${form.type === 'wd' ? 'المبلغ أكبر من الرصيد المتاح! لا يمكن إتمام السحب.' : 'هذا التعديل يجعل الرصيد سالباً بسبب مسحوبات لاحقة.'}</span></div>` : ''}</div>`;
  $('#fSave').disabled = bad || form.amount <= 0;
}
function saveTx() {
  if (!form) return;
  const amount = form.amount, date = $('#fDate').value || today(), note = $('#fNote').value.trim(), cat = form.cat;
  const after = form.base + (form.type === 'dep' ? amount : -amount);
  if (amount <= 0) return toast('أدخل مبلغاً صحيحاً');
  if (after < 0) { Snd.play('err'); return toast('المبلغ أكبر من الرصيد المتاح'); }
  if (!/^\d{4}-\d\d-\d\d$/.test(date)) return toast('اختر تاريخاً صحيحاً');
  if (form.id) {
    const t = S.tx.find(x => x.id === form.id); if (!t) return;
    const ch = [];
    if (t.amount !== amount) ch.push(`المبلغ: ${money(t.amount)} ← ${money(amount)}`);
    if (t.date !== date) ch.push(`التاريخ: ${dateAr(t.date)} ← ${dateAr(date)}`);
    if ((t.cat || '') !== cat) ch.push(`التصنيف: ${t.cat || '—'} ← ${cat || '—'}`);
    if ((t.note || '') !== note) ch.push(`الملاحظة: ${t.note || '—'} ← ${note || '—'}`);
    if (!ch.length) { closeSheet(); return; }
    (t.history = t.history || []).push({ at: Date.now(), changes: ch });
    Object.assign(t, { amount, date, note, cat });
    addLog(`تعديل ${TYPE[t.type]} (${wallet().name}) — ${ch.join('، ')}`);
    toast('تم حفظ التعديل');
  } else {
    S.tx.push({ id: uid(), wallet: S.active, type: form.type, amount, date, note, cat, at: Date.now() });
    Snd.play(form.type);
    toast(form.type === 'dep' ? `تم إيداع ${money(amount)}` : `تم سحب ${money(amount)}`);
  }
  save(); form = null; closeSheet(); render();
}
function showTx(id) {
  const t = S.tx.find(x => x.id === id); if (!t) return;
  const hist = (t.history || []).slice().reverse().map(h => `<div class="logrow">${h.changes.map(esc).join('<br>')}<time>${stamp(h.at)}</time></div>`).join('');
  openSheet('detail', `${sheetTitle('تفاصيل العملية')}
    <div style="text-align:center;margin-bottom:10px"><div class="tx ${t.type}" style="display:inline-flex;border:0;background:none;padding:0;width:auto"><span class="ico" style="width:54px;height:54px;border-radius:18px">${ic(t.type === 'dep' ? 'down' : 'up')}</span></div>
      <div style="font-size:26px;font-weight:800;color:var(--${t.type === 'dep' ? 'green' : 'red'})">${t.type === 'dep' ? '+' : '−'} ${moneyH(t.amount)}</div></div>
    <div class="card" style="padding:4px 16px">
      <div class="kv"><span>النوع</span><span>${TYPE[t.type]}</span></div>
      <div class="kv"><span>التاريخ</span><span>${dateAr(t.date)}</span></div>
      <div class="kv"><span>المحفظة</span><span>${esc((S.wallets.find(w => w.id === t.wallet) || {}).name || '')}</span></div>
      <div class="kv"><span>التصنيف</span><span>${esc(t.cat || '—')}</span></div>
      <div class="kv" style="border:0"><span>الملاحظة</span><span>${esc(t.note || '—')}</span></div></div>
    ${hist ? `<div class="h"><span>سجل تعديلات هذه العملية</span></div><div class="card">${hist}</div>` : ''}
    <div class="row"><button class="btn sm ghost" data-act="editTx" data-id="${t.id}">${ic('edit')} تعديل</button>
      <button class="btn sm danger" data-act="delTx" data-id="${t.id}">${ic('trash')} حذف</button></div>`);
}
async function delTx(id) {
  const t = S.tx.find(x => x.id === id); if (!t) return;
  if (t.type === 'dep' && balance(t.wallet, id) < 0) {
    Snd.play('err');
    return dialog({ title: 'لا يمكن الحذف', msg: 'حذف هذا الإيداع يجعل الرصيد سالباً. احذف أو عدّل المسحوبات المرتبطة أولاً.', cancel: '' });
  }
  const ok = await confirmDlg('تأكيد الحذف', `هل تريد فعلاً حذف ${TYPE[t.type]} بقيمة ${money(t.amount)} بتاريخ ${dateAr(t.date)}؟ لا يمكن التراجع.`, 'نعم، احذف', true);
  if (!ok) return;
  S.tx = S.tx.filter(x => x.id !== id);
  addLog(`حذف ${TYPE[t.type]} (${wallet().name}) بقيمة ${money(t.amount)} بتاريخ ${dateAr(t.date)}${t.note ? ' — ' + t.note : ''}`);
  save(); closeSheet(); render(); toast('تم حذف العملية');
}

/* ================= المحافظ / التصنيفات / الهدف ================= */
function walletsSheet() {
  openSheet('wallets', `${sheetTitle('المحافظ')}
    ${S.wallets.map(w => `<div class="item ${w.id === S.active ? 'on' : ''}"><button class="mid" data-act="pickWallet" data-id="${w.id}"><b>${esc(w.name)}</b><span>الرصيد: ${moneyH(balance(w.id))}</span></button>
      <button class="icon-btn" data-act="renWallet" data-id="${w.id}" aria-label="إعادة تسمية">${ic('edit')}</button>
      ${S.wallets.length > 1 ? `<button class="icon-btn r" data-act="delWallet" data-id="${w.id}" aria-label="حذف">${ic('trash')}</button>` : ''}</div>`).join('')}
    <button class="btn sm gold" data-act="addWallet" style="margin-top:8px">${ic('plus')} محفظة جديدة</button>
    <div class="small muted" style="text-align:center;margin-top:10px">لكل محفظة رصيد وسجل مستقل (مثل: المحل، البيت، الادخار)</div>`);
}
function catsSheet(type) {
  type = type || 'dep';
  openSheet('cats', `${sheetTitle('التصنيفات')}
    <div class="seg"><button class="${type === 'dep' ? 'on' : ''}" data-act="catsTab" data-v="dep">الإيداع</button><button class="${type === 'wd' ? 'on' : ''}" data-act="catsTab" data-v="wd">السحب</button></div>
    ${S.cats[type].map((c, i) => `<div class="item"><span class="mid"><b>${esc(c)}</b></span><button class="icon-btn r" data-act="delCat" data-t="${type}" data-i="${i}" aria-label="حذف">${ic('trash')}</button></div>`).join('') || '<div class="muted small" style="text-align:center;padding:10px">لا توجد تصنيفات</div>'}
    <button class="btn sm gold" data-act="addCat" data-t="${type}" style="margin-top:8px">${ic('plus')} تصنيف جديد</button>`);
}
function goalSheet() {
  const g = wallet().goal || {};
  openSheet('goal', `${sheetTitle('هدف الادخار — ' + esc(wallet().name))}
    <div class="field"><label>اسم الهدف</label><input class="input" id="gName" maxlength="40" placeholder="مثال: شراء سيارة" value="${esc(g.name || '')}"></div>
    <div class="field"><label>المبلغ المستهدف (${CUR})</label><input class="input big" id="gTarget" data-fmt="1" inputmode="numeric" placeholder="0" value="${g.target ? num(g.target) : ''}"></div>
    <div class="row"><button class="btn sm gold" data-act="saveGoal">حفظ الهدف</button>${g.target ? '<button class="btn sm danger" data-act="clearGoal">إزالة الهدف</button>' : ''}</div>`);
}

/* ================= القفل ================= */
async function hashPin(pin, salt) {
  const data = new TextEncoder().encode(salt + ':' + pin + ':qns');
  if (window.crypto && crypto.subtle) {
    const h = await crypto.subtle.digest('SHA-256', data);
    return Array.from(new Uint8Array(h)).map(b => b.toString(16).padStart(2, '0')).join('');
  }
  let a = 0x811c9dc5, b = 0x1b873593; // بديل بسيط عند غياب WebCrypto
  for (const c of data) { a = Math.imul(a ^ c, 16777619); b = Math.imul(b + c, 0x85ebca6b) ^ (a >>> 13); }
  return 'f' + (a >>> 0).toString(16) + (b >>> 0).toString(16);
}
let lockBuf = '', lockCb = null, hiddenAt = 0;
function showLock(cb) {
  lockBuf = ''; lockCb = cb || null;
  const L = $('#lock');
  L.innerHTML = `<span class="brand">QnS</span><p id="lockMsg">أدخل الرقم السري</p><div class="dots" id="dots"></div>
    <div class="pad">${[1, 2, 3, 4, 5, 6, 7, 8, 9].map(n => `<button data-act="key" data-v="${n}">${n}</button>`).join('')}
      <button class="t" data-act="bioUnlock" ${S.settings.bio && isNative ? '' : 'style="visibility:hidden"'} aria-label="بصمة">${ic('finger')}</button>
      <button data-act="key" data-v="0">0</button><button class="t" data-act="key" data-v="back" aria-label="مسح">${ic('back')}</button></div>`;
  L.hidden = false; drawDots();
  if (S.settings.bio) bioUnlock();
}
function drawDots() {
  const n = S.settings.pin.len;
  $('#dots').innerHTML = Array.from({ length: n }, (_, i) => `<i class="${i < lockBuf.length ? 'f' : ''}"></i>`).join('');
}
function unlock() { $('#lock').hidden = true; const cb = lockCb; lockCb = null; if (cb) cb(); }
async function lockKey(v) {
  const p = S.settings.pin; if (!p) return unlock();
  if (v === 'back') lockBuf = lockBuf.slice(0, -1); else if (lockBuf.length < p.len) lockBuf += v;
  drawDots();
  if (lockBuf.length === p.len) {
    const ok = (await hashPin(lockBuf, p.salt)) === p.hash;
    if (ok) return unlock();
    Snd.play('err'); lockBuf = '';
    const d = $('#dots'); d.classList.add('err'); $('#lockMsg').textContent = 'رقم سري خاطئ، حاول مجدداً';
    setTimeout(() => { d.classList.remove('err'); drawDots(); }, 420);
  }
}
async function bioUnlock() {
  if (!isNative || !S.settings.bio) return;
  try {
    const a = await N('NativeBiometric', 'isAvailable', { useFallback: false });
    if (!a.isAvailable) return;
    await N('NativeBiometric', 'verifyIdentity', { title: 'QnS', subtitle: 'افتح التطبيق ببصمة الإصبع', negativeButtonText: 'استخدام الرقم السري', maxAttempts: 3 });
    unlock();
  } catch (e) {}
}
function pinSheet() {
  const has = !!S.settings.pin;
  openSheet('pin', `${sheetTitle(has ? 'تغيير الرقم السري' : 'تفعيل الرقم السري')}
    ${has ? `<div class="field"><label>الرقم السري الحالي</label><input class="input big" id="pOld" type="password" inputmode="numeric" maxlength="6" autocomplete="off"></div>` : ''}
    <div class="field"><label>الرقم السري الجديد (4 إلى 6 أرقام)</label><input class="input big" id="pNew" type="password" inputmode="numeric" maxlength="6" autocomplete="off"></div>
    <div class="field"><label>تأكيد الرقم السري</label><input class="input big" id="pNew2" type="password" inputmode="numeric" maxlength="6" autocomplete="off"></div>
    <div class="row"><button class="btn sm gold" data-act="savePin">حفظ</button>${has ? '<button class="btn sm danger" data-act="removePin">إلغاء القفل</button>' : ''}</div>
    <div class="small muted" style="text-align:center;margin-top:10px">احفظ الرقم جيداً — لا يمكن استرجاعه في حال نسيانه.</div>`);
}
async function checkOld() {
  const p = S.settings.pin; if (!p) return true;
  const old = toLatin($('#pOld').value);
  if ((await hashPin(old, p.salt)) !== p.hash) { Snd.play('err'); toast('الرقم السري الحالي غير صحيح'); return false; }
  return true;
}
async function savePin() {
  if (!(await checkOld())) return;
  const a = toLatin($('#pNew').value), b = toLatin($('#pNew2').value);
  if (!/^\d{4,6}$/.test(a)) return toast('الرقم السري يجب أن يكون من 4 إلى 6 أرقام');
  if (a !== b) return toast('الرقمان غير متطابقين');
  const salt = uid() + uid();
  S.settings.pin = { salt, hash: await hashPin(a, salt), len: a.length };
  save(); closeSheet(); render(); toast('تم تفعيل القفل بالرقم السري');
}
async function removePin() {
  if (!(await checkOld())) return;
  S.settings.pin = null; S.settings.bio = false;
  save(); closeSheet(); render(); toast('تم إلغاء القفل');
}
async function toggleBio() {
  const st = S.settings;
  if (st.bio) { st.bio = false; save(); return render(); }
  if (!st.pin) return toast('فعّل الرقم السري أولاً');
  if (!isNative) return toast('البصمة متاحة على الهاتف فقط');
  try {
    const a = await N('NativeBiometric', 'isAvailable', { useFallback: false });
    if (!a.isAvailable) return toast('لا توجد بصمة مسجّلة على هذا الجهاز');
    await N('NativeBiometric', 'verifyIdentity', { title: 'QnS', subtitle: 'أكّد بصمتك لتفعيل الفتح بالبصمة', negativeButtonText: 'إلغاء' });
    st.bio = true; save(); render(); toast('تم تفعيل الفتح بالبصمة');
  } catch (e) { toast('تعذّر تفعيل البصمة'); }
}

/* ================= الملفات والمشاركة ================= */
function bytesToB64(u8) { let s = ''; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000)); return btoa(s); }
const textToB64 = t => bytesToB64(new TextEncoder().encode(t));
function downloadWeb(name, b64, mime) {
  const a = document.createElement('a');
  a.href = `data:${mime};base64,${b64}`; a.download = name; document.body.appendChild(a); a.click(); a.remove();
}
async function shareFile(name, b64, mime) {
  if (!isNative) { downloadWeb(name, b64, mime); return toast('تم تنزيل الملف'); }
  try {
    const r = await N('Filesystem', 'writeFile', { path: name, data: b64, directory: 'CACHE', recursive: true });
    await N('Share', 'share', { title: name, files: [r.uri], dialogTitle: 'مشاركة عبر' });
  } catch (e) { if (!/cancel/i.test(String(e && e.message))) toast('تعذّرت المشاركة'); }
}
async function saveToDocs(name, b64, mime) {
  if (!isNative) { downloadWeb(name, b64, mime); return toast('تم تنزيل الملف'); }
  try {
    await N('Filesystem', 'writeFile', { path: 'QnS/' + name, data: b64, directory: 'DOCUMENTS', recursive: true });
    dialog({ title: 'تم الحفظ', msg: `حُفظت النسخة في: Documents/QnS/${name}`, cancel: '' });
  } catch (e) { toast('تعذّر الحفظ، جرّب خيار المشاركة'); }
}
const fileStamp = () => { const d = new Date(); return `${iso(d)}_${pad2(d.getHours())}${pad2(d.getMinutes())}`; };
function backupData() {
  const copy = JSON.parse(JSON.stringify(S));
  copy.settings.pin = null; copy.settings.bio = false; // لا نصدّر بيانات القفل
  return textToB64(JSON.stringify({ app: 'QnS', format: 1, exportedAt: new Date().toISOString(), data: copy }, null, 1));
}
function restoreFromFile(file) {
  const r = new FileReader();
  r.onerror = () => toast('تعذّرت قراءة الملف');
  r.onload = async () => {
    let n = null;
    try { const j = JSON.parse(r.result); if (j && j.app === 'QnS') n = normalize(j.data); } catch (e) {}
    if (!n) return dialog({ title: 'ملف غير صالح', msg: 'هذا الملف ليس نسخة احتياطية صحيحة من QnS.', cancel: '' });
    const ok = await confirmDlg('استعادة البيانات', `تحتوي النسخة على ${n.tx.length} عملية و${n.wallets.length} محفظة. سيتم استبدال كل البيانات الحالية. هل تريد المتابعة؟`, 'استعادة', true);
    if (!ok) return;
    n.settings.pin = S.settings.pin; n.settings.bio = S.settings.bio;
    S = n; addLog('استعادة البيانات من نسخة احتياطية');
    save(); applyTheme(); syncReminder(); render(); toast('تمت استعادة البيانات بنجاح');
  };
  r.readAsText(file);
}

/* ---------- التقارير: Excel ---------- */
function reportRows() {
  const list = sorted(wtx().filter(t => UI.scope === 'all' || t.date.slice(0, 7) === UI.month)).reverse();
  return { list, t: totals(list), title: UI.scope === 'all' ? 'كل العمليات' : monthLabel(UI.month) };
}
function exportXlsx() {
  const { list, t, title } = reportRows();
  if (!list.length) return toast('لا توجد عمليات للتصدير');
  if (!window.XLSX) return toast('مكوّن Excel غير متاح');
  const rows = [['QnS — تقرير العمليات'], ['المحفظة', wallet().name], ['الفترة', title], [],
    ['التاريخ', 'النوع', 'التصنيف', 'الملاحظة', `إيداع (${CUR})`, `سحب (${CUR})`]];
  list.forEach(x => rows.push([x.date, TYPE[x.type], x.cat || '', x.note || '', x.type === 'dep' ? x.amount : '', x.type === 'wd' ? x.amount : '']));
  rows.push([], ['', '', '', 'الإجمالي', t.dep, t.wd], ['', '', '', 'الصافي', t.net], [], ['تطوير وتنفيذ: أمجد حبيب']);
  const ws = XLSX.utils.aoa_to_sheet(rows);
  ws['!cols'] = [{ wch: 13 }, { wch: 9 }, { wch: 16 }, { wch: 32 }, { wch: 16 }, { wch: 16 }];
  const wb = XLSX.utils.book_new(); wb.Workbook = { Views: [{ RTL: true }] };
  XLSX.utils.book_append_sheet(wb, ws, 'العمليات');
  const b64 = XLSX.write(wb, { type: 'base64', bookType: 'xlsx' });
  shareFile(`QnS_${fileStamp()}.xlsx`, b64, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
}

/* ---------- التقارير: PDF (يُرسم على Canvas لضمان عربية سليمة) ---------- */
function buildPdf(jpegs, W, H) {
  const enc = new TextEncoder(), parts = [], offs = []; let len = 0;
  const push = d => { const u = typeof d === 'string' ? enc.encode(d) : d; parts.push(u); len += u.length; };
  const obj = (n, body) => { offs[n] = len; push(`${n} 0 obj\n`); body(); push('\nendobj\n'); };
  push('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n');
  const n = jpegs.length, kids = jpegs.map((_, i) => `${3 + i * 3} 0 R`).join(' ');
  obj(1, () => push('<< /Type /Catalog /Pages 2 0 R >>'));
  obj(2, () => push(`<< /Type /Pages /Kids [${kids}] /Count ${n} >>`));
  jpegs.forEach((jpg, i) => {
    const p = 3 + i * 3, c = p + 1, im = p + 2, stream = `q 595 0 0 842 0 0 cm /Im0 Do Q`;
    obj(p, () => push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Im0 ${im} 0 R >> >> /Contents ${c} 0 R >>`));
    obj(c, () => push(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`));
    obj(im, () => { push(`<< /Type /XObject /Subtype /Image /Width ${W} /Height ${H} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpg.length} >>\nstream\n`); push(jpg); push('\nendstream'); });
  });
  const total = 2 + n * 3, xref = len;
  push(`xref\n0 ${total + 1}\n0000000000 65535 f \n`);
  for (let i = 1; i <= total; i++) push(String(offs[i]).padStart(10, '0') + ' 00000 n \n');
  push(`trailer\n<< /Size ${total + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`);
  const out = new Uint8Array(len); let o = 0; for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}
async function exportPdf() {
  const { list, t, title } = reportRows();
  if (!list.length) return toast('لا توجد عمليات للتصدير');
  toast('جارٍ تجهيز التقرير…');
  try { await Promise.all([document.fonts.load('700 20px Cairo', 'تقرير'), document.fonts.load('400 20px Cairo', 'تقرير'), document.fonts.load('700 40px "Playfair Display"', 'QnS')]); } catch (e) {}
  const W = 1240, H = 1754, M = 70, RH = 46, perFirst = 26, perNext = 31;
  const pages = []; let rest = list.slice();
  pages.push(rest.splice(0, perFirst)); while (rest.length) pages.push(rest.splice(0, perNext));
  const cols = [{ k: 'date', w: 170, l: 'التاريخ' }, { k: 'type', w: 110, l: 'النوع' }, { k: 'cat', w: 190, l: 'التصنيف' }, { k: 'note', w: 380, l: 'الملاحظة' }, { k: 'amt', w: 250, l: 'المبلغ' }];
  const jpegs = [];
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
  const c = cv.getContext('2d');
  const fit = (s, w) => { if (c.measureText(s).width <= w) return s; while (s.length > 1 && c.measureText(s + '…').width > w) s = s.slice(0, -1); return s + '…'; };
  pages.forEach((rows, pi) => {
    c.direction = 'rtl'; c.textAlign = 'right'; c.textBaseline = 'middle';
    c.fillStyle = '#fff'; c.fillRect(0, 0, W, H);
    let y;
    if (pi === 0) {
      const g = c.createLinearGradient(0, 0, W, 190); g.addColorStop(0, '#16275a'); g.addColorStop(1, '#070e26');
      c.fillStyle = g; c.fillRect(0, 0, W, 190);
      c.fillStyle = '#D4AF37'; c.fillRect(0, 190, W, 5);
      c.save(); c.direction = 'ltr'; c.textAlign = 'left'; c.font = '700 78px "Playfair Display", Georgia, serif';
      const gg = c.createLinearGradient(0, 50, 0, 140); gg.addColorStop(0, '#FFF4BE'); gg.addColorStop(1, '#C9A030'); c.fillStyle = gg;
      c.fillText('QnS', M, 98); c.restore();
      c.fillStyle = '#fff'; c.font = '700 38px Cairo'; c.fillText('تقرير الإيداع والسحب', W - M, 72);
      c.fillStyle = '#c2cbea'; c.font = '400 24px Cairo'; c.fillText(`المحفظة: ${wallet().name}   •   الفترة: ${title}`, W - M, 128);
      // ملخص
      const bw = (W - 2 * M - 40) / 3, by = 230;
      [['إجمالي الإيداع', t.dep, '#0E9F68', '#E8F7F0'], ['إجمالي السحب', t.wd, '#D5353B', '#FCECEC'], ['الصافي', t.net, '#0A1430', '#F3EEDC']].forEach((b, i) => {
        const x = W - M - bw - i * (bw + 20);
        c.fillStyle = b[3]; c.beginPath(); c.roundRect ? c.roundRect(x, by, bw, 120, 16) : c.rect(x, by, bw, 120); c.fill();
        c.fillStyle = '#5b6480'; c.font = '600 22px Cairo'; c.fillText(b[0], x + bw - 22, by + 36);
        c.fillStyle = b[2]; c.font = '800 30px Cairo'; c.fillText(money(b[1]), x + bw - 22, by + 82);
      });
      y = 395;
    } else y = 90;
    // رأس الجدول
    c.fillStyle = '#0A1430'; c.fillRect(M, y, W - 2 * M, RH);
    c.fillStyle = '#F6E3A1'; c.font = '700 22px Cairo';
    let x = W - M; cols.forEach(col => { c.fillText(col.l, x - 14, y + RH / 2); x -= col.w; });
    y += RH;
    rows.forEach((r, ri) => {
      if (ri % 2) { c.fillStyle = '#F5F7FC'; c.fillRect(M, y, W - 2 * M, RH); }
      x = W - M; c.font = '400 21px Cairo';
      cols.forEach(col => {
        let v = col.k === 'date' ? dateAr(r.date) : col.k === 'type' ? TYPE[r.type] : col.k === 'cat' ? (r.cat || '—') : col.k === 'note' ? (r.note || '—') : `${r.type === 'dep' ? '+' : '−'} ${money(r.amount)}`;
        c.fillStyle = col.k === 'amt' || col.k === 'type' ? (r.type === 'dep' ? '#0E9F68' : '#D5353B') : '#1b2440';
        c.font = (col.k === 'amt' ? '700' : '400') + ' 21px Cairo';
        if (col.k === 'date') c.font = '400 19px Cairo';
        c.fillText(fit(v, col.w - 24), x - 14, y + RH / 2); x -= col.w;
      });
      y += RH;
    });
    c.strokeStyle = '#d9deea'; c.lineWidth = 1; c.beginPath(); c.moveTo(M, y); c.lineTo(W - M, y); c.stroke();
    c.fillStyle = '#7a849f'; c.font = '400 19px Cairo';
    c.fillText('تطوير وتنفيذ: أمجد حبيب  •  QnS', W - M, H - 55);
    c.textAlign = 'left'; c.fillText(`صفحة ${pi + 1} من ${pages.length}  •  ${dateAr(today())}`, M, H - 55);
    const b64 = cv.toDataURL('image/jpeg', 0.86).split(',')[1], bin = atob(b64), u = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
    jpegs.push(u);
  });
  shareFile(`QnS_${fileStamp()}.pdf`, bytesToB64(buildPdf(jpegs, W, H)), 'application/pdf');
}

/* ================= التذكير اليومي ================= */
async function syncReminder(interactive) {
  if (!isNative) return false;
  const st = S.settings;
  try {
    await N('LocalNotifications', 'cancel', { notifications: [{ id: 1001 }] }).catch(() => {});
    if (!st.remind) return true;
    if (interactive) {
      const p = await N('LocalNotifications', 'requestPermissions');
      if (p.display !== 'granted') return false;
    }
    const [h, m] = st.remindTime.split(':').map(Number);
    await N('LocalNotifications', 'schedule', { notifications: [{ id: 1001, title: 'QnS — تذكير يومي', body: 'لا تنسَ تسجيل إيداعات ومسحوبات اليوم.', schedule: { on: { hour: h, minute: m }, allowWhileIdle: true } }] });
    return true;
  } catch (e) { return false; }
}

/* ================= الأحداث ================= */
const A = {
  nav: d => { UI.view = d.v; $('#view').dataset.v = ''; render(); },
  closeSheet: () => { form = null; closeSheet(); },
  dlg: d => closeDialog(d.v === '1'),
  newTx: d => txSheet(d.type),
  showTx: d => showTx(d.id),
  editTx: d => { const t = S.tx.find(x => x.id === d.id); if (t) txSheet(t.type, t); },
  delTx: d => delTx(d.id),
  saveTx,
  pickCat: (d, el) => { form.cat = form.cat === d.v ? '' : d.v; el.parentNode.querySelectorAll('.chip').forEach(c => c.classList.toggle('on', c.dataset.v === form.cat)); },
  filter: d => { UI[d.g] = d.v; render(); },
  month: d => { UI.month = shiftMonth(UI.month, +d.k); render(); },
  setMonth: d => { UI.month = d.v; render(); },
  catType: d => { UI.catType = d.v; render(); },
  scope: d => { UI.scope = d.v; render(); },
  pdf: exportPdf, xlsx: exportXlsx,
  wallets: walletsSheet,
  pickWallet: d => { S.active = d.id; save(); closeSheet(); render(); },
  addWallet: async () => {
    const n = await promptDlg('محفظة جديدة', '', 'مثال: المحل، البيت، الادخار'); if (!n) return;
    const w = { id: uid(), name: n, goal: null }; S.wallets.push(w); S.active = w.id; save(); render(); walletsSheet();
  },
  renWallet: async d => {
    const w = S.wallets.find(x => x.id === d.id); const n = await promptDlg('إعادة تسمية المحفظة', w.name); if (!n) return;
    w.name = n; save(); render(); walletsSheet();
  },
  delWallet: async d => {
    const w = S.wallets.find(x => x.id === d.id), cnt = wtx(w.id).length;
    if (!(await confirmDlg('حذف المحفظة', `سيتم حذف «${w.name}»${cnt ? ` مع ${cnt} عملية مسجّلة فيها` : ''} نهائياً. هل أنت متأكد؟`, 'نعم، احذف', true))) return;
    S.wallets = S.wallets.filter(x => x.id !== w.id); S.tx = S.tx.filter(t => t.wallet !== w.id);
    if (S.active === w.id) S.active = S.wallets[0].id;
    addLog(`حذف المحفظة «${w.name}» (${cnt} عملية)`); save(); render(); walletsSheet();
  },
  cats: () => catsSheet('dep'), catsTab: d => catsSheet(d.v),
  addCat: async d => {
    const n = await promptDlg('تصنيف جديد', '', 'اسم التصنيف'); if (!n) return;
    if (S.cats[d.t].includes(n)) return toast('التصنيف موجود مسبقاً');
    S.cats[d.t].push(n); save(); catsSheet(d.t);
  },
  delCat: async d => {
    const c = S.cats[d.t][+d.i];
    if (!(await confirmDlg('حذف التصنيف', `حذف «${c}»؟ العمليات السابقة تبقى كما هي.`, 'حذف', true))) return;
    S.cats[d.t].splice(+d.i, 1); save(); catsSheet(d.t);
  },
  goal: goalSheet,
  saveGoal: () => {
    const target = parseAmount($('#gTarget').value); if (target <= 0) return toast('أدخل المبلغ المستهدف');
    wallet().goal = { name: $('#gName').value.trim(), target }; save(); closeSheet(); render(); toast('تم حفظ الهدف');
  },
  clearGoal: () => { wallet().goal = null; save(); closeSheet(); render(); },
  theme: () => { S.settings.theme = S.settings.theme === 'dark' ? 'light' : 'dark'; save(); applyTheme(); render(); },
  sound: () => { S.settings.sound = !S.settings.sound; save(); render(); if (S.settings.sound) Snd.play('dep'); },
  testSnd: d => Snd.play(d.v, true),
  pin: pinSheet, savePin, removePin, bio: toggleBio,
  key: d => lockKey(d.v), bioUnlock,
  usd: () => { const st = S.settings; st.usd = !st.usd; save(); render(); if (st.usd && !(st.rate > 0)) A.rate(); },
  rate: async () => {
    const v = await dialog({ title: 'سعر صرف الدولار', msg: `كم ${CUR} يساوي الدولار الواحد؟`, input: { value: S.settings.rate || '', placeholder: 'مثال: 11000' }, ok: 'حفظ' });
    if (v == null) return; const r = parseDec(v); if (r <= 0) return toast('أدخل سعراً صحيحاً');
    S.settings.rate = r; save(); render();
  },
  remind: async () => {
    const st = S.settings;
    if (!isNative) return toast('التذكيرات متاحة على الهاتف فقط');
    st.remind = !st.remind;
    const ok = await syncReminder(true);
    if (st.remind && !ok) { st.remind = false; toast('يجب السماح بالإشعارات لتفعيل التذكير'); }
    else toast(st.remind ? `سيتم تذكيرك يومياً الساعة ${st.remindTime}` : 'تم إيقاف التذكير');
    save(); render();
  },
  backupSave: () => saveToDocs(`QnS_backup_${fileStamp()}.json`, backupData(), 'application/json'),
  backupShare: () => shareFile(`QnS_backup_${fileStamp()}.json`, backupData(), 'application/json'),
  restore: () => { const f = $('#fileInput'); f.value = ''; f.click(); }
};

function bindEvents() {
  document.addEventListener('click', e => {
    const el = e.target.closest('[data-act]'); if (!el || el.disabled) return;
    const fn = A[el.dataset.act]; if (fn) { e.preventDefault(); fn(el.dataset, el); }
  });
  document.addEventListener('input', e => {
    const el = e.target;
    if (el.id === 'fAmount' || el.dataset.fmt) {
      const v = parseAmount(el.value); el.value = v ? num(v) : '';
      if (el.id === 'fAmount' && form) { form.amount = v; updatePreview(); }
    } else if (el.id === 'q') { UI.q = el.value; $('#hlist').innerHTML = historyList(); }
    else if (el.id === 'vol') { S.settings.volume = el.value / 100; $('#volLbl').textContent = el.value + '%'; }
  });
  document.addEventListener('change', e => {
    const el = e.target;
    if (el.id === 'vol') { save(); Snd.play('dep'); }
    else if (el.id === 'remTime' && el.value) { S.settings.remindTime = el.value; save(); if (S.settings.remind) syncReminder(false); render(); }
    else if (el.id === 'fileInput' && el.files[0]) restoreFromFile(el.files[0]);
  });
  document.addEventListener('keydown', e => {
    if (e.key === 'Enter' && e.target.id === 'dlgInput') closeDialog(true);
    else if (e.key === 'Enter' && e.target.id === 'fAmount') e.target.blur();
  });
  // رفع المحتوى فوق لوحة المفاتيح
  const sheet = $('#sheet');
  sheet.addEventListener('focusin', e => {
    if (!e.target.matches('input:not([type=date]):not([type=time]),textarea')) return;
    if (matchMedia('(pointer:coarse)').matches) sheet.classList.add('kb');
    setTimeout(() => { try { e.target.scrollIntoView({ block: 'center', behavior: 'smooth' }); } catch (x) {} }, 280);
  });
  sheet.addEventListener('focusout', () => setTimeout(() => { if (!sheet.contains(document.activeElement) || !document.activeElement.matches('input,textarea')) sheet.classList.remove('kb'); }, 120));
  // القفل عند العودة من الخلفية
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) hiddenAt = Date.now();
    else if (S.settings.pin && hiddenAt && Date.now() - hiddenAt > 20000 && $('#lock').hidden && $('#splash') == null) showLock();
  });
  // زر الرجوع
  if (isNative) {
    try {
      cap.addListener('App', 'backButton', () => {
        if (!$('#dialogWrap').hidden) return closeDialog(false);
        if (!$('#lock').hidden) return N('App', 'exitApp').catch(() => {});
        if (sheetName) return A.closeSheet();
        if (UI.view !== 'home') return A.nav({ v: 'home' });
        N('App', 'exitApp').catch(() => {});
      });
    } catch (e) {}
  }
}

/* ================= شاشة الافتتاح ================= */
function runSplash() {
  return new Promise(async resolve => {
    const sp = $('#splash'), cv = $('#splashCanvas'), logo = $('#splashLogo');
    const finish = () => { sp.classList.add('out'); setTimeout(() => { sp.remove(); }, 650); resolve(); };
    if (/[?&]nosplash/.test(location.search)) { sp.remove(); return resolve(); }
    const ctx = cv.getContext && cv.getContext('2d');
    const vw = window.innerWidth, vh = window.innerHeight;
    const size = Math.round(Math.min(vw * 0.3, 132));
    logo.style.setProperty('--logo-size', size + 'px');
    try { await Promise.race([document.fonts.load(`700 ${size}px "Playfair Display"`, 'QnS'), new Promise(r => setTimeout(r, 700))]); } catch (e) {}
    Snd.play('intro');
    const reveal = () => {
      logo.classList.add('show', 'shine'); $('#splashLine').classList.add('show');
      setTimeout(() => $('#splashCredit').classList.add('show'), 250);
    };
    if (!ctx || matchMedia('(prefers-reduced-motion: reduce)').matches) { reveal(); return setTimeout(finish, 1800); }

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    cv.width = vw * dpr; cv.height = vh * dpr;
    // أخذ نقاط الشعار
    const rect = logo.getBoundingClientRect();
    const off = document.createElement('canvas'); off.width = vw; off.height = vh;
    const o = off.getContext('2d', { willReadFrequently: true });
    o.font = `700 ${size}px "Playfair Display", Georgia, serif`; o.textAlign = 'center'; o.textBaseline = 'alphabetic'; o.fillStyle = '#fff';
    const mt = o.measureText('QnS'), asc = mt.actualBoundingBoxAscent || size * 0.75, desc = mt.actualBoundingBoxDescent || size * 0.2;
    const cx = rect.left + rect.width / 2, cy = rect.top + rect.height / 2;
    o.fillText('QnS', cx, cy + (asc - desc) / 2 + size * 0.02);
    const x0 = Math.max(0, Math.floor(cx - rect.width / 2 - 10)), y0 = Math.max(0, Math.floor(cy - size)), w = Math.min(vw - x0, Math.ceil(rect.width + 20)), h = Math.min(vh - y0, size * 2);
    let pts = [];
    try {
      const img = o.getImageData(x0, y0, w, h).data, step = Math.max(2, Math.round(size / 42));
      for (let y = 0; y < h; y += step) for (let x = 0; x < w; x += step) if (img[(y * w + x) * 4 + 3] > 128) pts.push([x0 + x, y0 + y]);
    } catch (e) {}
    if (pts.length < 20) { reveal(); return setTimeout(finish, 1800); }
    const R = Math.hypot(vw, vh) * 0.6;
    const ps = pts.map(([tx, ty]) => {
      const a = Math.random() * Math.PI * 2, r = R * (0.45 + Math.random() * 0.6);
      return { tx, ty, sx: cx + Math.cos(a) * r, sy: cy + Math.sin(a) * r, d: Math.random() * 0.45, sw: (Math.random() - 0.5) * 160, s: 0.7 + Math.random() * 1.1, hue: 40 + Math.random() * 12, l: 55 + Math.random() * 25 };
    });
    const stars = Array.from({ length: 50 }, () => ({ x: Math.random() * vw, y: Math.random() * vh, p: Math.random() * 6, s: Math.random() * 1.3 + 0.3 }));
    const T = 1.55, DUR = 1.0, start = performance.now();
    let revealed = false, done = false;
    const ease = t => 1 - Math.pow(1 - t, 3);
    function frame(now) {
      if (done) return;
      const t = (now - start) / 1000;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, vw, vh);
      ctx.globalCompositeOperation = 'lighter';
      for (const s of stars) { ctx.fillStyle = `rgba(246,227,161,${0.15 + 0.25 * Math.abs(Math.sin(t * 2 + s.p))})`; ctx.fillRect(s.x, s.y, s.s, s.s); }
      const fade = t > T ? Math.max(0, 1 - (t - T) / 0.4) : 1;
      for (const p of ps) {
        const k = Math.min(1, Math.max(0, (t - p.d) / DUR)); if (k <= 0) continue;
        const e = ease(k), dx = p.tx - p.sx, dy = p.ty - p.sy, L = Math.hypot(dx, dy) || 1, sw = Math.sin(e * Math.PI) * p.sw;
        const x = p.sx + dx * e - dy / L * sw, y = p.sy + dy * e + dx / L * sw;
        ctx.fillStyle = `hsla(${p.hue},75%,${p.l}%,${(0.35 + 0.65 * e) * fade})`;
        const s = p.s * (k < 1 ? 1.5 : 1.15);
        ctx.fillRect(x - s / 2, y - s / 2, s, s);
      }
      if (t >= T && !revealed) { revealed = true; reveal(); }
      if (t < T + 0.45) requestAnimationFrame(frame); else ctx.clearRect(0, 0, vw, vh);
    }
    requestAnimationFrame(frame);
    setTimeout(() => { if (!revealed) { revealed = true; reveal(); } }, T * 1000 + 300); // ضمان الظهور
    setTimeout(() => { done = true; finish(); }, 3000);
  });
}

/* ================= الإقلاع ================= */
async function boot() {
  await load();
  applyTheme();
  N('SystemBars', 'setStyle', { style: 'DARK' }).catch(() => {});
  bindEvents();
  await runSplash();
  applyTheme();
  const start = () => { $('#app').hidden = false; render(); };
  if (S.settings.pin) showLock(start); else start();
  syncReminder(false);
}
window.QnS = { get state() { return S; }, balance, totals, parseAmount, money, buildPdf };
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
})();
