#!/usr/bin/env python3
"""Validate the content pack. Standard library only — safe to run in CI.

Checks the things that actually hurt a Short: slow/wordy hooks, timeline gaps,
missing re-hooks, unverified facts, over-long titles, placeholder text.

Usage:
    python tools/validate_content.py            # human report, exit 1 on errors
    python tools/validate_content.py --json    # machine-readable
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
EPISODE_DIR = ROOT / "content" / "episodes"
REPLIES_FILE = ROOT / "content" / "comments" / "replies.json"

STATUSES = {"draft", "ready", "scheduled", "published", "retired"}
REQUIRED_STRINGS = ("series", "topic", "payoff", "cta", "pinned_comment", "description")
BANNED_OPENERS = ("hey ", "hi guys", "welcome back", "today we", "in this video", "did you know")
PLACEHOLDERS = ("TODO", "TBD", "FIXME", "lorem ipsum", "XXX", "<insert")
TIME_RE = re.compile(r"^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)$")
DEVICES = ("hook", "open loop", "pattern", "re-hook", "mechanism", "payoff", "loop", "cta",
           "number", "comparison", "scale", "you-test", "correction", "actionable")


def _words(text: str) -> int:
    return len(text.split())


def load_episodes() -> list[tuple[Path, dict[str, Any]]]:
    episodes = []
    for path in sorted(EPISODE_DIR.glob("*.json")):
        if path.name.startswith("_"):
            continue
        with path.open(encoding="utf-8") as fh:
            episodes.append((path, json.load(fh)))
    return episodes


def validate_episode(path: Path, ep: dict[str, Any]) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    tag = f"[{path.name}]"

    for key in REQUIRED_STRINGS:
        if not isinstance(ep.get(key), str) or not ep[key].strip():
            errors.append(f"{tag} missing non-empty '{key}'")
    if not isinstance(ep.get("id"), str) or not ep["id"].startswith("ep-"):
        errors.append(f"{tag} id must look like 'ep-001'")
    elif not path.stem.startswith(ep["id"]):
        errors.append(f"{tag} filename must start with the episode id")
    if ep.get("status") not in STATUSES:
        errors.append(f"{tag} invalid status {ep.get('status')!r}")
    if ep.get("format") != "shorts":
        errors.append(f"{tag} format must be 'shorts'")
    if ep.get("language") != "en":
        errors.append(f"{tag} language must be 'en'")
    if not isinstance(ep.get("duration_seconds"), int) or not 20 <= ep["duration_seconds"] <= 60:
        errors.append(f"{tag} duration_seconds must be an int in 20..60")

    if ep.get("publish_at") not in (None, ""):
        try:
            datetime.fromisoformat(str(ep["publish_at"]).replace("Z", "+00:00"))
        except ValueError:
            errors.append(f"{tag} publish_at is not valid ISO-8601")

    fc = ep.get("fact_check") or {}
    if not fc.get("claim") or not fc.get("source_hint"):
        errors.append(f"{tag} fact_check needs claim + source_hint")
    if not isinstance(fc.get("verified"), bool):
        errors.append(f"{tag} fact_check.verified must be true/false")
    elif not fc["verified"] and ep.get("status") in ("ready", "scheduled", "published"):
        warnings.append(
            f"{tag} NOT FACT-CHECKED — publish_video.py will refuse this episode. "
            f"Verify: {fc.get('source_hint', '')[:90]}"
        )

    hooks = ep.get("hooks") or []
    if len(hooks) < 2:
        errors.append(f"{tag} need >= 2 hook variants for A/B testing")
    for i, hook in enumerate(hooks):
        if not isinstance(hook, str) or not hook.strip():
            errors.append(f"{tag} hook {i} is empty")
            continue
        if _words(hook) > 12:
            errors.append(f"{tag} hook {i} is {_words(hook)} words (max 12 / 3 seconds)")
        if hook.lower().startswith(BANNED_OPENERS):
            errors.append(f"{tag} hook {i} uses a banned intro: {hook!r}")
    sel = ep.get("selected_hook")
    if not isinstance(sel, int) or not 0 <= sel < len(hooks):
        errors.append(f"{tag} selected_hook out of range")

    titles = ep.get("titles") or []
    if len(titles) < 2:
        errors.append(f"{tag} need >= 2 title variants")
    for i, title in enumerate(titles):
        if len(title) > 100:
            errors.append(f"{tag} title {i} exceeds YouTube's 100-character limit")
        if len(title) > 70:
            warnings.append(f"{tag} title {i} is {len(title)} chars (45-70 is the sweet spot)")
        if not title.strip():
            errors.append(f"{tag} title {i} is empty")

    beats = ep.get("beats") or []
    if len(beats) < 5:
        errors.append(f"{tag} need >= 5 beats for a 45 s Short")
    cursor = 0.0
    total_vo_words = 0
    devices_seen: list[str] = []
    for i, beat in enumerate(beats):
        for key in ("t", "vo", "on_screen", "visual", "device"):
            if not isinstance(beat.get(key), str) or not beat[key].strip():
                errors.append(f"{tag} beat {i} missing '{key}'")
        m = TIME_RE.match(str(beat.get("t", "")))
        if not m:
            errors.append(f"{tag} beat {i} time must be 'start-end' (e.g. '8-16'), got {beat.get('t')!r}")
            continue
        start, end = float(m.group(1)), float(m.group(2))
        if start != cursor:
            errors.append(f"{tag} beat {i} starts at {start}, expected {cursor} (timeline gap/overlap)")
        if end <= start:
            errors.append(f"{tag} beat {i} has non-positive duration")
        seconds = end - start
        w = _words(str(beat.get("vo", "")))
        total_vo_words += w
        if seconds > 0:
            rate = w / seconds
            if rate > 3.4:
                errors.append(f"{tag} beat {i} VO is {rate:.1f} words/s (too fast to deliver)")
            if rate < 1.0:
                warnings.append(f"{tag} beat {i} VO is only {rate:.1f} words/s (dead air)")
        if _words(str(beat.get("on_screen", ""))) > 12:
            warnings.append(f"{tag} beat {i} on-screen text is too dense (2-4 words is best)")
        devices_seen.append(str(beat.get("device", "")).lower())
        cursor = end
    duration = ep.get("duration_seconds", 0)
    if isinstance(duration, int) and beats and cursor != float(duration):
        errors.append(f"{tag} beats end at {cursor}s but duration_seconds is {duration}")
    if isinstance(duration, int) and duration and total_vo_words:
        overall = total_vo_words / duration
        if not 1.8 <= overall <= 3.0:
            warnings.append(f"{tag} overall VO pace is {overall:.2f} words/s (target 2.3-2.8)")
    if not devices_seen or "hook" not in devices_seen[0]:
        errors.append(f"{tag} beat 0 must be the hook")
    if not any("re-hook" in d for d in devices_seen):
        warnings.append(f"{tag} no re-hook at ~50% — this is where Shorts lose viewers")
    if not any("payoff" in d for d in devices_seen):
        warnings.append(f"{tag} no payoff beat — the open loop never closes")
    if not devices_seen or not any(("loop" in d or "cta" in d) for d in devices_seen[-1:]):
        warnings.append(f"{tag} final beat should close the loop and ask one question")
    if "?" not in str(ep.get("cta", "")):
        warnings.append(f"{tag} CTA is not a question — binary questions drive comments")

    hashtags = ep.get("hashtags") or []
    if not hashtags or not any(h.lower() == "#shorts" for h in hashtags):
        errors.append(f"{tag} hashtags must include #shorts")
    if len(hashtags) > 5:
        warnings.append(f"{tag} {len(hashtags)} hashtags — 3-4 performs better than tag stuffing")
    for h in hashtags:
        if not h.startswith("#") or " " in h:
            errors.append(f"{tag} malformed hashtag {h!r}")
    if len(ep.get("description", "")) > 5000:
        errors.append(f"{tag} description exceeds YouTube's 5000-character limit")
    thumb = ep.get("thumbnail") or {}
    if not thumb.get("text"):
        errors.append(f"{tag} thumbnail.text is required")
    elif _words(thumb["text"]) > 4:
        warnings.append(f"{tag} thumbnail text has {_words(thumb['text'])} words (3 max)")
    if not ep.get("tags"):
        errors.append(f"{tag} tags are required")

    flat = json.dumps(ep).lower()
    for bad in PLACEHOLDERS:
        if bad.lower() in flat:
            errors.append(f"{tag} contains placeholder {bad!r}")
    return errors, warnings


def validate_replies(path: Path, data: dict[str, Any]) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    tag = "[replies.json]"
    cats = data.get("categories") or {}
    if "default" not in cats:
        errors.append(f"{tag} missing 'default' category")
    for name, cat in cats.items():
        replies = cat.get("replies") or []
        if not replies:
            errors.append(f"{tag} category '{name}' has no replies")
        for r in replies:
            if len(r.split()) < 5:
                warnings.append(f"{tag} '{name}' has a very short reply (add a fact or a question)")
            if "thanks" in r.lower() and "?" not in r and "!" in r:
                warnings.append(f"{tag} '{name}' looks like a generic thank-you: {r[:60]}")
    rules = data.get("rules") or {}
    if not rules.get("max_replies_per_run"):
        errors.append(f"{tag} rules.max_replies_per_run is required")
    if not rules.get("human_queue_keywords"):
        errors.append(f"{tag} rules.human_queue_keywords is required (safety gate)")
    return errors, warnings


def run() -> dict[str, Any]:
    result: dict[str, Any] = {"episodes": 0, "errors": [], "warnings": [], "ok": True}
    episodes = load_episodes()
    result["episodes"] = len(episodes)
    if not episodes:
        result["errors"].append("no episode JSON files found")
    for path, ep in episodes:
        errs, warns = validate_episode(path, ep)
        result["errors"].extend(errs)
        result["warnings"].extend(warns)
    with REPLIES_FILE.open(encoding="utf-8") as fh:
        replies = json.load(fh)
    errs, warns = validate_replies(REPLIES_FILE, replies)
    result["errors"].extend(errs)
    result["warnings"].extend(warns)
    result["ok"] = not result["errors"]
    return result


def main() -> int:
    ap = argparse.ArgumentParser(description="Validate the Shorts content pack")
    ap.add_argument("--json", action="store_true", help="emit JSON instead of a report")
    args = ap.parse_args()
    result = run()
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(f"Content pack: {result['episodes']} episodes + reply bank")
        print(f"Errors: {len(result['errors'])}  Warnings: {len(result['warnings'])}")
        for e in result["errors"]:
            print(f"  ERROR   {e}")
        for w in result["warnings"]:
            print(f"  WARN    {w}")
        if result["ok"]:
            print("\nStructure OK. Clear the fact-check warnings before publishing.")
        else:
            print("\nFix the errors above before publishing.")
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
