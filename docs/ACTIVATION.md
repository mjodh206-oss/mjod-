# Activation checklist — what "the authorization" actually means

**The authorization is not a password you give me.** It's an OAuth grant you create in your own
Google Cloud project while logged into the channel's Google account. I never receive it, never
store it, and never ask for it in chat. You put it in GitHub repo Secrets (or a local `token.json`)
and the scripts read it from there.

Time: ~15 minutes, once. After this, the loop runs itself.

---

## A. Google Cloud (do this in your browser)

1. Create a project → enable **YouTube Data API v3** and **YouTube Analytics API**.
2. OAuth consent screen → add the Google account that owns the channel as a **Test user**
   (otherwise `403: access_denied`).
3. Credentials → **OAuth client ID → Desktop app** → download the JSON.
4. ⚠️ **In Testing mode the grant expires after 7 days.** For an always-on channel, either
   re-authorise weekly or publish the consent screen to Production (YouTube scopes are *restricted*
   scopes, so Google may ask for a third-party assessment).
5. ⚠️ **Request the API compliance audit early.** Since July 2020, `videos.insert` from a new
   *unverified* project produces **private** videos. Until the audit passes, uploads won't be public.
6. Quota is 10,000 units/day → ~6 uploads/day. Our calendar (6/week) fits easily.

## B. Local, one time

```bash
python3 -m pip install -r requirements.txt
python automation/auth_setup.py --client-secret /path/to/client_secret.json
cp .env.example .env           # set GOOGLE_TOKEN_JSON to the absolute path of token.json
```

`token.json`, `client_secret*.json`, `.env` and all videos are gitignored.

## C. GitHub repo Secrets (Settings → Secrets and variables → Actions)

| Secret | Value |
|---|---|
| `YT_CLIENT_SECRET_JSON` | contents of the downloaded client JSON |
| `YT_TOKEN_JSON` | contents of `token.json` |

Optional, only if you also run the external agent: `AGENT_BASE_URL`, `AGENT_API_KEY`.

## D. Content gates (five minutes)

```bash
python tools/validate_content.py
```

For each of `content/episodes/ep-001…005.json`:
1. open `fact_check.source_hint`, verify the claim against the primary source,
2. set `"verified": true` (the publisher **refuses** `false`),
3. leave `status: "ready"`.

## E. Give the loop something to publish

Render one Short (1080×1920, ≤60 s), host the `.mp4` at a direct HTTPS URL, then add an item to
`content/publish_queue.json`:

```json
{
  "episode_id": "ep-001",
  "video_url": "https://your-storage/ep-001.mp4",
  "thumbnail_url": null,
  "publish_at": null,
  "enabled": true
}
```

`publish_at: null` = publish at the next scheduled run. Locally you can use `video_path` instead
of `video_url`. Nothing large is ever committed to Git.

## F. Flip the switch

```bash
# Preview the whole loop offline (no credentials needed)
python automation/run_channel.py

# Live, right now
python automation/run_channel.py --send --yes
```

Then enable `.github/workflows/channel-loop.yml` (daily 17:25 UTC, 5 min before the calendar
slot) — it will publish queued videos, reply to comments and write the scorecard on its own.
Publishing workflows are `workflow_dispatch` / scheduled with **manual queue entries**; nothing
uploads a video that you didn't put in the queue.

## G. Optional: the external GitHub agent

```bash
git clone https://github.com/darkzOGx/youtube-automation-agent.git ~/youtube-agent
cd ~/youtube-agent && npm install && npm run walkthrough && npm start
# then, from this repo:
export AGENT_BASE_URL=http://localhost:3456 AGENT_API_KEY=...
python integrations/agent_bridge.py readiness
python integrations/agent_bridge.py strategy --send --yes   # saves as DRAFT, does not activate
```

Approve videos and replies in its dashboard. See `docs/GITHUB_TOOLS.md` for the full comparison.

---

## What starts automatically, and what never does

| | |
|---|---|
| ✅ Automatic after activation | validation, filming sheets, queued uploads, metadata/thumbnail, channel question, safe comment replies, analytics report |
| 🔒 Always your decision | OAuth grant, fact verification, queue entries, pinning comments (no API for it), approving the external agent's videos/replies, activating its operator |
| 🚫 Never | storing credentials in chat or Git, bypassing `fact_check.verified`, replying to legal/medical/financial comments, deleting or hiding criticism |
