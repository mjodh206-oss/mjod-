(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.SahraCore = factory();
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  /**
   * Computes countdown and status for weekly Thursday night (الخميس = day 4).
   * Supports active celebration window from Thursday target time through Friday 02:00 AM.
   */
  function getNextThursdayInfo(nowInput, targetHour, targetMinute) {
    const now = nowInput instanceof Date ? new Date(nowInput.getTime()) : new Date(nowInput || Date.now());
    const hour = Number.isFinite(targetHour) ? targetHour : 21;
    const minute = Number.isFinite(targetMinute) ? targetMinute : 0;

    const day = now.getDay(); // 0 Sun .. 4 Thu .. 6 Sat
    const isFridayLateNight = day === 5 && now.getHours() < 3;

    if (isFridayLateNight) {
      return {
        days: 0,
        hours: 0,
        minutes: 0,
        seconds: 0,
        totalMs: 0,
        isTodayThursday: true,
        isTonightActive: true,
        nextDateISO: now.toISOString(),
        statusTitle: 'سهرتكم مشتعلة الليلة ✨',
        statusSubtitle: 'عيشوا اللحظة بدون استعجال.. الليلة إلكم بس'
      };
    }

    const candidate = new Date(now.getTime());
    const daysUntilThu = (4 - day + 7) % 7;
    candidate.setDate(now.getDate() + daysUntilThu);
    candidate.setHours(hour, minute, 0, 0);

    if (daysUntilThu === 0 && now.getTime() >= candidate.getTime()) {
      return {
        days: 0,
        hours: 0,
        minutes: 0,
        seconds: 0,
        totalMs: 0,
        isTodayThursday: true,
        isTonightActive: true,
        nextDateISO: candidate.toISOString(),
        statusTitle: 'حان موعد سهرة الخميس! 🕯️🔥',
        statusSubtitle: 'اقفلوا الإشعارات واختاروا مزاج سهرتكم الآن'
      };
    }

    const diffMs = Math.max(0, candidate.getTime() - now.getTime());
    const totalSeconds = Math.floor(diffMs / 1000);
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    const isTodayThursday = daysUntilThu === 0;
    return {
      days,
      hours,
      minutes,
      seconds,
      totalMs: diffMs,
      isTodayThursday,
      isTonightActive: false,
      nextDateISO: candidate.toISOString(),
      statusTitle: isTodayThursday
        ? 'الليلة خميسكم المنتظر! 🌙'
        : 'العد التنازلي لسهرة الخميس القادمة',
      statusSubtitle: isTodayThursday
        ? 'جهّزوا الأجواء.. ساعات قليلة وتبدأ ليلتكم'
        : 'موعدكم الأسبوعي اللي بيرجّع الشرارة'
    };
  }

  /**
   * Smart couple mood matcher for مقياس الأجواء (🌡️)
   */
  function computeMoodMatch(p1, p2, names) {
    const first = p1 || { energy: 'romantic', boldness: 'warm', craving: 'touch' };
    const second = p2 || { energy: 'romantic', boldness: 'warm', craving: 'talk' };
    const name1 = (names && names.partner1) || 'الشريك الأول';
    const name2 = (names && names.partner2) || 'الشريك الثاني';

    const boldnessRank = { calm: 1, warm: 2, fiery: 3 };
    const b1 = boldnessRank[first.boldness] || 2;
    const b2 = boldnessRank[second.boldness] || 2;
    const avgBold = (b1 + b2) / 2;

    let recommendedMood = 'warm';
    if (avgBold <= 1.4) {
      recommendedMood = 'calm';
    } else if (avgBold >= 2.6 || (avgBold >= 2.5 && (first.energy === 'playful' || second.energy === 'playful'))) {
      recommendedMood = 'fiery';
    } else {
      recommendedMood = 'warm';
    }

    let harmony = 84;
    if (first.boldness === second.boldness) harmony += 7;
    else if (Math.abs(b1 - b2) === 1) harmony += 3;

    if (first.energy === second.energy) harmony += 5;
    if (first.craving === second.craving) harmony += 4;
    else harmony += 2; // complementary cravings enrich the night

    harmony = Math.min(99, Math.max(79, harmony));

    const cravings = new Set([first.craving, second.craving]);
    const energies = new Set([first.energy, second.energy]);

    let mainFeature = 'truth-dare';
    let mainStep = 'ابدأوا بـ 🎭 حقيقة أو جرأة لكسر الجليد بضحكة وغزل.';
    if (cravings.has('touch') && energies.has('relaxed')) {
      mainFeature = 'pamper';
      mainStep = 'ابدأوا بـ 💆 جلسة تدليل دافئة بالشموع والزيوت بالتناوب (١٥ دقيقة لكل شريك).';
    } else if (recommendedMood === 'fiery' && cravings.has('play')) {
      mainFeature = 'fantasy-cards';
      mainStep = 'اسحبوا كرت مفاجئ من 🃏 كروت الخيال بالمستوى الناري وعيشوا السيناريو فوراً.';
    } else if (cravings.has('play')) {
      mainFeature = 'blindfold';
      mainStep = 'اشعلوا الحماس مع 🙈 تحدي العيون المغمّضة (تذوّق وهمس ولمس).';
    } else if (cravings.has('touch')) {
      mainFeature = 'seconds';
      mainStep = 'ابدأوا بـ 💋 تحدي الثواني: حضن صامت دافئ لمدة ٩٠ ثانية يوحّد نبضكم.';
    }

    const moodNames = {
      calm: 'هادي وحالم 🌙',
      warm: 'دافي ورومانسي 🕯️',
      fiery: 'ناري وجريء 🔥'
    };

    const playlistMap = {
      calm: '🌙 قمر وهدوء (بيانو ليلي ومطر ناعم)',
      warm: '🕯️ شموع وعنبر (نغمات دافئة ومدفأة حطب)',
      fiery: '🔥 نبض وشغف (أوتار مخملية وإيقاع دافئ)'
    };

    const eveningMap = {
      calm: 'سينما بالبيت على ضوء خافت أو سهرة شرفة دافئة',
      warm: 'عشاء على الشموع ورقصة بطيئة بالصالة',
      fiery: 'سهرة مفاجآت بكود الأسود فقط وكروت الخيال الجريئة'
    };

    const plan = [
      {
        icon: '📵',
        title: 'الخطوة ١: عزل العالم الخارجي',
        detail: 'فعّلوا وضع «لا موبايل» لساعتين مع قائمة تشغيل ' + playlistMap[recommendedMood],
        featureId: 'no-phone'
      },
      {
        icon: '🕯️',
        title: 'الخطوة ٢: جو السهرة الأنسب لـ ' + name1 + ' و ' + name2,
        detail: eveningMap[recommendedMood],
        featureId: 'ready-nights'
      },
      {
        icon: '✨',
        title: 'الخطوة ٣: قلب السهرة المشترك',
        detail: mainStep,
        featureId: mainFeature
      },
      {
        icon: '🎟️',
        title: 'الخطوة ٤: ختام بذكرى وقسيمة حب',
        detail: 'تبادلوا قسيمة حب سرّية واحفظوا لقطة الليلة في الألبوم السرّي 📸',
        featureId: 'coupons'
      }
    ];

    return {
      harmonyScore: harmony,
      recommendedMood,
      recommendedMoodLabel: moodNames[recommendedMood],
      headline: `انسجام أجوائكم الليلة ${harmony}% — مزاجكم الأنسب: ${moodNames[recommendedMood]}`,
      summary:
        first.boldness === second.boldness
          ? `قلوبكم على نفس الموجة تماماً يا ${name1} و ${name2}! اخترنا لكم سهرة تجمع الشغف والراحة.`
          : `مزيج رائع بين مزاج ${name1} ومزاج ${name2}؛ صممنا لكم سهرة متوازنة ترضي الطرفين وتقرّبكم أكتر.`,
      primaryFeatureId: mainFeature,
      playlistTitle: playlistMap[recommendedMood],
      plan
    };
  }

  /**
   * Lightweight deterministic PIN hash for local 4-digit secret vault lock.
   */
  function hashPin(pin) {
    const clean = String(pin || '').trim();
    if (!/^\d{4}$/.test(clean)) {
      return null;
    }
    let h = 2166136261;
    const salted = 'sahra-vault::' + clean + '::khamis';
    for (let i = 0; i < salted.length; i++) {
      h ^= salted.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return 'v1_' + (h >>> 0).toString(16).padStart(8, '0');
  }

  function verifyPin(inputPin, storedHash) {
    if (!storedHash) return false;
    const computed = hashPin(inputPin);
    return Boolean(computed && computed === storedHash);
  }

  function isNoteUnlocked(note, nowMs) {
    if (!note) return false;
    const now = Number.isFinite(nowMs) ? nowMs : Date.now();
    return now >= Number(note.unlockAt || 0);
  }

  function formatRemainingLock(note, nowMs) {
    const now = Number.isFinite(nowMs) ? nowMs : Date.now();
    const diff = Math.max(0, Number((note && note.unlockAt) || 0) - now);
    if (diff <= 0) return 'متاحة للفتح الآن 💌';
    const totalSec = Math.ceil(diff / 1000);
    const hrs = Math.floor(totalSec / 3600);
    const mins = Math.floor((totalSec % 3600) / 60);
    const secs = totalSec % 60;
    if (hrs > 24) {
      const days = Math.floor(hrs / 24);
      const remHrs = hrs % 24;
      return `تُفتح بعد ${days} يوم و ${remHrs} ساعة`;
    }
    if (hrs > 0) {
      return `تُفتح بعد ${hrs} س و ${mins} د`;
    }
    if (mins > 0) {
      return `تُفتح بعد ${mins} د و ${secs} ث`;
    }
    return `تُفتح بعد ${secs} ثانية`;
  }

  function formatClock(totalSeconds) {
    const s = Math.max(0, Math.floor(Number(totalSeconds) || 0));
    const hrs = Math.floor(s / 3600);
    const mins = Math.floor((s % 3600) / 60);
    const secs = s % 60;
    const mm = String(mins).padStart(2, '0');
    const ss = String(secs).padStart(2, '0');
    if (hrs > 0) {
      return String(hrs).padStart(2, '0') + ':' + mm + ':' + ss;
    }
    return mm + ':' + ss;
  }

  function pickRandomAvoidingRecent(items, recentIds, idKey, rng) {
    if (!Array.isArray(items) || items.length === 0) return null;
    const key = idKey || 'id';
    const recent = new Set(Array.isArray(recentIds) ? recentIds : []);
    const pool = items.filter((item) => !recent.has(item[key]));
    const source = pool.length > 0 ? pool : items;
    const rand = typeof rng === 'function' ? rng() : Math.random();
    const idx = Math.min(source.length - 1, Math.floor(rand * source.length));
    return source[idx];
  }

  return {
    getNextThursdayInfo,
    computeMoodMatch,
    hashPin,
    verifyPin,
    isNoteUnlocked,
    formatRemainingLock,
    formatClock,
    pickRandomAvoidingRecent
  };
});
