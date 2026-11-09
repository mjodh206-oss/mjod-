# Automation — how the "takeover" actually runs

These scripts talk to **your** YouTube account through the official YouTube Data API v3.
They do not hold your password, and no credential is ever stored in this repository.
You add the OAuth secrets once; after that publishing and comment replies can run on a schedule
from GitHub Actions.

> **Honest boundaries**
> - Nobody (agent or tool) can log into your YouTube account from here without your OAuth grant.
> - YouTube's API **cannot pin comments** — pin the channel's own question in YouTube Studio.
> - YouTube's API cannot generate a trend. It can only execute the system built in `docs/`.
> - All write scripts are **dry-run by default**. `--send --yes` is required to post anything.

---

## 1. One-time setup (about 10 minutes, done on your machine)

**Direct links** (Google Cloud console, repo Secrets, workflow run page): `docs/AUTH_LINKS.md`.
To print the exact Google consent URL instead of opening a browser, add `--no-browser`
(and optionally `--port 8080`) to `auth_setup.py` below.

1. Google Cloud Console → create a project → enable **YouTube Data API v3** and
   **YouTube Analytics API**.
2. OAuth consent screen (External, add your own account as a test user) → create an
   **OAuth client ID → Desktop app** → download `client_secret.json`.
3. `python3 -m pip install -r requirements.txt`
4. `python automation/auth_setup.py --client-secret /path/to/client_secret.json`
   A browser opens once. The script writes `token.json` (gitignored) and prints the token JSON.
5. Copy `.env.example` → `.env` and set `GOOGLE_TOKEN_JSON=/absolute/path/to/token.json`.
6. For GitHub Actions, add two repository **Secrets** (Settings → Secrets and variables → Actions):
   - `YT_CLIENT_SECRET_JSON` = contents of `client_secret.json`
   - `YT_TOKEN_JSON` = contents of `token.json`

Never paste either value into a chat, an issue, or a commit.

### Quota note
Default quota is **10,000 units/day**: an upload costs 1,600, each comment reply 50,
a thumbnail 50, list calls ~1. A 6-Shorts/week channel with 30 replies/day uses
roughly 2,000–3,000 units/day — comfortably inside the limit (~6 uploads/day max).

### Two Google-side blockers before anything is really public
- Uploads from a **new, unverified** API project are forced to **private** until the project
  passes YouTube's compliance audit (policy since 28 July 2020). Request the audit early.
- While the OAuth consent screen is in **Testing**, the grant **expires after 7 days**. An
  always-on channel needs a published/verified OAuth app or a weekly re-auth.
Both apply to every tool, including the external GitHub agent — details in `docs/ACTIVATION.md`.

---

## 2. The daily loop

**One command does all of it** — `python automation/run_channel.py` (dry run) or
`python automation/run_channel.py --send --yes` (live). It runs the steps below in order,
reads `content/publish_queue.json`, caps uploads at 2 per run, and checks the live channel for
duplicate titles so a fresh CI runner can never double-upload. Add an enabled item to the queue
first; an empty queue publishes nothing.

```bash
# 1. Write the next script (fills the 8-beat timeline for you)
python tools/new_episode.py --id ep-006 --topic "why your voice sounds wrong on a recording" --series "Brain Glitches"

# 2. Generate hook drafts, then rewrite 3 of them in your own voice
python tools/hook_engine.py "why your voice sounds wrong on a recording" --count 8

# 3. Validate the whole content pack (CI runs this too)
python tools/validate_content.py

# 4. Render the filming sheets
python tools/render_scripts.py

# 5. Fact-check, then set fact_check.verified = true and status = "ready" in the JSON
#    (publish_video.py REFUSES unverified claims)

# 6. Upload: preview first, then send
python automation/publish_video.py --episode ep-006 --file ~/renders/ep-006.mp4
python automation/publish_video.py --episode ep-006 --send --yes --thumbnail ~/renders/ep-006.jpg

# 7. First 60 minutes: pin the channel question in Studio, then reply to comments
python automation/reply_to_comments.py
python automation/reply_to_comments.py --send --yes --max-replies 30

# 8. Sunday review
python automation/analytics.py --days 28
```

Videos stay **out of Git** (`.gitignore` excludes `content/videos/` and `*.mp4`). For the
scheduled publishing workflow, provide a downloadable `video_url`; nothing large is committed.

---

## 3. GitHub Actions (optional, after pushing this branch)

| Workflow | Trigger | What it does | Needs secrets |
|---|---|---|---|
| `validate-content.yml` | push / PR | runs unit tests + content validator | none |
| `channel-loop.yml` | **daily 17:25 UTC** + manual | the whole loop: validate → sheets → queued uploads → replies → scorecard | same two secrets |
| `reply-to-comments.yml` | every 6 h + manual | posts up to 30 safe replies | `YT_CLIENT_SECRET_JSON`, `YT_TOKEN_JSON` |
| `publish-short.yml` | manual `workflow_dispatch` | downloads a rendered mp4 from a URL and uploads it | same two secrets |
| `weekly-report.yml` | Mondays 09:00 UTC | writes `reports/latest.md` as a downloadable artifact | same two secrets |

Reply deduplication does **not** depend on local state: the script checks the API for threads
the channel has already answered. `state/replied.json` is only a local cache, so Actions runs
are safe even with a fresh runner.

---

## 4. Safety gates (deliberate, don't remove them)

| Gate | Where |
|---|---|
| Dry run by default | `common.require_confirmation` |
| Unverified claims cannot be published | `publish_video.py` reads `fact_check.verified` |
| Double-upload protection | `state/published.json` + `youtube_video_id` |
| Sensitive comments → human queue | `content/comments/replies.json` → `human_queue_keywords` |
| Spam / links / own comments skipped | `reply_to_comments.py` |
| Reply caps (per run + hostile comments) | `rules.max_replies_per_run`, `negative.max_per_run` |
| No fact invention, no advice, no credential mentions | `rules.never` |

If a gate blocks you, fix the input — don't delete the gate. Educational channels live or die
on trust, and one confident wrong fact costs more than a missed upload.

---

## 5. Optional: the external GitHub agent

`docs/GITHUB_TOOLS.md` compares the real options. If you run
[darkzOGx/youtube-automation-agent](https://github.com/darkzOGx/youtube-automation-agent)
on your machine (Node ≥18.17, `npm start` → `http://localhost:3456`), this repo can talk to it:

```bash
python integrations/agent_bridge.py strategy              # build a valid draft strategy offline
python integrations/agent_bridge.py manifest             # 5 episodes for its Evidence desk
python integrations/agent_bridge.py readiness            # GET /api/readiness
AGENT_BASE_URL=http://localhost:3456 AGENT_API_KEY=... \
  python integrations/agent_bridge.py strategy --send --yes   # PUT as DRAFT only
```

The bridge never calls `/api/operator/start` and never approves a video or a reply. Its agent
requires human approval before publishing too — which is why it's the one worth recommending.
Comparison, star counts, licenses and caveats: `docs/GITHUB_TOOLS.md`.
