/* ══════════════════════════════════════════════════════════════
   ir.js — مولّدات بروتوكولات الأشعة تحت الحمراء + جسر الإرسال
   NEC · NEC-Extended · Samsung32 · Sony SIRC (12/15/20) · RAW
   المرجع الزمني: مواصفات البروتوكولات القياسية (µs)
   ══════════════════════════════════════════════════════════════ */
'use strict';

const IR = (() => {
  /* ---------- بناء أنماط التزييت ---------- */
  function buildNec(addrLow, addrHigh, cmd, extended) {
    const t = [9000, 4500];
    const bytes = [addrLow, extended ? addrHigh : (~addrLow) & 0xff, cmd, (~cmd) & 0xff];
    for (const b of bytes) pushLsb(t, b, 8, 560, 560, 1690);
    t.push(560);
    return t;
  }
  function buildSamsung32(addr, cmd) {
    const t = [4500, 4500];
    const bytes = [addr, (~addr) & 0xff, cmd, (~cmd) & 0xff];
    for (const b of bytes) pushLsb(t, b, 8, 560, 560, 1690);
    t.push(560);
    return t;
  }
  function buildSirc(cmdBits, addrBits, cmd, addr) {
    const t = [2400, 600];
    pushLsb(t, cmd, cmdBits, 600, 600, 1200);
    // عنوان SIRC من بايتات التخزين (little-endian) بسعة addrBits
    pushLsb(t, addr, addrBits, 600, 600, 1200);
    t.push(600);
    return t;
  }
  function pushLsb(out, value, bits, mark, zeroSpace, oneSpace) {
    for (let i = 0; i < bits; i++) {
      out.push(mark);
      out.push((value >> i) & 1 ? oneSpace : zeroSpace);
    }
  }

  /**
   * يحوّل زر من قاعدة الأكواد إلى { freq, pattern }
   * p: 'nec' | 'necext' | 'samsung32' | 'sirc' | 'sirc15' | 'sirc20' | 'raw'
   */
  function buildPattern(btn) {
    switch (btn.p) {
      case 'nec':       return { freq: 38000, pattern: buildNec(btn.a[0] | 0, 0, btn.c | 0, false) };
      case 'necext':    return { freq: 38000, pattern: buildNec(btn.a[0] | 0, btn.a[1] | 0, btn.c | 0, true) };
      case 'samsung32': return { freq: 38000, pattern: buildSamsung32(btn.a[0] | 0, btn.c | 0) };
      case 'sirc':      return { freq: 40000, pattern: buildSirc(7, 5, btn.c | 0, btn.a[0] | 0) };
      case 'sirc15':    return { freq: 40000, pattern: buildSirc(7, 8, btn.c | 0, btn.a[0] | 0) };
      case 'sirc20': {
        const addr = ((btn.a[1] | 0) << 8) | (btn.a[0] | 0); // 13 بت little-endian
        return { freq: 40000, pattern: buildSirc(7, 13, btn.c | 0, addr) };
      }
      case 'raw':       return { freq: btn.f || 38000, pattern: btn.raw.slice() };
      default:          return null;
    }
  }

  /* ---------- جسر الجسر الأصلي (WebView) أو وضع المتصفح ---------- */
  const bridge = typeof window !== 'undefined' ? window.AndroidBridge || null : null;
  const isNative = !!bridge;

  function nativeCall(name, ...args) {
    if (!bridge) return undefined;
    try { return bridge[name](...args); } catch (e) { console.warn('bridge error', name, e); return undefined; }
  }

  const hasIrEmitter = () => isNative && nativeCall('hasIrEmitter') === true;
  // وضع المحاكاة: داخل WebView بدون ناشر IR، أو معاينة المتصفح
  const isDemo = () => !isNative || !hasIrEmitter();

  let lastTransmitAt = 0;
  /**
   * يرسل زر IR. يعيد true عند نجاح التسليم للنافشر.
   * زمن الاستجابة المقاس على الأجهزة الحقيقية < 50ms لأن الإرسال متزامن.
   */
  function transmitButton(btn) {
    const built = buildPattern(btn);
    if (!built) return false;
    lastTransmitAt = performance.now();
    if (isNative) {
      if (hasIrEmitter()) {
        return nativeCall('transmitIr', built.freq, built.pattern) === true;
      }
      return true; // الوضع التجريبي الأصلي: نحاكي النجاح
    }
    return true; // معاينة المتصفح
  }
  function transmitLatencyMs() { return Math.round(performance.now() - lastTransmitAt); }

  /* ---------- اهتزاز وصوت ---------- */
  function vibrate(ms) {
    if (isNative) { nativeCall('vibrate', ms | 0); return; }
    try { navigator.vibrate && navigator.vibrate(ms | 0); } catch (e) { /* تجاهل */ }
  }
  function beep(kind) {
    // kind: 'tick' | 'ok' | 'save'
    if (isNative) { nativeCall('playBeep', kind === 'ok' ? 1 : (kind === 'save' ? 2 : 0)); return; }
  }

  /* ---------- تخزين دائم (SharedPreferences أو localStorage) ---------- */
  const store = {
    get(key, fallback) {
      try {
        const raw = isNative ? nativeCall('prefsGet', key) : window.localStorage.getItem('rs_' + key);
        if (raw === null || raw === undefined || raw === '') return fallback;
        return JSON.parse(raw);
      } catch (e) { return fallback; }
    },
    set(key, value) {
      try {
        const raw = JSON.stringify(value);
        if (isNative) nativeCall('prefsSet', key, raw);
        else window.localStorage.setItem('rs_' + key, raw);
        return true;
      } catch (e) { return false; }
    },
    remove(key) {
      if (isNative) nativeCall('prefsRemove', key);
      else try { window.localStorage.removeItem('rs_' + key); } catch (e) {}
    },
  };

  return {
    isNative, hasIrEmitter, isDemo, transmitButton, transmitLatencyMs,
    vibrate, beep, store, buildPattern,
    deviceInfo() { return isNative ? (nativeCall('deviceInfo') || '') : 'متصفح — وضع المعاينة'; },
  };
})();
