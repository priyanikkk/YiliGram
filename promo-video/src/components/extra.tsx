import React from 'react';
import {useCurrentFrame, useVideoConfig} from 'remotion';
import {FONT, mix, Roles, sp, SPRING} from '../theme';
import {morphPath, ShapeName} from '../shapes';
import {useLayout} from './ui';
import {CONFIG} from '../config';

/** Animated subtitles for one scene: each voice-over line springs in and fades out at the next one. */
export const Subtitles: React.FC<{lines: {at: number; text: string}[]; r: Roles; sceneSeconds: number}> = ({lines, r, sceneSeconds}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const {W, H, vertical, u} = useLayout();
  return (
    <>
      {lines.map((l, i) => {
        const start = Math.round(l.at * fps);
        const end = Math.round((lines[i + 1]?.at ?? sceneSeconds - 0.2) * fps);
        if (frame < start - 2 || frame > end + 12) return null;
        const tin = sp(frame, fps, start, SPRING.spatial);
        const tout = sp(frame, fps, end, SPRING.effects);
        const words = l.text.split(' ');
        return (
          <div key={i} style={{
            position: 'absolute', left: vertical ? 70 * u : W * 0.08, right: vertical ? 70 * u : W * 0.08,
            bottom: vertical ? 120 * u : 70 * u, display: 'flex', justifyContent: 'center',
            opacity: Math.min(1, tin * 1.5) * (1 - tout), transform: `translateY(${(1 - tin) * 30 * u}px)`,
          }}>
            <div style={{
              background: r.inverseSurface, color: r.inverseOnSurface, borderRadius: 28 * u, padding: `${18 * u}px ${30 * u}px`,
              fontFamily: FONT, fontSize: (vertical ? 40 : 38) * u * CONFIG.captionScale, lineHeight: 1.3, textAlign: 'center', maxWidth: vertical ? 940 * u : W * 0.7,
              fontVariationSettings: `'wght' 500`,
            }}>
              {words.map((w, wi) => {
                const lit = frame >= start + wi * 3.2;
                return <span key={wi} style={{opacity: lit ? 1 : 0.35}}>{w}{wi < words.length - 1 ? ' ' : ''}</span>;
              })}
            </div>
          </div>
        );
      })}
    </>
  );
};

export type BadgeKind = 'admin' | 'beta' | 'official';

