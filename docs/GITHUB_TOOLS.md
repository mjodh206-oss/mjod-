# GitHub tools that can actually run a YouTube channel

Researched on **2026-11-09** via the GitHub API (stars / last push / license / source files verified
directly, not guessed). Links go to the real repositories.

**Short answer:** the closest thing to "an app that takes over the whole channel" is
**[darkzOGx/youtube-automation-agent](https://github.com/darkzOGx/youtube-automation-agent)** —
MIT, ~5.1k stars, active. It researches → writes hooks/scripts/metadata → produces *real MP4s* →
fact-review → schedules/uploads → reads analytics → syncs comments and **drafts replies**. I wired
it into this repo: `integrations/agent_bridge.py` speaks its documented REST API.

**But read §3 before you ask it to "start publishing immediately".** There are three hard
blockers that no GitHub tool can bypass, plus a deliberate design choice: this agent **requires
your approval before every upload**.

---

## 1. Comparison

| Repo | Stars | Last push | License | Publishing | Comment replies | Shorts | Verdict |
|---|---:|---|---|---|---|---|---|
| [darkzOGx/youtube-automation-agent](https://github.com/darkzOGx/youtube-automation-agent) | 5,095 | 2026-10-27 | MIT | ✅ schedules/uploads *approved* videos | ✅ sync every 4 h, drafts replies, **approval + daily cap 50** | ✅ Shorts Repurposing Studio (9:16 + burned captions) | **Recommended** as the production engine |
| [n8n-io/n8n](https://github.com/n8n-io/n8n) | 209,921 | 2026-11-09 | Fair-code (not OSI) | ✅ native `Video: upload` node | ❌ YouTube node has **no comment resource** (resources are Channel / Playlist / PlaylistItem / Video / VideoCategory — verified in `YouTube.node.ts`) | via HTTP nodes | Good scheduler, needs custom API nodes for replies |
| [Anil-matcha/AI-Youtube-Shorts-Generator](https://github.com/Anil-matcha/AI-Youtube-Shorts-Generator) | 5,864 | 2026-11-09 | MIT | ❌ | ❌ | ✅ LLM highlight detection + Whisper + auto 9:16 crop, local mode | Best *clip factory*, needs a publisher |
| [porjo/youtubeuploader](https://github.com/porjo/youtubeuploader) | 894 | 2026-08-07 | Apache-2.0 | ✅ resumable uploads from disk/URL, bandwidth limit | ❌ | ❌ | Boring, reliable uploader; Go binary |
| [RayVentura/ShortGPT](https://github.com/RayVentura/ShortGPT) | 8,137 | **2025-02-10** | MIT | partial, experimental | ❌ | ✅ | ⚠️ Stale ~21 months, 86 open issues — don't build a channel on it |
| [naqashafzal/AI-Content-Studio](https://github.com/naqashafzal/AI-Content-Studio) | 924 | 2026-10-30 | ⚠️ GitHub reports `NOASSERTION` while the README shows an MIT badge | "manual publishing packages" | ❌ | ✅ | ⚠️ License ambiguity + not actually hands-free |
| [Hao0321/video-autopilot-kit](https://github.com/Hao0321/video-autopilot-kit) | 2,301 | 2026-11-05 | MIT | ❌ (CapCut JSON + ffmpeg only) | ❌ | ✅ | Editing toolkit, not a channel operator |

### Why the recommended one fits *our* channel
Reading its `docs/features.md`, `docs/api.md` and `index.js`:

- **Evidence desk** links each factual claim to a verified source before approval → matches our
  `fact_check` gate exactly.
- **Engagement studio** syncs comments every 4 h, quarantines spam/toxicity, drafts replies in
  channel voice, **never posts without explicit approval**, `youtube.force-ssl` re-auth required,
  daily cap (`ENGAGEMENT_DAILY_REPLY_CAP`, default 50) → same philosophy as our human queue.
- **Shorts Repurposing Studio** turns an approved video into three 9:16 drafts with captions.
- **Autonomous operator** accepts a strategy via `PUT /api/operator/strategy` and has
  `POST /api/operator/start`, `GET /api/readiness`, `POST /api/content/:id/approve`.
- Node ≥ 18.17, self-hosted at `http://localhost:3456`, `x-api-key` header for mutating routes.
- Its own README: *"You approve before anything is published."*

⚠️ It needs Node, an LLM provider key (Gemini has a free tier), and `npx playwright install chromium`
for slideshow rendering. I **cannot run it here**: this sandbox has no LLM key, no browser download
host, and no way to complete Google OAuth on your behalf. The bridge below is ready; the app runs
on **your** machine.

---

## 2. What I integrated into this repo

| File | What it does |
|---|---|
| `integrations/agent_bridge.py` | Generates a **valid** operator strategy from our episodes/calendar (validated against the agent's real enum values in `index.js`: `defaultFormat=explainer`, `defaultLength=short`, `primaryKpi=engagement`, `cadencePerWeek=6`, `status=draft`), exports an episode manifest for its Evidence desk, checks `/api/readiness`, and can `PUT` the strategy with `--send --yes` |
| `automation/run_channel.py` | **One command for the whole loop**: validate → render sheets → publish every queued video → reply to comments → weekly scorecard |
| `content/publish_queue.json` | The drop queue: add a rendered `.mp4` URL + episode id, and the next scheduled run publishes it |
| `.github/workflows/channel-loop.yml` | Runs that loop on schedule (17:25 UTC daily) |
| `docs/ACTIVATION.md` | The exact 15-minute activation checklist |

```bash
# 1. Generate + inspect the strategy (offline, no keys needed)
python integrations/agent_bridge.py strategy

# 2. On the machine running the agent, push it (AGENT_BASE_URL / AGENT_API_KEY env vars)
python integrations/agent_bridge.py strategy --send --yes

# 3. Our own loop, dry run first
python automation/run_channel.py
python automation/run_channel.py --send --yes
```

The bridge **never activates** the strategy (`status: "draft"`) and **never calls**
`/api/operator/start` or `/api/content/:id/approve`. Those stay your decision in the dashboard.

---

## 3. Why "start publishing immediately" is not possible — with any tool

These are Google-side facts, not limitations of this repo:

1. **OAuth consent needs your browser.** The authorization is a grant *you* create in Google Cloud
   Console and approve while logged into the channel's Google account. It cannot be handed to me
   in chat, and I will never ask for a password, token, or API key. You store it as a GitHub
   Secret / local `token.json`; I never see it.
2. **New API projects are forced to private.** YouTube's
   [2020 policy](https://developers.google.com/youtube/v3/revision_history#july-28,-2020)
   (quoted in `porjo/youtubeuploader`'s README): uploads via `videos.insert` from **unverified**
   API projects created after 28 July 2020 are restricted to *private* until the project passes a
   compliance audit. So a brand-new project will "publish" invisible videos. Request the audit early.
3. **Testing-mode OAuth expires in 7 days.** While the consent screen is in Testing, Google expires
   the sign-in weekly (the agent's README says so explicitly). An always-on channel needs either a
   re-auth each week or a published/verified OAuth app — and YouTube scopes are *restricted*
   scopes, which can require Google's third-party assessment.
4. **Quota:** 10,000 units/day by default; one upload = 1,600 units → roughly **6 uploads/day**
   (`porjo` README confirms). Our 6/week calendar is comfortable; a "post 20 a day" plan is not.
5. **No video files exist yet.** The five episodes here are scripts, not rendered MP4s. Even a
   fully automatic agent needs either your own footage or an LLM/TTS/image provider key to make one.
6. **The approval gate is intentional.** Both our system and the recommended agent refuse to
   publish unverified facts. A channel that auto-posts unverified AI facts is the fastest route to
   a trust collapse (and potentially a spam policy strike).

So the honest "immediate start" is: **you do the 15-minute activation** (OAuth + Secrets + verify
sources + drop one rendered video), and from that moment the scheduled loop publishes, replies and
reports without you touching it again. I built everything up to that line; I can't cross it for you.

---

## 4. Risk notes on the wider ecosystem

Most "fully automatic YouTube AI channel" repos are recent, star-heavy, and generate generic
voiceover + stock footage. They optimise *volume*, which is exactly what YouTube's
"inauthentic / repetitious content" policies penalise. Three filters before adopting any of them:

- **Is there a license file**, or only a badge in the README? (`AI-Content-Studio`: badge ≠ GitHub license.)
- **Is the last push recent and are there real tests?** (`ShortGPT`: last push Feb 2025.)
- **Does it require approval before upload, and does it fact-check?** If not, it will burn the channel.

Our `fact_check.verified` gate, reply human-queue, and daily caps exist for this reason.
