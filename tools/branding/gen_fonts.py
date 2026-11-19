#!/usr/bin/env python3
"""Builds the app's bundled UI fonts from branding/fonts (variable fonts, OFL).

Keeps only the weight axis (wght) so the app can pick any weight with
Typeface.Builder#setFontVariationSettings, and pins every other axis to its default:
the files shrink a lot and still cover all weights.

    .venv/bin/python tools/branding/gen_fonts.py
"""
import os

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "branding", "fonts")
OUT = os.path.join(ROOT, "TMessagesProj", "src", "main", "assets", "fonts")

FONTS = {
    # source file -> app asset
    "RobotoFlex.ttf": "yili_robotoflex.ttf",
    "GoogleSansFlex.ttf": "yili_googlesansflex.ttf",
}


def build(src: str, dst: str):
    font = TTFont(os.path.join(SRC, src))
    pins = {a.axisTag: a.defaultValue for a in font["fvar"].axes if a.axisTag != "wght"}
    if "opsz" in pins:
        pins["opsz"] = 14  # body text size
    out = instancer.instantiateVariableFont(font, pins)
    path = os.path.join(OUT, dst)
    out.save(path)
    print(f"{dst}: {os.path.getsize(path) // 1024} KiB, axes pinned: {', '.join(sorted(pins))}")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for s, d in FONTS.items():
        build(s, d)
