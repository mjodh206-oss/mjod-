# The Shorts Operating Playbook
**Channel type:** educational / facts / "wait, what?" · **Format:** 9:16 Shorts, 24–45 s · **Language:** English

> Honest premise: nobody can *guarantee* a trend. YouTube decides distribution from viewer
> behaviour, not from a title. What this system does is maximise the only inputs you control:
> the first second, retention to the last second, packaging CTR, and the first-hour comment
> velocity. Everything below is engineered around those four.

---

## 1. The four signals that actually move a Short

| Signal | What YouTube measures | What we control |
|---|---|---|
| **Hook / 3-second hold** | % of impressions still watching at 3 s | First frame + first spoken line (no greeting, no logo) |
| **Retention + loops** | average % viewed, rewatches | Open loops, pattern interrupts, payoff, seamless last→first frame |
| **Packaging** | swipe-away rate, CTR of title/thumbnail on shelf | Title, 3-word thumbnail text, first comment |
| **First-hour velocity** | views/min, comments/min, shares | Publishing slot, pinned question, reply to every comment in 60 min |

A Short that holds 80 %+ of viewers AND gets replays gets pushed to a wider test pool. A Short
that loses 40 % of viewers in the first 3 seconds is dead regardless of the topic. **Fix the first
second before you fix anything else.**

---

## 2. Second-by-second architecture (the template every episode follows)

| Time | Job | Device | Rule |
|---|---|---|---|
| `0.0–0.3 s` | Stop the thumb | Hard visual change + 3–5 word on-screen text | Motion in frame 1, never a title card |
| `0.3–3 s` | The hook | Contradiction / number shock / "you" test | ≤ 12 words, spoken AND on screen |
| `3–8 s` | Open the loop | "Here's the part nobody tells you" | Promise a payoff, don't deliver it |
| `8–18 s` | Stakes + detail 1 | Fast cut, new angle, number on screen | Cut every 1.5–2.5 s |
| `18–22 s` | **Re-hook** | Escalate: "but that's not even the weird part" | Mandatory. This is where most Shorts die |
| `22–36 s` | Detail 2 + mechanism | Explain the *why*, one idea only | No jargon without a visual |
| `36–44 s` | Payoff | Close the loop with the surprising answer | Deliver exactly what was promised |
| `44–50 s` | Loop/CTA | Last frame visually matches frame 1; one question | One question only, answerable in one word |

**Rule of thumb:** one idea, one payoff, one CTA. If a script needs two facts to make sense, it is
two Shorts.

---

## 3. Retention devices (use at least 4 per Short)

1. **Open loop** — state the oddity, withhold the cause until the payoff.
2. **Pattern interrupt** — camera angle, zoom, colour, sound hit, or text change every 1.5–2.5 s.
3. **Progressive numbers** — "243 days… 225 days… 117 days" — numbers force the eye to track.
4. **Re-hook at 50 %** — explicitly re-raise the stakes halfway.
5. **The "you" test** — make the viewer do something physical (try to tickle themselves, hold
   their breath, look at a word). Participation beats passive watching.
6. **False-statement correction** — state a plausible wrong belief, then break it.
7. **Seamless loop** — the final visual and final line land back on frame 1, so a replay feels
   like a continuation, not a restart.
8. **Caption pacing** — 2–4 words per caption, verb highlighted, never a wall of text.

---

## 4. Production checklist

- [ ] 1080 × 1920, 9:16, 30 fps minimum, subject inside the central safe zone (avoid UI overlap on right/bottom).
- [ ] **No intro, no "hey guys", no channel logo, no "today we'll talk about".** Frame 1 is content.
- [ ] Hook is ≤ 12 words and spoken within the first 0.5 s.
- [ ] Burned-in captions, 2–4 words, high contrast, positioned above the lower third.
- [ ] VO ≈ 2.3–2.8 words/second. Read the script out loud with a timer; if you rush, cut words.
- [ ] A visual or audio change at least every 2.5 s.
- [ ] Music bed under the VO, one whoosh/hit on each pattern interrupt.
- [ ] End frame echoes the opening frame for the loop.
- [ ] Fact-check the claim before publishing (see §10). Educational channels lose trust permanently on one bad fact.

