import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {Fab, FloatingShapes, Headline, Morph, Phone, Screen, useLayout} from '../components/ui';
import {TEXT} from '../content';
import {CONFIG} from '../config';
import {mixRoles, roles, sp, SPRING} from '../theme';
import {ShapeName} from '../shapes';

/**
 * Dynamic color: the "wallpaper" seed changes and the whole frame recolors with it, while the phone
 * shows real YiliGram screenshots with the matching presets. Then shapes morph and a FAB bounces.
 */
export const Expressive: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  const seeds = CONFIG.showcaseSeeds;
  const per = Math.round(2.2 * fps);
  const i = Math.min(seeds.length - 1, Math.floor(frame / per));
  const k = sp(frame, fps, i * per, SPRING.effectsSlow);
  const prev = roles(seeds[Math.max(0, i - 1)]), cur = roles(seeds[i]);
  const r = i === 0 ? cur : mixRoles(prev, cur, k);
  const screens = ['appearance_yiligram', 'appearance_classic', 'appearance_calm', 'appearance_vivid'];
  const phoneW = (vertical ? 470 : 400) * u;
  const phoneIn = sp(frame, fps, 4, SPRING.spatial);
  const lineAt = [6, 3.6 * fps, 6.4 * fps];
  const morphShapes: ShapeName[] = ['cookie9', 'pill', 'clover4', 'sunny', 'gem', 'cookie4'];
  const ms = Math.floor(frame / 14) % morphShapes.length;
  const mt = sp(frame % 14, fps, 0, SPRING.spatialFast);
  const bounce = sp(frame, fps, Math.round(6.6 * fps), SPRING.spatialFast);
  const out = sp(frame, fps, 10 * fps - 10, SPRING.effects);
  const wallpaper = (
    <div style={{position: 'relative', width: 230 * u, height: 230 * u}}>
      <Morph from="cookie12" to="cookie12" t={0} size={230 * u} color={r.primary} rotation={frame * 0.8} />
      <div style={{position: 'absolute', inset: 40 * u, borderRadius: '50%', background: r.tertiaryContainer}} />
      <div style={{position: 'absolute', inset: 80 * u, borderRadius: '50%', background: r.secondary}} />
    </div>
  );
  return (
    <AbsoluteFill style={{background: r.surfaceContainerLow, opacity: 1 - out}}>
      <FloatingShapes r={r} count={7} seed={5} opacity={0.5} />
      <div style={{
        position: 'absolute', left: vertical ? W / 2 - phoneW / 2 - 16 * u : W * 0.58, top: vertical ? H * 0.31 : H * 0.07,
        transform: `translateY(${(1 - phoneIn) * 400 * u}px) rotate(${(1 - phoneIn) * 8}deg)`,
      }}>
        <Phone width={phoneW} r={r}>
          {screens.map((s, si) => (
            <Screen key={s} name={s} opacity={si === i ? (si === 0 ? 1 : k) : si === i - 1 ? 1 : 0} />
          ))}
        </Phone>
      </div>
      <div style={{position: 'absolute', left: vertical ? W * 0.07 : W * 0.43, top: vertical ? H * 0.33 : H * 0.58,
        transform: `scale(${sp(frame, fps, 10, SPRING.spatialFast)})`}}>{wallpaper}</div>
      <div style={{position: 'absolute', left: vertical ? 70 * u : W * 0.07, right: vertical ? 70 * u : W * 0.48, top: vertical ? H * 0.07 : H * 0.18}}>
        {TEXT.expressive.lines.map((line, li) => (
          frame >= lineAt[li] - 2 ? (
            <Headline key={li} text={line} color={li === 0 ? r.primary : li === 1 ? r.tertiary : r.onSurface}
              size={(vertical ? 70 : 64) * u} delay={lineAt[li]} align={vertical ? 'center' : 'left'} weight={780} />
          ) : null
        ))}
        {!vertical ? (
          <div style={{display: 'flex', gap: 30 * u, marginTop: 50 * u, alignItems: 'center'}}>
            <Morph from={morphShapes[ms]} to={morphShapes[(ms + 1) % morphShapes.length]} t={mt} size={150 * u} color={r.tertiaryContainer} />
            <Fab r={r} size={150 * u} style={{transform: `translateY(${-interpolate(bounce, [0, 1], [0, 60]) * u}px)`}}>
              <Morph from="sunny" to="sunny" t={0} size={60 * u} color={r.onPrimaryContainer} rotation={frame * 2} />
            </Fab>
          </div>
        ) : null}
      </div>
      {vertical ? (
        <div style={{position: 'absolute', right: 70 * u, top: H * 0.36, display: 'flex', flexDirection: 'column', gap: 40 * u, alignItems: 'center'}}>
          <Morph from={morphShapes[ms]} to={morphShapes[(ms + 1) % morphShapes.length]} t={mt} size={150 * u} color={r.tertiaryContainer} />
          <Fab r={r} size={150 * u} style={{transform: `translateY(${-interpolate(bounce, [0, 1], [0, 60]) * u}px)`}}>
            <Morph from="sunny" to="sunny" t={0} size={60 * u} color={r.onPrimaryContainer} rotation={frame * 2} />
          </Fab>
        </div>
      ) : null}
    </AbsoluteFill>
  );
};
