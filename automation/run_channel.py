#!/usr/bin/env python3
"""ONE COMMAND for the whole channel loop.

    validate -> filming sheets -> publish queued Shorts -> reply to comments -> scorecard

Dry run by default. `--send --yes` executes for real. Publishing is driven by
content/publish_queue.json: nothing uploads unless you put a video in that queue.

Usage:
    python automation/run_channel.py                          # preview everything
    python automation/run_channel.py --send --yes             # run the loop now
    python automation/run_channel.py --steps publish,replies
    python automation/run_channel.py --max-publishes 1
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from common import (
    ROOT,
    load_episode,
    load_state,
    require_confirmation,
    youtube,
)

QUEUE_FILE = ROOT / "content" / "publish_queue.json"
VIDEO_DIR = ROOT / "content" / "videos"
ALL_STEPS = ["validate", "render", "publish", "replies", "report"]
MAX_DOWNLOAD_BYTES = 512 * 1024 * 1024


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def parse_time(value: str | None) -> datetime | None:
    if not value:
        return None
    dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def validate_queue(data: dict[str, Any]) -> list[str]:
    """Return a list of schema errors (also used by tests)."""
    errors: list[str] = []
    items = data.get("items")
    if not isinstance(items, list):
        return ["queue.items must be a list"]
    for i, item in enumerate(items):
        tag = f"items[{i}]"
        if not isinstance(item, dict):
            errors.append(f"{tag} must be an object")
            continue
        eid = item.get("episode_id", "")
        if not isinstance(eid, str) or not eid.startswith("ep-"):
            errors.append(f"{tag}.episode_id must look like ep-001")
        if not isinstance(item.get("enabled"), bool):
            errors.append(f"{tag}.enabled must be true/false")
        if item.get("enabled"):
            url, path = item.get("video_url"), item.get("video_path")
            if not url and not path:
                errors.append(f"{tag} needs video_url or video_path")
            if url and not str(url).startswith("https://"):
                errors.append(f"{tag}.video_url must be https://")
        for key in ("publish_at",):
            if item.get(key):
                try:
                    parse_time(item[key])
                except ValueError:
                    errors.append(f"{tag}.{key} is not ISO-8601")
        thumb = item.get("thumbnail_url")
        if thumb and not str(thumb).startswith("https://"):
            errors.append(f"{tag}.thumbnail_url must be https://")
    return errors


def load_queue() -> tuple[list[dict[str, Any]], list[str]]:
    if not QUEUE_FILE.exists():
        return [], ["content/publish_queue.json is missing"]
    try:
        data = json.loads(QUEUE_FILE.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [], [f"invalid queue JSON: {exc}"]
    errors = validate_queue(data)
    return (data.get("items", []) if not errors else []), errors


def is_due(item: dict[str, Any], now: datetime) -> bool:
    when = parse_time(item.get("publish_at"))
    return when is None or when <= now


def download(url: str, dest: Path) -> Path:
    VIDEO_DIR.mkdir(parents=True, exist_ok=True)
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https":
        raise ValueError("only https:// URLs are allowed")
    req = urllib.request.Request(url, headers={"User-Agent": "channel-loop/1.0"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        declared = int(resp.headers.get("Content-Length") or 0)
        if declared > MAX_DOWNLOAD_BYTES:
            raise ValueError(f"file is {declared / 1e6:.0f} MB (cap 512 MB)")
        tmp = dest.with_suffix(dest.suffix + ".part")
        written = 0
        with tmp.open("wb") as fh:
            while True:
                chunk = resp.read(1024 * 1024)
                if not chunk:
                    break
                written += len(chunk)
                if written > MAX_DOWNLOAD_BYTES:
                    tmp.unlink(missing_ok=True)
                    raise ValueError("download exceeded the 512 MB cap")
                fh.write(chunk)
        tmp.replace(dest)
    return dest


def resolve_media(item: dict[str, Any], live: bool) -> tuple[Path, Path | None]:
    eid = item["episode_id"]
    video = Path(item["video_path"]).expanduser() if item.get("video_path") else None
    if video and not video.is_absolute():
        video = ROOT / video
    expected = video or (VIDEO_DIR / f"{eid}.mp4")
    thumb: Path | None = None
    if live:
        if item.get("video_url"):
            expected = download(item["video_url"], VIDEO_DIR / f"{eid}.mp4")
        elif not expected.exists():
            raise FileNotFoundError(f"video_path not found: {expected}")
        if item.get("thumbnail_url"):
            thumb = download(item["thumbnail_url"], VIDEO_DIR / f"{eid}.jpg")
    return expected, thumb


def existing_titles(yt: Any) -> set[str]:
    """Live duplicate guard: a fresh GitHub Actions runner has no local state."""
    try:
        ch = yt.channels().list(part="contentDetails", mine=True).execute()
        uploads = ch["items"][0]["contentDetails"]["relatedPlaylists"]["uploads"]
        items = yt.playlistItems().list(part="contentDetails", playlistId=uploads,
                                        maxResults=50).execute().get("items", [])
        ids = [i["contentDetails"]["videoId"] for i in items]
        if not ids:
            return set()
        resp = yt.videos().list(part="snippet", id=",".join(ids[:50])).execute()
        return {v["snippet"]["title"] for v in resp.get("items", [])}
    except Exception as exc:  # noqa: BLE001
        print(f"  ! duplicate check unavailable ({str(exc)[:120]}); relying on local state")
        return set()


def run(cmd: list[str], label: str) -> bool:
    print(f"\n── {label} ──")
    print("   $ " + " ".join(cmd[1:]))
    result = subprocess.run(cmd, cwd=ROOT, check=False)
    if result.returncode != 0:
        print(f"   ✗ {label} failed (exit {result.returncode})")
        return False
    print(f"   ✓ {label} done")
    return True


def py(script: str, *args: str) -> list[str]:
    return [sys.executable, script, *args]


def main() -> int:
    ap = argparse.ArgumentParser(description="Run the full channel loop")
    ap.add_argument("--send", action="store_true", help="execute for real (default: dry run)")
    ap.add_argument("--yes", action="store_true", help="skip confirmation")
    ap.add_argument("--steps", default=",".join(ALL_STEPS),
                    help="comma-separated subset: " + ",".join(ALL_STEPS))
    ap.add_argument("--max-publishes", type=int, default=2,
                    help="safety cap on uploads per run (quota is ~6/day)")
    ap.add_argument("--max-replies", type=int, default=30)
    args = ap.parse_args()

    steps = [s.strip() for s in args.steps.split(",") if s.strip()]
    unknown = [s for s in steps if s not in ALL_STEPS]
    if unknown:
        print("Unknown steps: " + ", ".join(unknown), file=sys.stderr)
        return 2

    items, queue_errors = load_queue()
    due = [i for i in items if i.get("enabled") and is_due(i, now_utc())]
    print(f"Channel loop  ·  {now_utc().isoformat(timespec='seconds')}")
    print(f"Steps: {', '.join(steps)}  ·  queue: {len(items)} item(s), {len(due)} due")
    if queue_errors:
        print("QUEUE ERRORS: " + "; ".join(queue_errors))
    require_confirmation(
        args,
        f"this will publish up to {min(len(due), args.max_publishes)} video(s), "
        f"post up to {args.max_replies} comment replies, and pull analytics.",
    )

    failures: list[str] = []
    published = 0
    skipped = 0

    if "validate" in steps:
        if not run(py("tools/validate_content.py"), "validate content pack"):
            print("\nABORT: the content pack is invalid. Nothing was published.", file=sys.stderr)
            return 1
    if "render" in steps:
        if not run(py("tools/render_scripts.py"), "render filming sheets"):
            failures.append("render")

    if "publish" in steps:
        print("\n── publish ──")
        if not due:
            print("   Queue empty (or nothing due) → nothing to publish. "
                  "Add an enabled item to content/publish_queue.json.")
        yt = None
        titles: set[str] = set()
        state = load_state("published")
        if args.send and due:
            try:
                yt = youtube()
                titles = existing_titles(yt)
            except SystemExit as exc:
                print(f"   ✗ credentials unavailable: {exc}")
                return 2
        for item in due[: args.max_publishes]:
            eid = item["episode_id"]
            try:
                _, ep = load_episode(eid)
            except SystemExit as exc:
                print(f"   ! {eid}: {exc}")
                failures.append(f"publish {eid}")
                continue
            title = ep["titles"][0]
            if title in titles or (isinstance(state, dict) and eid in state):
                print(f"   ↷ {eid}: already published ({title[:50]}) — skipping")
                skipped += 1
                continue
            if not ep["fact_check"]["verified"]:
                print(f"   ↷ {eid}: REFUSED — fact_check.verified is false. "
                      "Verify the source, then set it to true.")
                skipped += 1
                continue
            try:
                video, thumb = resolve_media(item, args.send)
            except Exception as exc:  # noqa: BLE001
                print(f"   ! {eid}: media unavailable ({exc})")
                failures.append(f"media {eid}")
                continue
            cmd = py("automation/publish_video.py", "--episode", eid, "--file", str(video))
            if thumb:
                cmd += ["--thumbnail", str(thumb)]
            if args.send:
                cmd += ["--send", "--yes"]
            if run(cmd, f"publish {eid}"):
                if args.send:
                    published += 1
                    titles.add(title)
            else:
                failures.append(f"publish {eid}")

    if "replies" in steps:
        cmd = py("automation/reply_to_comments.py", "--max-replies", str(args.max_replies))
        if args.send:
            cmd += ["--send", "--yes"]
        if not run(cmd, "comment replies"):
            failures.append("replies")

    if "report" in steps:
        if args.send:
            if not run(py("automation/analytics.py", "--days", "7"), "weekly scorecard"):
                failures.append("report")
        else:
            print("\n── weekly scorecard ──\n   (dry run: analytics is read-only but needs "
                  "credentials; run with --send --yes to fetch it)")

    print("\n════════ SUMMARY ════════")
    print(f"  mode          : {'LIVE' if args.send else 'DRY RUN'}")
    print(f"  published     : {published}")
    print(f"  skipped       : {skipped}")
    print(f"  failed steps  : {', '.join(failures) if failures else 'none'}")
    if not args.send:
        print("  next          : python automation/run_channel.py --send --yes")
    else:
        print("  reminder      : pin the channel's question in YouTube Studio (API cannot pin)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
