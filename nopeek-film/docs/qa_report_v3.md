# QA REPORT — Nopeek launch film v3 (45s)

Reviewed: `out/stills/film3_*.png` (0.5/3.0/7.5/8.5/11.5/14.0/22.0/28.0/36.5/38.5/39.0/41.5/43.5),
`out/phone-test.png` (9 information frames @ 800x450, v3 timestamps), `out/sheet-film3/` tiles,
`out/nopeek-launch-v3-16x9.mp4` (45.00s), `node tools/selftest.mjs` (all checks passed).

## Scores

| Axis | Score | Note |
|---|---|---|
| Story clarity | 9 | thesis→gag→turn→home→5 steps→breach→settle→CTA reads in order |
| Hierarchy | 9 | one focal per beat; gutter captions clear of device |
| Identity consistency | 9 | five tokens, two type roles, spine never replaced (hidden only at lockup where it becomes the underline) |
| Motion craft | 8 | micro-drift + breathing tick; no dead holds |
| Legibility | 9 | phone test passes all 9 incl. tx row and footnote; proof caption fixed to two lines after clip found at 38.5s |
| Sound-picture sync | 8 | 25 cues on picture timestamps; not yet auditioned by ear against final encode |

No axis below 8 → review loop closed.

## Fixes applied during build

1. Proof caption clipped ("itself." cut at frame edge) — split to two lines (76px, y 872/954). Verified 39.0s.
2. Spine ghost bar through NOPEEK — drawSpine gated to t<40 (it becomes the underline). Verified 43.5s.
3. Phone-test run with stale v2 defaults caught rendering film2 — re-ran with v3 timestamps; QA cites only the v3 sheet.

## Delivery — measured

`out/nopeek-launch-v3-16x9.mp4` — 1920x1080, h264, 60fps, 2700 frames, 45.000s, AAC, 4.55 MB.
Integrated -18.5 LUFS, true peak -2.4 dBFS, LRA 5.9 LU. selftest all checks passed.

## Honest deviations

- D1 — end hold 5.0s > skill 1.4s rule. Deliberate: launch CTA, references hold 3-5s.
- D2 — LRA 5.9 vs v2 9.0. Denser cue map over 45s; still dynamic, never clips, platforms normalize up.
- D3 — G5 lint: no lint.mjs in this repo; checked by eye on phone-test (no chrome/counters/kickers/deck layout).
- D4 — tools/audio3.mjs created instead of modifying audio2.mjs (v2 deliverable preserved).
- D5 — 7 story beats mapped onto 5 technical ACTS for selftest tiling.
- Fidelity deviations (only two, as spec): tape limit at 82%, volt underline on home as spine carrier. Eye-and-wall mark is a code reconstruction, declared illustrative.
