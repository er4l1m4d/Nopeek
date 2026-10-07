// audio3.mjs — the v3 soundtrack: soft, sparse, cut to the 45s picture clock.
// 100 BPM half-time (beat 0.6s, bar 2.4s), 45.000s, 44.1kHz stereo.
// 25 cues; breach is the loudest single moment and still soft; no clap.
// Seeded noise: the mix is deterministic like the frames.
//   node tools/audio3.mjs            -> out/mix.wav  (FILM=film3.html chain copies it)
import { writeFileSync } from 'node:fs';
import path from 'node:path';
import { ROOT, ensureDir } from './lib.mjs';

const SR = 44100, DUR = Number(process.env.DUR || 45.0), N = Math.ceil(SR * DUR);
const BPM = 100, SPB = 60 / BPM;
const L = new Float64Array(N), R = new Float64Array(N);
const TAU = Math.PI * 2;
const beat = n => n * SPB;

let seed = 42;
function rnd() { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; }
function add(t0, dur, fn) {
  const i0 = Math.max(0, Math.floor(t0 * SR)), i1 = Math.min(N, Math.floor((t0 + dur) * SR));
  for (let i = i0; i < i1; i++) {
    const s = (i - i0) / SR, [a, b] = fn(s, 1 - s / dur);
    L[i] += a; R[i] += b;
  }
}
const env = (s, atk, dec) => (s < atk ? s / atk : Math.exp(-(s - atk) / dec));
const noise = () => rnd() * 2 - 1;
function kick(t, g) {
  g = g == null ? 1 : g;
  add(t, 0.36, s => {
    const f = 42 + 70 * Math.exp(-s * 30);
    const e = Math.exp(-s * 9) * (1 - Math.exp(-s * 700));
    const v = Math.sin(TAU * f * s) * e * g;
    return [v, v];
  });
}
function sub(t, hz, dur, g) {
  dur = dur == null ? 0.5 : dur; g = g == null ? 0.5 : g;
  add(t, dur, s => {
    const e = env(s, 0.010, dur * 0.45) * Math.exp(-s / (dur * 0.7));
    const v = (Math.sin(TAU * hz * s) + 0.18 * Math.sin(TAU * hz * 2 * s)) * e * g;
    return [v, v];
  });
}
function brush(t, g, dur) {
  g = g == null ? 0.06 : g; dur = dur == null ? 0.06 : dur;
  let lp = 0;
  add(t, dur, s => {
    lp += (noise() - lp) * 0.22;
    const e = Math.exp(-s / (dur * 0.4)) * g;
    const v = lp * e;
    return [v * 0.9, v * 1.1];
  });
}
function blip(t, hz, dur, g) {
  hz = hz == null ? 900 : hz; dur = dur == null ? 0.06 : dur; g = g == null ? 0.3 : g;
  add(t, dur, s => {
    const e = Math.exp(-s / (dur * 0.28));
    const v = Math.sin(TAU * hz * s * (1 - s * 0.35)) * e * g;
    return [v, v];
  });
}
function click(t, g) {
  g = g == null ? 0.2 : g;
  add(t, 0.04, s => {
    const v = (noise() * Math.exp(-s / 0.005) + Math.sin(TAU * 1150 * s) * Math.exp(-s / 0.010)) * g;
    return [v, v * 0.9];
  });
}
function pad(t, hzs, dur, g) {
  g = g == null ? 0.1 : g;
  add(t, dur, s => {
    const e = (1 - Math.exp(-s / (dur * 0.18))) * Math.exp(-Math.max(0, s - dur * 0.5) / (dur * 0.4));
    let v = 0;
    for (const h of hzs) v += Math.sin(TAU * h * s + Math.sin(TAU * 0.3 * s) * 0.5) / hzs.length;
    return [v * e * g, v * e * g * 0.94];
  });
}
function whoosh(t, dur, lo, hi, g) {
  dur = dur == null ? 0.4 : dur; lo = lo == null ? 300 : lo; hi = hi == null ? 2600 : hi; g = g == null ? 0.12 : g;
  let lp = 0;
  add(t, dur, s => {
    const u = s / dur, bw = lo * Math.pow(hi / lo, u);
    lp += (noise() - lp) * Math.min(1, bw / SR * TAU);
    const band = (noise() - lp) * Math.sin(Math.PI * u);
    const v = band * g * (1 - Math.pow(Math.abs(2 * u - 1), 2));
    return [v * (1 - u * 0.3), v * (0.7 + u * 0.3)];
  });
}
function impact(t, g) { g = g == null ? 1 : g; kick(t, g); whoosh(t, 0.26, 220, 3800, g * 0.22); }
function bell(t, hz, g, dur) {
  g = g == null ? 0.16 : g; dur = dur == null ? 1.4 : dur;
  add(t, dur, s => {
    let v = 0;
    for (const [m, a] of [[1, 1], [2.0, 0.38], [2.99, 0.18], [4.2, 0.08]])
      v += Math.sin(TAU * hz * m * s) * a * Math.exp(-s * (1.8 + m * 0.6));
    return [v * g, v * g * 0.86];
  });
}

