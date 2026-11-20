"""Builds public/voiceover.wav with Piper (offline TTS, Russian voice) from voiceover.md's spoken lines.

Each line is placed at its scene start + `at` (timings from src/config.ts and src/content.ts), so the
voice matches the subtitles. Lines that would run into the next one are reported.
Run: .venv/bin/python scripts/voiceover.py
"""
import os
import re
import struct
import subprocess
import tempfile
import wave

ROOT = os.path.join(os.path.dirname(__file__), "..")
VOICE = os.path.join(ROOT, "voices", "ru_RU-dmitri-medium.onnx")
PIPER = os.path.join(ROOT, ".venv", "bin", "piper")

config = open(os.path.join(ROOT, "src", "config.ts"), encoding="utf-8").read()
block = re.search(r"scenes:\s*\{(.*?)\}", config, re.S).group(1)
scenes = [(k, float(v)) for k, v in re.findall(r"(\w+):\s*([\d.]+)", block)]
starts, t = {}, 0.0
for k, v in scenes:
    starts[k] = t
    t += v
total = t

# Spoken text (pronunciation-friendly) per scene line, in the same order as VOICEOVER in content.ts.
md = open(os.path.join(ROOT, "voiceover.md"), encoding="utf-8").read()
lines = re.findall(r"^\|\s*(\w+)\s*\|\s*([\d.]+)\s*\|[^|]*\|\s*(.+?)\s*\|\s*$", md, re.M)

RATE = 22050
track = [0] * int(total * RATE)
for scene, at, spoken in lines:
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        tmp = f.name
    subprocess.run([PIPER, "-m", VOICE, "-f", tmp, "--length-scale", "0.95", "--sentence-silence", "0.15"],
                   input=spoken.encode("utf-8"), check=True, capture_output=True)
    with wave.open(tmp) as w:
        assert w.getframerate() == RATE and w.getsampwidth() == 2
        data = struct.unpack("<%dh" % w.getnframes(), w.readframes(w.getnframes()))
    os.unlink(tmp)
    start = int((starts[scene] + float(at)) * RATE)
    dur = len(data) / RATE
    print(f"{scene:14s} {float(at):5.1f}s  {dur:4.1f}s  {spoken}")
    for i, s in enumerate(data):
        if start + i < len(track):
            track[start + i] = max(-32768, min(32767, track[start + i] + s))

out = os.path.join(ROOT, "public", "voiceover.wav")
with wave.open(out, "w") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(struct.pack("<%dh" % len(track), *track))
print("written", os.path.abspath(out), f"{total:.1f}s")