---

## 5. Packaging

**Titles** — 45–70 characters, front-load the oddity, no clickbait that the payoff doesn't honour.
Three variants per episode live in `content/episodes/*.json`; track which pattern wins.

**Thumbnail text** — 3 words max, one number if possible. Shorts often show on the channel grid
and in search, so the grid still needs to read as a curated series.

**Hashtags** — `#shorts` + 2–3 topical tags, no more. Repeated tag stuffing does nothing.

**Description** — first line restates the hook, then 1–2 lines of context, then the CTA question,
then credits/source hints. Hashtags appended by `publish_video.py`.

**Pinned comment / first comment** — post it from the channel account immediately. It should be a
question with a low-effort answer ("Which one should I do next: A or B?"). This is the single
highest-leverage comment move. YouTube's API cannot pin comments — post it with the channel
account, then pin it in YouTube Studio.

---

## 6. Distribution ritual: the first 60 minutes

This is the part that most resembles "running the channel". Do it for every upload:

1. Publish in the slot from `content/CALENDAR.md` (consistency trains the audience and the system).
2. Within 60 s: post the channel's own question comment and pin it in Studio.
3. Minutes 1–60: **reply to every comment** with a specific, non-generic line from
   `content/comments/replies.json` (or run `reply_to_comments.py --send`). Early replies create
   early threads, and threads create return visits.
4. Post a Community-tab poll with the two next topic options.
5. Do **not** delete critical comments. A polite correction reply outperforms deletion.
6. At 24 h: log the numbers with `automation/analytics.py`; apply the kill/scale rules in §9.

---

## 7. Comment strategy (algorithm + community)

- Replies should **add one new fact or ask one question** — "Thanks!" is a wasted impression.
- Never argue, never dunk, never reply to bait. Route hate/spam/medical/legal to the human queue.
- Corrections: thank them, state the corrected fact, and pin or heart it. Trust > being right.
- Banned: fake controversy, "sub for sub", engagement-bait giveaways, copied replies on 20 comments.
- Full rules, categories, and safe templates: `content/comments/replies.json`.

---

## 8. Trend-jacking without becoming a news channel

Educational evergreen content wins long-term; trend wrappers win the first 48 hours. Combine them:

- Keep the **core mechanism** evergreen (cerebellum prediction, Olbers' paradox, memory encoding).
- Swap only the **first line and the example** to a topic currently in the conversation
  (a new space mission, a viral clip, a seasonal event).
- Window: publish within 24–48 h of the spike. After that, use the evergreen version.
- Never wrap a trend around an unverified claim.

---

## 9. Metrics & the weekly loop

| Metric | Floor | Good | Action if below floor |
|---|---|---|---|
| 3 s hold | 65 % | 75 %+ | Rewrite the hook, re-upload as a new Short |
| Average % viewed | 70 % | 85 %+ | Cut 5–8 s; add a re-hook at 50 % |
| Rewatch/loop rate | 3 % | 8 %+ | Fix the last→first frame match |
| Engaged views / 1k | 25 | 50+ | Sharpen the pinned question |
| Comments / 1k views | 3 | 8+ | Make the CTA a binary question |

Every Monday: export `reports/latest.md`, mark the top 20 % of episodes, and clone their **hook
family and pacing** — not their topic — into next week's scripts. Kill the bottom 20 % families.
A/B hooks across episodes (3 variants per episode in JSON), never by uploading the same video
three times.

---

## 10. Guardrails (non-negotiable)

- Every episode JSON carries a `fact_check` block. `"verified": false` means **do not publish**.
- Prefer primary sources; state uncertainty ("researchers found", "one explanation is") instead
  of false certainty.
- No health, medical, financial, or legal advice in Shorts.
- No fabricated statistics, no AI-generated "footage" presented as real evidence.
- Respect YouTube's spam/deceptive-practices policies — misleading hooks that the payoff doesn't
  honour destroy retention and can limit reach.
- Automation is **human-in-the-loop by default**: every publishing/reply script is `--dry-run`
  until you pass `--send`. No credentials are stored in this repo.
