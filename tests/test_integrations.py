"""Offline tests for the GitHub-tool bridge and the one-command channel loop."""
from __future__ import annotations

import json
import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "automation"))
sys.path.insert(0, str(ROOT / "integrations"))

from agent_bridge import (  # noqa: E402
    build_manifest,
    build_strategy,
    validate_strategy,
)
from validate_content import load_episodes  # noqa: E402
from run_channel import is_due, validate_queue  # noqa: E402


class AgentBridgeTests(unittest.TestCase):
    def test_strategy_matches_agent_validation_rules(self):
        s = build_strategy(load_episodes())
        self.assertEqual(validate_strategy(s), [])
        self.assertEqual(s["status"], "draft")
        self.assertEqual(s["defaultFormat"], "explainer")
        self.assertEqual(s["defaultLength"], "short")
        self.assertEqual(s["cadencePerWeek"], 6)
        self.assertLessEqual(len(s["constraints"]), 2000)
        self.assertIn("Brain Glitches", s["contentPillars"])

    def test_bridge_refuses_to_emit_an_active_strategy(self):
        s = build_strategy(load_episodes())
        s["status"] = "active"
        self.assertTrue(any("auto-activate" in e for e in validate_strategy(s)))

    def test_manifest_carries_verifiable_claims(self):
        m = build_manifest(load_episodes())
        self.assertEqual(len(m["episodes"]), 5)
        for ep in m["episodes"]:
            with self.subTest(episode=ep["id"]):
                self.assertTrue(ep["claim"])
                self.assertTrue(ep["source_hint"])
                self.assertIsInstance(ep["verified"], bool)
                self.assertTrue(ep["selected_hook"])


class QueueTests(unittest.TestCase):
    def test_committed_queue_is_valid_and_empty(self):
        data = json.loads((ROOT / "content" / "publish_queue.json").read_text(encoding="utf-8"))
        self.assertEqual(validate_queue(data), [])
        self.assertEqual(data["items"], [])

    def test_invalid_items_are_rejected(self):
        bad = {"items": [
            {"episode_id": "nope", "enabled": True, "video_url": "http://insecure.example/a.mp4"},
            {"episode_id": "ep-001", "enabled": True},
            {"episode_id": "ep-002", "enabled": "yes", "video_url": "https://x/a.mp4"},
            {"episode_id": "ep-003", "enabled": False, "publish_at": "not-a-date"},
        ]}
        errors = validate_queue(bad)
        self.assertGreaterEqual(len(errors), 5)

    def test_valid_item_passes(self):
        good = {"items": [{
            "episode_id": "ep-001",
            "video_url": "https://storage.example/ep-001.mp4",
            "thumbnail_url": None,
            "publish_at": None,
            "enabled": True,
        }]}
        self.assertEqual(validate_queue(good), [])

    def test_due_logic(self):
        now = datetime(2026, 11, 9, 18, 0, tzinfo=timezone.utc)
        self.assertTrue(is_due({"publish_at": None}, now))
        self.assertTrue(is_due({"publish_at": "2026-11-09T17:30:00Z"}, now))
        self.assertFalse(is_due({"publish_at": (now + timedelta(days=1)).isoformat()}, now))


class AuthSetupTests(unittest.TestCase):
    def test_help_works_without_google_dependencies(self):
        import subprocess
        result = subprocess.run(
            [sys.executable, "automation/auth_setup.py", "--help"],
            cwd=ROOT, capture_output=True, text=True, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--no-browser", result.stdout)
        self.assertIn("--port", result.stdout)
        self.assertIn("--no-print-token", result.stdout)

    def test_auth_links_doc_has_no_fabricated_consent_url(self):
        """Every consent URL shown in the doc must be an obvious placeholder, never a real-looking link."""
        text = (ROOT / "docs" / "AUTH_LINKS.md").read_text(encoding="utf-8")
        self.assertIn("https://console.cloud.google.com/apis/credentials", text)
        self.assertIn("https://github.com/mjodh206-oss/mjod-/settings/secrets/actions", text)
        self.assertIn("--no-browser", text)
        consent_urls = [line for line in text.splitlines()
                        if "accounts.google.com/o/oauth2" in line]
        self.assertTrue(consent_urls)
        self.assertTrue(all("XXXX" in url for url in consent_urls),
                        "a consent URL with a real-looking client_id must never be hardcoded")


if __name__ == "__main__":
    unittest.main()
