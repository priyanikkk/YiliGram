import React from 'react';
import {AbsoluteFill, useCurrentFrame, useVideoConfig} from 'remotion';
import {FloatingShapes, Headline, useLayout} from '../components/ui';
import {Badge, BadgeKind} from '../components/extra';
import {TEXT} from '../content';
import {CONFIG} from '../config';
import {FONT, roles, sp, SPRING} from '../theme';

/** The app's typing animation (blur-in letters, gliding cursor, particle deletion) and the badges. */
export const Details: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  const sec = (s: number) => Math.round(s * fps);
  const r = roles(CONFIG.showcaseSeeds[3]);
  const text = TEXT.details.typed;
  const typeStart = sec(0.4), perChar = 3;
  const deleteAt = sec(2.6), deleteN = 8;
  const typedCount = Math.min(text.length, Math.max(0, Math.floor((frame - typeStart) / perChar)));
  const deleted = Math.max(0, Math.min(deleteN, Math.floor((frame - deleteAt) / 3)));
  const fieldW = (vertical ? 900 : 860) * u, fs = 60 * u;
  // Real x of every character (prefix widths in the loaded variable font).
  const xs = React.useMemo(() => {
    const ctx = document.createElement('canvas').getContext('2d')!;
    ctx.font = `400 ${fs}px ${FONT}`;
    return Array.from({length: text.length + 1}, (_, i) => ctx.measureText(text.slice(0, i)).width);
  }, [fs, text]);
  const cursor = (() => {
    // Ease toward the target like the app's gliding cursor (exponential, ~70 ms).
    let x = 0;
    for (let f = 0; f <= frame; f++) {
      const tc = Math.min(text.length, Math.max(0, Math.floor((f - typeStart) / perChar)));
      const dc = Math.max(0, Math.min(deleteN, Math.floor((f - deleteAt) / 3)));
      const target = xs[Math.max(0, tc - dc)];
      x += (target - x) * (1 - Math.exp(-(1000 / fps) / 70));
    }
    return x;
  })();
  const badgeAt = sec(3.6);
  const kinds: BadgeKind[] = ['admin', 'beta', 'official'];
  const out = sp(frame, fps, sec(CONFIG.scenes.details) - 10, SPRING.effects);
  return (
    <AbsoluteFill style={{background: r.surfaceContainerLow, opacity: 1 - out}}>
      <FloatingShapes r={r} count={6} seed={21} opacity={0.4} />
      <div style={{position: 'absolute', left: 0, right: 0, top: vertical ? H * 0.08 : H * 0.08}}>
        <Headline text={TEXT.details.typing} color={r.onSurface} size={(vertical ? 88 : 80) * u} delay={2} weight={800} />
      </div>
      {/* Message field */}
      <div style={{
        position: 'absolute', left: W / 2 - fieldW / 2, top: vertical ? H * 0.24 : H * 0.22, width: fieldW, height: 150 * u,
        borderRadius: 75 * u, background: r.surfaceContainerHighest, display: 'flex', alignItems: 'center', padding: `0 ${56 * u}px`,
        transform: `scale(${sp(frame, fps, 2, SPRING.spatial)})`,
      }}>
        <div style={{position: 'relative', fontFamily: FONT, fontSize: fs, color: r.onSurface, height: fs * 1.2, flex: 1}}>
          {Array.from(text).map((ch, i) => {
            const born = typeStart + i * perChar;
            if (frame < born) return null;
            const isDeleted = i >= typedCount - deleted && i < typedCount && frame >= deleteAt;
            const t = sp(frame, fps, born, SPRING.spatialFast);
            const blur = Math.max(0, (1 - t) * 10 * u);
            if (isDeleted) {
              const dIdx = typedCount - 1 - i;
              const dt = Math.min(1, Math.max(0, (frame - deleteAt - dIdx * 3) / 14));
              return (
                <span key={i} style={{position: 'absolute', left: xs[i], top: 0}}>
                  <span style={{display: 'inline-block', opacity: 1 - dt, transform: `translateY(${-dt * 30 * u}px) scale(${1 - dt * 0.5})`}}>{ch}</span>
                  {Array.from({length: 6}, (_, p) => {
                    const ang = (-160 + p * 28) * Math.PI / 180;
                    const dist = dt * (50 + p * 9) * u;
                    return <span key={p} style={{position: 'absolute', left: (xs[i + 1] - xs[i]) / 2 + Math.cos(ang) * dist, top: fs * 0.5 + Math.sin(ang) * dist + dt * dt * 40 * u,
                      width: 8 * u, height: 8 * u, borderRadius: '50%', background: r.primary, opacity: 1 - dt}} />;
                  })}
                </span>
              );
            }
            return (
              <span key={i} style={{position: 'absolute', left: xs[i], top: 0, display: 'inline-block', fontVariationSettings: `'wght' 400`,
                transform: `translateY(${(1 - t) * 18 * u}px) scale(${0.35 + 0.65 * t})`, filter: `blur(${blur}px)`, opacity: Math.min(1, t * 2)}}>{ch === ' ' ? ' ' : ch}</span>
            );
          })}
          <div style={{position: 'absolute', left: cursor, top: fs * 0.05, width: 5 * u, height: fs * 1.1, borderRadius: 3 * u, background: r.primary}} />
        </div>
      </div>
      {/* Badges */}
      <div style={{position: 'absolute', left: 0, right: 0, top: vertical ? H * 0.45 : H * 0.4, opacity: Math.min(1, sp(frame, fps, badgeAt, SPRING.effects))}}>
        {frame >= badgeAt - 2 ? <Headline text={TEXT.details.badges} color={r.primary} size={(vertical ? 76 : 64) * u} delay={badgeAt} weight={760} /> : null}
        <div style={{display: 'flex', justifyContent: 'center', gap: (vertical ? 50 : 90) * u, marginTop: 40 * u}}>
          {kinds.map((k, i) => {
            const t = sp(frame, fps, badgeAt + 6 + i * 6, SPRING.spatialFast);
            // Each badge briefly morphs into the next one and back.
            const mAt = sec(5.6) + i * 8;
            const m = sp(frame, fps, mAt, SPRING.spatial) - sp(frame, fps, mAt + 16, SPRING.spatial);
            return (
              <div key={k} style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 22 * u, transform: `scale(${t})`}}>
                <Badge kind={k} morphTo={kinds[(i + 1) % 3]} t={m} size={(vertical ? 220 : 170) * u} r={r} id={`d${i}`} />
                <div style={{fontFamily: FONT, fontSize: (vertical ? 36 : 34) * u, color: r.onSurface, fontVariationSettings: `'wght' 600`, width: 300 * u, textAlign: 'center'}}>
                  {TEXT.details.badgeNames[i]}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </AbsoluteFill>
  );
};
