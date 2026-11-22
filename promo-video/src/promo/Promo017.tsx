/**
 * YiliGram 0.1.7 promo: the interface style picker (Telegram, half or full Material 3) and the main
 * features, with music and UI sounds, no voice. Music: scripts/music_update.py src/promo/Promo017.tsx music_promo017.m4a
 */
import React from 'react';
import {CONFIG} from '../config';
import {FONT, mix, Roles, roles, sp, SPRING} from '../theme';
import {ShapeName, shapePath} from '../shapes';
import {Switch} from '../components/ui';
import {
  Deleted, Ghost, Hidden, Intro, IntroText, makeTrack, Outro, Profile, SceneDef, sceneFramesOf, Stage, Templates, useScene, Wallpapers,
} from '../update/Update';

type Level = 'telegram' | 'half' | 'full';

/** A small chat screen drawn in one of the three styles. */
const MiniChat: React.FC<{level: Level; r: Roles; w: number; selected: number}> = ({level, r, w, selected}) => {
  const {frame, u} = useScene();
  const h = w * 1.75;
  const full = level === 'full', half = level === 'half';
  const tg = level === 'telegram';
  const tgBlue = '#3390EC';
  const accent = tg ? tgBlue : r.primary;
  const avatar = (size: number, letter: string, color: string) => (
    <div style={{position: 'relative', width: size, height: size, flexShrink: 0}}>
      <svg width={size} height={size} style={{overflow: 'visible'}}>
        <path d={shapePath((full ? 'cookie9' : 'circle') as ShapeName, size / 2, size / 2, size, full ? frame : 0)} fill={color} />
      </svg>
      <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff',
        fontSize: size * 0.42, fontVariationSettings: `'wght' 700`}}>{letter}</div>
    </div>
  );
  const bubble = (out: boolean, text: string) => {
    const big = w * 0.07;
    const radius = full
      ? `${big}px ${big}px ${out ? 3 * u : big}px ${out ? big : 3 * u}px`
      : `${big * 0.8}px ${big * 0.8}px ${out ? 0 : big * 0.8}px ${out ? big * 0.8 : 0}px`;
    return (
      <div style={{alignSelf: out ? 'flex-end' : 'flex-start', padding: `${w * 0.025}px ${w * 0.045}px`, borderRadius: radius, fontSize: w * 0.055,
        background: out ? (tg ? '#E3F5D6' : r.primaryContainer) : (tg ? '#FFFFFF' : r.surfaceContainerHighest),
        color: out ? (tg ? '#1A1A1A' : r.onPrimaryContainer) : (tg ? '#1A1A1A' : r.onSurface), maxWidth: '80%'}}>{text}</div>
    );
  };
  const bg = tg ? '#DCE6D2' : full ? r.surfaceContainerLow : r.surfaceContainer;
  return (
    <div style={{width: w, height: h, borderRadius: w * 0.09, overflow: 'hidden', position: 'relative', fontFamily: FONT,
      background: bg, boxShadow: `0 ${16 * u}px ${44 * u}px rgba(0,0,0,${0.12 + 0.18 * selected})`,
      outline: `${6 * u * selected}px solid ${mix(r.surfaceContainerLow, r.primary, selected)}`, outlineOffset: 6 * u}}>
      {full ? (
        <svg width={w} height={h} style={{position: 'absolute', inset: 0}}>
          {Array.from({length: 9}, (_, i) => {
            const s = (w * 0.16) * (0.7 + (i % 3) * 0.25);
            const x = ((i * 37) % 100) / 100 * w, y = ((i * 61 + 13) % 100) / 100 * h * 0.8 + h * 0.1;
            return <path key={i} d={shapePath((['flower', 'cookie9', 'softBurst'] as ShapeName[])[i % 3], x, y, s, frame + i * 30)}
              fill={[r.primaryContainer, r.secondaryContainer, r.tertiaryContainer][i % 3]} opacity={0.7} />;
          })}
        </svg>
      ) : null}
      <div style={{position: 'absolute', left: 0, right: 0, top: 0, height: h * 0.14, background: tg ? '#517DA2' : full ? 'transparent' : r.surfaceContainerHigh,
        display: 'flex', alignItems: 'center', gap: w * 0.04, padding: `0 ${w * 0.05}px`}}>
        {avatar(w * 0.13, 'А', tg ? '#E17076' : r.tertiary)}
        <div style={{fontSize: w * 0.06, color: tg ? '#fff' : r.onSurface, fontVariationSettings: `'wght' 700`}}>Аня</div>
      </div>
      <div style={{position: 'absolute', left: w * 0.05, right: w * 0.05, top: h * 0.2, display: 'flex', flexDirection: 'column', gap: w * 0.03}}>
        {bubble(false, 'Привет! 👋')}
        {bubble(true, 'Смотри, новый стиль')}
        {bubble(false, 'Красиво!')}
      </div>
      <div style={{position: 'absolute', left: w * 0.06, right: w * 0.06, bottom: h * 0.17, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: `${w * 0.03}px ${w * 0.045}px`, borderRadius: full ? w * 0.08 : half ? w * 0.05 : w * 0.02, background: tg ? '#fff' : r.surface}}>
        <span style={{fontSize: w * 0.05, color: tg ? '#1A1A1A' : r.onSurface}}>Тёмная тема</span>
        {tg ? (
          <svg width={w * 0.16} height={w * 0.09}>
            <rect x={0} y={w * 0.025} width={w * 0.16} height={w * 0.04} rx={w * 0.02} fill="#A0C8EE" />
            <circle cx={w * 0.12} cy={w * 0.045} r={w * 0.04} fill={tgBlue} />
          </svg>
        ) : <Switch on={1} r={r} scale={w * 0.0028} />}
      </div>
      <div style={{position: 'absolute', left: w * 0.05, right: w * 0.05, bottom: h * 0.04, height: h * 0.085, borderRadius: full ? h * 0.05 : w * 0.03,
        background: tg ? '#fff' : r.surfaceContainerHighest, display: 'flex', alignItems: 'center', padding: `0 ${w * 0.05}px`,
        fontSize: w * 0.05, color: tg ? '#999' : r.onSurfaceVariant}}>
        Сообщение
        <div style={{marginLeft: 'auto', width: h * 0.06, height: h * 0.06, borderRadius: full ? h * 0.02 : '50%', background: accent}} />
      </div>
    </div>
  );
};

