# Nopeek v3 Launch Film — Design Spec (approved 2026-10-07)

## 1. Intent (brainstorming understanding)
- **Outcome:** 45s 16:9 meticulous product launch film, code-rendered, readable silent on phone.
- **For:** General screen-time crowd in feed. No crypto jargon in body.
- **Success:** Viewer can retell: free resolutions break → put $5 vs friend → phone counts → money settles → get app. Pacing never rushes reading. Sound soft, follows picture. Every screen matches shipped Kotlin.
- **Assumptions confirmed by user:** 45s master, kinetic-type only, hybrid crypto (hide till settle), keep 6 beats, Nopeek branding locked, CTA "Get Nopeek now", TikTok 30m vs alex.skr $5/$10, pacing follows references, synth-only sound, softer thesis hook.

## 2. Logline
Resolutions are free. So is breaking them. Nopeek puts $5 of your own money on it, against a friend, on your screen time.

## 3. Film shape (6 beats → 45s)
| Act | Time | Beats | Copy (locked) |
|---|---|---|---|
| Problem | 0.0-7.0 | hairline draws, tape gag, OVER sits | RESOLUTIONS ARE FREE. / SO IS BREAKING THEM. |
| Table moment | 7.0-11.0 | volt $5 flood → pull-back | So put $5 on it. |
| Intro app | 11.0-16.0 | HomeScreen live | This is a duel. |
| What it does | 16.0-20.5 | DuelScreen live, clocks count | The phone counts. |
| How it works | 20.5-34.0 | 5 steps x ~2.7s: Pick/Limit/Stake/Rival/Lock | Pick it. / Limit it. / Stake it. / Name rival. / Lock it. |
| Proof + CTA | 34.0-40.0 | breach coral + WON $10.00 + tx 5Hq2…tK9x | The money settles itself. |
| Lockup | 40.0-45.0 | eye-and-wall, hairline home | NOPEEK / Get Nopeek now. / Android first · Test stakes today |

Reading law: `hold = words*0.35 + 1.5s`, max 6 words/statement, result visible >=1s (Stripe rule). Reference pace: 3-4s per feature.

## 4. Visual system (from tokens.json + style_guide.md)
- Pitch #10261B ground, chalk #F1F0E6, volt #D4FF00 = you, sky #8FD3FF = opponent, coral #FF6A4D = over, amber support.
- Type: Big Shoulders Display Black 900 statements (168/88/60/48/36/28), Schibsted Grotesk UI only (16/14/12).
- Flat 2D, no fades, no glow, no particles. Radius 6/14/pill. One hero per frame.
- Spine: hairline → tape (limit at 82%) → stepper → tape → volt underline. Never replaced, only reshaped. Morph or flood handoffs, no cuts.
- Camera: one move at a time, log-space zoom, lands on downbeat. Statements in screen space.
- Fidelity map: HomeScreen.kt / CreateScreen.kt (Which app?/How long a day?/How long does it run?/What's on the line?/Who are you facing?/Review and approve.) / DuelScreen.kt ($10 pot, 18:42 SAFE, 27:10 AT RISK, 8:28 ahead, 11:18 left, Lock TikTok) / Cards.kt SharedTape / WonScreen.kt ($10.00, Your $5.00 back plus alex.skr's $5.00, Transaction 5Hq2…tK9x) / ic_launcher_foreground.xml eye-and-wall.

## 5. Motion (motionmaxxing + Emil, free-only)
- appEase cubic-bezier(0.2,0.8,0.2,1) for masks/camera/floods. spr() closed-form springs for markers (w16-18 z0.6-0.7), cursor (w6 z0.95), re-shape near-critical. trk() sum-of-springs for multi-target. Type never springs.
- Entries ease-out from oversize (not scale 0), exits ease-in shrink. UI 1300/1600/2200/2000ms tokens honored. Micro-drift ~1px/frame for alive holds + breathing limit tick.
- Gates G0-G5: render exists, proof >=0.04H fragment >=0.55W, no >3 flat frames (except deliberate floods), end hold 4-5s (documented D2 deviation), one hero, no chrome via lint.mjs.

## 6. Sound (soft, synth-only, no ElevenLabs)
- 100 BPM half-time (kick 1+3 only), warm pad + sub pulse, bell-and-wood accents, no clap.
- ~22 cues: chalk thuds, OVER tick, 5 steps press+result, breach soft impact (loudest, -3dB under v1), count ticks, wipe, settle. Silence default, low-passed.
- Duck pad -4dB under type. Target -18.1 LUFS / -2.4 dBTP / LRA 9.0. tools/audio2.mjs pattern reused.

## 7. Build contract
- `nopeek-film/film3.html` + `window.seek(t)` pure (no timers/random/transitions). FPS 60 DUR 45.0 TOTAL 2700.
- Tools reuse: lib.mjs / render.mjs / encode.mjs / sheet.mjs / phone.mjs / still.mjs / selftest.mjs (24/24 PASS required).
- Deliverables: out/nopeek-launch-v3-16x9.mp4, contact sheet 1/2s, phone test 800x450, 4 proof stills (hook/home/breach/CTA), NOTE.md with G0-G5 numbers + frame sentences.
- Deviations (only 2): tape limit 82%, volt underline on home as spine carrier.

## 8. Risks
- 45s@60fps = 2700 frames ~7min render, clear out/frames first.
- 5-step act is densest; if retention says trim, cut window-result hold first.
- "Get Nopeek now" implies availability; true only as APK devnet — footnote covers it. If store slips, swap to "Duels open soon".
- UI reconstruction at spec, not capture; beats swap without touching rest.

## 9. What v3 retires vs v1/v2
- Retires v1 8-events-in-6s hook, 1.5s steps, 10s race, hidden tx row, 4-state flow, 62-cue map, black first frames.
- Keeps v2 reading law, spine rule, flood grammar, breach downbeat, eye lockup, deterministic pipeline.
- Compresses v2 72s → 45s by 5 steps (not 6 separate full beats) + tighter pre/post holds per references.
