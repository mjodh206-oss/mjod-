#!/usr/bin/env python3
"""Offline hook generator. No API, no network — just the 20 proven families.

Outputs DRAFTS to rewrite in your own voice, always <= 12 words. A rule-based
generator cannot know the actual fact, so treat every line as a shape to fill in,
not as a finished hook.

Usage:
    python tools/hook_engine.py "why honey never spoils"
    python tools/hook_engine.py "why honey never spoils" --count 8 --json
"""
from __future__ import annotations

import argparse
import json
import re
import sys

from validate_content import _words

# (family, template). {t} = a short clause extracted from the topic, e.g.
# "why honey never spoils" -> "honey never spoils".
TEMPLATES: list[tuple[str, str]] = [
    ("contradiction", "The real reason {t} isn't the obvious one."),
    ("number shock", "One number. That's why {t}."),
    ("you-test", "Test this yourself: {t}."),
    ("forbidden", "Your brain hides this from you: {t}."),
    ("false belief", "Everyone gets this backwards: {t}."),
    ("time collapse", "Three thousand years. Still true: {t}."),
    ("cost of not knowing", "You keep getting this wrong: {t}."),
    ("removed rule", "One missing detail and suddenly: {t}."),
    ("ranking", "The weirdest part? Why {t}."),
    ("withheld answer", "Why {t}? Not for the obvious reason."),
    ("two-fact collision", "Two true facts, and suddenly: {t}."),
    ("second-person error", "Wrong your whole life about this: {t}."),
    ("invisible", "Right now, inside you: {t}."),
    ("object focus", "One ordinary object explains {t}."),
    ("countdown", "Three reasons. One explains {t}."),
    ("bet", "One sentence. Explain {t}. Go."),
    ("scale jump", "Zoom out. Suddenly, {t}."),
    ("name-drop paradox", "Same word, same sound — {t}."),
    ("one-word correction", "One word changes everything about {t}."),
    ("loop tease", "By the end of this: {t}."),
]

LEADING = re.compile(
    r"^(why|how|what|when|where|who|is|are|does|do|can|could|would|should)\s+", re.IGNORECASE
)
STOP = {"why", "how", "what", "when", "where", "who", "the", "a", "an", "of", "to", "in",
        "and", "that", "this", "it", "your", "we", "i", "you", "from", "with", "about",
        "get", "gets", "never", "still", "does", "do", "is", "are", "on", "at", "for"}


def clause(topic: str, limit: int = 6) -> str:
    cleaned = LEADING.sub("", topic.strip()).rstrip("?.!").strip()
    words = cleaned.split()[:limit]
    return " ".join(words) if words else topic.strip()[:40]


def short_topic(topic: str, limit: int = 4) -> str:
    """Keyword-only phrase, useful for thumbnail / title ideation."""
    words = re.findall(r"[A-Za-z0-9'-]+", topic)
    kept = [w for w in words if w.lower() not in STOP][:limit]
    return " ".join(kept) if kept else clause(topic)


def generate(topic: str, count: int = 6) -> list[dict[str, object]]:
    phrase = clause(topic)
    out: list[dict[str, object]] = []
    for family, template in TEMPLATES:
        hook = template.format(t=phrase)
        hook = hook[0].upper() + hook[1:]
        n = _words(hook)
        if n <= 12:
            out.append({"family": family, "hook": hook, "words": n})
        if len(out) >= count:
            break
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate hook drafts from proven families")
    ap.add_argument("topic", help="the episode topic, e.g. 'why honey never spoils'")
    ap.add_argument("--count", type=int, default=6, help="number of variants (max 20)")
    ap.add_argument("--json", action="store_true", help="machine-readable output")
    args = ap.parse_args()
    hooks = generate(args.topic, max(1, min(args.count, len(TEMPLATES))))
    if args.json:
        print(json.dumps({"topic": args.topic, "clause": clause(args.topic),
                          "keywords": short_topic(args.topic), "hooks": hooks}, indent=2))
    else:
        print(f'Topic: "{args.topic}"  →  clause: "{clause(args.topic)}"  ·  '
              f'keywords: "{short_topic(args.topic)}"\n')
        for h in hooks:
            print(f"  [{h['family']:<22}] {h['hook']}  ({h['words']} words)")
        print("\nDrafts only. Rewrite in your own voice, then paste 3 into the episode JSON.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
