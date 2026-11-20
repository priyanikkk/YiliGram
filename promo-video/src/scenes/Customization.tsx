import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {Chip, FloatingShapes, Headline, Phone, Switch, useLayout} from '../components/ui';
import {MockChats, Slider} from '../components/extra';
import {TEXT} from '../content';
import {CONFIG} from '../config';
import {FONT, mixRoles, roles, sp, SPRING} from '../theme';

/**
 * One slider drives the whole interface: corner roundness of every element follows it. Then the
 * theme goes light -> dark -> AMOLED, while customization chips fly in and switches flip by themselves.
 */
export const Customization: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  const sec = (s: number) => Math.round(s * fps);
  // Slider: 0.5 -> 1 -> 0.05 -> 0.7, each move on a spring.
  const moves = [[sec(1.5), 0.5, 1], [sec(3.4), 1, 0.05], [sec(5.4), 0.05, 0.72]] as const;
  let value = 0.5;
  for (const [at, a, b] of moves) if (frame >= at) value = a + (b - a) * sp(frame, fps, at, SPRING.spatial);
  // Theme: light, then dark at 8.5 s, AMOLED at 11.5 s.
  const light = roles(CONFIG.seed), dark = roles(CONFIG.seed, true), amoled = roles(CONFIG.seed, true, true);
  const d = sp(frame, fps, sec(8.5), SPRING.effectsSlow), a = sp(frame, fps, sec(11.5), SPRING.effectsSlow);
  const r = mixRoles(mixRoles(light, dark, d), amoled, a);
  const phoneW = (vertical ? 380 : 380) * u;
  const enter = sp(frame, fps, 2, SPRING.spatial);
  const out = sp(frame, fps, sec(CONFIG.scenes.customization) - 10, SPRING.effects);
  const chips = TEXT.customization.chips;
  const themeLabel = frame < sec(8.5) ? 'Светлая' : frame < sec(11.5) ? 'Тёмная' : 'AMOLED';
  return (
    <AbsoluteFill style={{background: r.surfaceContainerLow, opacity: 1 - out}}>
      <FloatingShapes r={r} count={6} seed={9} opacity={0.4} />
      <div style={{position: 'absolute', left: vertical ? 60 * u : W * 0.06, right: vertical ? 60 * u : W * 0.52, top: vertical ? 90 * u : H * 0.12}}>
        <Headline text={TEXT.customization.headline} color={r.onSurface} size={(vertical ? 92 : 84) * u} delay={4} align={vertical ? 'center' : 'left'} weight={800} />
      </div>
      <div style={{
        position: 'absolute', left: vertical ? W / 2 - phoneW / 2 - 15 * u : W * 0.6, top: vertical ? H * 0.2 : H * 0.06,
        transform: `scale(${0.85 + 0.15 * enter}) translateY(${(1 - enter) * 300 * u}px)`,
      }}>
        <Phone width={phoneW} r={r}>
          <MockChats r={r} round={value} scale={phoneW / (1080 * u)} />
        </Phone>
      </div>
      {/* The slider that drives everything */}
      <div style={{
        position: 'absolute', left: vertical ? W / 2 - 380 * u : W * 0.07, top: vertical ? H * 0.675 : H * 0.42,
        width: 760 * u, padding: `${24 * u}px ${36 * u}px`, borderRadius: 40 * u, background: r.surfaceContainerHigh,
        transform: `translateY(${(1 - sp(frame, fps, sec(0.8), SPRING.spatial)) * 200 * u}px)`,
      }}>
        <div style={{fontFamily: FONT, fontSize: 36 * u, color: r.onSurfaceVariant, display: 'flex', justifyContent: 'space-between'}}>
          <span>Скругление</span><span style={{color: r.primary, fontVariationSettings: `'wght' 700`}}>{Math.round(value * 200)}%</span>
        </div>
        <Slider value={value} r={r} width={688 * u} u={u} />
      </div>
      {/* Theme row with self-flipping switches */}
      <div style={{
        position: 'absolute', left: vertical ? W / 2 - 380 * u : W * 0.07, top: vertical ? H * 0.785 : H * 0.6, width: 760 * u,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontFamily: FONT, fontSize: 40 * u, color: r.onSurface,
        opacity: sp(frame, fps, sec(7.6), SPRING.effects),
      }}>
        <span style={{fontVariationSettings: `'wght' 600`}}>Тема: {themeLabel}</span>
        <div style={{display: 'flex', gap: 24 * u}}>
          <Switch on={d} r={r} scale={2.4 * u} />
          <Switch on={a} r={r} scale={2.4 * u} />
        </div>
      </div>
      {/* Chips of everything that can be customized: two columns beside the phone (vertical) or a row */}
      {(() => {
        const all = [...chips, TEXT.customization.presets];
        const chip = (c: string, i: number) => {
          const t = sp(frame, fps, (i === all.length - 1 ? sec(9.5) : sec(1.2) + i * 5), SPRING.spatialFast);
          return (
            <div key={c} style={{transform: `scale(${t})`, opacity: Math.min(1, t)}}>
              <Chip label={c} r={r} selected={i % 3 === 0 || i === all.length - 1} size={(vertical ? 27 : 30) * u} />
            </div>
          );
        };
        if (!vertical) {
          return (
            <div style={{position: 'absolute', left: W * 0.06, right: W * 0.45, top: H * 0.68, display: 'flex', flexWrap: 'wrap', gap: 14 * u}}>
              {all.map(chip)}
            </div>
          );
        }
        const half = Math.ceil(all.length / 2);
        const col = (items: string[], offset: number, side: 'left' | 'right') => (
          <div style={{position: 'absolute', [side]: 22 * u, top: H * 0.25, display: 'flex', flexDirection: 'column', gap: 26 * u,
            alignItems: side === 'left' ? 'flex-end' : 'flex-start', width: 250 * u}}>
            {items.map((c, i) => chip(c, i + offset))}
          </div>
        );
        return (
          <>
            {col(all.slice(0, half), 0, 'left')}
            {col(all.slice(half), half, 'right')}
          </>
        );
      })()}
      {vertical ? null : (
        <div style={{position: 'absolute', left: W * 0.07, top: H * 0.33, fontFamily: FONT, color: r.primary, fontSize: 40 * u,
          opacity: interpolate(frame, [sec(1), sec(2)], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'})}}>
          Цвета · формы · шрифты · масштаб · анимации
        </div>
      )}
    </AbsoluteFill>
  );
};