/* music: thin where type must be read; pulse builds under the how; lockup to silence */
const AM = [110, 130.81, 164.81, 196, 220, 261.63, 329.63];
for (let b = 0; b < 120 && beat(b) < DUR; b++) {
  const t = beat(b), inBar = b % 4;
  const hook = t < 11, home = t >= 11 && t < 16, how = t >= 16 && t < 34, proof = t >= 34 && t < 40;
  if (hook) {
    if (inBar === 0 && Math.floor(b / 4) % 2 === 0) kick(t, 0.34);
    if (b === 0) pad(t, [AM[0], AM[2], AM[3]], 11.0, 0.10);
  } else if (home) {
    if (inBar === 0) kick(t, 0.42);
    if (inBar === 2) sub(t, AM[0], 0.5, 0.20);
  } else if (how) {
    if (inBar === 0 || inBar === 2) kick(t, 0.46);
    if (inBar === 1 || inBar === 3) brush(t, 0.05);
    if (b % 8 === 0) pad(t, [AM[1], AM[3], AM[4]], 4.8, 0.08);
  } else if (proof) {
    if (inBar === 0) kick(t, 0.50);
    if (b % 8 === 4) pad(t, [AM[0], AM[2], AM[5]], 4.8, 0.09);
  }
}

/* sound design: only meaning gets sound; breach is loudest, still soft */
const SFX = [
  [0.30, t => whoosh(t, 0.5, 500, 3000, 0.09)],
  [0.80, t => blip(t, 300, 0.10, 0.10)],
  [3.50, t => blip(t, 285, 0.10, 0.09)],
  [4.10, t => blip(t, 900, 0.05, 0.07)],
  [4.24, t => { blip(t, 1200, 0.05, 0.09); blip(t + 0.06, 1500, 0.05, 0.08); }],
  [6.30, t => { click(t, 0.22); blip(t, 240, 0.16, 0.11); }],
  [6.60, t => blip(t, 380, 0.12, 0.10)],
  [7.40, t => kick(t, 0.40)],
  [9.60, t => { blip(t, 700, 0.09, 0.12); bell(t + 0.05, 880, 0.07, 0.8); }],
  [10.60, t => whoosh(t, 0.8, 200, 2200, 0.11)],
  [13.60, t => blip(t, 620, 0.09, 0.09)],
  [16.80, t => { click(t, 0.24); blip(t + 0.15, 1450, 0.07, 0.11); }],
  [19.80, t => { click(t, 0.24); blip(t + 0.15, 1450, 0.07, 0.11); }],
  [23.40, t => { blip(t, 420, 0.5, 0.05); blip(t + 0.16, 520, 0.4, 0.045); }],
  [27.00, t => { for (let i = 0; i < 8; i++) click(t + i * 0.125 + (i % 3) * 0.008, 0.13 + (i % 3) * 0.03); }],
  [29.20, t => bell(t, 1180, 0.09, 0.5)],
  [32.70, t => click(t, 0.30)],
  [33.20, t => { whoosh(t, 0.5, 180, 4200, 0.18); kick(t, 0.55); }],
  [34.20, t => bell(t, 440, 0.12, 1.0)],
  [37.00, t => { impact(t, 0.45); sub(t, 180, 0.3, 0.12); }],
  [38.20, t => { bell(t, 880, 0.18, 2.0); bell(t + 0.09, 1320, 0.10, 1.6); }],
  [38.40, t => { for (let i = 0; i < 5; i++) blip(t + i * 0.30, 700 + i * 110, 0.05, 0.06); }],
  [41.20, t => { whoosh(t, 0.4, 400, 6000, 0.13); bell(t + 0.1, 1760, 0.07, 0.9); }],
  [42.70, t => blip(t, 560, 0.10, 0.10)],
  [43.20, t => click(t, 0.08)],
];
for (const [t, fn] of SFX) fn(t);

/* mix: gentle tanh knee, one scale to ~-16.5 LUFS with -4 dBFS ceiling */
const T = 0.30;
let sum = 0, peak = 0;
for (let i = 0; i < N; i++) {
  L[i] = T * Math.tanh(L[i] / T);
  R[i] = T * Math.tanh(R[i] / T);
  sum += L[i] * L[i] + R[i] * R[i];
  peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
}
const rms = Math.sqrt(sum / (2 * N));
const g = Math.min(0.117 / (rms || 1), 0.63 / (peak || 1));
const fade = Math.floor(SR * 1.2);
const pcm = Buffer.alloc(N * 4);
for (let i = 0; i < N; i++) {
  let l = L[i] * g, r = R[i] * g;
  if (i < 220) { const k = i / 220; l *= k; r *= k; }
  const j = N - i; if (j < fade) { const k = j / fade; l *= k; r *= k; }
  pcm.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(l * 32767))), i * 4);
  pcm.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(r * 32767))), i * 4 + 2);
}
const hdr = Buffer.alloc(44);
hdr.write('RIFF', 0); hdr.writeUInt32LE(36 + pcm.length, 4); hdr.write('WAVE', 8);
hdr.write('fmt ', 12); hdr.writeUInt32LE(16, 16); hdr.writeUInt16LE(1, 20);
hdr.writeUInt16LE(2, 22); hdr.writeUInt32LE(SR, 24); hdr.writeUInt32LE(SR * 4, 28);
hdr.writeUInt16LE(4, 32); hdr.writeUInt16LE(16, 34);
hdr.write('data', 36); hdr.writeUInt32LE(pcm.length, 40);

ensureDir(path.join(ROOT, 'out'));
writeFileSync(path.join(ROOT, 'out', 'mix.wav'), Buffer.concat([hdr, pcm]));
console.log(`mix.wav (v3)  ${(pcm.length / 1048576).toFixed(1)} MB  ${DUR.toFixed(2)}s  ${BPM} BPM half-time  peak ${peak.toFixed(3)} -> rms ${rms.toFixed(4)} trim ${g.toFixed(3)}`);
console.log(`sound-design cues: ${SFX.length}`);
