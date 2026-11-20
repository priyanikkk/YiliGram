import React from 'react';
import {AbsoluteFill, useCurrentFrame, useVideoConfig} from 'remotion';
import {Chip, FloatingShapes, Headline, Phone, Screen, ShapeSvg, useLayout} from '../components/ui';
import {Code} from '../components/extra';
import {TEXT} from '../content';
import {CONFIG} from '../config';
import {FONT, roles, sp, SPRING} from '../theme';

/**
 * Python plugins: the real hook_titles.plugin is typed in an editor while a title bar on the right
 * gets its ✦ mark; then the plugin manager (a real screenshot) and the developer features.
 */
export const Plugins: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  const sec = (s: number) => Math.round(s * fps);
  const r = roles(CONFIG.seed, true);
  const code = TEXT.plugins.code;
  const total = code.join('\n').length;
  const chars = Math.round(Math.min(total, Math.max(0, frame - sec(1.2)) * 3.2));
  const hooked = sp(frame, fps, sec(1.2) + Math.ceil(total / 3.2) + 6, SPRING.spatialFast);
  const editorIn = sp(frame, fps, sec(0.6), SPRING.spatial);
  const swap = sp(frame, fps, sec(8.2), SPRING.spatial); // editor slides away, phone with the manager comes in
  const out = sp(frame, fps, sec(CONFIG.scenes.plugins) - 10, SPRING.effects);
  const editorW = vertical ? 960 * u : W * 0.5;
  const codeSize = (vertical ? 21 : 19) * u;
  const phoneW = (vertical ? 360 : 360) * u;
  return (
    <AbsoluteFill style={{background: r.surface, opacity: 1 - out}}>
      <FloatingShapes r={r} count={6} seed={13} opacity={0.3} />
      <div style={{position: 'absolute', left: vertical ? 60 * u : W * 0.06, right: vertical ? 60 * u : W * 0.5, top: vertical ? 90 * u : H * 0.08}}>
        <Headline text={TEXT.plugins.headline} color={r.onSurface} size={(vertical ? 104 : 90) * u} delay={2} align={vertical ? 'center' : 'left'} weight={820} />
      </div>
      {/* Editor */}
      <div style={{
        position: 'absolute', left: vertical ? W / 2 - editorW / 2 : W * 0.05, top: vertical ? H * 0.22 : H * 0.4, width: editorW,
        borderRadius: 36 * u, background: r.surfaceContainerHigh, overflow: 'hidden',
        transform: `translateY(${(1 - editorIn) * 500 * u}px) translateX(${swap * -1.2 * W}px)`,
        opacity: 1 - swap,
      }}>
        <div style={{display: 'flex', alignItems: 'center', gap: 14 * u, padding: `${20 * u}px ${28 * u}px`, background: r.surfaceContainerHighest}}>
          {[r.error, r.tertiary, r.primary].map((c, i) => <div key={i} style={{width: 22 * u, height: 22 * u, borderRadius: '50%', background: c}} />)}
          <span style={{fontFamily: FONT, fontSize: 30 * u, color: r.onSurfaceVariant, marginLeft: 16 * u}}>hook_titles.plugin</span>
        </div>
        <div style={{padding: `${24 * u}px ${20 * u}px`}}>
          <Code lines={code} r={r} size={codeSize} visibleChars={chars} />
        </div>
      </div>
      {/* Title bar that gets hooked */}
      <div style={{
        position: 'absolute', left: vertical ? W / 2 - 400 * u : W * 0.58, top: vertical ? H * 0.64 : H * 0.12, width: 800 * u,
        borderRadius: 32 * u, background: r.surfaceContainer, padding: `${30 * u}px ${36 * u}px`, display: 'flex', alignItems: 'center', gap: 28 * u,
        opacity: (1 - swap) * Math.min(1, sp(frame, fps, sec(1.6), SPRING.effects)),
      }}>
        <svg width={50 * u} height={50 * u} viewBox="0 0 24 24"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" fill={r.onSurface} /></svg>
        <span style={{fontFamily: FONT, fontSize: 52 * u, color: r.onSurface, fontVariationSettings: `'wght' 600`}}>
          Настройки чатов
          <span style={{display: 'inline-block', marginLeft: 16 * u, color: r.primary, transform: `scale(${hooked}) rotate(${(1 - hooked) * -180}deg)`}}>✦</span>
        </span>
      </div>
      {/* Plugin manager (real screenshot) */}
      <div style={{
        position: 'absolute', left: vertical ? W / 2 - phoneW / 2 - 15 * u : W * 0.62, top: vertical ? H * 0.22 : H * 0.08,
        transform: `translateY(${(1 - swap) * H}px)`,
      }}>
        <Phone width={phoneW} r={r}><Screen name="plugins_list" /></Phone>
      </div>
      {/* Developer features */}
      <div style={{
        position: 'absolute', left: vertical ? 40 * u : W * 0.06, right: vertical ? 40 * u : W * 0.42, top: vertical ? H * 0.665 : H * 0.42,
        display: 'flex', flexWrap: 'wrap', gap: 18 * u, justifyContent: vertical ? 'center' : 'flex-start',
        transform: vertical ? 'none' : `translateY(${swap * 0}px)`,
      }}>
        {TEXT.plugins.points.map((p, i) => {
          const t = sp(frame, fps, sec(8.6) + i * 7, SPRING.spatialFast);
          const icon = i === 3 ? <div style={{width: 22 * u, height: 22 * u, borderRadius: '50%', background: r.error}} />
            : i === 4 ? <ShapeSvg name="clover4" size={34 * u} fill={r.tertiary} /> : <ShapeSvg name="cookie9" size={30 * u} fill={r.primary} />;
          return (
            <div key={p} style={{transform: `scale(${t})`, opacity: Math.min(1, t)}}>
              <Chip label={p} r={r} selected={i < 2} size={(vertical ? 32 : 30) * u} icon={icon} />
            </div>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};
