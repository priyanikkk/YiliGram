import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {Body, Chip, FloatingShapes, Headline, LoadingIndicator, Logo, Morph, QR, ShapeSvg, useLayout} from '../components/ui';
import {LINKS, TEXT} from '../content';
import {CONFIG} from '../config';
import {FONT, roles, sp, SPRING} from '../theme';

/** A notification drops onto a lock screen; "Firebase" is struck out and becomes "UnifiedPush". */
export const Push: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  const sec = (s: number) => Math.round(s * fps);
  const r = roles(CONFIG.showcaseSeeds[1]);
  const drop = sp(frame, fps, sec(1.0), SPRING.spatialFast);
  const strike = interpolate(frame, [sec(2.4), sec(3.0)], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  const swap = sp(frame, fps, sec(3.2), SPRING.spatialFast);
  const out = sp(frame, fps, sec(CONFIG.scenes.push) - 10, SPRING.effects);
  const cardW = (vertical ? 900 : 820) * u;
  return (
    <AbsoluteFill style={{background: r.surfaceContainerLow, opacity: 1 - out}}>
      <FloatingShapes r={r} count={6} seed={33} opacity={0.4} />
      <div style={{position: 'absolute', left: vertical ? 60 * u : W * 0.06, right: vertical ? 60 * u : W * 0.5, top: vertical ? H * 0.1 : H * 0.2}}>
        <Headline text={TEXT.push.headline} color={r.onSurface} size={(vertical ? 92 : 84) * u} delay={2} align={vertical ? 'center' : 'left'} weight={800} />
        <div style={{display: 'flex', justifyContent: vertical ? 'center' : 'flex-start', marginTop: 40 * u, position: 'relative', height: 90 * u}}>
          <div style={{position: 'absolute', opacity: 1 - swap, transform: `scale(${1 - 0.3 * swap})`}}>
            <Chip label="Firebase" r={r} size={40 * u} />
            <div style={{position: 'absolute', left: -10 * u, top: '50%', height: 6 * u, width: `${strike * 110}%`, background: r.error, borderRadius: 3 * u}} />
          </div>
          <div style={{position: 'absolute', opacity: swap, transform: `scale(${0.6 + 0.4 * swap})`}}>
            <Chip label={TEXT.push.sub} r={r} selected size={40 * u} icon={<ShapeSvg name="sunny" size={44 * u} fill={r.primary} />} />
          </div>
        </div>
      </div>
      <div style={{
        position: 'absolute', left: vertical ? W / 2 - cardW / 2 : W * 0.55, top: vertical ? H * 0.42 : H * 0.36, width: cardW,
        transform: `translateY(${(1 - drop) * -700 * u}px)`, opacity: Math.min(1, drop * 2),
        borderRadius: 48 * u, background: r.surfaceContainerHighest, padding: 40 * u, display: 'flex', gap: 34 * u, alignItems: 'center',
        boxShadow: `0 ${20 * u}px ${60 * u}px rgba(0,0,0,.18)`,
      }}>
        <Logo size={130 * u} />
        <div style={{fontFamily: FONT}}>
          <div style={{fontSize: 46 * u, color: r.onSurface, fontVariationSettings: `'wght' 700`}}>{TEXT.push.notification.title}</div>
          <div style={{fontSize: 40 * u, color: r.onSurfaceVariant}}>{TEXT.push.notification.text}</div>
        </div>
      </div>
      <div style={{position: 'absolute', left: vertical ? W / 2 - 60 * u : W * 0.7, top: vertical ? H * 0.6 : H * 0.66}}>
        <LoadingIndicator size={120 * u} color={r.primary} container={r.primaryContainer} />
      </div>
    </AbsoluteFill>
  );
};

