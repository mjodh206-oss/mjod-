"""Offline tests for the render engine + ready-to-paste queue (no Pillow needed to import)."""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "automation"))

from build_short import TIME_RE, load_episodes, srt_time  # noqa: E402
from run_channel import validate_queue  # noqa: E402


class RenderEngineTests(unittest.TestCase):
    def test_srt_timestamps(self):
        self.assertEqual(srt_time(0), "00:00:00,000")
        self.assertEqual(srt_time(3.5), "00:00:03,500")
        self.assertEqual(srt_time(62.25), "00:01:02,250")

    def test_beat_time_parser(self):
        m = TIME_RE.match("22-27")
        self.assertEqual((float(m.group(1)), float(m.group(2))), (22.0, 27.0))
        self.assertIsNone(TIME_RE.match("bad"))

    def test_all_episodes_loaded(self):
        eps = load_episodes(None)
        self.assertEqual(len(eps), 5)
        self.assertTrue(all(ep["format"] == "shorts" for ep in eps))

    def test_rendered_files_exist_when_assets_are_present(self):
        mp4s = sorted((ROOT / "content" / "videos").glob("ep-*.mp4"))
        if not mp4s:
            self.skipTest("media is gitignored and not present in a fresh clone")
        for ep in load_episodes(None):
            with self.subTest(episode=ep["id"]):
                mp4 = ROOT / "content" / "videos" / f"{ep['id']}.mp4"
                srt = ROOT / "content" / "videos" / f"{ep['id']}.srt"
                thumb = ROOT / "assets" / "thumbnails" / f"{ep['id']}.jpg"
                self.assertTrue(mp4.exists(), f"missing {mp4}")
                self.assertTrue(srt.exists(), f"missing {srt}")
                self.assertTrue(thumb.exists(), f"missing {thumb}")
                self.assertGreater(mp4.stat().st_size, 100_000)
                cues = srt.read_text(encoding="utf-8").splitlines()
                self.assertEqual(sum(1 for line in cues if "-->" in line), 8,
                                 "one SRT cue per beat")


class ReadyQueueTests(unittest.TestCase):
    def test_ready_queue_is_valid(self):
        data = json.loads((ROOT / "content" / "publish_queue.ready.json").read_text(encoding="utf-8"))
        self.assertEqual(validate_queue(data), [])
        self.assertEqual(len(data["items"]), 5)

    def test_ready_queue_is_disabled_and_points_at_local_files(self):
        data = json.loads((ROOT / "content" / "publish_queue.ready.json").read_text(encoding="utf-8"))
        for item in data["items"]:
            with self.subTest(episode=item["episode_id"]):
                self.assertFalse(item["enabled"], "must never auto-publish before authorization")
                self.assertIsNone(item.get("video_url"))
                self.assertTrue(str(item["video_path"]).endswith(".mp4"))


if __name__ == "__main__":
    unittest.main()
