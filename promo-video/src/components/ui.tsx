import React from 'react';
import {AbsoluteFill, Img, interpolate, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import QRCode from 'qrcode';
import {FONT, mix, Roles, sp, SPRING} from '../theme';
import {LOADING_SEQUENCE, morphPath, ShapeName, shapePath} from '../shapes';

/** Layout helper: vertical (1080×1920) or horizontal (1920×1080); `u` scales 1080-based sizes. */
export const useLayout = () => {
  const {width, height} = useVideoConfig();
  const vertical = height >= width;
  return {W: width, H: height, vertical, u: Math.min(width, height) / 1080};
};

/** A morphing M3 shape. `t` is the morph progress (springs overshoot a little, which is fine). */
export const Morph: React.FC<{
  from: ShapeName; to: ShapeName; t: number; size: number; color: string; rotation?: number;
  style?: React.CSSProperties; fill?: string; children?: React.ReactNode;
}> = ({from, to, t, size, color, rotation = 0, style, fill, children}) => (
  <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{overflow: 'visible', ...style}}>
    {children}
    <path d={morphPath(from, to, t, size / 2, size / 2, size, rotation)} fill={fill ?? color} />
  </svg>
);

/** Drifting, slowly morphing background shapes in container colors. */
export const FloatingShapes: React.FC<{r: Roles; count?: number; seed?: number; opacity?: number}> = ({r, count = 9, seed = 1, opacity = 0.55}) => {
  const frame = useCurrentFrame();
  const {W, H, u} = useLayout();
  const shapes: ShapeName[] = ['cookie9', 'clover4', 'sunny', 'pill', 'softBurst', 'cookie4', 'flower', 'gem', 'puffy', 'clover8', 'pentagon', 'verySunny'];
  const colors = [r.primaryContainer, r.secondaryContainer, r.tertiaryContainer, r.surfaceContainerHighest];
  return (
    <AbsoluteFill style={{pointerEvents: 'none'}}>
      {Array.from({length: count}, (_, i) => {
        const k = i * 7.13 + seed * 3.7;
        const rnd = (n: number) => (Math.sin(k * (n + 1) * 12.9898) * 43758.5453) % 1;
        const fx = Math.abs(rnd(1)), fy = Math.abs(rnd(2));
        const size = (140 + Math.abs(rnd(3)) * 260) * u;
        const x = fx * W + Math.sin(frame / (90 + i * 13) + i) * 40 * u - size / 2;
        const y = fy * H + Math.cos(frame / (110 + i * 9) + i) * 50 * u - size / 2;
        const cycle = 150 + i * 17;
        const step = Math.floor((frame + i * 37) / cycle);
        const local = (frame + i * 37) % cycle;
        const a = shapes[(step + i) % shapes.length], b = shapes[(step + i + 1) % shapes.length];
        const t = sp(local, 30, cycle - 40, SPRING.spatialSlow);
        return (
          <div key={i} style={{position: 'absolute', left: x, top: y, opacity}}>
            <Morph from={a} to={b} t={t} size={size} color={colors[i % colors.length]} rotation={frame * (0.15 + (i % 3) * 0.1) + i * 40} />
          </div>
        );
      })}
    </AbsoluteFill>
  );
};

/** M3 Expressive loading indicator: shapes morph every 650 ms with a fast spatial spring, spinning. */
export const LoadingIndicator: React.FC<{size: number; color: string; container?: string; delay?: number}> = ({size, color, container, delay = 0}) => {
  const frame = useCurrentFrame() - delay;
  const {fps} = useVideoConfig();
  const stepFrames = Math.round(0.65 * fps);
  const step = Math.max(0, Math.floor(frame / stepFrames));
  const local = Math.max(0, frame - step * stepFrames);
  const a = LOADING_SEQUENCE[step % LOADING_SEQUENCE.length], b = LOADING_SEQUENCE[(step + 1) % LOADING_SEQUENCE.length];
  const t = sp(local, fps, 0, SPRING.spatialFast);
  const rot = step * 90 + 90 * t + frame * (360 / (4.666 * fps));
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{overflow: 'visible'}}>
      {container ? <circle cx={size / 2} cy={size / 2} r={size / 2} fill={container} /> : null}
      <path d={morphPath(a, b, t, size / 2, size / 2, size * (container ? 0.62 : 0.86), rot)} fill={color} />
    </svg>
  );
};

