/**
 * Material 3 tokens: color roles generated from a seed with material-color-utilities, the M3
 * Expressive spring tokens, and the type ramp. Every color in the video comes from here.
 */
import {argbFromHex, hexFromArgb, Hct, MaterialDynamicColors as M, SchemeTonalSpot, SchemeVibrant} from '@material/material-color-utilities';
import {spring, SpringConfig} from 'remotion';
import {CONFIG} from './config';

export type Roles = {
  primary: string; onPrimary: string; primaryContainer: string; onPrimaryContainer: string;
  secondary: string; onSecondary: string; secondaryContainer: string; onSecondaryContainer: string;
  tertiary: string; onTertiary: string; tertiaryContainer: string; onTertiaryContainer: string;
  error: string; surface: string; onSurface: string; onSurfaceVariant: string;
  surfaceContainerLowest: string; surfaceContainerLow: string; surfaceContainer: string;
  surfaceContainerHigh: string; surfaceContainerHighest: string; outline: string; outlineVariant: string;
  inverseSurface: string; inverseOnSurface: string; inversePrimary: string;
};

const cache = new Map<string, Roles>();

/** All roles of the scheme for a seed. `amoled` turns the dark surfaces pure black. */
export const roles = (seed: string = CONFIG.seed, dark = false, amoled = false, vibrant = false): Roles => {
  const key = `${seed}|${dark}|${amoled}|${vibrant}`;
  const hit = cache.get(key);
  if (hit) return hit;
  const hct = Hct.fromInt(argbFromHex(seed));
  const s = vibrant ? new SchemeVibrant(hct, dark, 0) : new SchemeTonalSpot(hct, dark, 0);
  const c = (d: {getArgb: (x: typeof s) => number}) => hexFromArgb(d.getArgb(s));
  const r: Roles = {
    primary: c(M.primary), onPrimary: c(M.onPrimary), primaryContainer: c(M.primaryContainer), onPrimaryContainer: c(M.onPrimaryContainer),
    secondary: c(M.secondary), onSecondary: c(M.onSecondary), secondaryContainer: c(M.secondaryContainer), onSecondaryContainer: c(M.onSecondaryContainer),
    tertiary: c(M.tertiary), onTertiary: c(M.onTertiary), tertiaryContainer: c(M.tertiaryContainer), onTertiaryContainer: c(M.onTertiaryContainer),
    error: c(M.error), surface: c(M.surface), onSurface: c(M.onSurface), onSurfaceVariant: c(M.onSurfaceVariant),
    surfaceContainerLowest: c(M.surfaceContainerLowest), surfaceContainerLow: c(M.surfaceContainerLow), surfaceContainer: c(M.surfaceContainer),
    surfaceContainerHigh: c(M.surfaceContainerHigh), surfaceContainerHighest: c(M.surfaceContainerHighest),
    outline: c(M.outline), outlineVariant: c(M.outlineVariant),
    inverseSurface: c(M.inverseSurface), inverseOnSurface: c(M.inverseOnSurface), inversePrimary: c(M.inversePrimary),
  };
  if (amoled && dark) {
    r.surface = '#000000';
    r.surfaceContainerLowest = '#000000';
    r.surfaceContainerLow = '#0A0A0A';
    r.surfaceContainer = '#111111';
  }
  cache.set(key, r);
  return r;
};

/** Blend two hex colors (both from roles) for in-frame recoloring. */
export const mix = (a: string, b: string, t: number): string => {
  const pa = parseInt(a.slice(1), 16), pb = parseInt(b.slice(1), 16);
  const ch = (p: number, sh: number) => (p >> sh) & 255;
  const k = Math.max(0, Math.min(1, t));
  const out = [16, 8, 0].map((sh) => Math.round(ch(pa, sh) + (ch(pb, sh) - ch(pa, sh)) * k));
  return '#' + out.map((v) => v.toString(16).padStart(2, '0')).join('');
};

export const mixRoles = (a: Roles, b: Roles, t: number): Roles => {
  const o = {} as Roles;
  (Object.keys(a) as (keyof Roles)[]).forEach((k) => (o[k] = mix(a[k], b[k], t)));
  return o;
};

/**
 * M3 Expressive motion springs (from the M3 motion tokens): damping ratio and stiffness,
 * converted to Remotion's mass/stiffness/damping (damping = ratio * 2 * sqrt(stiffness * mass)).
 */
const m3 = (ratio: number, stiffness: number): Partial<SpringConfig> => ({
  mass: 1, stiffness, damping: ratio * 2 * Math.sqrt(stiffness), overshootClamping: false,
});
export const SPRING = {
  spatialFast: m3(0.6, 800),
  spatial: m3(0.8, 380),
  spatialSlow: m3(0.8, 200),
  effectsFast: m3(1, 3800),
  effects: m3(1, 1600),
  effectsSlow: m3(1, 800),
};

/** Spring progress 0..1 (with overshoot) starting at frame `delay`. */
export const sp = (frame: number, fps: number, delay = 0, config = SPRING.spatial, durationInFrames?: number) =>
  spring({frame: frame - delay, fps, config, durationInFrames});

export const FONT = 'YiliFlex';
