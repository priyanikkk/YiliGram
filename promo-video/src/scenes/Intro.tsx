import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {Body, FloatingShapes, Headline, Logo, Morph, useLayout} from '../components/ui';
import {TEXT} from '../content';
import {roles, sp, SPRING} from '../theme';
import {ShapeName} from '../shapes';

/** Shapes morph into the logo's disc, the cat appears, the name grows in weight. */
export const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  const r = roles(undefined, true);
  const seq: ShapeName[] = ['cookie9', 'clover4', 'sunny', 'softBurst', 'cookie4', 'circle'];
  const step = 11;
  const idx = Math.min(seq.length - 2, Math.floor(frame / step));
  const t = sp(frame, fps, idx * step, SPRING.spatialFast);
  const colors = [r.primary, r.tertiary, r.secondary, r.primaryContainer, r.tertiaryContainer, '#1F1B16'];
  const grow = sp(frame, fps, 4, SPRING.spatial);
  const size = (vertical ? 520 : 380) * u * (0.3 + 0.7 * grow);
  const logoIn = sp(frame, fps, step * (seq.length - 1) + 2, SPRING.effectsSlow);
  const rot = interpolate(frame, [0, step * seq.length], [-140, 0], {extrapolateRight: 'clamp'});
  const lift = sp(frame, fps, 70, SPRING.spatialSlow);
  const cy = vertical ? H * 0.4 - lift * 140 * u : H * 0.36 - lift * 90 * u;
  const out = sp(frame, fps, 7 * fps - 10, SPRING.effects);
  return (
    <AbsoluteFill style={{background: r.surface, opacity: 1 - out}}>
      <FloatingShapes r={r} count={8} seed={2} opacity={0.35 * Math.min(1, frame / 30)} />
      <div style={{position: 'absolute', left: W / 2 - size / 2, top: cy - size / 2}}>
        <Morph from={seq[idx]} to={seq[idx + 1]} t={t} size={size} color={colors[Math.min(idx + 1, colors.length - 1)]} rotation={rot} />
        <div style={{position: 'absolute', inset: 0, opacity: logoIn, transform: `scale(${0.6 + 0.4 * logoIn})`}}>
          <Logo size={size} />
        </div>
      </div>
      <div style={{position: 'absolute', left: 0, right: 0, top: cy + (vertical ? 330 : 230) * u}}>
        <Headline text={TEXT.intro.title} color={r.onSurface} size={(vertical ? 170 : 128) * u} delay={72} weight={850} />
        <Body text={TEXT.intro.subtitle} color={r.primary} size={(vertical ? 58 : 42) * u} delay={92} weight={520} style={{marginTop: (vertical ? 18 : 6) * u}} />
      </div>
    </AbsoluteFill>
  );
};
