"""Offline tests: python3 -m unittest discover -s tests -v"""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tools"))

from hook_engine import generate  # noqa: E402
from render_scripts import build_markdown  # noqa: E402
from validate_content import (  # noqa: E402
    REPLIES_FILE,
    load_episodes,
    run,
    validate_episode,
)


class ContentPackTests(unittest.TestCase):
    def test_pack_has_no_structural_errors(self):
        result = run()
        self.assertEqual(result["errors"], [], "\n".join(result["errors"]))
        self.assertGreaterEqual(result["episodes"], 5)

    def test_every_episode_validates_individually(self):
        for path, ep in load_episodes():
            with self.subTest(episode=path.name):
                errors, _ = validate_episode(path, ep)
                self.assertEqual(errors, [], "\n".join(errors))

    def test_hooks_are_three_seconds_or_less(self):
        for path, ep in load_episodes():
            for hook in ep["hooks"]:
                with self.subTest(episode=path.name, hook=hook):
                    self.assertLessEqual(len(hook.split()), 12)
                    self.assertFalse(hook.lower().startswith(("hey", "welcome", "today we")))

    def test_timelines_are_contiguous_and_end_at_duration(self):
        for path, ep in load_episodes():
            with self.subTest(episode=path.name):
                cursor = 0
                for beat in ep["beats"]:
                    start, end = (int(x) for x in beat["t"].split("-"))
                    self.assertEqual(start, cursor)
                    self.assertGreater(end, start)
                    cursor = end
                self.assertEqual(cursor, ep["duration_seconds"])

    def test_every_episode_has_rehook_and_payoff(self):
        for path, ep in load_episodes():
            devices = " ".join(b["device"] for b in ep["beats"])
            with self.subTest(episode=path.name):
                self.assertIn("re-hook", devices)
                self.assertIn("payoff", devices)

    def test_unverified_facts_are_flagged(self):
        """All shipped episodes start unverified; the publisher must refuse them."""
        result = run()
        self.assertTrue(any("NOT FACT-CHECKED" in w for w in result["warnings"]))


class ReplyBankTests(unittest.TestCase):
    def test_reply_bank_is_complete(self):
        data = json.loads(REPLIES_FILE.read_text(encoding="utf-8"))
        self.assertIn("default", data["categories"])
        self.assertTrue(data["rules"]["human_queue_keywords"])
        self.assertGreater(data["rules"]["max_replies_per_run"], 0)
        for name, cat in data["categories"].items():
            with self.subTest(category=name):
                self.assertTrue(cat["replies"], f"{name} has no replies")


class ToolTests(unittest.TestCase):
    def test_hook_engine_produces_short_variants(self):
        hooks = generate("why honey never spoils", count=8)
        self.assertEqual(len(hooks), 8)
        self.assertEqual(len({h["family"] for h in hooks}), 8)
        for h in hooks:
            self.assertLessEqual(h["words"], 12)

    def test_render_includes_all_episodes(self):
        md = build_markdown(load_episodes())
        for _, ep in load_episodes():
            self.assertIn(ep["id"], md)
            self.assertIn(ep["pinned_comment"], md)


if __name__ == "__main__":
    unittest.main()
