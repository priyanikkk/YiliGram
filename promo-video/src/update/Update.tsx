/**
 * "What's new in YiliGram 0.1.4": a short update video with music and UI sounds, no voice.
 * Scenes and their sounds are listed in UPDATE below; the music is made by scripts/music_update.py.
 */
import React from 'react';
import {AbsoluteFill, Audio, interpolate, Sequence, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {Body, Chip, FloatingShapes, Headline, Logo, Morph, ShapeSvg, Switch, useLayout} from '../components/ui';
import {CONFIG} from '../config';
import {FONT, mix, Roles, roles, sp, SPRING} from '../theme';
import {ShapeName, shapePath} from '../shapes';

type Sfx = 'poof' | 'pop' | 'click' | 'tick' | 'ding';
type SceneDef = {key: string; seconds: number; C: React.FC; sfx: [number, Sfx][]};

const useScene = () => {
  const frame = useCurrentFrame();
  const {fps, durationInFrames} = useVideoConfig();
  const layout = useLayout();
  const sec = (s: number) => Math.round(s * fps);
  const out = sp(frame, fps, durationInFrames - 10, SPRING.effects);
  return {frame, fps, sec, out, ...layout};
};

/** Headline on top (vertical) or on the left (horizontal), demo below or to the right. */
const Stage: React.FC<{r: Roles; title: string; sub?: string; seed?: number; children: React.ReactNode}> = ({r, title, sub, seed = 1, children}) => {
  const {frame, fps, out, W, H, vertical, u} = useScene();
  return (
    <AbsoluteFill style={{background: r.surfaceContainerLow, opacity: 1 - out}}>
      <FloatingShapes r={r} count={6} seed={seed} opacity={0.35} />
      <div style={{position: 'absolute', left: vertical ? 50 * u : W * 0.05, right: vertical ? 50 * u : W * 0.55, top: vertical ? H * 0.07 : H * 0.3}}>
        <Headline text={title} color={r.onSurface} size={(vertical ? 96 : 92) * u} delay={2} align={vertical ? 'center' : 'left'} weight={820} />
        {sub ? <Body text={sub} color={r.primary} size={(vertical ? 44 : 40) * u} delay={12} align={vertical ? 'center' : 'left'} weight={540} style={{marginTop: 18 * u}} /> : null}
      </div>
      <div style={{
        position: 'absolute', left: vertical ? 0 : W * 0.45, right: 0, top: vertical ? H * 0.3 : 0, bottom: 0,
        display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${0.92 + 0.08 * sp(frame, fps, 4, SPRING.spatial)})`,
      }}>
        {children}
      </div>
    </AbsoluteFill>
  );
};

const Card: React.FC<{r: Roles; w: number; children: React.ReactNode; style?: React.CSSProperties}> = ({r, w, children, style}) => {
  const {u} = useLayout();
  return (
    <div style={{width: w, borderRadius: 56 * u, background: r.surface, padding: 36 * u, boxShadow: `0 ${18 * u}px ${60 * u}px rgba(0,0,0,.14)`, fontFamily: FONT, ...style}}>
      {children}
    </div>
  );
};

const Bubble: React.FC<{r: Roles; out?: boolean; text: string; size: number; style?: React.CSSProperties; meta?: React.ReactNode}> = ({r, out, text, size, style, meta}) => (
  <div style={{
    alignSelf: out ? 'flex-end' : 'flex-start', maxWidth: '82%', padding: `${size * 0.45}px ${size * 0.7}px`, borderRadius: size * 0.9,
    borderBottomRightRadius: out ? size * 0.25 : size * 0.9, borderBottomLeftRadius: out ? size * 0.9 : size * 0.25,
    background: out ? r.primaryContainer : r.surfaceContainerHighest, color: out ? r.onPrimaryContainer : r.onSurface,
    fontSize: size, fontFamily: FONT, lineHeight: 1.25, ...style,
  }}>
    {text}{meta}
  </div>
);

const Avatar: React.FC<{r: Roles; size: number; letter: string; color?: string; shape?: ShapeName}> = ({r, size, letter, color, shape = 'circle'}) => (
  <div style={{position: 'relative', width: size, height: size, flexShrink: 0}}>
    <ShapeSvg name={shape} size={size} fill={color ?? r.tertiaryContainer} />
    <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT,
      fontSize: size * 0.42, color: r.onTertiaryContainer, fontVariationSettings: `'wght' 700`}}>{letter}</div>
  </div>
);

// ---------------------------------------------------------------- scenes

const Intro: React.FC = () => {
  const {frame, fps, out, W, H, vertical, u} = useScene();
  const r = roles(undefined, true);
  const seq: ShapeName[] = ['sunny', 'cookie9', 'flower', 'circle'];
  const step = 9;
  const idx = Math.min(seq.length - 2, Math.floor(frame / step));
  const t = sp(frame, fps, idx * step, SPRING.spatialFast);
  const size = (vertical ? 440 : 340) * u * (0.3 + 0.7 * sp(frame, fps, 2, SPRING.spatial));
  const logoIn = sp(frame, fps, step * 3, SPRING.effectsSlow);
  const cy = vertical ? H * 0.36 : H * 0.36;
  const ver = sp(frame, fps, 50, SPRING.spatialFast);
  return (
    <AbsoluteFill style={{background: r.surface, opacity: 1 - out}}>
      <FloatingShapes r={r} count={8} seed={5} opacity={0.35 * Math.min(1, frame / 20)} />
      <div style={{position: 'absolute', left: W / 2 - size / 2, top: cy - size / 2}}>
        <Morph from={seq[idx]} to={seq[idx + 1]} t={t} size={size} color={[r.primary, r.tertiary, r.secondary][idx] ?? r.primary} rotation={frame * 2} />
        <div style={{position: 'absolute', inset: 0, opacity: logoIn, transform: `scale(${0.6 + 0.4 * logoIn})`}}><Logo size={size} /></div>
      </div>
      <div style={{position: 'absolute', left: 0, right: 0, top: cy + size / 2 + 40 * u}}>
        <Headline text="YiliGram" color={r.onSurface} size={(vertical ? 160 : 120) * u} delay={30} weight={850} />
        <div style={{display: 'flex', justifyContent: 'center', marginTop: 10 * u}}>
          <div style={{transform: `scale(${ver})`, padding: `${14 * u}px ${44 * u}px`, borderRadius: 60 * u, background: r.primary, color: r.onPrimary,
            fontFamily: FONT, fontSize: (vertical ? 64 : 52) * u, fontVariationSettings: `'wght' 760`}}>0.1.4</div>
        </div>
        <Body text="Что нового" color={r.primary} size={(vertical ? 56 : 44) * u} delay={60} weight={600} style={{marginTop: 30 * u}} />
      </div>
    </AbsoluteFill>
  );
};

const Ghost: React.FC = () => {
  const {frame, fps, sec, vertical, u} = useScene();
  const r = roles(CONFIG.showcaseSeeds[1], true);
  const on = sp(frame, fps, sec(1.3), SPRING.spatialFast);
  const w = (vertical ? 860 : 820) * u, fs = 40 * u;
  const statusOn = interpolate(frame, [sec(1.4), sec(1.8)], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  const rows = [['Онлайн', 'Не в сети'], ['Печатает…', 'Тишина'], ['Прочитано ✓✓', 'Не прочитано ✓']];
  return (
    <Stage r={r} title={'Режим\nпризрака'} sub="Читайте незаметно" seed={11}>
      <Card r={r} w={w}>
        <div style={{display: 'flex', alignItems: 'center', gap: 28 * u}}>
          <div style={{position: 'relative', width: 150 * u, height: 150 * u, flexShrink: 0, transform: `scale(${1 + 0.15 * Math.sin(Math.min(1, on) * Math.PI)})`}}>
            <Morph from="circle" to="cookie9" t={on} size={150 * u} color={mix(r.surfaceContainerHighest, r.primary, Math.min(1, on))} />
            <svg width={150 * u} height={150 * u} style={{position: 'absolute', left: 0, top: 0}} viewBox="0 0 24 24">
              <path d="M12 3a7 7 0 0 0-7 7v10l2.3-1.6L9.6 20l2.4-1.6 2.4 1.6 2.3-1.6L19 20V10a7 7 0 0 0-7-7zm-2.5 8a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm5 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z"
                transform="translate(6 6) scale(.5)" fill={on > 0.5 ? r.onPrimary : r.onSurfaceVariant} />
            </svg>
          </div>
          <div style={{flex: 1, fontSize: 46 * u, color: r.onSurface, fontVariationSettings: `'wght' 700`}}>Режим призрака</div>
          <Switch on={on} r={r} scale={2.4 * u} />
        </div>
        <div style={{marginTop: 36 * u, display: 'flex', flexDirection: 'column', gap: 18 * u}}>
          {rows.map(([a, b], i) => {
            const k = interpolate(frame, [sec(1.6) + i * 6, sec(2.1) + i * 6], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
            return (
              <div key={i} style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: `${18 * u}px ${26 * u}px`,
                borderRadius: 30 * u, background: r.surfaceContainerHigh, fontSize: fs}}>
                <span style={{color: r.onSurfaceVariant}}>{['Статус', 'Набор', 'Сообщения'][i]}</span>
                <span style={{position: 'relative', color: mix(r.primary, r.onSurfaceVariant, k), fontVariationSettings: `'wght' 600`}}>
                  <span style={{opacity: 1 - k}}>{a}</span>
                  <span style={{position: 'absolute', right: 0, opacity: k, whiteSpace: 'nowrap'}}>{b}</span>
                </span>
              </div>
            );
          })}
        </div>
        <div style={{opacity: statusOn, marginTop: 24 * u, fontSize: 32 * u, color: r.onSurfaceVariant, textAlign: 'center'}}>Одна кнопка над списком чатов</div>
      </Card>
    </Stage>
  );
};

const Deleted: React.FC = () => {
  const {frame, fps, sec, vertical, u} = useScene();
  const r = roles(CONFIG.showcaseSeeds[3]);
  const w = (vertical ? 860 : 820) * u, fs = 42 * u;
  const shake = frame > sec(1.0) && frame < sec(1.5) ? Math.sin(frame * 2.2) * 10 * u : 0;
  const del = sp(frame, fps, sec(1.4), SPRING.spatialFast);
  const hist = sp(frame, fps, sec(2.6), SPRING.spatial);
  const versions = ['Встретимся в 6?', 'Встретимся в 7?', 'Встретимся в 7 у метро'];
  return (
    <Stage r={r} title={'Удалённые\nи правки'} sub="Остаются у вас на телефоне" seed={13}>
      <div style={{display: 'flex', flexDirection: 'column', gap: 30 * u, width: w}}>
        <Card r={r} w={w} style={{display: 'flex', flexDirection: 'column', gap: 16 * u}}>
          <Bubble r={r} text="Привет! Как дела?" size={fs} />
          <div style={{alignSelf: 'flex-start', transform: `translateX(${shake}px)`, opacity: 1 - 0.35 * Math.min(1, del)}}>
            <Bubble r={r} text="Секретное сообщение 🤫" size={fs} style={{maxWidth: '100%'}} />
          </div>
          <div style={{alignSelf: 'flex-start', transform: `scale(${del})`, transformOrigin: 'left center', padding: `${8 * u}px ${22 * u}px`, borderRadius: 30 * u,
            background: r.tertiaryContainer, color: r.onTertiaryContainer, fontSize: 30 * u, fontVariationSettings: `'wght' 650`}}>
            удалено · сохранено в YiliGram
          </div>
        </Card>
        <Card r={r} w={w} style={{opacity: Math.min(1, hist * 1.5), transform: `translateY(${(1 - hist) * 80 * u}px)`}}>
          <div style={{fontSize: 34 * u, color: r.primary, fontVariationSettings: `'wght' 700`, marginBottom: 14 * u}}>История правок</div>
          {versions.map((v, i) => {
            const k = sp(frame, fps, sec(2.9) + i * 7, SPRING.spatialFast);
            const last = i === versions.length - 1;
            return (
              <div key={i} style={{display: 'flex', gap: 18 * u, alignItems: 'center', padding: `${10 * u}px 0`, opacity: k, transform: `translateX(${(1 - k) * 40 * u}px)`}}>
                <div style={{width: 18 * u, height: 18 * u, borderRadius: '50%', background: last ? r.primary : r.outline}} />
                <div style={{fontSize: 38 * u, color: last ? r.onSurface : r.onSurfaceVariant, textDecoration: last ? 'none' : 'line-through'}}>{v}</div>
              </div>
            );
          })}
        </Card>
      </div>
    </Stage>
  );
};

const Hidden: React.FC = () => {
  const {frame, fps, sec, vertical, u} = useScene();
  const r = roles(CONFIG.showcaseSeeds[2], true);
  const w = (vertical ? 860 : 820) * u;
  const chats = [['А', 'Аня', 'Увидимся завтра'], ['М', 'Мама', 'Купи хлеб'], ['С', 'Секретный чат', 'Никто не узнает'], ['Р', 'Работа', 'Отчёт готов']];
  const hide = sp(frame, fps, sec(1.2), SPRING.spatial);
  const lock = sp(frame, fps, sec(2.4), SPRING.spatialFast);
  const unlock = sp(frame, fps, sec(3.3), SPRING.spatialFast);
  return (
    <Stage r={r} title={'Скрытые\nчаты'} sub="Под PIN-кодом или отпечатком" seed={17}>
      <div style={{position: 'relative', width: w}}>
        <Card r={r} w={w} style={{padding: 24 * u}}>
          {chats.map(([l, name, msg], i) => {
            const isSecret = i === 2;
            const k = isSecret ? hide : 0;
            const shift = i > 2 ? -hide * 132 * u : 0;
            return (
              <div key={i} style={{display: 'flex', alignItems: 'center', gap: 26 * u, height: 132 * u, opacity: 1 - k,
                transform: `translate(${k * 600 * u}px, ${shift}px)`}}>
                <Avatar r={r} size={96 * u} letter={l} shape="cookie9" />
                <div>
                  <div style={{fontSize: 40 * u, color: r.onSurface, fontVariationSettings: `'wght' 680`}}>{name}</div>
                  <div style={{fontSize: 32 * u, color: r.onSurfaceVariant}}>{msg}</div>
                </div>
              </div>
            );
          })}
        </Card>
        <div style={{position: 'absolute', right: -20 * u, bottom: -80 * u, width: 230 * u, height: 230 * u, transform: `scale(${lock})`}}>
          <Morph from="cookie12" to="circle" t={unlock} size={230 * u} color={mix(r.tertiaryContainer, r.primary, unlock)} rotation={frame} />
          <svg width={230 * u} height={230 * u} viewBox="0 0 24 24" style={{position: 'absolute', left: 0, top: 0}}>
            <g transform="translate(6.5 6.5) scale(.46)" fill="none" stroke={unlock > 0.5 ? r.onPrimary : r.onTertiaryContainer} strokeWidth={1.8} strokeLinecap="round">
              <path d="M12 3a8 8 0 0 0-8 8v2" /><path d="M20 13v-2a8 8 0 0 0-3-6.2" /><path d="M12 7a4 4 0 0 0-4 4v3a10 10 0 0 0 1.5 5" />
              <path d="M16 11a4 4 0 0 0-1-2.6" /><path d="M12 11v4a6 6 0 0 0 2 4.5" /><path d="M16 14.5a8 8 0 0 0 1 4" />
            </g>
          </svg>
        </div>
      </div>
    </Stage>
  );
};

const Templates: React.FC = () => {
  const {frame, fps, sec, vertical, u} = useScene();
  const r = roles(CONFIG.seed);
  const w = (vertical ? 900 : 840) * u, fs = 44 * u;
  const pick = sp(frame, fps, sec(1.0), SPRING.spatialFast);
  const filled = frame >= sec(1.3);
  const trans = sp(frame, fps, sec(2.7), SPRING.spatial);
  const templates = ['Привет, {name}!', 'Перезвоню в {time}', 'Уже еду 🚗'];
  const ru = 'Привет, Аня! Уже еду';
  const en = 'Hi, Anya! On my way';
  return (
    <Stage r={r} title={'Шаблоны\nи перевод'} sub="Ответ в одно касание" seed={19}>
      <div style={{width: w, display: 'flex', flexDirection: 'column', gap: 26 * u}}>
        <div style={{display: 'flex', flexWrap: 'wrap', gap: 16 * u, justifyContent: 'center'}}>
          {templates.map((t, i) => {
            const k = sp(frame, fps, 8 + i * 5, SPRING.spatialFast);
            return <div key={i} style={{transform: `scale(${k * (i === 0 ? 1 + 0.1 * Math.sin(Math.min(1, pick) * Math.PI) : 1)})`}}>
              <Chip label={t} r={r} selected={i === 0 && pick > 0.5} size={34 * u} />
            </div>;
          })}
        </div>
        <div style={{height: 150 * u, borderRadius: 75 * u, background: r.surfaceContainerHighest, display: 'flex', alignItems: 'center', padding: `0 ${50 * u}px`,
          fontFamily: FONT, fontSize: fs, color: r.onSurface, position: 'relative', overflow: 'hidden'}}>
          {filled ? (
            <>
              <span style={{position: 'absolute', left: 50 * u, opacity: 1 - trans, transform: `translateY(${-trans * 60 * u}px)`, filter: `blur(${trans * 8 * u}px)`}}>{ru}</span>
              <span style={{position: 'absolute', left: 50 * u, opacity: trans, transform: `translateY(${(1 - trans) * 60 * u}px)`, filter: `blur(${(1 - Math.min(1, trans)) * 8 * u}px)`}}>{en}</span>
            </>
          ) : <span style={{color: r.onSurfaceVariant}}>Сообщение</span>}
          <div style={{position: 'absolute', right: 26 * u, width: 100 * u, height: 100 * u, borderRadius: 50 * u, background: r.primary,
            display: 'flex', alignItems: 'center', justifyContent: 'center', transform: `scale(${1 + 0.12 * Math.sin(Math.min(1, sp(frame, fps, sec(2.2), SPRING.spatialFast)) * Math.PI)})`}}>
            <svg width={50 * u} height={50 * u} viewBox="0 0 24 24"><path d="M3 20l18-8L3 4v6l12 2-12 2z" fill={r.onPrimary} /></svg>
          </div>
        </div>
        <div style={{display: 'flex', justifyContent: 'center', opacity: Math.min(1, sp(frame, fps, sec(2.3), SPRING.effects))}}>
          <Chip label="Перевести на английский" r={r} selected={trans > 0.5} size={34 * u} icon={<span style={{fontSize: 40 * u}}>🌐</span>} />
        </div>
      </div>
    </Stage>
  );
};

const Voice: React.FC = () => {
  const {frame, fps, sec, vertical, u} = useScene();
  const r = roles(CONFIG.showcaseSeeds[1]);
  const w = (vertical ? 880 : 820) * u;
  const speeds = ['1x', '1.5x', '2x', '3x'];
  const idx = Math.min(3, Math.max(0, Math.floor((frame - sec(0.9)) / 12)));
  const bump = sp(frame, fps, sec(0.9) + idx * 12, SPRING.spatialFast);
  const skip = sp(frame, fps, sec(2.8), SPRING.spatial);
  const bars = 34;
  const silent = (i: number) => (i >= 12 && i < 19);
  const progress = interpolate(frame, [sec(0.5), sec(4.2)], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  return (
    <Stage r={r} title="Голосовые+" sub={'Пропуск тишины, 3x,\nпродолжение с места остановки'} seed={23}>
      <Card r={r} w={w} style={{display: 'flex', alignItems: 'center', gap: 26 * u, borderRadius: 80 * u}}>
        <div style={{width: 120 * u, height: 120 * u, borderRadius: 60 * u, background: r.primary, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0}}>
          <svg width={56 * u} height={56 * u} viewBox="0 0 24 24"><path d="M7 5h4v14H7zM13 5h4v14h-4z" fill={r.onPrimary} /></svg>
        </div>
        <div style={{flex: 1, display: 'flex', alignItems: 'center', height: 120 * u, gap: 6 * u}}>
          {Array.from({length: bars}, (_, i) => {
            const h = silent(i) ? 6 : 20 + Math.abs(Math.sin(i * 1.7) * 60) + Math.abs(Math.cos(i * 0.6) * 30);
            const gone = silent(i) ? skip : 0;
            const played = i / bars < progress;
            return <div key={i} style={{width: (1 - gone) * 9 * u, marginRight: gone ? -6 * u * gone : 0, height: h * u * (1 - gone), borderRadius: 5 * u,
              background: played ? r.primary : r.outlineVariant, opacity: 1 - gone}} />;
          })}
        </div>
        <div style={{transform: `scale(${1 + 0.18 * Math.sin(Math.min(1, bump) * Math.PI)})`, padding: `${10 * u}px ${22 * u}px`, borderRadius: 26 * u,
          background: r.secondaryContainer, color: r.onSecondaryContainer, fontFamily: FONT, fontSize: 40 * u, fontVariationSettings: `'wght' 760`, minWidth: 90 * u, textAlign: 'center'}}>
          {speeds[idx]}
        </div>
      </Card>
    </Stage>
  );
};

const Wallpapers: React.FC = () => {
  const {frame, fps, sec, vertical, u} = useScene();
  const r = roles(CONFIG.seed);
  const pw = (vertical ? 270 : 230) * u, ph = pw * 1.9;
  const styles = ['Фигуры M3', 'Пузыри', 'Волны'];
  const bubbleShape = Math.min(2, Math.max(0, Math.floor((frame - sec(2.4)) / 18)));
  const radiusFor = (k: number, out: boolean) => {
    const big = pw * 0.16;
    if (k === 0) return `${big}px ${big}px ${out ? 6 * u : big}px ${out ? big : 6 * u}px`;
    if (k === 1) return `${pw}px`;
    return `${big}px ${big}px ${out ? 4 * u : big}px ${out ? big : 4 * u}px`;
  };
  const pattern = (s: number) => {
    const els: React.ReactNode[] = [];
    const cols = [r.primaryContainer, r.secondaryContainer, r.tertiaryContainer];
    if (s === 2) {
      for (let i = 0; i < 7; i++) {
        let d = `M ${-40} ${i * 70 + 30}`;
        for (let x = -40; x < pw + 60; x += 60) d += ` q 15 -16 30 0 t 30 0`;
        els.push(<path key={i} d={d} stroke={cols[i % 3]} strokeWidth={12 * u} fill="none" strokeLinecap="round" transform={`translate(${(frame * 0.6) % 60} 0)`} />);
      }
    } else {
      const names: ShapeName[] = ['cookie9', 'flower', 'softBurst', 'clover4', 'sunny', 'gem'];
      for (let i = 0; i < 12; i++) {
        const rx = Math.abs(Math.sin((i + 1) * 12.9898 + s) * 43758.5453) % 1, ry = Math.abs(Math.sin((i + 1) * 78.233 + s * 3) * 12543.17) % 1;
        const x = (0.08 + rx * 0.84) * pw, y = (0.05 + ry * 0.62) * ph + Math.sin(frame / 30 + i) * 6 * u;
        const size = (s === 1 ? 50 + (i % 4) * 18 : 36 + (i % 3) * 12) * u;
        els.push(<g key={i} transform={`translate(${x - size / 2} ${y - size / 2})`} opacity={s === 1 ? 0.6 : 0.85}>
          <path d={shapePath(s === 1 ? 'circle' : names[i % names.length], size / 2, size / 2, size, frame + i * 40)} fill={cols[i % 3]} />
        </g>);
      }
    }
    return <svg width={pw} height={ph} style={{position: 'absolute', inset: 0}}>{els}</svg>;
  };
  return (
    <Stage r={r} title={'Обои и формы\nпузырей'} sub="В цветах вашей темы" seed={29}>
      <div style={{display: 'flex', gap: 30 * u}}>
        {styles.map((name, i) => {
          const k = sp(frame, fps, 6 + i * 6, SPRING.spatialFast);
          return (
            <div key={i} style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 * u, transform: `translateY(${(1 - k) * 200 * u}px)`, opacity: Math.min(1, k * 2)}}>
              <div style={{position: 'relative', width: pw, height: ph, borderRadius: 40 * u, overflow: 'hidden', background: `linear-gradient(170deg, ${r.surfaceContainerLow}, ${r.surfaceContainer})`,
                boxShadow: `0 ${14 * u}px ${40 * u}px rgba(0,0,0,.15)`}}>
                {pattern(i)}
                <div style={{position: 'absolute', left: 16 * u, right: 16 * u, bottom: 24 * u, display: 'flex', flexDirection: 'column', gap: 10 * u, fontFamily: FONT, fontSize: 24 * u}}>
                  <div style={{alignSelf: 'flex-start', padding: `${10 * u}px ${16 * u}px`, background: r.surfaceContainerHighest, color: r.onSurface, borderRadius: radiusFor(bubbleShape, false)}}>Привет!</div>
                  <div style={{alignSelf: 'flex-end', padding: `${10 * u}px ${16 * u}px`, background: r.primaryContainer, color: r.onPrimaryContainer, borderRadius: radiusFor(bubbleShape, true)}}>Как обои? 😍</div>
                </div>
              </div>
              <div style={{fontFamily: FONT, fontSize: 32 * u, color: r.onSurface, fontVariationSettings: `'wght' 650`}}>{name}</div>
            </div>
          );
        })}
      </div>
    </Stage>
  );
};

const Profile: React.FC = () => {
  const {frame, fps, sec, vertical, u} = useScene();
  const r = roles(CONFIG.showcaseSeeds[3], true);
  const w = (vertical ? 860 : 800) * u;
  const m = sp(frame, fps, sec(0.8), SPRING.spatial);
  const m2 = sp(frame, fps, sec(2.4), SPRING.spatial);
  const avatarShape: [ShapeName, ShapeName, number] = m2 > 0.02 ? ['flower', 'cookie12', m2] : ['circle', 'flower', m];
  const buttons = ['Чат', 'Звук', 'Звонок', 'Ещё'];
  const tabsHide = interpolate(frame, [sec(3.0), sec(3.4)], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  return (
    <Stage r={r} title={'Морфинг\nи профиль M3'} sub="Аватарки перетекают в свою форму" seed={31}>
      <Card r={r} w={w} style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 24 * u, position: 'relative', overflow: 'hidden', paddingBottom: 150 * u}}>
        <div style={{position: 'relative', width: 300 * u, height: 300 * u}}>
          <Morph from={avatarShape[0]} to={avatarShape[1]} t={avatarShape[2]} size={300 * u} color={r.tertiaryContainer} rotation={frame * 0.5} />
          <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT, fontSize: 130 * u, color: r.onTertiaryContainer, fontVariationSettings: `'wght' 760`}}>А</div>
        </div>
        <div style={{fontSize: 54 * u, color: r.onSurface, fontVariationSettings: `'wght' 760`}}>Аня</div>
        <div style={{display: 'flex', gap: 16 * u}}>
          {buttons.map((b, i) => {
            const k = sp(frame, fps, sec(1.2) + i * 4, SPRING.spatialFast);
            return <div key={i} style={{width: 170 * u, height: 130 * u, borderRadius: 40 * u, background: r.secondaryContainer, color: r.onSecondaryContainer,
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32 * u, fontVariationSettings: `'wght' 650`, transform: `scale(${k})`}}>{b}</div>;
          })}
        </div>
        <div style={{position: 'absolute', bottom: 30 * u, left: '50%', marginLeft: -260 * u, width: 520 * u, height: 100 * u, borderRadius: 50 * u,
          background: r.surfaceContainerHighest, display: 'flex', alignItems: 'center', justifyContent: 'space-around',
          transform: `translateY(${tabsHide * 160 * u}px)`, opacity: 1 - tabsHide * 0.6}}>
          {['cookie9', 'softSquare', 'sunny'].map((s, i) => <ShapeSvg key={i} name={s as ShapeName} size={44 * u} fill={i === 0 ? r.primary : r.onSurfaceVariant} />)}
        </div>
      </Card>
    </Stage>
  );
};

const Extras: React.FC = () => {
  const {frame, fps, vertical, u} = useScene();
  const r = roles(CONFIG.seed, true);
  const items = ['ID в профилях', 'К первому сообщению', 'Бэкап настроек', 'Сила вибрации', 'Расписание темы', 'Иконки бесплатно', 'Монохромные иконки', 'Перевод по умолчанию'];
  return (
    <Stage r={r} title={'И ещё\nмелочи'} seed={37}>
      <div style={{display: 'flex', flexWrap: 'wrap', gap: 20 * u, justifyContent: 'center', width: (vertical ? 940 : 900) * u}}>
        {items.map((t, i) => {
          const k = sp(frame, fps, 8 + i * 4, SPRING.spatialFast);
          return <div key={i} style={{transform: `scale(${k}) rotate(${(1 - Math.min(1, k)) * (i % 2 ? 8 : -8)}deg)`}}>
            <Chip label={t} r={r} selected={i % 3 === 0} size={36 * u} icon={<ShapeSvg name={(['cookie4', 'sunny', 'flower'] as ShapeName[])[i % 3]} size={40 * u} fill={r.primary} />} />
          </div>;
        })}
      </div>
    </Stage>
  );
};

const Outro: React.FC = () => {
  const {frame, fps, out, W, H, vertical, u} = useScene();
  const r = roles(undefined, true);
  const logo = sp(frame, fps, 2, SPRING.spatialFast);
  return (
    <AbsoluteFill style={{background: r.surface, opacity: 1 - out}}>
      <FloatingShapes r={r} count={8} seed={43} opacity={0.35} />
      <div style={{position: 'absolute', left: 0, right: 0, top: vertical ? H * 0.22 : H * 0.14, display: 'flex', flexDirection: 'column', alignItems: 'center'}}>
        <div style={{transform: `scale(${logo})`}}><Logo size={(vertical ? 300 : 230) * u} /></div>
        <div style={{marginTop: 40 * u}}>
          <Headline text={'Обновление\nуже в приложении'} color={r.onSurface} size={(vertical ? 96 : 84) * u} delay={8} weight={820} />
        </div>
        <Body text="Оформление YiliGram → Обновления" color={r.primary} size={(vertical ? 42 : 38) * u} delay={24} weight={560} style={{marginTop: 20 * u}} />
        <div style={{marginTop: 50 * u, transform: `scale(${sp(frame, fps, 34, SPRING.spatialFast)})`}}>
          <Chip label={CONFIG.links.github + '/releases'} r={r} selected size={36 * u} />
        </div>
      </div>
      <div style={{position: 'absolute', bottom: 60 * u, left: 0, right: 0, textAlign: 'center', fontFamily: FONT, fontSize: 28 * u, color: r.onSurfaceVariant,
        opacity: Math.min(1, frame / 30)}}>Неофициальный клиент. Не связан с Telegram</div>
    </AbsoluteFill>
  );
};

// ---------------------------------------------------------------- timeline

export const UPDATE: SceneDef[] = [
  {key: 'intro', seconds: 4.5, C: Intro, sfx: [[0, 'poof'], [0.3, 'poof'], [0.6, 'poof'], [1.0, 'pop'], [1.7, 'ding']]},
  {key: 'ghost', seconds: 5, C: Ghost, sfx: [[1.3, 'click'], [1.6, 'tick'], [1.8, 'tick'], [2.0, 'tick']]},
  {key: 'deleted', seconds: 5, C: Deleted, sfx: [[1.0, 'tick'], [1.4, 'pop'], [2.9, 'tick'], [3.15, 'tick'], [3.4, 'tick']]},
  {key: 'hidden', seconds: 4.5, C: Hidden, sfx: [[1.2, 'poof'], [2.4, 'pop'], [3.3, 'ding']]},
  {key: 'templates', seconds: 5, C: Templates, sfx: [[1.0, 'click'], [2.2, 'tick'], [2.7, 'poof']]},
  {key: 'voice', seconds: 4.5, C: Voice, sfx: [[0.9, 'tick'], [1.3, 'tick'], [1.7, 'tick'], [2.1, 'click'], [2.8, 'poof']]},
  {key: 'wallpapers', seconds: 5, C: Wallpapers, sfx: [[0.2, 'pop'], [0.4, 'pop'], [0.6, 'pop'], [2.4, 'tick'], [3.0, 'tick'], [3.6, 'tick']]},
  {key: 'profile', seconds: 4.5, C: Profile, sfx: [[0.8, 'poof'], [1.2, 'tick'], [2.4, 'poof'], [3.0, 'click']]},
  {key: 'extras', seconds: 4, C: Extras, sfx: [[0.3, 'pop'], [0.7, 'pop'], [1.1, 'pop']]},
  {key: 'outro', seconds: 5, C: Outro, sfx: [[0.1, 'pop'], [1.1, 'ding']]},
];

export const UPDATE_FPS = CONFIG.fps;
const frames = (s: number) => Math.round(s * UPDATE_FPS);
export const UPDATE_FRAMES = UPDATE.reduce((n, s) => n + frames(s.seconds), 0);

export const UpdateTrack: React.FC = () => {
  let at = 0;
  const parts = UPDATE.map((s) => {
    const from = at;
    at += frames(s.seconds);
    return {s, from};
  });
  return (
    <AbsoluteFill style={{background: '#000'}}>
      {parts.map(({s, from}) => (
        <Sequence key={s.key} from={from} durationInFrames={frames(s.seconds)} name={s.key}>
          <s.C />
        </Sequence>
      ))}
      <Audio src={staticFile('music_update.m4a')} volume={0.65} />
      {parts.flatMap(({s, from}) => s.sfx.map(([t, name], i) => (
        <Sequence key={`${s.key}-${i}`} from={from + frames(t)} durationInFrames={UPDATE_FPS}>
          <Audio src={staticFile(`sfx/${name}.wav`)} volume={0.5} />
        </Sequence>
      )))}
    </AbsoluteFill>
  );
};
