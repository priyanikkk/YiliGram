/**
 * Material 3 Expressive shapes as polar radius functions r(θ) in 0..1. Every shape is sampled at
 * the same N angles, so morphing is a per-angle interpolation of radii: smooth, no point matching.
 */
export type ShapeName =
  | 'circle' | 'cookie4' | 'cookie6' | 'cookie9' | 'cookie12' | 'clover4' | 'clover8'
  | 'sunny' | 'verySunny' | 'softBurst' | 'burst' | 'flower' | 'puffy'
  | 'softSquare' | 'pill' | 'pentagon' | 'triangle' | 'gem' | 'oval';

const N = 240;
const TAU = Math.PI * 2;

const scallop = (n: number, depth: number) => (t: number) => 1 - depth * (1 - Math.cos(n * t)) / 2;
const superellipse = (n: number, a = 1, b = 1) => (t: number) =>
  1 / Math.pow(Math.pow(Math.abs(Math.cos(t)) / a, n) + Math.pow(Math.abs(Math.sin(t)) / b, n), 1 / n);
const polygon = (sides: number, rounding: number) => (t: number) => {
  const seg = TAU / sides;
  const local = ((t % seg) + seg) % seg - seg / 2;
  const r = Math.cos(Math.PI / sides) / Math.cos(local);
  return r * (1 - rounding) + rounding * Math.cos(Math.PI / sides);
};

const FN: Record<ShapeName, (t: number) => number> = {
  circle: () => 1,
  oval: superellipse(2, 1, 0.78),
  cookie4: scallop(4, 0.2),
  cookie6: scallop(6, 0.16),
  cookie9: scallop(9, 0.12),
  cookie12: scallop(12, 0.1),
  clover4: (t) => 0.62 + 0.38 * Math.pow(Math.abs(Math.cos(2 * t)), 0.55),
  clover8: (t) => 0.7 + 0.3 * Math.pow(Math.abs(Math.cos(4 * t)), 0.6),
  sunny: (t) => 0.84 + 0.16 * Math.pow((1 + Math.cos(8 * t)) / 2, 1.4),
  verySunny: (t) => 0.72 + 0.28 * Math.pow((1 + Math.cos(8 * t)) / 2, 2.2),
  softBurst: (t) => 0.82 + 0.18 * Math.pow((1 + Math.cos(12 * t)) / 2, 1.6),
  burst: (t) => 0.7 + 0.3 * Math.pow((1 + Math.cos(12 * t)) / 2, 3),
  flower: (t) => 0.66 + 0.34 * Math.pow((1 + Math.cos(8 * t)) / 2, 0.8),
  puffy: scallop(10, 0.08),
  softSquare: superellipse(4.2),
  pill: superellipse(6, 1, 0.56),
  pentagon: polygon(5, 0.22),
  triangle: polygon(3, 0.3),
  gem: polygon(6, 0.18),
};

/** Radii of a shape at the N sample angles, normalized so the shape's max radius is 1. */
const radiiCache = new Map<ShapeName, number[]>();
export const radii = (name: ShapeName): number[] => {
  const hit = radiiCache.get(name);
  if (hit) return hit;
  const f = FN[name];
  const rs = Array.from({length: N}, (_, i) => f((i / N) * TAU - Math.PI / 2));
  const max = Math.max(...rs);
  const out = rs.map((r) => r / max);
  radiiCache.set(name, out);
  return out;
};

/** SVG path of a morph between two shapes. `t` may overshoot (springs): radii stay positive. */
export const morphPath = (
  from: ShapeName, to: ShapeName, t: number, cx: number, cy: number, size: number, rotationDeg = 0,
): string => {
  const a = radii(from), b = radii(to);
  const rot = (rotationDeg * Math.PI) / 180;
  const R = size / 2;
  let d = '';
  for (let i = 0; i < N; i++) {
    const r = Math.max(0.05, a[i] + (b[i] - a[i]) * t) * R;
    const ang = (i / N) * TAU - Math.PI / 2 + rot;
    const x = cx + r * Math.cos(ang), y = cy + r * Math.sin(ang);
    d += (i === 0 ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1);
  }
  return d + 'Z';
};

export const shapePath = (name: ShapeName, cx: number, cy: number, size: number, rotationDeg = 0) =>
  morphPath(name, name, 0, cx, cy, size, rotationDeg);

/** The sequence of the M3 Expressive loading indicator. */
export const LOADING_SEQUENCE: ShapeName[] = ['softBurst', 'cookie9', 'pentagon', 'pill', 'sunny', 'cookie4', 'oval'];
