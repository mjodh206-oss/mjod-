#!/usr/bin/env python3
"""Fetch new comments on recent uploads and reply with specific, on-brand lines.

Human-in-the-loop by default: dry run unless --send --yes.

Safety:
  * never replies to the channel's own comments or to threads it already answered
  * skips spam, links from strangers, and duplicate replies (state/replied.json)
  * routes legal / medical / financial / copyright / personal-data comments to a human queue
  * caps replies per run and caps replies to hostile comments
  * never invents facts, sources, promises, or advice (see content/comments/replies.json)

Usage:
    python automation/reply_to_comments.py                       # preview everything
    python automation/reply_to_comments.py --send --yes --max-replies 30
    python automation/reply_to_comments.py --max-videos 20 --json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from common import (
    ROOT,
    add_send_flags,
    channel_id,
    load_all_episodes,
    load_state,
    paginate,
    require_confirmation,
    save_state,
    youtube,
)

REPLIES_FILE = ROOT / "content" / "comments" / "replies.json"
PRIORITY = [
    "correction", "source_request", "answer_yes", "answer_no", "part_two",
    "question", "disagreement", "negative", "first", "praise", "off_topic",
]
LINK_RE = re.compile(r"https?://|www\.|\.com/|t\.me/", re.IGNORECASE)


def keyword_hit(text: str, keyword: str) -> bool:
    low, kw = text.lower(), keyword.lower().strip()
    if not kw:
        return False
    if len(kw.split()) > 1 or not re.fullmatch(r"[a-z0-9'?]+", kw):
        return kw in low
    return re.search(r"\b" + re.escape(kw) + r"\b", low) is not None


def classify(text: str, bank: dict) -> tuple[str, list[str]]:
    cats = bank["categories"]
    for name in PRIORITY:
        cat = cats.get(name)
        if cat and any(keyword_hit(text, kw) for kw in cat.get("match", [])):
            return name, cat["replies"]
    return "default", cats["default"]["replies"]


def recent_video_ids(yt: Any, limit: int) -> list[str]:
    """Search (forMine) with a reliable uploads-playlist fallback."""
    try:
        resp = yt.search().list(part="snippet", forMine=True, type="video",
                               order="date", maxResults=min(limit, 50)).execute()
        ids = [i["id"]["videoId"] for i in resp.get("items", []) if "videoId" in i.get("id", {})]
        if ids:
            return ids
    except Exception:
        pass
    ch = yt.channels().list(part="contentDetails", mine=True).execute()
    uploads = ch["items"][0]["contentDetails"]["relatedPlaylists"]["uploads"]
    resp = yt.playlistItems().list(part="contentDetails", playlistId=uploads,
                                   maxResults=min(limit, 50)).execute()
    return [i["contentDetails"]["videoId"] for i in resp.get("items", [])]


def topic_map() -> tuple[dict[str, str], str]:
    """video_id -> topic, plus the next planned topic."""
    state = load_state("published")
    mapping = {}
    for ep_id, info in state.items() if isinstance(state, dict) else []:
        if isinstance(info, dict) and info.get("video_id"):
            mapping[info["video_id"]] = ep_id
    episodes = [ep for _, ep in load_all_episodes()]
    by_id = {ep["id"]: ep["topic"] for ep in episodes}
    upcoming = [ep["topic"] for ep in episodes if ep.get("status") in ("draft", "ready")]
    return ({vid: by_id.get(eid, "this topic") for vid, eid in mapping.items()},
            upcoming[0] if upcoming else "the next topic in the series")


def render_reply(template: str, name: str, topic: str, next_topic: str) -> str:
    return (template.replace("{name}", name)
                    .replace("{topic}", topic)
                    .replace("{next}", next_topic))


def main() -> int:
    ap = argparse.ArgumentParser(description="Reply to new YouTube comments (dry run by default)")
    add_send_flags(ap)
    ap.add_argument("--max-replies", type=int, default=None, help="cap for this run")
    ap.add_argument("--max-videos", type=int, default=10, help="how many recent videos to scan")
    ap.add_argument("--json", action="store_true", help="machine-readable report")
    args = ap.parse_args()

    bank = json.loads(REPLIES_FILE.read_text(encoding="utf-8"))
    rules = bank["rules"]
    cap = args.max_replies or rules["max_replies_per_run"]
    topics, next_topic = topic_map()

    print(f"Reply bank v{bank['version']} · cap {cap} replies · scanning {args.max_videos} videos")
    require_confirmation(
        args,
        f"this will POST up to {cap} replies ({cap * 50} quota units) to your channel.",
    )

    try:
        from googleapiclient.errors import HttpError
    except ImportError:
        if args.send:
            print("ERROR: run `python3 -m pip install -r requirements.txt` first", file=sys.stderr)
            return 2
        HttpError = Exception  # dry run still works without the SDK

    if not args.send:
        # Offline preview: show what a run would look like using the bank itself.
        samples = ["no way that worked", "source?", "love this", "actually that's not right",
                   "part 2 please", "first!", "why does this happen?", "can you sponsor me"]
        plan = []
        for text in samples:
            cat, replies = classify(text, bank)
            plan.append({"author": "preview", "text": text, "category": cat,
                         "reply": render_reply(replies[0], "Alex", "this topic", next_topic)})
        skipped = [p for p in plan if any(keyword_hit(p["text"], k) for k in rules["human_queue_keywords"])]
        for p in plan:
            flag = " → HUMAN QUEUE" if p in skipped else ""
            print(f"  [{p['category']:<16}] \"{p['text']}\" → {p['reply']}{flag}")
        if args.json:
            print(json.dumps({"dry_run": True, "plan": plan}, indent=2))
        print(f"\n{len(plan)} sample comments classified. Live mode needs YouTube credentials.")
        print("Next: python automation/reply_to_comments.py --send --yes --max-replies 30")
        return 0

    yt = youtube()
    cid = channel_id(yt)
    replied_ids = set(load_state("replied") or [])
    planned: list[dict[str, str]] = []
    skipped: list[dict[str, str]] = []
    negative_used = 0

    for vid in recent_video_ids(yt, args.max_videos):
        if len(planned) >= cap:
            break
        try:
            threads = paginate(
                yt.commentThreads(),
                yt.commentThreads().list(part="snippet,replies", videoId=vid,
                                         maxResults=100, textFormat="plainText", order="time"),
            )
        except HttpError as exc:
            skipped.append({"video": vid, "text": "(list failed)", "reason": str(exc)[:120]})
            continue

        for thread in threads:
            if len(planned) >= cap:
                break
            top = thread["snippet"]["topLevelComment"]
            snippet = top["snippet"]
            comment_id = top["id"]
            text = snippet.get("textDisplay", "")
            author = snippet.get("authorDisplayName", "there")
            author_cid = snippet.get("authorChannelId", {}).get("value")

            if author_cid and author_cid == cid:
                continue
            if comment_id in replied_ids:
                continue
            replies = thread.get("replies", {}).get("comments", [])
            if any(r["snippet"].get("authorChannelId", {}).get("value") == cid for r in replies):
                continue  # already answered by the channel
            if len(text.strip()) < rules["min_comment_chars"]:
                continue
            if rules.get("skip_links_from_strangers") and LINK_RE.search(text):
                skipped.append({"video": vid, "text": text[:60], "reason": "link from stranger"})
                continue
            if any(keyword_hit(text, kw) for kw in rules["spam_patterns"]):
                skipped.append({"video": vid, "text": text[:60], "reason": "spam pattern"})
                continue
            if any(keyword_hit(text, kw) for kw in rules["human_queue_keywords"]):
                skipped.append({"video": vid, "text": text[:60], "reason": "HUMAN QUEUE (sensitive)"})
                continue

            category, templates = classify(text, bank)
            cat_cfg = bank["categories"].get(category, {})
            max_per = cat_cfg.get("max_per_run")
            if max_per is not None and negative_used >= max_per:
                continue
            # Stable per-comment variation (no randomness, reproducible across runs).
            idx = sum(ord(c) for c in comment_id) % len(templates)
            name = (author.split() or ["there"])[0][:20]
            reply = render_reply(templates[idx], name, topics.get(vid, "this topic"), next_topic)
            planned.append({
                "video_id": vid, "comment_id": comment_id, "author": name,
                "text": text[:100], "category": category, "reply": reply,
            })
            if category == "negative":
                negative_used += 1

    print(f"\nPlanned replies: {len(planned)}   Skipped: {len(skipped)}")
    for p in planned:
        print(f"  [{p['category']:<16}] {p['author']}: \"{p['text']}\"")
        print(f"      → {p['reply']}")
    for s in skipped:
        print(f"  SKIP ({s['reason']}) {s.get('text', '')}")

    if args.json:
        print(json.dumps({"planned": planned, "skipped": skipped}, indent=2, ensure_ascii=False))

    if not args.send:
        return 0

    posted = 0
    for p in planned:
        try:
            yt.comments().insert(
                part="snippet",
                body={"snippet": {"parentId": p["comment_id"], "textOriginal": p["reply"]}},
            ).execute()
            replied_ids.add(p["comment_id"])
            posted += 1
        except HttpError as exc:
            print(f"  ! failed for {p['comment_id']}: {str(exc)[:160]}")
    save_state("replied", sorted(replied_ids))
    print(f"\n✓ Posted {posted} replies ({posted * 50} quota units). "
          "Sensitive comments were left for you, not answered by a bot.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