/** Kinetic headline: each line rises on a spring, letters grow in weight (variable font axis). */
export const Headline: React.FC<{
  text: string; color: string; size: number; delay?: number; align?: 'left' | 'center'; weight?: number; width?: number;
  exitAt?: number; style?: React.CSSProperties;
}> = ({text, color, size, delay = 0, align = 'center', weight = 760, width = 100, exitAt, style}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const lines = text.split('\n');
  const exit = exitAt !== undefined ? sp(frame, fps, exitAt, SPRING.effects) : 0;
  return (
    <div style={{textAlign: align, fontFamily: FONT, color, ...style}}>
      {lines.map((line, li) => {
        const t = sp(frame, fps, delay + li * 6, SPRING.spatial);
        return (
          <div key={li} style={{overflow: 'hidden', lineHeight: 1.08, paddingBottom: size * 0.08}}>
            <div style={{
              transform: `translateY(${(1 - t) * size * 1.1 + exit * -size * 0.6}px)`,
              opacity: Math.min(1, t * 1.4) * (1 - exit),
              fontSize: size,
              letterSpacing: -size * 0.02,
            }}>
              {Array.from(line).map((ch, ci) => {
                const w = sp(frame, fps, delay + li * 6 + ci * 1.2, SPRING.effectsSlow);
                return (
                  <span key={ci} style={{
                    fontVariationSettings: `'wght' ${Math.round(interpolate(w, [0, 1], [200, weight], {extrapolateRight: 'clamp'}))}, 'wdth' ${width}`,
                    whiteSpace: 'pre',
                  }}>{ch}</span>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
};

/** Body text with a spring fade-up. */
export const Body: React.FC<{text: string; color: string; size: number; delay?: number; align?: 'left' | 'center'; weight?: number; style?: React.CSSProperties}> = ({text, color, size, delay = 0, align = 'center', weight = 450, style}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const t = sp(frame, fps, delay, SPRING.spatial);
  return (
    <div style={{
      fontFamily: FONT, color, fontSize: size, textAlign: align, whiteSpace: 'pre-line', lineHeight: 1.25,
      fontVariationSettings: `'wght' ${weight}`, transform: `translateY(${(1 - t) * size}px)`, opacity: Math.min(1, t), ...style,
    }}>{text}</div>
  );
};

/** M3 assist/filter chip. */
export const Chip: React.FC<{label: string; r: Roles; selected?: boolean; size?: number; icon?: React.ReactNode; style?: React.CSSProperties}> = ({label, r, selected, size = 34, icon, style}) => (
  <div style={{
    display: 'inline-flex', alignItems: 'center', gap: size * 0.3, height: size * 2.05, padding: `0 ${size * 0.75}px`,
    borderRadius: size * 0.6, fontFamily: FONT, fontSize: size, fontVariationSettings: `'wght' 560`,
    background: selected ? r.secondaryContainer : 'transparent', color: selected ? r.onSecondaryContainer : r.onSurfaceVariant,
    border: selected ? 'none' : `${Math.max(2, size * 0.06)}px solid ${r.outline}`, whiteSpace: 'nowrap', ...style,
  }}>
    {icon}{label}
  </div>
);

/** M3 switch (52×32 dp scaled): track, growing thumb, check. `on` is 0..1 (spring). */
export const Switch: React.FC<{on: number; r: Roles; scale?: number}> = ({on, r, scale = 3}) => {
  const w = 52 * scale, h = 32 * scale, k = Math.max(0, Math.min(1, on));
  const thumb = (16 + 8 * k) * scale / 2;
  const cx = h / 2 + (w - h) * on;
  return (
    <svg width={w} height={h} style={{overflow: 'visible'}}>
      <rect x={1 * scale} y={1 * scale} width={w - 2 * scale} height={h - 2 * scale} rx={(h - 2 * scale) / 2}
        fill={mix(r.surfaceContainerHighest, r.primary, k)} stroke={r.outline} strokeOpacity={1 - k} strokeWidth={2 * scale} />
      <circle cx={cx} cy={h / 2} r={thumb} fill={mix(r.outline, r.onPrimary, k)} />
      {k > 0.5 ? (
        <path d={`M${cx - 5 * scale} ${h / 2} l${3.5 * scale} ${3.5 * scale} l${6.5 * scale} ${-7 * scale}`} stroke={r.primary}
          strokeWidth={2 * scale} fill="none" strokeLinecap="round" strokeLinejoin="round" opacity={(k - 0.5) * 2} />
      ) : null}
    </svg>
  );
};

/** M3 FAB (large, primary container). */
export const Fab: React.FC<{r: Roles; size: number; children?: React.ReactNode; style?: React.CSSProperties}> = ({r, size, children, style}) => (
  <div style={{
    width: size, height: size, borderRadius: size * 0.3, background: r.primaryContainer, color: r.onPrimaryContainer,
    display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: `0 ${size * 0.06}px ${size * 0.16}px rgba(0,0,0,.18)`, ...style,
  }}>{children}</div>
);

/** Phone frame. Children fill the screen (1080×2400 aspect). */
export const Phone: React.FC<{width: number; r: Roles; children: React.ReactNode; style?: React.CSSProperties}> = ({width, r, children, style}) => {
  const h = width * (2400 / 1080);
  const bezel = width * 0.035;
  return (
    <div style={{
      width: width + bezel * 2, height: h + bezel * 2, borderRadius: width * 0.13, background: '#101010', padding: bezel,
      boxShadow: `0 ${width * 0.05}px ${width * 0.14}px rgba(0,0,0,.35), inset 0 0 0 ${bezel * 0.25}px #2A2A2A`, ...style,
    }}>
      <div style={{width, height: h, borderRadius: width * 0.1, overflow: 'hidden', position: 'relative', background: r.surface}}>
        {children}
        <div style={{position: 'absolute', top: width * 0.025, left: '50%', width: width * 0.04, height: width * 0.04, marginLeft: -width * 0.02, borderRadius: '50%', background: '#000'}} />
      </div>
    </div>
  );
};

/** A real YiliGram screenshot (from the emulator) filling the phone screen. */
export const Screen: React.FC<{name: string; opacity?: number}> = ({name, opacity = 1}) => (
  <Img src={staticFile(`screens/${name}.png`)} style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', opacity}} />
);

/** QR code drawn as SVG modules. */
export const QR: React.FC<{text: string; size: number; fg: string; bg: string}> = ({text, size, fg, bg}) => {
  const q = QRCode.create(text, {errorCorrectionLevel: 'M'});
  const n = q.modules.size;
  const quiet = 2;
  const cell = size / (n + quiet * 2);
  const rects: React.ReactNode[] = [];
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    if (q.modules.get(x, y)) rects.push(<rect key={`${x}-${y}`} x={(x + quiet) * cell} y={(y + quiet) * cell} width={cell + 0.4} height={cell + 0.4} fill={fg} />);
  }
  return (
    <svg width={size} height={size}>
      <rect width={size} height={size} rx={size * 0.08} fill={bg} />
      {rects}
    </svg>
  );
};

/** The YiliGram logo clipped to a circle. */
export const Logo: React.FC<{size: number; style?: React.CSSProperties}> = ({size, style}) => (
  <div style={{width: size, height: size, borderRadius: '50%', overflow: 'hidden', ...style}}>
    <Img src={staticFile('logo.png')} style={{width: '100%', height: '100%', objectFit: 'cover', transform: 'scale(1.12)'}} />
  </div>
);

/** Static shape (for icons, badges). */
export const ShapeSvg: React.FC<{name: ShapeName; size: number; fill: string; rotation?: number}> = ({name, size, fill, rotation = 0}) => (
  <svg width={size} height={size} style={{overflow: 'visible'}}><path d={shapePath(name, size / 2, size / 2, size, rotation)} fill={fill} /></svg>
);