/** A commit graph grows on springs; the license sits in a burst. */
export const OpenSource: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  const sec = (s: number) => Math.round(s * fps);
  const r = roles(CONFIG.showcaseSeeds[2], true);
  const out = sp(frame, fps, sec(CONFIG.scenes.openSource) - 10, SPRING.effects);
  const nodes = 9;
  const gx = vertical ? W * 0.12 : W * 0.55, gy = vertical ? H * 0.56 : H * 0.3, gw = vertical ? W * 0.76 : W * 0.38;
  return (
    <AbsoluteFill style={{background: r.surface, opacity: 1 - out}}>
      <FloatingShapes r={r} count={6} seed={41} opacity={0.3} />
      <div style={{position: 'absolute', left: vertical ? 60 * u : W * 0.06, right: vertical ? 60 * u : W * 0.5, top: vertical ? H * 0.08 : H * 0.16}}>
        <Headline text={TEXT.openSource.headline} color={r.onSurface} size={(vertical ? 100 : 90) * u} delay={2} align={vertical ? 'center' : 'left'} weight={820} />
        <div style={{display: 'flex', justifyContent: vertical ? 'center' : 'flex-start', marginTop: 30 * u}}>
          <div style={{position: 'relative', width: 340 * u, height: 340 * u, transform: `scale(${sp(frame, fps, 12, SPRING.spatialFast)}) rotate(${frame * 0.4}deg)`}}>
            <Morph from="burst" to="softBurst" t={sp(frame, fps, sec(2.5), SPRING.spatial)} size={340 * u} color={r.primaryContainer} />
            <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `rotate(${-frame * 0.4}deg)`,
              fontFamily: FONT, fontSize: 64 * u, color: r.onPrimaryContainer, fontVariationSettings: `'wght' 800`}}>{TEXT.openSource.license}</div>
          </div>
        </div>
        <Body text={TEXT.openSource.sub} color={r.onSurfaceVariant} size={(vertical ? 48 : 42) * u} delay={sec(1.4)} align={vertical ? 'center' : 'left'} style={{marginTop: 30 * u}} />
      </div>
      <svg style={{position: 'absolute', left: 0, top: 0}} width={W} height={H}>
        {Array.from({length: nodes}, (_, i) => {
          const t = sp(frame, fps, sec(0.8) + i * 5, SPRING.spatialFast);
          const x = gx + (i / (nodes - 1)) * gw;
          const y = gy + (vertical ? 440 * u : 380 * u) + Math.sin(i * 1.3) * 30 * u;
          const nx = gx + ((i + 1) / (nodes - 1)) * gw, ny = gy + (vertical ? 440 * u : 380 * u) + Math.sin((i + 1) * 1.3) * 30 * u;
          const lt = sp(frame, fps, sec(0.8) + i * 5 + 3, SPRING.effects);
          const branch = i === 3 || i === 6;
          return (
            <g key={i}>
              {i < nodes - 1 ? <line x1={x} y1={y} x2={x + (nx - x) * lt} y2={y + (ny - y) * lt} stroke={r.outline} strokeWidth={6 * u} strokeLinecap="round" /> : null}
              {branch ? <path d={`M${x} ${y} q ${40 * u} ${-120 * u} ${110 * u} ${-130 * u}`} stroke={r.tertiary} strokeWidth={6 * u} fill="none" opacity={t} /> : null}
              {branch ? <circle cx={x + 110 * u} cy={y - 130 * u} r={20 * u * t} fill={r.tertiary} /> : null}
              <circle cx={x} cy={y} r={26 * u * t} fill={i === nodes - 1 ? r.primary : r.secondaryContainer} stroke={r.primary} strokeWidth={5 * u} />
            </g>
          );
        })}
      </svg>
    </AbsoluteFill>
  );
};

