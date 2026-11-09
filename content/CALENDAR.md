# 30-Day Publishing Calendar — English Educational Shorts

**Start:** Tuesday 2026-11-10 · **Cadence:** 6 uploads/week (Mon–Sat), Sunday = review only
**Slot:** `17:30 UTC` = 12:30 Montréal (EST) / 19:30 Central Europe / 20:30 Gulf.
Lock the slot for 14 days, then adjust using `reports/latest.md` — never change it daily.

> "Trained" consistency matters more than the exact hour. One slot, one series logo, one style.

---

## Week 1 — the five ready-to-shoot episodes

| Date | Day | Episode | Hook family | Series | First-60-min goal |
|---|---|---|---|---|---|
| 2026-11-10 | Tue | `ep-001` Can't tickle yourself | you-test (#3) | Brain Glitches | 20+ comments on the yes/no CTA |
| 2026-11-11 | Wed | `ep-002` Venus day > year | number shock (#2) | Space Math | 20+ comments on 243 vs 117 |
| 2026-11-12 | Thu | `ep-003` Why the sky is dark | false belief (#5) | One Question | push shares, not comments |
| 2026-11-13 | Fri | `ep-004` 3,000-year-old honey | time collapse (#6) | Everyday Impossible | yes/no CTA + Community poll |
| 2026-11-14 | Sat | **Trend slot** (see below) | trend wrapper | any | publish within 24–48 h of the spike |
| 2026-11-15 | Sun | no upload | — | — | run `analytics.py`, fill the scorecard |
| 2026-11-16 | Mon | `ep-005` Baker / baker | name-drop paradox (#18) | Brain Glitches | highest comment-velocity play of the week |

**Before any upload:** set `fact_check.verified = true` in the episode JSON after checking the
`source_hint`. `publish_video.py` refuses to upload unverified claims. That gate exists so one bad
fact never costs the channel its credibility.

---

## The repeatable weekly template (weeks 2–4)

| Day | Slot content | Why |
|---|---|---|
| Mon | **Brain Glitches** (psychology / memory / perception) | Highest comment rate, starts the week strong |
| Tue | **Space Math** (numbers that shouldn't fit) | Highest share rate |
| Wed | **One Question, One Universe** (big paradox) | Best completion/rewatch rate |
| Thu | **Everyday Impossible** (ordinary object, hidden mechanism) | Broadest, most searchable |
| Fri | **Viewer pick** from Monday's pinned comment | Closes the community loop |
| Sat | **Trend slot** — evergreen core, trend wrapper in line 1 only | 24–48 h window |
| Sun | Review: `analytics.py`, kill/scale rules, write next week's 5 scripts | The system improves only here |

Generate a new script shell with:
`python tools/new_episode.py --id ep-006 --topic "why we get goosebumps from music" --series "Brain Glitches"`

---

## Topic bank for weeks 2–4 (evergreen, fact-checkable)

1. Why you can't remember a dream unless you write it down immediately
2. Why a Wimbledon serve and a neutron star share a number
3. The reason mirrors flip left/right but not up/down
4. Why boiling water sometimes freezes faster (and why that's contested)
5. How a bacterium survives a vacuum
6. Why your voice sounds wrong on a recording
7. The day the Earth got 1.8 milliseconds longer
8. Why you always pick the longest checkout line (probability, not luck)
9. Why blue foods are almost never natural
10. Why a photon takes ~100,000 years to leave the Sun (but 8 minutes to reach us)
11. Why you can smell rain before it arrives
12. Why octopus blood is blue (hemocyanin)
13. Why the "invisible gorilla" experiment breaks attention
14. Why the Moon is drifting away ~3.8 cm/year
15. Why you can hear someone smile on the phone

---

## Trend slot rules

- Keep the **mechanism** evergreen; swap only line 1 and the example.
- Publish within 24–48 h of the spike. Miss the window → use the evergreen version.
- Never attach a trend to an unverified claim; never use a tragedy as a hook.

## First-60-minute ritual (every single upload)

1. Publish at the locked slot (`publish_video.py --send`, or Studio).
2. Within 60 s: post the channel's own question comment (`pinned_comment` from the JSON) and **pin it in Studio** — the API cannot pin.
3. Minutes 1–60: reply to every comment with a specific line — `python automation/reply_to_comments.py --send --max-replies 30`.
4. Community-tab poll with the next two topics.
5. 24 h later: `python automation/analytics.py`, then the kill/scale rules in `docs/PLAYBOOK.md` §9.

## Sunday scorecard (copy into `reports/weekly-notes.md`)

```
Week of YYYY-MM-DD
Top hook family: __________   3s hold: ____%   Avg % viewed: ____%
Worst hook family: ________  → retire for 2 weeks
Most-commented episode: ____  → clone its CTA shape next week
Next week's 5 topics: ______________________________________
```
