#!/usr/bin/env python3
"""Bridge: this content pack  ->  darkzOGx/youtube-automation-agent (self-hosted).

The agent's REST API is documented in its docs/api.md; the strategy field values below were
validated against its index.js validation code (enums + integer ranges), so the payload is
accepted rather than guessed.

Nothing here activates the agent or approves a video: the strategy is always saved as "draft".
No credential is stored in this repo — AGENT_API_KEY comes from the environment.

Usage:
    python integrations/agent_bridge.py strategy                # build + print (offline)
    python integrations/agent_bridge.py manifest               # episode manifest for Evidence desk
    python integrations/agent_bridge.py readiness              # GET /api/readiness
    python integrations/agent_bridge.py strategy --send --yes  # PUT /api/operator/strategy
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tools"))
try:
    from validate_content import load_episodes  # noqa: E402
except ImportError:  # when imported as integrations.agent_bridge from another package
    from tools.validate_content import load_episodes  # noqa: E402

OUT_DIR = ROOT / "integrations" / "out"

# Mirrors index.js: parseOperatorStrategy() in the agent.
FORMATS = {"explainer", "tutorial", "list", "review", "story"}
LENGTHS = {"short", "medium", "long"}
KPIS = {"views", "watch_hours", "subscribers", "engagement", "revenue"}
STATUSES = {"draft", "active", "paused"}


def build_strategy(episodes: list[tuple[Path, dict[str, Any]]]) -> dict[str, Any]:
    pillars: list[str] = []
    for _, ep in episodes:
        series = ep["series"]
        if series not in pillars:
            pillars.append(series)
    hooks = [ep["hooks"][ep["selected_hook"]] for _, ep in episodes]
    return {
        "objective": "Become the default 45-second answer to 'wait, what?' science questions",
        "audience": "Curious English-speaking YouTube Shorts viewers who stop for a surprising fact",
        "valueProposition": "One verified surprising fact, a hook in the first second, a payoff by second 43",
        "contentPillars": pillars[:5] or ["Brain Glitches", "Space Math", "Everyday Impossible"],
        "cadencePerWeek": 6,
        "videosPerRun": 5,
        "defaultFormat": "explainer",
        "defaultLength": "short",
        "successMetric": "3s hold >=65%, average % viewed >=70%, comments per 1k views >=3",
        "primaryKpi": "engagement",
        "targetValue": 1000,
        "targetWindowDays": 28,
        "monthlyBudget": 25,
        "outcomeCurrency": "USD",
        "constraints": (
            "English only. 9:16 vertical, 24-45 seconds. Hook <=12 words in the first 0.5s; "
            "no greeting or logo; re-hook at ~50%; payoff closes the loop; last frame loops to "
            "frame 1. One verified fact per Short: every claim must have a primary source before "
            "approval (Evidence desk). No medical, legal or financial advice. No invented stats, "
            "no fake controversy, no sub-for-sub. Never publish without human approval. "
            "Sample hooks already written in this channel's voice: " + " | ".join(hooks[:2])
        ),
        "status": "draft",  # deliberately NOT active — activation stays a human decision
    }


def build_manifest(episodes: list[tuple[Path, dict[str, Any]]]) -> dict[str, Any]:
    return {
        "source": "mjod- content pack",
        "language": "en",
        "format": "shorts",
        "note": "Import into the agent's Evidence desk: each claim needs a verified source before approval.",
        "episodes": [
            {
                "id": ep["id"],
                "topic": ep["topic"],
                "series": ep["series"],
                "duration_seconds": ep["duration_seconds"],
                "selected_hook": ep["hooks"][ep["selected_hook"]],
                "claim": ep["fact_check"]["claim"],
                "source_hint": ep["fact_check"]["source_hint"],
                "verified": ep["fact_check"]["verified"],
                "payoff": ep["payoff"],
                "cta": ep["cta"],
            }
            for _, ep in episodes
        ],
    }


def validate_strategy(s: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if s.get("defaultFormat") not in FORMATS:
        errors.append("defaultFormat must be one of " + ", ".join(sorted(FORMATS)))
    if s.get("defaultLength") not in LENGTHS:
        errors.append("defaultLength must be one of " + ", ".join(sorted(LENGTHS)))
    if s.get("primaryKpi") not in KPIS:
        errors.append("primaryKpi must be one of " + ", ".join(sorted(KPIS)))
    if s.get("status") not in STATUSES:
        errors.append("status must be one of " + ", ".join(sorted(STATUSES)))
    if not 1 <= s.get("cadencePerWeek", 0) <= 7:
        errors.append("cadencePerWeek must be 1..7")
    if not 1 <= s.get("videosPerRun", 0) <= 5:
        errors.append("videosPerRun must be 1..5")
    if not 7 <= s.get("targetWindowDays", 0) <= 365:
        errors.append("targetWindowDays must be 7..365")
    if not isinstance(s.get("contentPillars"), list) or not s["contentPillars"]:
        errors.append("contentPillars must be a non-empty list")
    if len(s.get("constraints", "")) > 2000:
        errors.append("constraints exceeds the agent's 2000-char limit")
    if s.get("status") != "draft":
        errors.append("bridge must never auto-activate the operator (status must stay 'draft')")
    return errors


def api_call(method: str, path: str, body: dict | None = None) -> Any:
    base = os.environ.get("AGENT_BASE_URL", "http://localhost:3456").rstrip("/")
    key = os.environ.get("AGENT_API_KEY", "")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        base + path,
        data=data,
        method=method,
        headers={
            "Content-Type": "application/json",
            "x-api-key": key,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return json.loads(raw) if raw.strip().startswith(("{", "[")) else raw
    except urllib.error.HTTPError as exc:
        raise SystemExit(f"Agent returned HTTP {exc.code} for {method} {path}: "
                         f"{exc.read().decode('utf-8', 'replace')[:300]}") from exc
    except urllib.error.URLError as exc:
        raise SystemExit(
            f"Cannot reach the agent at {base}. Is it running? (npm start in the cloned repo)\n"
            f"Detail: {exc.reason}"
        ) from exc


def write_out(name: str, data: Any) -> Path:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / name
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return path


def main() -> int:
    ap = argparse.ArgumentParser(description="Bridge to the self-hosted YouTube Automation Agent")
    ap.add_argument("command", choices=["strategy", "manifest", "readiness"])
    ap.add_argument("--send", action="store_true", help="PUT the strategy to the agent (strategy only)")
    ap.add_argument("--yes", action="store_true", help="skip confirmation (non-interactive)")
    ap.add_argument("--no-write", action="store_true", help="don't write integrations/out/*")
    args = ap.parse_args()

    episodes = load_episodes()

    if args.command == "strategy":
        strategy = build_strategy(episodes)
        errors = validate_strategy(strategy)
        if errors:
            print("INVALID STRATEGY:\n  " + "\n  ".join(errors), file=sys.stderr)
            return 2
        print(json.dumps(strategy, indent=2, ensure_ascii=False))
        if not args.no_write:
            print("\n✓ wrote " + str(write_out("operator-strategy.json", strategy).relative_to(ROOT)))
        if args.send:
            if not args.yes:
                print("\nRefusing to push a strategy without --yes (it would change the agent's config).",
                      file=sys.stderr)
                return 2
            if not os.environ.get("AGENT_API_KEY"):
                print("NOTE: AGENT_API_KEY is unset; the agent may reject a mutating request.",
                      file=sys.stderr)
            result = api_call("PUT", "/api/operator/strategy", strategy)
            print("✓ Strategy saved as DRAFT. Activate it in the dashboard only after review.")
            print(json.dumps(result, indent=2, ensure_ascii=False)[:600])
        elif not args.yes:
            print("\nDRAFT only — nothing was sent. Add --send --yes to PUT it to the agent.")
        return 0

    if args.command == "manifest":
        manifest = build_manifest(episodes)
        print(f"Episode manifest: {len(manifest['episodes'])} episodes, "
              f"{sum(1 for e in manifest['episodes'] if e['verified'])} fact-verified")
        for e in manifest["episodes"]:
            print(f"  {e['id']}  [{e['series']}]  {e['topic'][:62]}")
        if not args.no_write:
            print("✓ wrote " + str(write_out("episode-manifest.json", manifest).relative_to(ROOT)))
        return 0

    # readiness
    result = api_call("GET", "/api/readiness")
    print(json.dumps(result, indent=2, ensure_ascii=False)[:2000])
    return 0


if __name__ == "__main__":
    sys.exit(main())
