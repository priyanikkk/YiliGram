"""Synthesizes the short UI sounds into public/sfx (16-bit mono WAV, standard library only)."""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "public", "sfx")


def write(name, samples):
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, name + ".wav"), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        peak = max(1e-9, max(abs(s) for s in samples))
        w.writeframes(b"".join(struct.pack("<h", int(32000 * 0.9 * s / peak)) for s in samples))


def env(i, n, attack=0.004, decay=6.0):
    t = i / RATE
    a = min(1.0, t / attack) if attack > 0 else 1.0
    return a * math.exp(-decay * t / (n / RATE))


random.seed(7)
# poof: band-passed noise sweeping down (the "air" of a shape morph)
n = int(RATE * 0.35)
lp = 0.0
poof = []
for i in range(n):
    cutoff = 0.25 * (1 - i / n) + 0.02
    lp += cutoff * (random.uniform(-1, 1) - lp)
    poof.append(lp * env(i, n, 0.01, 5))
write("poof", poof)
# pop: short sine blip with a pitch drop
n = int(RATE * 0.16)
write("pop", [math.sin(2 * math.pi * (900 - 500 * i / n) * i / RATE) * env(i, n, 0.002, 7) for i in range(n)])
# click: tiny filtered tick (switch)
n = int(RATE * 0.05)
write("click", [(math.sin(2 * math.pi * 2600 * i / RATE) + 0.4 * random.uniform(-1, 1)) * env(i, n, 0.0005, 9) for i in range(n)])
# tick: softer, lower (slider)
n = int(RATE * 0.06)
write("tick", [math.sin(2 * math.pi * 1400 * i / RATE) * env(i, n, 0.001, 8) for i in range(n)])
# ding: two partials (notification)
n = int(RATE * 0.9)
write("ding", [(math.sin(2 * math.pi * 1320 * i / RATE) + 0.5 * math.sin(2 * math.pi * 1980 * i / RATE)) * env(i, n, 0.003, 5) for i in range(n)])
print("sfx written to", os.path.abspath(OUT))
