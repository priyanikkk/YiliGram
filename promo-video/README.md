# YiliGram promo video

A promo for YiliGram made with [Remotion](https://www.remotion.dev) (React + TypeScript), in the
Material 3 Expressive style: an M3 palette generated from the logo's color, expressive shape
morphing, spring motion, variable-font typography.

| Output | Size | Length |
|---|---|---|
| `out/yiligram-1080x1920.mp4` | 1080×1920, 30 fps, H.264 | 78 s |
| `out/yiligram-1920x1080.mp4` | 1920×1080, same scenes, adaptive layout | 78 s |
| `out/yiligram-teaser.mp4` | 1080×1920 | 12 s |
| `out/yiligram-teaser.gif` | 432×768, 15 fps | 12 s |

## Commands

```bash
npm install
npm run preview          # Remotion Studio: scrub, tweak, preview
npm run render           # all four outputs into out/
npm run render:vertical  # one at a time: render:vertical, render:horizontal, render:teaser, render:gif
npm run stills           # a still of every scene (both formats) into out/stills, for checking layout
npm run voiceover        # rebuild public/voiceover.wav from voiceover.md (Piper, see below)
npm run sfx              # rebuild the UI sounds in public/sfx
```

The first render downloads a headless Chrome for Remotion.

## Where to change things

| What | File |
|---|---|
| Seed color, scene lengths, links, BPM, audio switches | `src/config.ts` |
| All on-screen text and the subtitles | `src/content.ts` |
| Voice-over text (with a pronunciation column for the TTS) | `voiceover.md` |
| Color roles (material-color-utilities), M3 springs, font | `src/theme.ts` |
| Expressive shapes and morphing | `src/shapes.ts` |
| Shared components (phone, chips, switch, slider, loading indicator, QR, badges, code) | `src/components/` |
| Scenes | `src/scenes/` |

Every color is an M3 role of the scheme generated from `CONFIG.seed` (`#8C7A5B`, the champagne of the
YiliGram cat, also the app's default seed). Scene 2 recolors the frame through `CONFIG.showcaseSeeds`.
Shapes are polar radius functions sampled at the same angles, so any two morph smoothly. Motion uses
Remotion's `spring()` with the M3 Expressive spatial / effects spring tokens.

The phone screens in scenes 2 and 4 are real YiliGram screenshots taken on the emulator
(`public/screens`); the chat list in scene 3 is drawn in code with demo names only.

## Audio

- **Voice-over**: `public/voiceover.wav`, generated offline with [Piper](https://github.com/rhasspy/piper)
  and the Russian voice `ru_RU-dmitri-medium`:
  ```bash
  python3 -m venv .venv && .venv/bin/pip install piper-tts
  mkdir -p voices && cd voices
  curl -LO https://huggingface.co/rhasspy/piper-voices/resolve/main/ru/ru_RU/dmitri/medium/ru_RU-dmitri-medium.onnx
  curl -LO https://huggingface.co/rhasspy/piper-voices/resolve/main/ru/ru_RU/dmitri/medium/ru_RU-dmitri-medium.onnx.json
  cd .. && npm run voiceover
  ```
  To use your own recording, replace `public/voiceover.wav` (78 s, lines starting at the seconds listed
  in `voiceover.md`, counted from each scene's start in `src/config.ts`). Set `hasVoiceover: false`
  for subtitles only.
- **Music**: none is shipped. Put a track at `public/music.mp3`, set `hasMusic: true` and its `bpm` in
  `src/config.ts`.
- **UI sounds**: clicks, ticks, the "poof" of a morph and a notification ding, synthesized by
  `scripts/sfx.py` into `public/sfx` (cues in `src/Root.tsx`).

## Fonts

Roboto Flex (SIL OFL, `public/fonts/RobotoFlex-OFL.txt`), animated on its weight and width axes.
Google Sans Flex has no Cyrillic, so it is not used for this Russian video.

Rendered videos (`out/`), the TTS voices and the Python venv are not committed.