const Styles: React.FC = () => {
  const {frame, fps, sec, vertical, u} = useScene();
  const r = roles(CONFIG.seed);
  const levels: [Level, string][] = [['telegram', 'Как в Telegram'], ['half', 'Наполовину M3'], ['full', 'Полностью M3']];
  const w = (vertical ? 290 : 215) * u;
  const pickAt = [sec(1.0), sec(2.4), sec(3.8)];
  const active = frame < pickAt[1] ? 0 : frame < pickAt[2] ? 1 : 2;
  return (
    <Stage r={r} title={'Стиль\nинтерфейса'} sub="Выберите, сколько Material 3 вам нужно" seed={47}>
      <div style={{display: 'flex', gap: (vertical ? 28 : 36) * u, alignItems: 'flex-end'}}>
        {levels.map(([level, title], i) => {
          const k = sp(frame, fps, 6 + i * 6, SPRING.spatialFast);
          const sel = i === active ? sp(frame, fps, pickAt[i], SPRING.spatialFast) : 0;
          return (
            <div key={level} style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 22 * u,
              transform: `translateY(${(1 - k) * 220 * u - sel * 30 * u}px) scale(${0.94 + 0.08 * sel})`, opacity: Math.min(1, k * 2)}}>
              <MiniChat level={level} r={r} w={w} selected={Math.min(1, sel)} />
              <div style={{display: 'flex', alignItems: 'center', gap: 12 * u, fontFamily: FONT, fontSize: 32 * u, color: r.onSurface,
                fontVariationSettings: `'wght' ${i === active ? 760 : 520}`}}>
                <svg width={34 * u} height={34 * u}>
                  <circle cx={17 * u} cy={17 * u} r={14 * u} fill="none" stroke={i === active ? r.primary : r.outline} strokeWidth={3 * u} />
                  <circle cx={17 * u} cy={17 * u} r={8 * u * Math.min(1, sel)} fill={r.primary} />
                </svg>
                {title}
              </div>
            </div>
          );
        })}
      </div>
    </Stage>
  );
};

const Intro017: React.FC = () => (
  <IntroText.Provider value={{version: '0.1.7', subtitle: 'Telegram в стиле Material 3'}}>
    <Intro />
  </IntroText.Provider>
);

export const PROMO017: SceneDef[] = [
  {key: 'intro', seconds: 4.5, C: Intro017, sfx: [[0, 'poof'], [0.3, 'poof'], [0.6, 'poof'], [1.0, 'pop'], [1.7, 'ding']]},
  {key: 'styles', seconds: 6, C: Styles, sfx: [[0.2, 'pop'], [0.4, 'pop'], [0.6, 'pop'], [1.0, 'click'], [2.4, 'click'], [3.8, 'click'], [4.0, 'ding']]},
  {key: 'ghost', seconds: 5, C: Ghost, sfx: [[1.3, 'click'], [1.6, 'tick'], [1.8, 'tick'], [2.0, 'tick']]},
  {key: 'deleted', seconds: 5, C: Deleted, sfx: [[1.0, 'tick'], [1.4, 'pop'], [2.9, 'tick'], [3.15, 'tick'], [3.4, 'tick']]},
  {key: 'hidden', seconds: 4.5, C: Hidden, sfx: [[1.2, 'poof'], [2.4, 'pop'], [3.3, 'ding']]},
  {key: 'templates', seconds: 5, C: Templates, sfx: [[1.0, 'click'], [2.2, 'tick'], [2.7, 'poof']]},
  {key: 'wallpapers', seconds: 5, C: Wallpapers, sfx: [[0.2, 'pop'], [0.4, 'pop'], [0.6, 'pop'], [2.4, 'tick'], [3.0, 'tick'], [3.6, 'tick']]},
  {key: 'profile', seconds: 4.5, C: Profile, sfx: [[0.8, 'poof'], [1.2, 'tick'], [2.4, 'poof'], [3.0, 'click']]},
  {key: 'outro', seconds: 5, C: Outro, sfx: [[0.1, 'pop'], [1.1, 'ding']]},
];

export const PROMO017_FRAMES = sceneFramesOf(PROMO017);
export const Promo017Track = makeTrack(PROMO017, 'music_promo017.m4a');

