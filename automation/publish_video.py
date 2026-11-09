#!/usr/bin/env python3
"""Upload one Short with fully-built metadata from its episode JSON.

Safety gates (all on by default):
  * dry run unless --send --yes
  * refuses episodes whose fact_check.verified is false
  * refuses double uploads (state/published.json + youtube_video_id in the JSON)
  * refuses a missing video file

Usage:
    python automation/publish_video.py --episode ep-001 --file ~/renders/ep-001.mp4
    python automation/publish_video.py --episode ep-001 --send --yes
    python automation/publish_video.py --episode ep-002 --schedule 2026-11-11T17:30:00Z --send --yes
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

from common import (
    CONTENT_DIR,
    ROOT,
    add_send_flags,
    build_description,
    channel_id,
    load_episode,
    load_state,
    require_confirmation,
    save_state,
    youtube,
)


def build_body(ep: dict, privacy: str, schedule: str | None) -> dict:
    title = ep["titles"][0]
    status: dict = {
        "privacyStatus": "private" if schedule else privacy,
        "selfDeclaredMadeForKids": False,
        "embeddable": True,
        "license": "youtube",
    }
    if schedule:
        status["publishAt"] = schedule  # YouTube auto-publishes a private video at this time
    return {
        "snippet": {
            "title": title,
            "description": build_description(ep),
            "tags": ep.get("tags", []),
            "categoryId": "27",  # Education
            "defaultLanguage": "en",
            "defaultAudioLanguage": "en",
        },
        "status": status,
    }


def resolve_video_file(ep: dict, cli_path: str | None) -> Path:
    candidate = cli_path or ep.get("video_file") or str(CONTENT_DIR / "videos" / f"{ep['id']}.mp4")
    path = Path(candidate).expanduser()
    if not path.is_absolute():
        path = ROOT / path
    return path


def main() -> int:
    ap = argparse.ArgumentParser(description="Publish a Short from its episode JSON")
    add_send_flags(ap)
    ap.add_argument("--file", help="path to the rendered .mp4 (default: content/videos/<id>.mp4)")
    ap.add_argument("--thumbnail", help="optional .jpg/.png thumbnail")
    ap.add_argument("--privacy", choices=["public", "unlisted", "private"], default="public")
    ap.add_argument("--schedule", help="ISO-8601 UTC time, e.g. 2026-11-11T17:30:00Z")
    ap.add_argument("--no-comment", action="store_true", help="skip posting the channel's own question")
    ap.add_argument("--force", action="store_true", help="allow re-upload of an already published episode")
    ap.add_argument("--force-unverified", action="store_true",
                    help="DANGEROUS: bypass the fact-check gate (not recommended)")
    args = ap.parse_args()

    if not args.episode:
        ap.error("--episode is required (e.g. --episode ep-001)")
    if args.schedule:
        try:
            datetime.fromisoformat(args.schedule.replace("Z", "+00:00"))
        except ValueError:
            ap.error("--schedule must be ISO-8601, e.g. 2026-11-11T17:30:00Z")

    _, ep = load_episode(args.episode)
    body = build_body(ep, args.privacy, args.schedule)
    video = resolve_video_file(ep, args.file)

    print(f"Episode      : {ep['id']} — {ep['topic']}")
    print(f"Title        : {body['snippet']['title']}")
    print(f"Hook         : {ep['hooks'][ep['selected_hook']]}")
    print(f"Duration     : {ep['duration_seconds']} s · {ep['format']} · {ep['language']}")
    print(f"Video file   : {video}")
    print(f"Privacy      : {body['status']['privacyStatus']}"
          + (f" → publishes at {args.schedule}" if args.schedule else ""))
    print(f"Description  : {len(body['snippet']['description'])} chars · "
          f"{len(ep['hashtags'])} hashtags · {len(ep['tags'])} tags")
    print(f"Fact-check   : {'VERIFIED' if ep['fact_check']['verified'] else 'NOT VERIFIED'}")
    print()

    if not ep["fact_check"]["verified"]:
        if args.send and not args.force_unverified:
            print("REFUSED: refusing to publish an un-fact-checked episode.\n"
                  f"  Verify: {ep['fact_check']['source_hint']}\n"
                  "  Then set fact_check.verified = true in the episode JSON.\n"
                  "  (--force-unverified exists for testing only — don't use it on a real channel.)",
                  file=sys.stderr)
            return 2
        if args.force_unverified:
            print("!! WARNING: fact-check gate bypassed. Do not use this on a live upload. !!\n")
        else:
            print("WARNING: not fact-checked yet — the dry run is fine, but --send will be refused.\n"
                  f"  Verify: {ep['fact_check']['source_hint']}\n")

    state = load_state("published")
    already = state.get(ep["id"]) or ep.get("youtube_video_id")
    if already and not args.force:
        print(f"REFUSED: {ep['id']} is already published as "
              f"{already if isinstance(already, str) else already.get('video_id')}. Use --force to override.",
              file=sys.stderr)
        return 2
    if not video.exists():
        if args.send:
            print(f"REFUSED: video file not found: {video}\n"
                  "Render the Short first, or pass --file /absolute/path.mp4.\n"
                  "Video files stay out of Git (see .gitignore).", file=sys.stderr)
            return 2
        print(f"(dry run) video file not present yet: {video} — "
              "metadata preview continues.\n")
    if video.suffix.lower() not in (".mp4", ".mov", ".m4v", ".avi", ".webm"):
        print(f"WARNING: unexpected extension {video.suffix}; YouTube prefers .mp4 (H.264 + AAC).")

    thumb = Path(args.thumbnail).expanduser() if args.thumbnail else None
    if thumb and not thumb.exists():
        print(f"REFUSED: thumbnail not found: {thumb}", file=sys.stderr)
        return 2

    require_confirmation(
        args,
        f"this will UPLOAD {ep['id']} to your channel (quota: 1600 units) and post a channel comment.",
    )
    if not args.send:
        print("Planned request body:")
        print(json.dumps(body, indent=2, ensure_ascii=False)[:1400])
        print("\nNext: python automation/publish_video.py --episode "
              f"{ep['id']} --send --yes")
        return 0

    try:
        from googleapiclient.errors import HttpError
        from googleapiclient.http import MediaFileUpload
    except ImportError:
        print("ERROR: run `python3 -m pip install -r requirements.txt` first", file=sys.stderr)
        return 2

    yt = youtube()
    media = MediaFileUpload(str(video), chunksize=8 * 1024 * 1024, resumable=True)
    request = yt.videos().insert(
        part="snippet,status", body=body, media_body=media, notifySubscribers=True
    )
    response = None
    while response is None:
        status, response = request.next_chunk()
        if status:
            print(f"  upload {int(status.progress() * 100)}%")
    video_id = response["id"]
    print(f"✓ Uploaded: https://www.youtube.com/watch?v={video_id}")

    if thumb:
        try:
            yt.thumbnails().set(
                videoId=video_id,
                media_body=MediaFileUpload(str(thumb), resumable=False),
            ).execute()
            print("✓ Thumbnail set")
        except HttpError as exc:
            print(f"! Thumbnail failed ({exc}); the upload itself succeeded.")

    if not args.no_comment:
        try:
            yt.commentThreads().insert(
                part="snippet",
                body={
                    "snippet": {
                        "channelId": channel_id(yt),
                        "videoId": video_id,
                        "topLevelComment": {"snippet": {"textOriginal": ep["pinned_comment"]}},
                    }
                },
            ).execute()
            print("✓ Channel question posted — PIN IT MANUALLY in YouTube Studio (the API cannot pin).")
        except HttpError as exc:
            print(f"! Channel comment failed ({exc}); post it manually from Studio.")

    state[ep["id"]] = {
        "video_id": video_id,
        "title": body["snippet"]["title"],
        "published_at": datetime.utcnow().isoformat(timespec="seconds") + "Z",
        "hook_family": ep["hooks"][ep["selected_hook"]],
    }
    save_state("published", state)
    print("✓ Recorded in state/published.json — analytics.py will pick it up.")
    print("\nFirst 60 minutes: reply to every comment → "
          "python automation/reply_to_comments.py --send --yes --max-replies 30")
    return 0


if __name__ == "__main__":
    sys.exit(main())
