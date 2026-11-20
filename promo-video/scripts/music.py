"""Synthesizes a light background track into public/music.m4a (no samples, standard library + ffmpeg).

D major, I-V-vi-IV, at CONFIG.bpm: a soft pad, a plucked arpeggio on eighths, a gentle kick from the
second scene on, airy hats in the middle part, and a fade-out at the end.
Run: python3 scripts/music.py
"""
import math
import os
import random
import re
import struct
import subprocess
import wave

ROOT = os.path.join(os.path.dirname(__file__), "..")
cfg = open(os.path.join(ROOT, "src", "config.ts"), encoding="utf-8").read()
BPM = float(re.search(r"bpm:\s*([\d.]+)", cfg).group(1))
block = re.search(r"scenes:\s*\{(.*?)\}", cfg, re.S).group(1)
TOTAL = sum(float(v) for _, v in re.findall(r"(\w+):\s*([\d.]+)", block))
INTRO = float(re.search(r"intro:\s*([\d.]+)", block).group(1))

RATE = 22050
N = int(TOTAL * RATE)
beat = 60.0 / BPM
bar = beat * 4
out = [0.0] * N

def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)

# D A Bm G (two bars each); chord tones as MIDI notes
CHORDS = [[62, 66, 69], [57, 61, 64], [59, 62, 66], [55, 59, 62]]

def add(start, dur, fn):
    a = int(start * RATE)
    b = min(N, a + int(dur * RATE))
    for i in range(a, b):
        out[i] += fn((i - a) / RATE)

random.seed(3)
t = 0.0
k = 0
while t < TOTAL:
    chord = CHORDS[(k // 2) % 4]
    # Pad: three chord tones with soft harmonics, slow attack and release, across the bar
    for n in chord:
        f = midi(n)
        add(t, bar + 0.4, lambda x, f=f: 0.05 * min(1, x / 0.6) * min(1, (bar + 0.4 - x) / 0.6)
            * (math.sin(2 * math.pi * f * x) + 0.3 * math.sin(4 * math.pi * f * x + 0.5) + 0.12 * math.sin(2 * math.pi * f * 1.003 * x)))
    # Bass on the root
    fb = midi(chord[0] - 24)
    add(t, bar, lambda x: 0.11 * min(1, x / 0.02) * math.exp(-1.2 * x) * math.sin(2 * math.pi * fb * x))
    # Arpeggio: eighths up and down the chord, an octave higher
    arp = [chord[0] + 12, chord[1] + 12, chord[2] + 12, chord[1] + 12 + (12 if k % 2 else 0)]
    for e in range(8):
        f = midi(arp[e % 4] + (12 if e in (3, 7) else 0))
        add(t + e * beat / 2, beat * 0.9, lambda x, f=f: 0.06 * min(1, x / 0.004) * math.exp(-7 * x)
            * (math.sin(2 * math.pi * f * x) + 0.25 * math.sin(6 * math.pi * f * x)))
    if t >= INTRO - 0.5 and t < TOTAL - bar * 2:
        # Kick on 1 and 3
        for b4 in (0, 2):
            add(t + b4 * beat, 0.3, lambda x: 0.32 * math.exp(-14 * x) * math.sin(2 * math.pi * (110 * math.exp(-18 * x) + 45) * x))
        # Hats on the offbeats in the middle section
        if 0.25 * TOTAL < t < 0.85 * TOTAL:
            for b4 in range(4):
                add(t + b4 * beat + beat / 2, 0.06, lambda x: 0.035 * math.exp(-60 * x) * random.uniform(-1, 1))
    t += bar
    k += 1

# Fade in / out and normalize
fade_in, fade_out = int(1.0 * RATE), int(3.0 * RATE)
for i in range(N):
    g = min(1.0, i / fade_in) * min(1.0, (N - i) / fade_out)
    out[i] *= g
peak = max(abs(s) for s in out) or 1
wav = os.path.join(ROOT, "public", "music.wav")
with wave.open(wav, "w") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(b"".join(struct.pack("<h", int(30000 * s / peak)) for s in out))
m4a = os.path.join(ROOT, "public", "music.m4a")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ar", "44100", "-c:a", "aac", "-b:a", "160k", m4a], check=True)
os.remove(wav)
print("written", os.path.abspath(m4a), f"{TOTAL:.0f}s at {BPM:.0f} BPM")
