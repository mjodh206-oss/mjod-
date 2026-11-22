"""Preview stills from the part-4 renderer."""
import os
import sys

import cv2
import numpy as np

from .render_p5 import render_frame, SECTIONS
from .core import FPS
from .timeline import EP


def sec_at(t):
    for i, s in enumerate(SECTIONS):
        if t < s["end"] or i == len(SECTIONS) - 1:
            return s
    return SECTIONS[-1]


def save(times, prefix=None):
    prefix = prefix or (EP["build"] + "/p5")
    os.makedirs(os.path.dirname(prefix) or ".", exist_ok=True)
    for t in times:
        sec = sec_at(t)
        fr = render_frame(int(t * FPS), 0, lambda f: sec)
        p = f"{prefix}_{t:06.2f}.png"
        cv2.imwrite(p, cv2.cvtColor(fr, cv2.COLOR_RGB2BGR))
        print("wrote", p)


if __name__ == "__main__":
    times = [float(x) for x in sys.argv[1:]] or [0.2, 0.8, 1.4]
    save(times)
