#!/usr/bin/env python3
"""Shared auth + helpers for the YouTube automation scripts.

Credentials are read from environment variables (local .env / GitHub Actions Secrets).
They are NEVER written into this repository. Two env vars, either a filesystem path to a
JSON file OR the raw JSON string itself:

    GOOGLE_CLIENT_SECRET_JSON / YT_CLIENT_SECRET_JSON   -> OAuth client config
    GOOGLE_TOKEN_JSON         / YT_TOKEN_JSON           -> authorized user token (with refresh_token)

Run `python automation/auth_setup.py` once to produce the token JSON.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
EPISODE_DIR = ROOT / "content" / "episodes"
STATE_DIR = ROOT / "state"
REPORT_DIR = ROOT / "reports"
CONTENT_DIR = ROOT / "content"

SCOPES = [
    "https://www.googleapis.com/auth/youtube.upload",       # videos.insert
    "https://www.googleapis.com/auth/youtube.force-ssl",   # comments.insert / moderation
    "https://www.googleapis.com/auth/youtube.readonly",    # channels / videos / commentThreads
    "https://www.googleapis.com/auth/yt-analytics.readonly",  # retention metrics
]

# Default YouTube Data API quota is 10,000 units/day.
# videos.insert = 1600 | thumbnails.set = 50 | comments.insert = 50 | most list calls = 1-3
QUOTA = {"videos.insert": 1600, "thumbnails.set": 50, "comments.insert": 50, "list": 1}


def read_secret(*names: str) -> dict[str, Any] | None:
    """Return parsed JSON from the first env var that is set (path OR inline JSON)."""
    for name in names:
        raw = os.environ.get(name)
        if not raw or not raw.strip():
            continue
        raw = raw.strip()
        if raw.startswith("{"):
            return json.loads(raw)
        path = Path(raw).expanduser()
        if not path.exists():
            raise FileNotFoundError(f"{name} points to a missing file: {path}")
        return json.loads(path.read_text(encoding="utf-8"))
    return None


def load_dotenv() -> None:
    """Minimal .env loader (no third-party dependency). Never overrides real env vars."""
    env = ROOT / ".env"
    if not env.exists():
        return
    for line in env.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip().strip('"').strip("'")
        os.environ.setdefault(key, value)


def credentials() -> Any:
    """Build a refreshable google.oauth2.credentials.Credentials object."""
    load_dotenv()
    token = read_secret("GOOGLE_TOKEN_JSON", "YT_TOKEN_JSON")
    client = read_secret("GOOGLE_CLIENT_SECRET_JSON", "YT_CLIENT_SECRET_JSON")

    try:
        from google.auth.transport.requests import Request
        from google.oauth2.credentials import Credentials
    except ImportError as exc:
        raise SystemExit(
            "Missing dependencies. Run: python3 -m pip install -r requirements.txt"
        ) from exc

    if token:
        creds = Credentials.from_authorized_user_info(token, SCOPES)
    elif client and sys.stdin.isatty():
        from google_auth_oauthlib.flow import InstalledAppFlow
        flow = InstalledAppFlow.from_client_config(client, SCOPES)
        creds = flow.run_local_server(port=0, open_browser=True)
    else:
        raise SystemExit(
            "No YouTube credentials found.\n"
            "  1) python automation/auth_setup.py        (one-time, opens a browser)\n"
            "  2) Set GOOGLE_TOKEN_JSON / YT_TOKEN_JSON (JSON or path) locally,\n"
            "     or add the YT_TOKEN_JSON GitHub Actions Secret.\n"
            "Never paste tokens into chat or commit them to Git."
        )

    if creds.expired and creds.refresh_token:
        creds.refresh(Request())
    if not creds.valid:
        raise SystemExit("Credentials are invalid. Re-run: python automation/auth_setup.py")
    return creds


def youtube() -> Any:
    from googleapiclient.discovery import build
    return build("youtube", "v3", credentials=credentials(), cache_discovery=False)


def analytics_api() -> Any:
    from googleapiclient.discovery import build
    return build("youtubeAnalytics", "v2", credentials=credentials(), cache_discovery=False)


def channel_id(yt: Any) -> str:
    configured = os.environ.get("YOUTUBE_CHANNEL_ID", "").strip()
    if configured:
        return configured
    resp = yt.channels().list(part="id", mine=True).execute()
    items = resp.get("items", [])
    if not items:
        raise SystemExit("Could not resolve the channel id (channels.list mine=True returned nothing)")
    return items[0]["id"]


def load_episode(episode_id: str) -> tuple[Path, dict[str, Any]]:
    matches = sorted(p for p in EPISODE_DIR.glob(f"{episode_id}*.json") if not p.name.startswith("_"))
    if not matches:
        raise SystemExit(f"Unknown episode '{episode_id}'. Files: {[p.name for p in EPISODE_DIR.glob('ep-*.json')]}")
    path = matches[0]
    return path, json.loads(path.read_text(encoding="utf-8"))


def load_all_episodes() -> list[tuple[Path, dict[str, Any]]]:
    out = []
    for path in sorted(EPISODE_DIR.glob("ep-*.json")):
        out.append((path, json.loads(path.read_text(encoding="utf-8"))))
    return out


def build_description(ep: dict[str, Any]) -> str:
    parts = [ep["description"].strip(), "", " ".join(ep["hashtags"])]
    return "\n".join(parts).strip()


def state_path(name: str) -> Path:
    override = os.environ.get("YT_STATE_PATH")
    if override and name == "replied":
        return Path(override).expanduser()
    return STATE_DIR / f"{name}.json"


def load_state(name: str) -> Any:
    path = state_path(name)
    if not path.exists():
        return {} if name == "published" else []
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {} if name == "published" else []


def save_state(name: str, data: Any) -> None:
    path = state_path(name)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def add_send_flags(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--send", action="store_true", help="actually call the API (default: dry run)")
    parser.add_argument("--yes", action="store_true", help="skip the interactive confirmation (CI)")
    parser.add_argument("--episode", help="episode id, e.g. ep-001")


def require_confirmation(args: argparse.Namespace, summary: str) -> None:
    if not args.send:
        print("DRY RUN — no API writes. Add --send --yes to execute for real.\n")
        return
    if args.yes:
        print("LIVE MODE (--send --yes). " + summary + "\n")
        return
    if not sys.stdin.isatty():
        raise SystemExit("Refusing to post without confirmation. Pass --yes explicitly in non-interactive mode.")
    print("LIVE MODE: " + summary)
    answer = input('Type "POST" to continue: ').strip()
    if answer != "POST":
        raise SystemExit("Aborted; nothing was sent.")


def paginate(resource: Any, request: Any) -> list[dict[str, Any]]:
    """Follow nextPageToken for a googleapiclient resource (e.g. yt.commentThreads())."""
    items: list[dict[str, Any]] = []
    while request is not None:
        response = request.execute()
        items.extend(response.get("items", []))
        request = resource.list_next(request, response)
    return items