/** Final screen: where to find YiliGram. Two M3 cards with QR codes, the Releases chip. */
export const Download: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  const r = roles(undefined, true);
  const qr = (vertical ? 300 : 240) * u;
  const card = (label: string, link: string, url: string, i: number, shapeColor: string) => {
    const t = sp(frame, fps, 8 + i * 7, SPRING.spatial);
    return (
      <div key={label} style={{
        width: vertical ? 880 * u : 780 * u, borderRadius: 56 * u, background: r.surfaceContainerHigh, padding: 40 * u,
        display: 'flex', alignItems: 'center', gap: 40 * u, transform: `translateY(${(1 - t) * 300 * u}px) scale(${0.9 + 0.1 * t})`, opacity: Math.min(1, t * 1.5),
      }}>
        <QR text={url} size={qr} fg={r.onSurface} bg={r.surfaceContainerLowest} />
        <div style={{fontFamily: FONT, minWidth: 0, flex: 1}}>
          <div style={{display: 'flex', alignItems: 'center', gap: 16 * u}}>
            <Morph from="cookie9" to="sunny" t={sp(frame % 60, fps, 30, SPRING.spatial)} size={56 * u} color={shapeColor} rotation={frame} />
            <span style={{fontSize: 40 * u, color: r.onSurfaceVariant, fontVariationSettings: `'wght' 550`}}>{label}</span>
          </div>
          {/* Domain on one line, path on the next: never broken in the middle of a word */}
          <div style={{fontSize: (vertical ? 44 : 38) * u, color: r.onSurface, fontVariationSettings: `'wght' 760, 'wdth' 85`, marginTop: 16 * u, lineHeight: 1.15}}>
            {link.split('/').map((part, pi, all) => <div key={pi}>{part}{pi < all.length - 1 ? '/' : ''}</div>)}
          </div>
        </div>
      </div>
    );
  };
  return (
    <AbsoluteFill style={{background: r.surface}}>
      <FloatingShapes r={r} count={8} seed={51} opacity={0.35} />
      <div style={{position: 'absolute', left: 0, right: 0, top: vertical ? H * 0.07 : H * 0.07, display: 'flex', flexDirection: 'column', alignItems: 'center'}}>
        <Logo size={(vertical ? 170 : 140) * u} style={{transform: `scale(${sp(frame, fps, 0, SPRING.spatialFast)})`}} />
        <Headline text={TEXT.download.headline} color={r.onSurface} size={(vertical ? 100 : 84) * u} delay={4} weight={820} style={{marginTop: 20 * u}} />
      </div>
      <div style={{
        position: 'absolute', left: 0, right: 0, top: vertical ? H * 0.31 : H * 0.36, display: 'flex',
        flexDirection: vertical ? 'column' : 'row', alignItems: 'center', justifyContent: 'center', gap: 40 * u,
      }}>
        {card(TEXT.download.telegramLabel, CONFIG.links.telegram, LINKS.telegram, 0, r.primary)}
        {card(TEXT.download.githubLabel, CONFIG.links.github, LINKS.github, 1, r.tertiary)}
      </div>
      <div style={{position: 'absolute', left: 0, right: 0, top: vertical ? H * 0.77 : H * 0.74, display: 'flex', justifyContent: 'center',
        transform: `scale(${sp(frame, fps, 24, SPRING.spatialFast)})`}}>
        <Chip label={TEXT.download.apk} r={r} selected size={(vertical ? 40 : 36) * u} icon={<LoadingIndicator size={52 * u} color={r.onSecondaryContainer} />} />
      </div>
    </AbsoluteFill>
  );
};

/** Small disclaimer card. */
export const Disclaimer: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  const r = roles(undefined, true);
  const t = sp(frame, fps, 2, SPRING.spatial);
  return (
    <AbsoluteFill style={{background: r.surface, alignItems: 'center', justifyContent: 'center'}}>
      <div style={{display: 'flex', alignItems: 'center', gap: 28 * u, padding: `${30 * u}px ${44 * u}px`, borderRadius: 40 * u,
        background: r.surfaceContainerHigh, transform: `translateY(${(1 - t) * 80 * u}px)`, opacity: t, maxWidth: (vertical ? 920 : 1100) * u}}>
        <Logo size={90 * u} />
        <div style={{fontFamily: FONT, fontSize: (vertical ? 38 : 36) * u, color: r.onSurfaceVariant, lineHeight: 1.3}}>{TEXT.disclaimer}</div>
      </div>
    </AbsoluteFill>
  );
};
