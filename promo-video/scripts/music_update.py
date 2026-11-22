"""Synthesizes the track of the 0.1.4 update video into public/music_update.m4a.

A major, vi-IV-I-V at 122 BPM: plucked arpeggio, soft pad, bass, kick and claps from the second scene,
a bell on every scene change. Scene lengths are read from src/update/Update.tsx.
Run: python3 scripts/music_update.py
"""
import math
import os
import random
import re
import struct
import subprocess
import wave

ROOT = os.path.join(os.path.dirname(__file__), "..")
src = open(os.path.join(ROOT, "src", "update", "Update.tsx"), encoding="utf-8").read()
SCENES = [float(v) for v in re.findall(r"\{key: '\w+', seconds: ([\d.]+)", src)]
TOTAL = sum(SCENES)

BPM = 122.0
RATE = 22050
N = int(TOTAL * RATE)
beat = 60.0 / BPM
bar = beat * 4
out = [0.0] * N


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


# F#m D A E
CHORDS = [[66, 69, 73], [62, 66, 69], [57, 61, 64], [64, 68, 71]]


def add(start, dur, fn):
    a = int(start * RATE)
    b = min(N, a + int(dur * RATE))
    for i in range(max(0, a), b):
        out[i] += fn((i - a) / RATE)


random.seed(7)
t, k = 0.0, 0
first = SCENES[0]
while t < TOTAL:
    chord = CHORDS[k % 4]
    for n in chord:
        f = midi(n - 12)
        add(t, bar + 0.3, lambda x, f=f: 0.035 * min(1, x / 0.4) * min(1, (bar + 0.3 - x) / 0.4)
            * (math.sin(2 * math.pi * f * x) + 0.25 * math.sin(4 * math.pi * f * x)))
    fb = midi(chord[0] - 24)
    for b4 in range(4):
        add(t + b4 * beat, beat * 0.9, lambda x: 0.10 * min(1, x / 0.01) * math.exp(-3 * x) * math.sin(2 * math.pi * fb * x))
    arp = [chord[0], chord[1], chord[2], chord[0] + 12, chord[2], chord[1], chord[0] + 12, chord[2] + 12]
    for e in range(8):
        f = midi(arp[e] + 12)
        add(t + e * beat / 2, beat * 0.8, lambda x, f=f: 0.05 * min(1, x / 0.003) * math.exp(-8 * x)
            * (math.sin(2 * math.pi * f * x) + 0.3 * math.sin(4 * math.pi * f * x) + 0.1 * math.sin(6 * math.pi * f * x)))
    if first - 0.3 <= t < TOTAL - bar * 1.5:
        for b4 in range(4):
            add(t + b4 * beat, 0.3, lambda x: 0.30 * math.exp(-13 * x) * math.sin(2 * math.pi * (120 * math.exp(-20 * x) + 48) * x))
        for b4 in (1, 3):
            add(t + b4 * beat, 0.12, lambda x: 0.06 * math.exp(-30 * x) * random.uniform(-1, 1))
        for b4 in range(4):
            add(t + b4 * beat + beat / 2, 0.05, lambda x: 0.025 * math.exp(-70 * x) * random.uniform(-1, 1))
    t += bar
    k += 1

# A bell on every scene change
at = 0.0
for i, s in enumerate(SCENES):
    f = midi([81, 85, 88, 86][i % 4])
    add(at + 0.02, 1.6, lambda x, f=f: 0.06 * math.exp(-3.5 * x) * (math.sin(2 * math.pi * f * x) + 0.4 * math.sin(2 * math.pi * f * 2.76 * x)))
    at += s

fade_in, fade_out = int(0.6 * RATE), int(2.5 * RATE)
for i in range(N):
    out[i] *= min(1.0, i / fade_in) * min(1.0, (N - i) / fade_out)
peak = max(abs(s) for s in out) or 1
wav = os.path.join(ROOT, "public", "music_update.wav")
with wave.open(wav, "w") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(b"".join(struct.pack("<h", int(30000 * s / peak)) for s in out))
m4a = os.path.join(ROOT, "public", "music_update.m4a")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ar", "44100", "-c:a", "aac", "-b:a", "160k", m4a], check=True)
os.remove(wav)
print("written", os.path.abspath(m4a), f"{TOTAL:.1f}s at {BPM:.0f} BPM")