/** The three YiliGram badges, as drawn in the app (YiliBadgeDrawable). */
export const Badge: React.FC<{kind: BadgeKind; size: number; r: Roles; morphTo?: BadgeKind; t?: number; id?: string}> = ({kind, size, r, morphTo, t = 0, id = 'b'}) => {
  const shapeOf: Record<BadgeKind, ShapeName> = {admin: 'softBurst', beta: 'cookie4', official: 'sunny'};
  const to = morphTo ?? kind;
  const show = t < 0.5 ? kind : to;
  const glyphAlpha = morphTo ? Math.abs(t - 0.5) * 2 : 1;
  const c = size / 2, rr = size / 2;
  const grad = (k: BadgeKind) => (k === 'admin' ? [r.primary, r.tertiary] : k === 'official' ? [r.secondary, r.primary] : [r.tertiaryContainer, r.tertiaryContainer]);
  const [g1a, g1b] = grad(kind), [g2a, g2b] = grad(to);
  const tt = Math.max(0, Math.min(1, t));
  return (
    <svg width={size} height={size} style={{overflow: 'visible'}}>
      <defs>
        <linearGradient id={`g-${id}`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={mix(g1a, g2a, tt)} />
          <stop offset="1" stopColor={mix(g1b, g2b, tt)} />
        </linearGradient>
      </defs>
      <path d={morphPath(shapeOf[kind], shapeOf[to], t, c, c, size)} fill={`url(#g-${id})`} />
      <g opacity={glyphAlpha}>
        {show === 'admin' ? (
          <path d={`M${c} ${c - rr * 0.52} Q${c + rr * 0.13} ${c - rr * 0.13} ${c + rr * 0.52} ${c} Q${c + rr * 0.13} ${c + rr * 0.13} ${c} ${c + rr * 0.52} Q${c - rr * 0.13} ${c + rr * 0.13} ${c - rr * 0.52} ${c} Q${c - rr * 0.13} ${c - rr * 0.13} ${c} ${c - rr * 0.52}Z`} fill={r.onPrimary} />
        ) : show === 'official' ? (
          <path d={`M${c - rr * 0.38} ${c + rr * 0.02} L${c - rr * 0.1} ${c + rr * 0.3} L${c + rr * 0.4} ${c - rr * 0.26}`} stroke={r.onPrimary} strokeWidth={rr * 0.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        ) : (
          <text x={c} y={c + rr * 0.42} textAnchor="middle" fontFamily={FONT} fontSize={rr * 1.25} style={{fontVariationSettings: `'wght' 800`}} fill={r.onTertiaryContainer}>β</text>
        )}
      </g>
    </svg>
  );
};

/** Python with simple syntax highlighting in M3 roles. */
export const Code: React.FC<{lines: string[]; r: Roles; size: number; visibleChars: number}> = ({lines, r, size, visibleChars}) => {
  const KW = new Set(['class', 'def', 'self', 'return', 'if', 'for', 'in', 'import', 'from', 'None', 'True', 'False']);
  let budget = visibleChars;
  return (
    <div style={{fontFamily: 'monospace', fontSize: size, lineHeight: 1.5, color: r.onSurface, whiteSpace: 'pre'}}>
      {lines.map((line, i) => {
        const shown = line.slice(0, Math.max(0, budget));
        budget -= line.length + 1;
        const parts = shown.split(/("[^"]*"?|\b\w+\b|[()[\]:.,=])/g).filter((p) => p !== '');
        return (
          <div key={i} style={{minHeight: size * 1.5}}>
            <span style={{color: r.outline, marginRight: size}}>{String(i + 1).padStart(2, ' ')}</span>
            {parts.map((p, pi) => {
              let color = r.onSurface;
              if (KW.has(p)) color = r.primary;
              else if (p.startsWith('"')) color = r.tertiary;
              else if (/^[A-Z]\w+$/.test(p)) color = r.secondary;
              else if (/^\w+$/.test(p) && parts[pi + 1] === '(') color = r.onPrimaryContainer;
              return <span key={pi} style={{color, fontWeight: KW.has(p) ? 700 : 400}}>{p}</span>;
            })}
            {budget > -line.length - 1 && budget <= 0 && shown.length < line.length + 1 ? (
              <span style={{display: 'inline-block', width: size * 0.12, height: size * 1.1, background: r.primary, verticalAlign: 'middle', marginLeft: 2}} />
            ) : null}
          </div>
        );
      })}
    </div>
  );
};

/** A mock YiliGram chat list + chat, drawn in M3 roles (demo names only; no real data). */
export const MockChats: React.FC<{r: Roles; round: number; scale?: number}> = ({r, round, scale = 1}) => {
  const {u} = useLayout();
  const chats = [
    ['Команда YiliGram', 'Новая сборка уже в канале', r.primaryContainer],
    ['Мария', 'Смотри, какие формы!', r.tertiaryContainer],
    ['Плагины', 'hook_titles: ✦ добавлен', r.secondaryContainer],
    ['Алексей', 'Тёмная тема — огонь 🔥', r.primaryContainer],
    ['Дизайн', 'Material 3 Expressive', r.tertiaryContainer],
  ];
  const s = u * scale;
  return (
    <div style={{position: 'absolute', inset: 0, background: r.surface, fontFamily: FONT, color: r.onSurface}}>
      <div style={{padding: `${110 * s}px ${48 * s}px ${30 * s}px`, fontSize: 64 * s, fontVariationSettings: `'wght' 650`}}>YiliGram</div>
      <div style={{margin: `0 ${40 * s}px ${30 * s}px`, height: 110 * s, borderRadius: 55 * s * round, background: r.surfaceContainerHigh,
        display: 'flex', alignItems: 'center', padding: `0 ${44 * s}px`, color: r.onSurfaceVariant, fontSize: 40 * s}}>Поиск</div>
      <div style={{display: 'flex', gap: 20 * s, padding: `0 ${40 * s}px ${30 * s}px`}}>
        {['Все', 'Личные', 'Каналы'].map((c, i) => (
          <div key={c} style={{padding: `${18 * s}px ${36 * s}px`, borderRadius: 40 * s * round, fontSize: 36 * s,
            background: i === 0 ? r.secondaryContainer : 'transparent', border: i === 0 ? 'none' : `${3 * s}px solid ${r.outlineVariant}`,
            color: i === 0 ? r.onSecondaryContainer : r.onSurfaceVariant}}>{c}</div>
        ))}
      </div>
      {chats.map(([name, msg, color], i) => (
        <div key={i} style={{display: 'flex', alignItems: 'center', gap: 32 * s, padding: `${26 * s}px ${44 * s}px`}}>
          <div style={{width: 130 * s, height: 130 * s, borderRadius: 65 * s * round + 18 * s * (1 - round), background: color,
            display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 54 * s, color: r.onSurface, flexShrink: 0}}>{(name as string)[0]}</div>
          <div style={{flex: 1, minWidth: 0}}>
            <div style={{fontSize: 44 * s, fontVariationSettings: `'wght' 600`}}>{name}</div>
            <div style={{fontSize: 38 * s, color: r.onSurfaceVariant, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'}}>{msg}</div>
          </div>
        </div>
      ))}
      <div style={{position: 'absolute', right: 50 * s, bottom: 260 * s, width: 190 * s, height: 190 * s, borderRadius: 57 * s * round + 10 * s,
        background: r.primaryContainer, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
        <svg width={80 * s} height={80 * s} viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" fill={r.onPrimaryContainer} /></svg>
      </div>
      <div style={{position: 'absolute', left: 60 * s, right: 60 * s, bottom: 60 * s, height: 160 * s, borderRadius: 80 * s * round + 20 * s,
        background: r.surfaceContainer, display: 'flex', justifyContent: 'space-around', alignItems: 'center'}}>
        {['Чаты', 'Контакты', 'Настройки', 'Профиль'].map((t, i) => (
          <div key={t} style={{fontSize: 30 * s, color: i === 0 ? r.onSecondaryContainer : r.onSurfaceVariant, padding: `${16 * s}px ${24 * s}px`,
            borderRadius: 40 * s * round + 10 * s, background: i === 0 ? r.secondaryContainer : 'transparent'}}>{t}</div>
        ))}
      </div>
    </div>
  );
};

/** M3 Expressive slider (16 dp track, handle bar, stop indicator) at value 0..1. */
export const Slider: React.FC<{value: number; r: Roles; width: number; u: number}> = ({value, r, width, u}) => {
  const h = 48 * u, track = 32 * u, gap = 12 * u, handle = 8 * u;
  const x = value * width;
  return (
    <svg width={width} height={h * 2} style={{overflow: 'visible'}}>
      <rect x={0} y={h - track / 2} width={Math.max(0, x - gap - handle / 2)} height={track} rx={track / 2} fill={r.primary} />
      <rect x={x + gap + handle / 2} y={h - track / 2} width={Math.max(0, width - x - gap - handle / 2)} height={track} rx={track / 2} fill={r.secondaryContainer} />
      <circle cx={width - track / 2} cy={h} r={4 * u} fill={r.primary} />
      <rect x={x - handle / 2} y={h - 44 * u} width={handle} height={88 * u} rx={handle / 2} fill={r.primary} />
    </svg>
  );
};
