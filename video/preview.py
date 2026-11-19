"""Render still frames at given timestamps for fast visual iteration."""
import os
import sys

import cv2
import numpy as np

from .render import render_frame, SECTIONS
from .core import FPS
from .timeline import EP
import os


def sec_at(t):
    for i, s in enumerate(SECTIONS):
        if t < s["end"] or i == len(SECTIONS) - 1:
            return s
    return SECTIONS[-1]


def save(times, prefix=None):
    prefix = prefix or (EP["build"] + "/preview")
    os.makedirs(os.path.dirname(prefix) or ".", exist_ok=True)
    panels = []
    for t in times:
        fi = int(t * FPS)
        sec = sec_at(t)
        fr = render_frame(fi, 0, lambda f: sec)
        p = f"{prefix}_{t:06.2f}.png"
        cv2.imwrite(p, cv2.cvtColor(fr, cv2.COLOR_RGB2BGR))
        panels.append(p)
        print("wrote", p)
    # contact sheet
    imgs = [cv2.imread(p) for p in panels]
    imgs = [cv2.resize(i, (360, 640)) for i in imgs]
    sheet = np.hstack(imgs)
    out = prefix + "_sheet.png"
    cv2.imwrite(out, sheet)
    print("sheet", out)


if __name__ == "__main__":
    times = [float(x) for x in sys.argv[1:]] or [0.3, 2.2, 6.2, 10.6, 12.0, 14.5]
    save(times)
