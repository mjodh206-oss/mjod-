"""Timeline construction — the single source of truth for AV sync.

Speech segments were measured from the generated voiceover (build/vo_analysis.json).
Every visual event is anchored to a real audio event so nothing drifts.
"""
import json
import os

VO_ANALYSIS = "build/vo_analysis.json"
SPEED = 1.0           # VO already time-warped with atempo 1.09 (build/vo)
LEAD_IN = 0.38        # beat before a section's voice starts
LEAD_OUT = 0.24       # beat after it ends
HOOK_PRE = 0.28

with open(VO_ANALYSIS) as f:
    _RAW = json.load(f)


def clip(name):
    d = _RAW[name]
    dur = d["dur"]
    segs = [(s, e) for s, e in d["segs"]]
    return dur, segs


SECTIONS = []


def add(**kw):
    SECTIONS.append(kw)


# ------------------------------------------------------------------ content
NUMERALS = ["", "01", "02", "03", "04", "05", "06", "07"]
AR_NUM = ["", "الأولى", "الثانية", "الثالثة", "الرابعة", "الخامسة", "السادسة", "السابعة"]

TIPS = [
    dict(n=1, vo="vo_01_socrates", who="سقراط", who_en="SOCRATES · 470 BC",
         quote="اعرف نفسك", quote2="الحياة التي لا تُفحَص… لا تستحق أن تُعاش",
         accent=(255, 214, 130), glow=(255, 160, 60)),
    dict(n=2, vo="vo_02_marcus", who="ماركوس أوريليوس", who_en="MARCUS AURELIUS · 170 AD",
         quote="لديك سلطة على عقلك", quote2="لا على ما يحدث لك",
         accent=(255, 226, 168), glow=(255, 176, 90)),
    dict(n=3, vo="vo_03_seneca", who="سينيكا", who_en="SENECA · 50 AD",
         quote="نحن نتألم في الخيال", quote2="أكثر مما نتألم في الواقع",
         accent=(206, 226, 255), glow=(120, 180, 255)),
    dict(n=4, vo="vo_04_aristotle", who="أرسطو", who_en="ARISTOTLE · 340 BC",
         quote="نحن ما نفعله مراراً", quote2="فالتميّز… ليس فعلاً، بل عادة",
         accent=(255, 224, 178), glow=(255, 190, 110)),
    dict(n=5, vo="vo_05_nietzsche", who="نيتشه", who_en="NIETZSCHE · 1889",
         quote="من يملك سبباً يعيش من أجله", quote2="يتحمّل أيّ طريقةٍ للحياة",
         accent=(255, 196, 196), glow=(255, 110, 90)),
    dict(n=6, vo="vo_06_ali", who="الإمام علي", who_en="ALI IBN ABI TALIB · 650 AD",
         quote="قيمةُ كلِّ امرئٍ ما يُحسِن", quote2="اسأل نفسك… ماذا تُتقن؟",
         accent=(198, 255, 226), glow=(90, 230, 190)),
    dict(n=7, vo="vo_07_truth", who="الحقيقة", who_en="THE TRUTH · NOW",
         quote="لا أحد قادم لينقذك", quote2="من تكون غداً… هو ما تفعله الآن",
         accent=(255, 240, 225), glow=(255, 90, 40)),
]

# ------------------------------------------------------------------ build
t = 0.0
hook_dur, hook_segs = clip("vo_00_hook")
hook_start = HOOK_PRE
add(kind="hook", idx=0, vo="vo_00_hook", start=0.0, vo_start=hook_start,
    vo_end=hook_start + hook_dur, end=hook_start + hook_dur + 0.20,
    segs=hook_segs, dur=hook_dur)
t = hook_start + hook_dur + 0.20

for tip in TIPS:
    d, segs = clip(tip["vo"])
    vs = t + LEAD_IN
    add(kind="tip", idx=tip["n"], start=t, vo_start=vs, vo_end=vs + d,
        end=vs + d + LEAD_OUT, segs=segs, dur=d, **tip)
    t = vs + d + LEAD_OUT

out_d, out_segs = clip("vo_08_outro")
END_HOLD = 2.2
add(kind="outro", idx=8, vo="vo_08_outro", start=t, vo_start=t + LEAD_IN, vo_end=t + LEAD_IN + out_d,
    end=t + LEAD_IN + out_d + END_HOLD, segs=out_segs, dur=out_d)
TOTAL = t + LEAD_IN + out_d + END_HOLD

# global beats
IMPACTS = [s["start"] + 0.02 for s in SECTIONS]          # section slams
NUM_IMPACTS = [s["vo_start"] + 0.30 for s in SECTIONS if s["kind"] == "tip"]
TRUTH = [s for s in SECTIONS if s["kind"] == "tip" and s["idx"] == 7][0]

if __name__ == "__main__":
    for s in SECTIONS:
        print(f'{s["kind"]:6s} {s["idx"]} {s["start"]:7.2f} vo {s["vo_start"]:7.2f} end {s["end"]:7.2f}')
    print("TOTAL", round(TOTAL, 2), "frames", int(TOTAL * 30))


# ------------------------------------------------------------------ word alignment
AR_LEN_W = {"ا": 1, "أ": 1, "إ": 1, "آ": 1, "ل": 1, "م": 1, "ن": 1, "و": 0.9, "ي": 0.9,
            "ب": 0.9, "ت": 0.9, "ث": 0.9, "ج": 0.9, "ح": 0.9, "خ": 0.9, "د": 0.9,
            "ذ": 0.9, "ر": 0.9, "ز": 0.9, "س": 0.9, "ش": 0.9, "ص": 0.9, "ض": 0.9,
            "ط": 0.9, "ظ": 0.9, "ع": 0.9, "غ": 0.9, "ف": 0.9, "ق": 0.9, "ك": 0.9,
            "ه": 0.8, "ة": 0.8, "ء": 0.6, "ئ": 0.7, "ؤ": 0.7, "ى": 0.8, "ً": 0, "ٌ": 0,
            "ٍ": 0, "َ": 0, "ُ": 0, "ِ": 0, "ّ": 0, "ْ": 0, "…": 0, ".": 0, "،": 0, "؟": 0}


def word_weight(w):
    return max(0.6, sum(AR_LEN_W.get(ch, 0.8) for ch in w))


def align(words, segs, t0):
    """Distribute words across measured speech segments proportionally.

    Returns list of (word, start, end) in absolute seconds.
    """
    seg_durs = [max(0.08, e - s) for s, e in segs]
    total_seg = sum(seg_durs)
    weights = [word_weight(w) for w in words]
    total_w = sum(weights)
    out = []
    wi = 0
    for (s, e), d in zip(segs, seg_durs):
        n_share = d / total_seg * total_w
        take = []
        acc = 0.0
        while wi < len(words) and (acc + weights[wi] * 0.5 <= n_share) and len(take) < len(words):
            acc += weights[wi]
            take.append(wi)
            wi += 1
        if not take and wi < len(words):
            take = [wi]
            acc = weights[wi]
            wi += 1
        # distribute inside segment by weight
        tot = sum(weights[i] for i in take) or 1
        cursor = s
        for i in take:
            span = d * weights[i] / tot
            out.append((words[i], t0 + cursor, t0 + cursor + span))
            cursor += span
    # leftovers
    if wi < len(words):
        s, e = segs[-1]
        out.append((words[wi], t0 + e - 0.2, t0 + e))
    return out
