# Nopeek — Handoff 2.4 Revamp Implementation Checklist

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. (Execution method chosen by the user: **native** — the agent executes here, task by task, with evidence per task; the user reviews at phase gates.) Where an independent review is wanted, superpowers:subagent-driven-development may be used for that gate. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Nopeek Android app and its spine to the Handoff 2.4 design — every screen, component, motion and money path — through 12 gated phases.

**Architecture:** Ordering 1 (user-decided): tokens → shell → contract v2 → schema/oracle → app data layer → components → screens → motion pass → device pass → extras → close-out. Screens wait for the money spine (build-ops §4's rule) but the design-system layer lands first so the redesign is visible early. Screens are replaced one at a time and their predecessors deleted in the same task (user-decided cutover).

**Tech Stack:** Kotlin 2.2.21 / Jetpack Compose (BOM 2024.12.01) / Room 2.7.1 / WorkManager 2.10.1 / navigation-compose 2.9.2 · Anchor-era Solana program (Playground CLI 0.29) · Supabase Postgres + Deno edge function · Node test suites.

**Spec:** `docs/superpowers/specs/2026-10-18-nopeek-redesign-design.md` — the plan argues from the spec and the handoff; executors read all three:
1. this checklist, 2. the spec, 3. `nopeek new design handoff/nopeek-master-handoff.html` (§ cited throughout; it wins on anything user-visible) + `nopeek-build-ops.html` (gates).

**Scale, honestly:** ~105 tasks. One task = one working sitting. Every task ends with its "complete when" list ticked and evidence attached. The user reviews at phase gates, not every task.

---

## Global Constraints

Every task's requirements implicitly include this section. Exact values copied from the handoff/build-ops.

**Build pins (do not drift):** Gradle 9.7.1 · AGP 8.6.1 · Kotlin 2.2.21 (Compose compiler must match exactly) · KSP 2.2.21-2.0.5 · JDK 17, auto-download off · compile 35 / min 26 / target 35 · Compose BOM 2024.12.01 · `-Xmx2048m` · navigation 2.9.2 · Room 2.7.1 · WorkManager 2.10.1 · Solana clientlib 2.1.1 · Robolectric 4.14.1 · `ui-test-manifest` stays `debugImplementation`; `testOptions.unitTests.isIncludeAndroidResources = true`.

**Terminology (handoff §8 + corrections):** "pool", never "pot" — including identifiers and string resources (`grep -i pot res/values/strings.xml` must come back empty at close-out). Handles are `@alex` (display carries the `@`). Banned in UI/listing/screenshots: wager, bet, odds, prize, jackpot, cash out. "Stake" stays (devnet) — real-funds gate does not open in this campaign. Sentence case everywhere; capitals only inside display words.

**Tokens (handoff §2–4):** zero raw hex outside `NopeekRef` (Tier 1); screens use `Nopeek.colors / .type / .shape / .size`; component parts point at tier 2. Gradients only in: Home hero · onboarding scene panels · Stats hero · Profile wallet hero · Welcome background — never on text/buttons/rows/tiles; data on flat `surfaceRaised`. Grain (140dp noise tile, 0.18, overlay) ships with every gradient — it is structural, not decoration. Contrast measured at the **lightest point** of a gradient, never an average; lowest sanctioned pair is 5.04:1; coral is never text on `surfaceHigh`.

**Type (handoff §3):** display = Big Shoulders Display 900; body = Schibsted Grotesk 500/600/700; display text never wraps (`softWrap = false`, authored `\n`); clamp display at 1.3× (`fontScale.coerceAtMost(1.3f)`); keep a number and its unit in one Text; Material button labels get `NopeekText.TitleSm` explicitly.

**Layout (handoff §4):** margin 16dp · touch targets ≥48dp, 8dp apart · top bar 56dp · nav pill 66dp / 10dp inset / radius 33 · FAB 52dp at 92dp from bottom · buttons 48/56 · slider 56 track / 48 knob / 4dp inset · card radius xl 24 / lg 28 · FAB list padding 88dp · gauge 196dp / stroke 14 / sweep 270° from 135° / opponent marker 7dp + 3dp ring · limit = the full extent of every track (overage is colour + figure, never geometry).

**Motion (handoff §10):** "alive but disciplined" — response motion mandatory on every control, orchestrated moments max one per screen, spectacle only on Win. Four easings only (`standard`, `decelerated`, `emphasis`, `NopeekSpring`); durations per §10.2. Retarget, never restart; data forward only; >15% jumps snap; one loop per screen, banned except win + Welcome mark. Cite the M-ID in code for every motion (the ledger §10.7 is the checklist). Reduced motion: final values, except slider travel (feedback) and press tint.

**Process (build-ops):** `:app:testDebugUnitTest --rerun` or it didn't run; oracle tests via `node --test "supabase/functions/oracle/*.test.ts"`; tsc gate; never trust Playground's "Deployed" text — deploy proof is `lastDeploySlot` (ProgramData bytes 4–12, LE u64) increasing; commits stage **explicit paths only** (a parallel session works in this tree — never `git add -A`); secrets (service_role, ORACLE_API_SECRET, deploy keypair) never in the app, a screenshot, or a doc; when implementation contradicts the handoff, the handoff gets a revision — the code does not silently diverge.

**Every task also ends with:** the app builds (`:app:assembleDebug`), the touched suites are green **with `--rerun`**, and the commit cites the task ID (e.g. `feat(ui): nav pill (P2-1)`).

---

## Review Focus

Five input classes the spec implies but no single task's happy path exercises, most likely to bite first. Each has its test pinned to an owning task below (tagged **RF-n**).

1. **RF-1 — Font scale 1.3× + longest copy** on non-scrolling screens (the handoff's #1 recorded defect, twice). *Expect:* authored display lines never wrap; the last control never leaves the frame. *Test:* P7a-2, P9-1 (sweep + pseudo-locale).
2. **RF-2 — Usage permission revoked mid-duel** (the money-safety path). *Expect:* Home pauses calmly, the oracle holds, grace lapses to void with both stakes returned — never a silent loss. *Tests:* P4-4 (T-05/T-06/T-12), P7k-1.
3. **RF-3 — Committing offline** (create 6 slide, accept slide). *Expect:* both re-arm with "Needs a connection" in the same inline slot as the wallet-reject line — never a toast, never a false success. *Tests:* P7d-6, P7e-1.
4. **RF-4 — UTC midnight edges** (create 23:50, accept 00:10; per-UTC-day accounting; streak definition). *Expect:* clock starts at join (T-10); one over-limit day loses the duel (T-11); a no-duel day neither adds nor breaks the streak; a voided day does not break it. *Tests:* P3-3, P4-3, P5-3 (streak).
5. **RF-5 — Short / insufficient wallet at the moment of commitment.** *Expect:* the short state prices the promise (amber gap, Continue demoted), "Get test USDC" in place; a wallet rejection is inline, never a dialog; success fires on the wallet callback only. *Tests:* P7d-4, P7d-6, P5-8.

---

## How this checklist is used

- Each phase opens with a **Phase open** step: re-verify every path, symbol and signature named in that phase against the current HEAD; if drift is found, correct **this document first**, then proceed. (Later phases were written against 2026-10-18 code; earlier phases will have moved it.)
- Each task's "Complete when" items are the definition of done — tick them here and attach evidence in the task's Evidence line.
- A phase gate is not passed until every task in it is ticked **and** the gate line at the phase's end is ticked.
- Mechanical steps ("write failing test, run it red, implement, run it green, commit") are implied for every code task by the executing-plans sub-skill; this document names the tests, files and values each cycle must prove.

---

# Phase 0 — Preflight & guardrails

**Gate:** baseline suites green and recorded; conventions in repo; branch exists.

- [ ] **Phase open** — confirm branch state, current test counts, licence files present in `design system/fonts/`.

### P0-1 · Branch + baseline record

**Files:** none (evidence into this doc) · **Depends:** —

- [ ] Create branch `nopeek-v1` from `nopeek-colosseum` HEAD (record HEAD hash here).
- [ ] Run the four gates: `:app:assembleDebug` · `:app:testDebugUnitTest --rerun` · `node --test "supabase/functions/oracle/*.test.ts"` · `npx tsc -p supabase/functions/tsconfig.json --noEmit`.
- [ ] Record counts and the exact HEAD hash in this task's Evidence line.

**Complete when:** branch exists; four commands green; counts + hash recorded. **Evidence:** command output lines here.

### P0-2 · Strings & i18n convention

**Files:** create `app/src/main/res/values/strings.xml` · **Depends:** —

- [ ] Create `strings.xml` under `values/` (default locale — never `values-en/`), with `app_name` only for now.
- [ ] Confirm in this doc (Global Constraints already state it): all copy lands in `strings.xml`; no concatenation; plurals via `<plurals>`; no hard-coded `$` prefix in layouts.

**Complete when:** file exists; `app_name` referenced from `MainActivity`/manifest path that currently holds it. **Evidence:** file path + commit.

### P0-3 · Licence & font assets

**Files:** create `app/src/main/assets/licenses/OFL_BigShouldersDisplay.txt`, `…/OFL_SchibstedGrotesk.txt` · **Depends:** —

- [ ] Copy both OFL texts from `design system/fonts/` into app assets; add `AUTHORS.txt` naming both families (build-ops §8.1).
- [ ] Verify `Satoshi-Variable.ttf` and `JetBrainsMono*` are referenced nowhere in `app/` (grep); record result — Satoshi stays unshipped pending a licence call.

**Complete when:** licence files in assets; grep results recorded. **Evidence:** grep output + paths.

### P0-4 · Source-hygiene ratchet

**Files:** create `app/src/test/java/com/nopeek/app/ui/theme/SourceHygieneTest.kt` · **Depends:** —

- [ ] Test scans `app/src/main` for (a) raw `Color(0x` literals outside `ui/theme`, (b) `"pot"`/`"Pot"` in UI copy, against a **shrink-only allowlist** (exact file list of current violators scheduled for deletion/replacement).
- [ ] Mutation-check the ratchet: add a fake hex literal to a live file, watch the test go red, revert.
- [ ] Verify green with the allowlist as it stands.

**Complete when:** test green; mutation observed; allowlist committed. Every later UI task removes its files from the allowlist. **Evidence:** red/green outputs.

---

# Phase 1 — Tokens v1.2

**Gate:** token/typography/contrast tests extended and green; design-system exports match the app; no new raw hex outside `NopeekRef`.

- [ ] **Phase open** — read handoff §2–§4 specimens again before touching theme files.

### P1-1 · Field tokens, grain, field primitive

**Files:** `ui/theme/Color.kt` (6 new tier-1: `fieldOkHi #1A3D24 / fieldOkLo #112D1E / fieldRiskHi #3B2F15 / fieldRiskLo #26200E / fieldOverHi #452018 / fieldOverLo #2A1410`); create `res/drawable/grain_tile.png` (140×140, generated); create `ui/theme/Field.kt` · **Depends:** P0 · **Produces:** `enum NopeekField { OK, RISK, OVER }`; `fun Modifier.fieldBackground(field: NopeekField, shape: Shape)` — §2's recipe exactly: radial ellipse `135% 86% at (50%, −14%)`, stops `Hi 0% → Lo 74%` (Compose: `Brush.radialGradient(colorStops = arrayOf(0f to Hi, 0.74f to Lo))`, per §2's code sample) + grain overlay at 0.18 alpha, `BlendMode.Overlay`, 140dp tile.

- [ ] Extend `NopeekTokensTest`: exact stop hexes; lightest-point pairs ≥ documented (`fieldOk` volt 10.4, muted 5.06; `fieldRisk` amber 7.09, muted 5.48; `fieldOver` coral 5.04, muted 5.96) using `ContrastAssert` compositing helpers.
- [ ] Generate `grain_tile.png` deterministically (checked-in artifact; no runtime shader).
- [ ] Implement `Field.kt` with an internal `@Preview`-style sample used by the contrast test.

**Complete when:** tests green; drawable in res; `Field.kt` compiles; gradient not yet used by any screen. **Evidence:** test lines + commit.

### P1-2 · Layout constants & shapes

**Files:** `ui/theme/Dimens.kt` · **Depends:** P0 · **Produces:** `NopeekSize` additions — `NavPillHeight 66`, `NavPillInset 10`, `NavIndicatorW 48/NavIndicatorH 28`, `FabBottomOffset 92`, `GaugeDiameter 196`, `GaugeStroke 14`, `GaugeArcRadius 80`, `OppMarkerD 20`, `OppMarkerRing 3` (marker = the opponent **avatar** disc per amended §6.9 — §4's "7dp disc" entry is stale; record that correction in the handoff at P11-4), `OptionRowMin 64`, `ChipDecision 48`, `ChipFilter 40`, `TextField 56`, `SegmentBar 4`, `SegmentGap 4`, `FixtureRow 76`, `ScenePanel 276`, `ScenePanelPad 20`, `TouchMin 48`, `GestureClearance 24`, `ListBottomPadFab 88`; tile ladder `48/44/42/40/38/24`; `NopeekShape` additions `Xl 24dp`, `Lg 28dp` (keep `Sm 6`, `Md 14`, `Full`).

- [ ] Extend the token test: every constant exact; shapes exact.
- [ ] Do **not** delete `BottomNav 80` yet — retired with the old bar in P2-3.

**Complete when:** tests green; constants available app-wide. **Evidence:** test lines.

### P1-3 · Motion tokens

**Files:** `ui/components/Motion.kt` (+ retire the comment-only easing), `Dimens.kt` · **Depends:** P0 · **Produces:** `NopeekEase` = `standard(0.2,0.8,0.2,1)`, `decelerated(0.05,0.7,0.1,1)`, `emphasis(0.3,1.3,0.4,1)` (≤4% overshoot, selection pops only), `spring` = `spring(dampingRatio 0.72f, stiffness StiffnessMediumLow)`; `NopeekDuration` = `TapDown 100`, `TapUp 140`, `Micro 150`, `MicroPop 200`, `Travel 250`, `TravelMax 350`, `Sheet 300`, `DataEnter 1300`, `Fill 1600`, `CountUp 2200`, `Drain 2200`; keep `rememberReducedMotion()`; add `Modifier.pressScale()` helper (1→0.97, RF: tint under reduced motion).

- [ ] Test: easing control points exact; durations exact; `pressScale` collapses to tint under reduced motion (Robolectric with `animator_duration_scale` 0).

**Complete when:** tests green. **Evidence:** test lines.

### P1-4 · Typography audit + clamp helper

**Files:** `ui/theme/Type.kt` · **Depends:** P0 · **Produces:** `fun TextUnit.clamped(scale: Float) = this * scale.coerceAtMost(1.3f)`; display styles exposed with softWrap guidance; verify every §3 row (display.moment 168/151 … label.sm 12/16, weights) against `NopeekText`.

- [ ] Extend `NopeekTypographyTest` for any drift found; assert the clamp at 1.0/1.3/2.0×.
- [ ] Verify Material slots still map (no visual change intended in this task).

**Complete when:** typography test green incl. clamp; drift list empty or fixed. **Evidence:** test lines.

### P1-5 · Design-system export sync

**Files:** `design system/tokens.json`, `design system/kotlin/*.kt` · **Depends:** P1-1…P1-4 · **Produces:** one source of truth = the app theme; the design-system copies are regenerated to match.

- [ ] Update the four `design system/kotlin/*` files to mirror the app theme exactly (namespace comments updated).
- [ ] Add `DesignSystemSyncTest`: parses `design system/tokens.json` and asserts hexes/values equal `NopeekRef`/`NopeekSize` (plain JUnit, file read from repo path).
- [ ] Mutation-check: change one token in `tokens.json`, watch the test go red, revert.

**Complete when:** sync test green; red observed. **Evidence:** outputs.

---

# Phase 2 — Shell

**Gate:** app runs at 360 & 412dp on the new frame; route tests updated; no dead destination; old bar gone.

- [ ] **Phase open** — re-read handoff §5 + §6.3; confirm current `Shell.kt`/`NavGraph.kt` shape.

### P2-1 · Floating nav pill (§6.3) — M-05

**Files:** create `ui/components/NavPill.kt`; extend `ui/components/InteractiveComponentContractTest.kt` · **Depends:** P1-2 · **Produces:** `NopeekNavPill(selected: Destination, onSelect: (Destination) -> Unit, modifier)` — 66dp pill, radius 33, `surfaceRaised`, 10dp inset; item ~77dp; indicator 48×28 volt pill with spring travel (M-05, retarget mid-flight, reduced→final); labels `label.sm`.

- [ ] Semantics tests first: selected state exposed per destination; each target ≥48dp, 8dp apart; label + icon read correctly.
- [ ] Contrast rows: label/icon on pill; selected on volt.
- [ ] Icons: add any §5 glyphs missing from `Phosphor.kt` (chart glyph for Stats is the known gap); update `NavIconMappingTest`.

**Complete when:** contract + contrast tests green; icon map test green. **Evidence:** outputs.

### P2-2 · Route table (§5) + NavHost restructure

**Files:** `navigation/NavRoutes.kt`, `navigation/NavGraph.kt`, delete `ui/screens/OpenDuelsScreen.kt`; update `navigation/NavRoutesTest.kt` · **Depends:** P2-1 · **Produces:** route constants for the §5 table — `welcome · onboarding/1…6 · home · duels · stats · profile · profile/edit · wallet/connect · create/1…7 · duel/{id} · duel/{id}/result · accept/{inviteId}`; old routes `open_duels` deleted now; `splash · choice · create_challenge · active_challenge · recovery_lock` remain until their replacements land (Cutover register).

- [ ] Register only routes whose screens exist; each future screen task flips its route on.
- [ ] Start destination: keep `splash` decision logic until 7l-1 replaces it.

**Complete when:** app builds and runs; `NavRoutesTest` matches the table; `open_duels` gone from code and tests. **Evidence:** test lines; screenshot.

### P2-3 · Shell swap — edge-to-edge + pill + content-under

**Files:** rewrite `ui/components/Shell.kt`; `MainActivity.kt` (edge-to-edge + insets); old screens get minimal bottom-padding fix (`ListBottomPadFab`); delete `NopeekBottomBar`/`BottomTab` and `NopeekSize.BottomNav`; update affected tests · **Depends:** P2-1, P2-2.

- [ ] Four destinations render inside the pill; content scrolls beneath; FAB host slot placed at 92dp.
- [ ] Insets from `WindowInsets` only — no hard-coded status/nav bar values anywhere.
- [ ] Minimal padding patches to old screens (no restyle) so nothing sits under the pill.

**Complete when:** runs at 360 & 412dp; all four destinations reachable; no screen's last control hidden; old bar deleted. **Evidence:** screenshots both widths + commit.

### P2-4 · Extended FAB (§6.2) + §6.5 top bars

**Files:** create `ui/components/Fab.kt`, `ui/components/TopBar.kt`; extend contract/contrast tests · **Depends:** P1-2.

- [ ] FAB: 52dp extended, icon+label, press scale M-01; semantics role; contrast row.
- [ ] TopBar: 56dp detail bar (back, title `title.md`, overflow slot) + screen-header variant (display title + muted line); back target ≥48dp; contrast rows.
- [ ] Home/Duels mount the FAB in their tasks (7a/7b) — here it's the component.

**Complete when:** both components green in contract + contrast tests. **Evidence:** outputs.

### P2-5 · Transitions & predictive back (M-10, M-06 base)

**Files:** `navigation/NavGraph.kt` (nav transitions enter 300/exit 200 `standard`), `AndroidManifest.xml` (predictive back), `ui/components/Motion.kt` if a helper is needed · **Depends:** P2-3.

- [ ] Create steps slide with direction; back cancels the forward run (M-10) — verify once Create lands.
- [ ] Enable `android:enableOnBackInvokedCallback="true"`; verify system back gestures.
- [ ] Reduced motion: transitions collapse (system animator scale honoured).

**Complete when:** manual verification on emulator recorded (two screenshots + note); no custom page animation beyond shared-axis. **Evidence:** note here.

---

# Phase 3 — Contract v2 (`nopeek_duel`)

**Gate:** new suite green; in-place upgrade proven by `lastDeploySlot` increase; IDLs in sync in the same commit; mutation teeth recorded.

**Recorded consequence:** after P3-9's deploy, the dev app's chain layer is out of sync until Phase 5 (devnet-only, no users — recorded in the P3 gate; do not demo chain flows between P3-9 and P5 close).

- [ ] **Phase open** — re-read handoff §15 rulings table + current `contracts/program/src/lib.rs` end to end.

### P3-1 · Rename in place

**Files:** `contracts/program/src/lib.rs`, `contracts/program/Cargo.toml`, `contracts/program/idl.json`, `app/src/main/assets/idl.json` (names only), test files referencing names · **Depends:** —

- [ ] `volo_challenge` → `nopeek_duel` module + crate; `VoloError` → `NopeekError`; IDL metadata names updated (deploy waits for P3-9; this is source rename only).
- [ ] Existing suite green under new names — zero behavior change.

**Complete when:** suite green; no `volo` string left outside git history. **Evidence:** grep + suite.

### P3-2 · PDA seeds → `[challenge, nonce]`

**Files:** `lib.rs` (create/join/settle/recover seeds), `contracts/tests/playground-case17.test.ts`, and the mirrored Kotlin pins (updated in P5; **here** update only if the contract suite needs them) · **Depends:** P3-1.

- [ ] Create takes no opponent; the position is filled at join. Same `(creator, nonce)` derives same PDA (T-09).
- [ ] Update the playground cases to the new seed rule.

**Complete when:** T-09 contract-side green (same nonce stable, different nonce differs); create→send→accept representable. **Evidence:** test output.

### P3-3 · Clock starts at join (T-10)

**Files:** `lib.rs` · **Depends:** P3-2.

- [ ] `create` stores duration only; `join` sets `start_time`/`end_time`. Sent holds escrow with no clock.
- [ ] T-10: created 23:50, joined 00:10 → window starts 00:10. **RF-4.**

**Complete when:** T-10 green. **Evidence:** output.

### P3-4 · Decline (T-01) + Expire (T-02, config window)

**Files:** `lib.rs`; replace `refund_unjoined` with `expire` · **Depends:** P3-3.

- [ ] `decline_challenge`: opponent, CREATED only → DECLINED; no refund instruction, nothing locked moves. T-01 green.
- [ ] `expire_challenge`: creator-callable after `join_window` (config, default **6h** matching the accept-sheet copy — flag for the gate) → EXPIRED, creator refunded only. T-02 green.

**Complete when:** T-01/T-02 green; statuses exist; config value recorded. **Evidence:** outputs; gate note.

### P3-5 · Forfeit (T-03)

**Files:** `lib.rs` · **Depends:** P3-3.

- [ ] `forfeit`: either side, ACTIVE only → whole stake total transfers to the opponent (both stakes), FORFEITED; replay fails. T-03 asserts exact amount, not a fraction.

**Complete when:** T-03 green. **Evidence:** output.

### P3-6 · Void path (T-05) + PAUSED

**Files:** `lib.rs` · **Depends:** P3-3.

- [ ] `void_challenge`: callable only with the unverifiable signal; PAUSED state with 24h grace; grace lapse → VOIDED, both stakes returned. No path from UNVERIFIED to a loss. **RF-2.**
- [ ] Record (gate note): deliberate void-signalling abuse mitigation (rate-limit new duels per wallet) has no parameters yet — implemented only if the user promotes it; oracle monitoring counts UNVERIFIED %.

**Complete when:** T-05 green; PAUSED/VOIDED statuses exist. **Evidence:** outputs + gate note.

### P3-7 · Recover (T-04) — treasury sweep retired

**Files:** `lib.rs` (delete `resolve_lock` treasury logic) · **Depends:** P3-3.

- [ ] LOCKED → after 7-day cooldown (604800 s), each side withdraws its own stake once. Before 7d fails; after moves exactly one stake to its owner, once.
- [ ] `TREASURY` constant removal audited (grep) — nothing sweeps escrow anymore.

**Complete when:** T-04 green; grep clean. **Evidence:** outputs.

### P3-8 · Verdict codes + STEADY

**Files:** `lib.rs` codes/IDL enums · **Depends:** P3-4…P3-7.

- [ ] Final code set: WON, LOST, STEADY (renamed from BOTH_PASS_REFUND), LOCKED, FORFEITED, VOIDED, DECLINED, EXPIRED — settlement stays one verdict per side at window end (§15 #8); day accounting lives in the oracle (P4-3).
- [ ] `settle` accepts the final verdict set; idempotency (T-07) still green.

**Complete when:** T-07 + code-set tests green. **Evidence:** outputs.

### P3-9 · Deploy + IDL sync + evidence

**Files:** `contracts/program/idl.json`, `app/src/main/assets/idl.json`, `contracts/DEPLOYED_PROGRAM_ID.txt`, `contracts/ATTESTATION_DEPLOY_NOTES.md` · **Depends:** P3-2…P3-8.

- [ ] Build + deploy in place (Playground CLI 0.29) — same program ID `DJftv46…`.
- [ ] **Deploy proof:** read ProgramData bytes 4–12 as LE u64 over RPC; require `lastDeploySlot` > 511960495; record slot + ELF sha256.
- [ ] IDL structural diff between the two files = zero (add `IdlStructuralDiffTest` if missing); ship `assets/idl.json` in the same commit.

**Complete when:** slot advanced (recorded); sha256 recorded; diff test green; `DEPLOYED_PROGRAM_ID.txt` updated. **Evidence:** RPC output, hash, test.

### P3-10 · Mutation teeth for T-01…T-05, T-10

**Files:** none (procedure) · **Depends:** P3-3…P3-6.

- [ ] For each guard: break it once, confirm the **named** test goes red, restore, confirm green. Ten minutes each, recorded per test.

**Complete when:** six mutations recorded with red/green evidence. **Evidence:** table here.

---

# Phase 4 — Schema + oracle

**Gate:** oracle suites green; migration applied and verified via PostgREST; oracle deployed with upload-count proof.

- [ ] **Phase open** — re-read §15 #5/#8/#9/#10/#13 + current `index.ts` route map.

### P4-1 · Schema v2 migration

**Files:** create `supabase/migrations/20261018_nopeek_v2_schema.sql` (old files stay as history) · **Depends:** P3.

- [ ] Day-indexed `usage_reports` (`day_index`), drop the one-verified-per-side uniqueness → per side **per day**; challenge statuses for the full §15 state machine; `settlement_results` code set incl. STEADY/VOIDED; RLS unchanged in spirit (insert anon/auth, select service-role).
- [ ] Re-applied from scratch per cutover ruling (no rows worth keeping); applied via `supabase db push`.
- [ ] PostgREST probe recorded (the repo's 42501-style verification).

**Complete when:** probe recorded; app's old paths documented as broken-until-P5. **Evidence:** probe output.

### P4-2 · Profiles + avatars bucket

**Files:** same migration or `…_profiles_avatars.sql` · **Depends:** P4-1.

- [ ] `profiles(wallet_pubkey → unique handle 3–15 chars, avatar_url)`; public `avatars` bucket with size/type limits; service-role + owner write policies.
- [ ] Probe: anon insert denied where expected; handle uniqueness enforced.

**Complete when:** probes recorded. **Evidence:** outputs.

### P4-3 · Day accounting (T-11) + hold (T-12)

**Files:** `supabase/functions/oracle/determine_result.ts`, `checks.ts`, tests · **Depends:** P4-1.

- [ ] PASS only if **every** UTC day passed; report payload carries + validates `day_index`. T-11 green incl. a midnight-boundary fixture. **RF-4.**
- [ ] T-12: permission-loss still holds (never fails); re-grant resumes the same window/streak.

**Complete when:** T-11 + T-12 green. **Evidence:** outputs.

### P4-4 · UNVERIFIED → pause → void (T-05/T-06)

**Files:** `determine_result.ts`, `index.ts` (grace tracking + void call) · **Depends:** P4-3, P3-6.

- [ ] UNVERIFIED can only hold or void — never WON/LOST (T-06). 24h grace tracked server-side; lapse → void_challenge invoked. **RF-2.**

**Complete when:** T-06 green + grace→void test green. **Evidence:** outputs.

### P4-5 · Settle codes end-to-end

**Files:** `index.ts`, tests · **Depends:** P4-4.

- [ ] `/settle` handles WON/LOST/STEADY/LOCKED/FORFEITED/VOIDED/DECLINED/EXPIRED; idempotent (T-07 server half).

**Complete when:** settle matrix tests green. **Evidence:** outputs.

### P4-6 · Delete-account endpoint (T-15 server half)

**Files:** `index.ts` (new route), `auth.ts`, tests · **Depends:** P4-2.

- [ ] Order: release balance → void open duels (opponent refunded) → delete reports/profile/avatar. Resumable; a failed middle step leaves money safe; key erasure is client-side (P5). Wallet-signature verified (shares `verifyWalletSignature` with P4-7).

**Complete when:** T-15 server test green (order asserted with fixtures). **Evidence:** output.

### P4-7 · AUTH-1 server half

**Files:** `auth.ts`, `index.ts` (`/enroll`, `/register-challenge`), tests + attack fixtures · **Depends:** P4-1.

- [ ] Both writes require a wallet-signed body (`ed25519.verify` via the pinned `@noble/curves`); replay + wrong-wallet fixtures rejected. Unauthenticated write previously possible for a victim's wallet → now closed.

**Complete when:** reject/accept tests green. **Evidence:** outputs.

### P4-8 · Close `ORACLE_API_SECRET` gap

**Files:** Supabase secrets + `index.ts` guard, tests · **Depends:** P4-5.

- [ ] Secret set via `supabase secrets set`; `/settle` + `/register-challenge` reject without it; app-side header wiring lands in P5-9.

**Complete when:** rejection test green. **Evidence:** output + gate note (secrets never printed).

### P4-9 · Events table + retention

**Files:** migration + tests · **Depends:** P4-1.

- [ ] `public.events` per build-ops §6.1 exact shape; RLS insert-any, select service-role; 180-day retention note in migration comment.

**Complete when:** insert probe succeeds for anon; select probe denied. **Evidence:** outputs.

### P4-10 · Oracle deploy + monitoring affordances

**Files:** deploy procedure; `/health` includes last-successful-settle timestamp if not already · **Depends:** P4-3…P4-9.

- [ ] Deploy; verify via "Uploading asset" count (5+); `/health` 200; record the three monitoring numbers' sources (health ping, stuck-ACTIVE count, UNVERIFIED %).

**Complete when:** deployed with uploaded-asset count recorded; health output recorded. **Evidence:** outputs.

---

# Phase 5 — App data layer & identity spine

**Gate:** model/verdict/repo tests green incl. T-13/T-14 logic and T-09/T-10 Kotlin pins; no UI yet; app builds.

- [ ] **Phase open** — re-read handoff §7.0 + build-ops §2.2 (handle validation table) + §3 (T-13/T-14).

### P5-1 · §7.0 model verbatim

**Files:** create `data/model/Duel.kt`; retire `Challenge.kt` pieces into it · **Depends:** P3-8 · **Produces:** `DuelState` ×9; `Duel` with §7.0 fields exactly; `Profile(handle, avatarUrl)`; `DayVerdict` + `verdictFor`; `youRemainingSeconds/youAreOver/poolUsdc`.

- [ ] `DuelModelTest`: verdict thresholds (OVER beats AT_RISK beats ON_TRACK; ties → larger stake → shorter remaining); remaining/over math; pool = 2× stake.

**Complete when:** test green with exact names. **Evidence:** lines.

### P5-2 · Room v2

**Files:** `data/local/*` · **Depends:** P5-1.

- [ ] New entities mirroring `Duel` + local-only fields (status, report state); DAO by `(owner, status)` with an index; destructive version bump documented (no rows worth keeping).

**Complete when:** DAO tests green; build green. **Evidence:** lines.

### P5-3 · DuelRepository + polling + streak

**Files:** `data/repositories/DuelRepository.kt` (replaces `ChallengeRepository`), tests (replace `ResolveNextStatusTest`) · **Depends:** P5-2.

- [ ] Observe/refresh; decode the full status set (incl. the previously unmapped LOCKED_RESOLVED → LOCKED); staleness signal for the offline banner; poll cadence per existing 5s repo pattern retained for foreground, WorkManager for background.
- [ ] `StreakTest`: consecutive UTC days where every active duel's limit was met; no-duel days neither add nor break; voided day does not break. **RF-4.**

**Complete when:** repo + streak tests green. **Evidence:** lines.

### P5-4 · Client instruction layer → new IDL

**Files:** `data/solana/*` · **Depends:** P3-9, P5-3.

- [ ] Instruction builders: create(nonce, no opponent) · join(sets clock) · decline · expire · forfeit · void · recover; seeds `[challenge, nonce]`; TransactionCompiler updates.
- [ ] Update `PdaTest` (T-09 Kotlin side), `ProgramInstructionsTest`, `TransactionCompilerTest`, `SettlementLogicTest` to the new set; parity with `assets/idl.json` (parse test).

**Complete when:** all listed suites green. **Evidence:** lines.

### P5-5 · Day-indexed reporting

**Files:** `data/attest/CanonicalPayload.kt`, `data/usage/*` · **Depends:** P4-3, P5-3.

- [ ] Payload gains `day_index`; collector tags the day; scheduler/recovery submit per-day windows; frozen vector deliberately updated (record why in commit).

**Complete when:** payload/recovery/aggregator suites green; T-12 app-side logic documented. **Evidence:** lines.

### P5-6 · Keystore check (T-13)

**Files:** create `data/session/SessionStore.kt`, `data/identity/KeystoreCheck.kt` (+Robolectric test) · **Depends:** P0.

- [ ] Local-only resolution (alias present → account present), 2s ceiling → "not found", zero network calls asserted; `onboardingComplete`/`signedOut` flags.

**Complete when:** T-13 green. **Evidence:** lines.

### P5-7 · Sign-out logic (T-14)

**Files:** `SessionStore.kt`, wallet manager · **Depends:** P5-6.

- [ ] Clear session + in-memory key; keystore alias survives; **refused** while a duel is ACTIVE or a stake is held; refusal names the duel for the escape hatch.

**Complete when:** T-14 repo-unit green. **Evidence:** lines.

### P5-8 · Wallet service (keystore keypair)

**Files:** `data/wallet/*` · **Depends:** P5-6.

- [ ] Interface + JVM-testable core (mnemonic derive/import, sign bytes) + thin Android keystore adapter (device-tested in P9); balance via `TokenBalanceApi`; committed line computed from live duels. **RF-5.**

**Complete when:** core tests green; adapter compiles; device test scheduled (P9). **Evidence:** lines.

### P5-9 · AUTH-1 client half + settle secret

**Files:** `data/attest/*`, `data/usage/OracleReportApi.kt` · **Depends:** P4-7, P4-8, P5-8.

- [ ] Sign `/enroll` + `/register-challenge` bodies with the wallet key; attach `ORACLE_API_SECRET` header on `/settle`; byte-vectors match the oracle fixtures exactly.

**Complete when:** vector-match tests green both sides. **Evidence:** lines.

### P5-10 · Profiles API + handle validation

**Files:** create `data/identity/ProfilesApi.kt`, `data/identity/HandleRules.kt`; delete `MockSeekerProvider.kt` (+ its test) · **Depends:** P4-2.

- [ ] Handle rules exactly per build-ops §2.2 (empty `ab` invalid · `Bob!` normalised + caret rule · taken `alex` amber alert · reserved `nopeek/admin/you` same UI · free volt tick; 3–15; debounced 300ms; server is the gate).
- [ ] Fallbacks: truncated pubkey `7xKp…9fQ2` for handles; gradient+initial for avatars — never "unknown", never blank.

**Complete when:** `HandleRulesTest` reproduces the table verbatim; fallback tests green. **Evidence:** lines.

### P5-11 · DemoSeeder retirement

**Files:** delete `data/demo/DemoSeeder.kt`, its test, `assets/demo-config.json` (cutover §4) · **Depends:** P5-2.

- [ ] Remove wiring from `MainActivity`; grep proves no references.

**Complete when:** deleted; build green. **Evidence:** grep + build.

---

# Phase 6 — Components (§6, 32 entries)

**Gate:** `InteractiveComponentContractTest` + contrast tests green for the whole catalogue; each component cites its M-IDs.

**Rule:** semantics tests are **written first** in every task. Specimens and exact numbers live in §6 by name — reference by name (the file's numbering has quirks: two entries commented `9`, no `31`). Components already landed: Nav pill (P2-1), FAB + Top bars (P2-4).

- [ ] **Phase open** — re-read §6 entries for this phase in the handoff before starting.

### P6-1 · Buttons · Status label · Brand mark & wordmark

**Files:** `ui/components/Buttons.kt`, `Status.kt`, create `ui/components/Mark.kt` · **Depends:** P1.

- [ ] Buttons: primary/outline/quiet per §6.1, M-01/M-02; Status label: glyph + word variants (§6.6), amber/coral/muted semantics; Mark: lids draw-on (M-41 cycle), blink (M-22), one-shot form (M-44), Welcome breathing (M-21) — geometry per `nopeek-icon/` drawables.
- [ ] Contract + contrast tests for all three.

**Complete when:** green. **Evidence:** lines.

### P6-2 · Tabs · Step progress · Choice chips + chip grid · Filter chip · Option row

**Files:** `ui/components/Chips.kt`, `DataDisplay.kt`, new `Tabs.kt` · **Depends:** P1.

- [ ] Tabs (§6.4: 48dp, 3dp volt indicator, counts) M-06; Step progress (§6.22: five 4dp segments, counter text) M-36; Chips (§6.23 decision 48dp + **>4 lays out 3-column**; grid never shrinks <48dp, never wraps) M-03; Filter chip (40dp) M-07; Option row (64dp min; row is the target; selected semantics) M-04.

**Complete when:** green incl. the 3-column grid rule. **Evidence:** lines.

### P6-3 · Gauge · Shared tape · Mini tape · Single-player bar · Scoreboard

**Files:** `ui/components/Gauge.kt` (new), `Cards.kt` split, `Charts.kt` · **Depends:** P1.

- [ ] Gauge (§6.9: 196dp / 14dp stroke / radius 80; sweep 270° from 135°, round caps; track = onSurface 14% across the full sweep; fill = state colour `min(used/limit,1)`; opponent marker = their **avatar** 20dp + 3dp ring at `min(oppUsed/limit,1)` — amended §6.9 supersedes §4's "7dp disc"; centre content slot) M-24/M-25/M-35; tapes and bars per §6.10–6.13; scoreboard §6.14.
- [ ] Arc math keeps `sweepDegrees` tests; marker position = `min(oppUsed/limit, 1)`; decorative parts marked `invisibleToUser`.

**Complete when:** green + gauge math tests green. **Evidence:** lines.

### P6-4 · Stats strip · Chart · Receipt · Fixture row · Today row

**Files:** `DataDisplay.kt`, `Charts.kt`, `Cards.kt` · **Depends:** P6-3.

- [ ] Stats strip (§6.18: one long value max; tappable to Stats) M-33; Chart (§6.19: 108dp plot, radius 3, volt/amber/coral at-under-over, dashed limit line at limit/peak; 7/30/All with weekly aggregation) M-32; Receipt (§6.15: dashed rules only on Win) M-31; Fixture row (§6.16: 76dp, status beside title, two-marker mini tape) M-11; Today row (§6.17: 38dp tile, single-player bar) M-34.
- [ ] Chart math tests extended (peaks, aggregation, bar colour bands).

**Complete when:** green. **Evidence:** lines.

### P6-5 · Avatar · App tile · Pool mark · Wallet hero · Offline banner · Confirmation chip

**Files:** new `PoolMark.kt`, `Avatar.kt`, `OfflineBanner.kt`, `ConfirmationChip.kt`; `Cards.kt` cleanup (delete old `Pot`) · **Depends:** P6-1.

- [ ] Avatar (§6.8: photo ✕ gradient+initial fallback; 40dp Profile ring; photo crossfade M-18); App tile (real `PackageManager` icons, tile ladder); Pool mark (§6.9 vault: frame, hinge tick, two slots; live fill M-26; win drain M-29); Wallet hero (§6.30: gradient field, copy row M-17, committed line); Offline banner (§6.32: 40dp strip, amber glyph, last-synced; enter/exit M-19); Confirmation chip (M-40: rise 6+fade 200, hold 3s, volt success / amber block, overlays without shifting content).
- [ ] Delete `Pot` component; grep `Pot` clean in ui/components.

**Complete when:** green; old Pot gone. **Evidence:** lines.

### P6-6 · Text field · Paste field · Numbered steps · Scene panel

**Files:** `DataDisplay.kt`, new `ScenePanel.kt` · **Depends:** P6-1.

- [ ] Text field (§6.27: 56dp, focus tween 150, no shake — colour + word only) M-16; Paste field (paste-first, feedback states); Numbered steps (§6.28 checklist rows); Scene panel (§6.29: 276dp, field gradient, centred cards 14dp apart, rotation ≤3°, shadow-only depth, no overlap) M-23.

**Complete when:** green. **Evidence:** lines.

### P6-7 · Slide to confirm (§6.21)

**Files:** new `SlideToConfirm.kt` · **Depends:** P1-3.

- [ ] 56dp track (`surfaceHigh` + 1.5dp inset outline), 48dp knob at 4dp inset, commit at 90% of travel, early release → full `NopeekSpring` back (instant under reduced motion — **knob travel itself stays**); label `title.sm` **always onSurface, centred on the whole track** (knob and trail pass beneath it), nowrap, always names the amount; trail = primary 16%; done → knob rests trailing, label becomes the completed phrase **without moving**, one haptic, control disables — stake variant's completed label stays volt.
- [ ] **Danger variant** (the system's only sanctioned danger fill): knob fills `danger`, trail settles at 22% when done, completed label turns **coral** — permitted only because the completed control is disabled (WCAG exempts inactive components), triple-signalled. Exactly three uses: create stake · accept invite · forfeit. **Custom accessibility action mandatory** (TalkBack), Switch Access rides it. M-02, M-12…M-15.

**Complete when:** contract tests prove the custom action + commit threshold; contrast rows: label onSurface over track surfaceHigh, plus the coral-on-track completed-danger row with its disabled-state exemption noted. **Evidence:** lines.

---

# Phase 7 — Screens (verdict order)

**Gate per sub-phase:** all its screens pass their build-ops §2.1 row + D1–D8; each swap deletes its old counterpart; device spot-check.

**Every screen task implicitly includes:** strings → `strings.xml`; M-IDs cited in comments; contrast test on the real composables; states per row; events per D8; old counterpart deleted; SourceHygiene allowlist shrunk.

- [ ] **Phase open** — re-read the §7 section for this sub-phase; confirm §7.2 scene count (see Carried Decisions §A).

### 7a · Home

**7a-1 · Home hero — verdict + gauge + duel line** — M-24, M-35
**Files:** rewrite `ui/screens/HomeScreen.kt` (hero block); tests `app/src/test/.../ui/screens/HomeScreenTest.kt` (new) · **Depends:** P5, P6-3
- [ ] Top bar: wordmark 30dp + "Today ▾" + two 48dp circles (refresh, overflow); hero radius 28, state field gradient, padding 18/16/36; page dots worst-first; gauge centre: verdict word + `display.clock` figure + short unit line; duel line with 24dp tile.
- [ ] States: on track / at risk / over (figure switches to time **over**); empty-no-limit (real screen time, muted bar, "No limit set yet" + suggestion card); permission-missing (`fieldRisk`, "Nothing to\ncount yet.", no FAB).
- [ ] A11y: verdict+figure+unit = one node sentence; gauge track + marker decorative.

**Complete when:** test green, all five states rendered at 360/412, D1–D8. **Evidence:** screenshots + lines.

**7a-2 · Home body — stats strip + today rows + FAB states** — M-33, M-11, M-19, M-05
**Files:** same + `HomeScreenTest.kt` · **Depends:** 7a-1
- [ ] Strip pulled −16dp over hero's 36dp tail; one long value max; tappable → Stats; today rows 38dp tile + single-player bar; FAB dropped in permission-missing; offline banner slot.
- [ ] **RF-1 test:** 1.3× font scale + pseudo-locale strings — no wrap on display lines, last control present.

**Complete when:** green; strip tap navigates; RF-1 observed. **Evidence:** screenshots + lines.

### 7b · Duels

**7b-1 · Duels list** — M-06, M-11, M-20
**Files:** rewrite `ui/screens/DuelsScreen.kt` · **Depends:** P5, P6-4
- [ ] Header "DUELS" + "$X on the line."; tabs Active/Invites/Settled with counts that equal visible rows; ruled 76dp fixture rows (status beside title); invite as the one allowed card; 88dp bottom padding; per-tab empties exactly as §7.4 (invites/settled empty = one centred line, no theatre); FAB.
- [ ] Deep-link arrival: row keeps 8% volt tint until first tap (M-20 tint-only reduced mode).

**Complete when:** green; tab counts test green. **Evidence:** lines.

**7b-2 · Row-level states (sent · declined · expired · pending payout)**
**Files:** `DuelsScreen.kt`, `Status.kt` rows · **Depends:** 7b-1
- [ ] "Sent · waiting for @alex" (no clocks) · "Sent · @alex declined — Duel someone else" (muted, no drama) · "Invite expired — Send again" (prefilled) · pending payout = clock glyph + "Pending" solid muted, never spinner/opacity.

**Complete when:** four states render per §7.13 rows; tests pin copy. **Evidence:** lines.

### 7c · Live duel — M-25

**Files:** rewrite `DuelScreen.kt` (→ `duel/{id}`) · **Depends:** P5, P6-3, 7b-1
- [ ] 56dp bar: back · "TikTok, 30 min limit" · overflow; hero card: gauge (centre = remaining, unit line) → lead glyph+phrase ("8:28 ahead"/"4:12 behind") → 1dp rule → two clock columns; pool card (pool glass 44dp + "$10 pool" `display.figure` + sentence); two ruled fact rows ("Duel ends → in 6h 12m", "Limit resets → Midnight UTC"); primary "Lock TikTok"; forfeit **only** in overflow → dialog (7e-2).
- [ ] Three states: ahead volt; behind (arc + phrase amber together, marker shorter than fill); over (arc full coral, centre flips to over). Lead = fill-end − marker, asserted numerically in a test.
- [ ] Marker + lead entry 1300ms then 400ms (M-25); reduced → final values.

**Complete when:** test green incl. lead math; states rendered; D1–D8. **Evidence:** lines + screenshots.

### 7d · Create 1–7

**7d-1 · Step 1 — which app**
**Files:** rewrite `ui/screens/CreateScreen.kt` (scaffold + step 1) · **Depends:** P5, P6-2
- [ ] Progress bar top (back; **close on step 1**; five 4dp segments; "1 of 5"); option rows: real icon + 7-day average; component reuse from P6-2; M-36.

**Complete when:** green; segments = 5 (never 6). **Evidence:** lines.

**7d-2 · Step 2 — daily limit**
- [ ] `display.hero` value + four chips (15/30/60/120) one row (70dp each) + "Set a custom limit" text button → number field (validate 5–480) + comparison card (last week's actual beside the promise). M-03.

**Complete when:** green incl. validation bounds test. **Evidence:** lines.

**7d-3 · Step 3 — duration**
- [ ] `display.hero` + five chips in the **3-column grid** (3+2, 98dp each); never shrink, never wrap.

**Complete when:** green + geometry test (equal widths, last cell empty). **Evidence:** lines.

**7d-4 · Step 4 — stake + pool**
- [ ] One card: left $5 `display.figure` over "your stake"; right 44dp waiting vault + "$10 pool" over "your $5 + their $5" (one line); divider = two equal flex min 20dp either side (geometry test); chips retarget stake/pool/label; volt fill grows left→right (M-26); balance line states what is held (not raw balance).
- [ ] **RF-5 short state:** chips stay selectable; amber gap line; Continue demoted to hollow pill; "Get test USDC" in its place → wallet/connect.

**Complete when:** green incl. divider-gap geometry + short-state tests. **Evidence:** lines.

**7d-5 · Step 5 — opponent**
- [ ] Recent opponents (with last result) → field (last resort, validation per `HandleRules`) → invite-link row; the only field in Create.

**Complete when:** green. **Evidence:** lines.

**7d-6 · Step 6 — review + slide morph** — M-02, M-12…M-15
- [ ] Five tappable rows; primary "Confirm stake" **not** wallet language; tap arms → button morphs to slider + Cancel (M-02); success **only** on wallet callback; "Confirming on-chain…" inline text under slider while in flight (never overlay).
- [ ] **RF-3:** offline → slide re-arms with "Needs a connection" in the same inline slot; **RF-5:** wallet rejection inline (never a dialog), dismissible, retry stays armed.

**Complete when:** green incl. armed/sliding/committed/offline-blocked/rejected states. **Evidence:** lines.

**7d-7 · Step 7 — sent**
- [ ] No bar; pool rises 300ms; volt fill lands ~600ms; "Sent." +100ms; tx row fades last (M-27, re-entry skips); shows **only your layer**; copy says the clock has not started; notify soft-ask row (pre-tick → OS prompt on tick only — wiring in P10-1; row renders now).

**Complete when:** green; no progress affordance anywhere. **Evidence:** lines.

### 7e · Commit surfaces

**7e-1 · Accept sheet** — M-08, M-12…M-15
**Files:** new `ui/screens/AcceptSheet.kt` (route `accept/{inviteId}`) · **Depends:** P6-7
- [ ] Grab handle → 48dp tile + "@marcus invited you" + expiry → five rows (App/Daily limit/Duration/Your stake/Pool) → "The clock starts the moment you accept." → slide → "Not now"; dismiss **declines nothing**.
- [ ] **RF-3:** offline-blocked slide re-arms inline. Accept does not move money before the slide.

**Complete when:** green; dismiss-no-decline test green. **Evidence:** lines.

**7e-2 · Forfeit dialog**
**Files:** `StateViews.kt` · **Depends:** P6-7
- [ ] `surfaceRaised`, 28dp, 24dp padding, 72% scrim, "Forfeit?" `display.figure`, body naming amount + irrevocability, **danger slide** (knob fills `danger` — the only sanctioned danger fill; completed label coral, §6.21), "Keep going" text; M-09.

**Complete when:** green. **Evidence:** lines.

### 7f · Results family

**7f-1 · Template + Won** — M-28…M-31
**Files:** new `ui/screens/ResultScreen.kt` (rewrite `WonScreen.kt` into it) · **Depends:** P6-5
- [ ] One template, six states, flat register (no cards/gradients); Won = full-bleed volt, "WON" `display.moment` once per outcome, sunburst + confetti (the app's only decorative motion), pool drains sky→volt 2200ms with count-up landing together, receipt 2dp dashed `onWin` 28%, actions Share win (→P10-5) / Rematch outline / Done text.

**Complete when:** green; count-up lands on exact pool (test). **Evidence:** lines.

**7f-2 · Loss family — Steady · Lost · Locked · Forfeit · Voided** — M-37 (none), M-39
- [ ] Lost "PEEKED\nPAST IT" two authored lines, sentence leads with the limit, quiet rematch; Locked amber (not coral), both clocks coral "Over", both stakes held, recovery countdown row "Recovery opens → in 6d 23h", at zero "Withdraw your $5" outline button (M-39) → one withdraw; Forfeit — "FORFEIT", **no rematch**; Voided — "Voided" muted, both returned, "Back to duels" only, no share; Steady — volt word, "You both stayed under. Stakes returned.", no confetti, "Duel again" quiet.
- [ ] Assert **no animation** runs on the loss family beyond M-10/M-39.

**Complete when:** five states rendered; no-motion assertion green. **Evidence:** lines.

### 7g · Stats / History

**7g-1 · Overview** — M-32, M-33
**Files:** rewrite `StatsScreen.kt` · **Depends:** P6-4; absorbs Activity
- [ ] "STATS" + "All apps ▾" scope selector where Home has "Today ▾" (trigger only; dropdown surface deferred); tabs; range chips 40dp **Overview only** (disappearance on tab switch is correct); hero = days under the limit; content card: strip → rule → chart → caption; hero label-above-figure for multi-char values; empty state (real average, muted bars, no limit line, caption, CTA).

**Complete when:** green; caption names the over day (colour never alone). **Evidence:** lines.

**7g-2 · History** — M-11
- [ ] Day group headers over ruled rows: 42dp tile, outcome `title.sm`, context `label.sm`, right column amount `display.label` + glyph-and-word status; wallet events share the list; short titles ("Faucet deposit"); empty state = glyph tile + one honest sentence.

**Complete when:** green. **Evidence:** lines.

**7g-3 · Absorption proof**
- [ ] Delete old Activity remnants; verify every old Activity surface maps into Stats (cutover register); grep clean.

**Complete when:** clean. **Evidence:** grep.

### 7h · Profile + edit

**7h-1 · Profile**
**Files:** rewrite `ProfileScreen.kt` · **Depends:** P6-5, P5-10
- [ ] "PROFILE" title; identity row (40dp avatar volt ring, @handle, record "9 W · 5 L · 1 locked" — **drop the line if the release keeps no settled history**) → pushes `profile/edit`; wallet hero (label row truncated address + copy M-17, balance `display.clock` + "USDC", committed line, "Get test USDC" → wallet/connect); settings rows 56dp ruled: Usage access (On status) · Notifications · Appearance · Help and privacy · **Sign out** and **Delete account** as the last two rows (danger label, no chevron).

**Complete when:** green; all five rows navigate (sign-out → 7l-5; delete → 7l-7). **Evidence:** lines.

**7h-2 · profile/edit** — M-16, M-18
- [ ] @handle field + photo (Android Photo Picker → crop → Supabase Storage); Save disabled until a field changes; handle validation UI identical to onboarding copy; fallback avatars hold until ready (M-18).

**Complete when:** green; Save-state test green. **Evidence:** lines.

### 7i · Welcome + onboarding + wallet connect

> Scene count: 2.4's changelog says seven scenes (1–4 story, 5 wallet, 6 identity, 7 all-set); §5's route line says `1…6`. **Resolve at phase open against the §7.2 specimens and patch the handoff if needed** (Carried Decisions §A).

**7i-1 · Welcome** — M-21, M-22
**Files:** rewrite `SplashScreen.kt` → `ui/screens/WelcomeScreen.kt`; delete `ChoiceScreen` · **Depends:** P6-1
- [ ] Full-screen fieldOk gradient; 96dp icon tile (mark 76dp); wordmark 44dp, both E's volt; "Stay under.\nWin the pool."; body; "Get started" 56dp; "I already have an account" 48dp text → keystore check (7l-2).
- [ ] Mark: draws, blinks, breathes while the screen is up (the one sanctioned loop; tap = blink + light haptic, M-22).

**Complete when:** green; breathing loop stops on exit. **Evidence:** lines.

**7i-2 · Onboarding story scenes (1–2)**
- [ ] Per-scene anatomy: 44dp skip row → 276dp scene panel → headline → body → dots → primary; scene cards deal in (M-23, stagger 100ms); copy per §7.2; skip lands on Home's permission state.
- [ ] Panel cards: never overlap; depth = shadow + 1dp inset.

**Complete when:** green; skip-flow test lands on Home permission-missing. **Evidence:** lines.

**7i-3 · Onboarding scenes (3–4) — permissions + wallet story**
- [ ] Secondary button on tailored scenes only; permission scene skip → Home's permission-missing; "not now" branch; copy per §7.2; panel heights within the 742dp budget (the tight scenes — measure).

**Complete when:** green; skip tests recorded. **Evidence:** lines.

**7i-4 · Wallet connect (`wallet/connect`)** — M-10, M-04
- [ ] 56dp bar; line: "Nopeek keeps stakes in a wallet you hold. Nothing moves without your approval."; ruled options: Create a Nopeek wallet (keystore — attested default) · Import a recovery phrase (paste-first) · Use Seeker **muted "Coming soon"**; success returns to caller with fresh balance; a wallet that exists skips this screen forever.

**Complete when:** green; returns to caller state, not Home. **Evidence:** lines.

**7i-5 · Onboarding identity step**
- [ ] Handle field per `HandleRules` with the §2.2 table states; skippable ("Decide later") → fallback avatar + identity nudge later; propagates to Profile + duel rows + cards.

**Complete when:** green; validation table re-checked here too. **Evidence:** lines.

**7i-6 · "You're in" + first Home**
- [ ] Checklist scene + volt READY card (the only filled-primary scene card — a label, not an action); no skip; gradient panel; "Create your first duel" → Create 1 or Home.

**Complete when:** green. **Evidence:** lines.

### 7j · Interstitial overlay

**7j-1 · Overlay service**
**Files:** new `overlay/*` (SYSTEM_ALERT_WINDOW service + sheet host) · **Depends:** P3-6, P6
- [ ] Not a route; bottom sheet over dimmed app; deliberate **non**-AccessibilityService; permission flow; once-per-session gate (first foreground of a staked app per session); M-38.

**Complete when:** manual demo recorded: second fire in same session suppressed. **Evidence:** note + screenshots.

**7j-2 · Safe and over states**
- [ ] Safe: "Not now" volt primary, "Open TikTok anyway" quiet; pillar copy "Open it and the clock runs. Your $5 is still on the table."; remaining + unit line + status + mini tape.
- [ ] Over: inversion flips back — "Open TikTok" primary; copy says plainly nothing changes the outcome. Never a full-screen takeover.

**Complete when:** both states demoed + recorded. **Evidence:** screenshots.

### 7k · Cross-screen failure states

**7k-1 · Offline banner wiring + paused + report pending** — M-19
**Files:** `Shell.kt` hosts banner on Home/Duels/live only; `HomeScreen.kt`, `DuelScreen.kt`, `DuelsScreen.kt` state wiring · **Depends:** P6-5, 7a, 7b, 7c
- [ ] Banner: 40dp strip under top bar, amber glyph + "Offline" + last-synced; gauges/clocks freeze at last-known; never a toast. **RF-2:** counting-paused hero `fieldRisk` "We can't check your limit.", rows "Paused", FAB gone, interstitial silent; 24h grace messaging.
- [ ] "Report pending" amber row word after 30 min without a landed report.

**Complete when:** all three states demoable; tests pin copy. **Evidence:** screenshots + lines.

**7k-2 · Confirmation chip roll-out** — M-40
- [ ] Use for: refresh, wallet funded, profile saved, access re-granted (volt) and blocks (amber); overlays, never shifts content; new message replaces in place.

**Complete when:** demoed on at least two surfaces. **Evidence:** note.

### 7l · Exits

**7l-1 · Cold start · splash** — M-41, M-44
**Files:** rewrite `SplashScreen.kt` region (loader) · **Depends:** P6-1
- [ ] 400–900ms cover: mark draws left→right 400ms, 80ms stagger, one blink, erase; one cycle then hold; repeats only past 3s. No skeleton anywhere.

**Complete when:** recorded timing note. **Evidence:** note.

**7l-2 · Keystore check** — M-41, M-10
- [ ] "Looking for your wallet" → found → permission step (only if access off) → Home; not found → restore sheet; 2s ceiling resolves "not found"; never contacts a server (T-13 in UI form).

**Complete when:** both branches demoed. **Evidence:** note.

**7l-3 · Restore sheet** — M-08
- [ ] Recovery phrase (paste-first) · "find by handle and sign a challenge" (lookup finds, **keypair signs** — lookup alone never authenticates) · "start new instead".

**Complete when:** demoed; the "lookup alone proves nothing" rule visible. **Evidence:** note.

**7l-4 · One permission step**
- [ ] Usage access + notifications only; consequence named ("without it the duel cannot be counted"); "Not now" lands on a Home that explains why duelling is off.

**Complete when:** both branches demoed. **Evidence:** note.

**7l-5 · Before you go — blocked / clear** — M-10
- [ ] Blocked: names the live duel + one button to it (sign-out refused). Clear: exactly what signing out does — forgets this device's key, data stays until delete, coming back = restore sheet; "Signing out is not deleting" made unmistakable.

**Complete when:** both states demoed; refusal test (T-14 UI contract) green. **Evidence:** lines + note.

**7l-6 · Signed out** — M-44
- [ ] Mark draws once and holds (one-shot form — no breathing here); "Start again" single button.

**Complete when:** demoed. **Evidence:** note.

**7l-7 · Delete · confirm + type to confirm** — M-09, M-43
- [ ] Dialog states balance released + record destroyed; then type `@yourhandle`; button stays inert until the string matches (150ms colour tween only — **no shake**); gates named in plain words (settle first, withdraw first).

**Complete when:** mismatch/match demoed; no-shake confirmed. **Evidence:** note.

**7l-8 · Delete · progress + gone** — M-42, M-45
- [ ] Five named steps, determinate, moves when work returns (not a timer); refund step first; a failed step says which and stops; header keeps "‹ Delete account" with inert chevron 35%; headline grows an ellipsis (M-45, no reflow). Gone: "Closed." — account closed now / live data deleted now / backups purged ≤30 days / on-chain settlement stays public; four rows, four truths.

**Complete when:** demoed end-to-end incl. a failed-step rehearsal. **Evidence:** note + screenshots.

---

# Phase 8 — Motion pass

**Gate:** every screen cites its M-IDs; reduced-motion and haptics behaviours verified per ledger.

- [ ] **P8-1 · Response-motion audit** — grep every UI file for M-ID comments; fill every §10.4 gap (buttons, chips, rows, tabs, sliders, fields, lists); each retargetable by construction (`animate*AsState`/`Animatable`, never reset).
- [ ] **P8-2 · Orchestrated moments (§10.5)** — one per screen verified against the beat sheets: Welcome M-21/22 · onboarding deal M-23 · Home M-24 · live M-25 · Create 4 M-26 · Sent M-27 · Won M-28–31 · Stats M-32/33 · exits M-41–45.
- [ ] **P8-3 · Reduced-motion audit** — walk the ledger's "Reduced" column on device; two sanctioned exceptions only (slider knob travel; press tint instead of scale).
- [ ] **P8-4 · Haptics audit** — confirm tick on slider commit; selection tick on chip/toggle/tab; nothing on scroll; guarded by `haptic_feedback_enabled`; MIUI variance noted for P9.

**Complete when:** all four sweeps recorded; zero uncited motions. **Evidence:** audit tables here.

---

# Phase 9 — Device pass

**Gate:** results recorded here; every filed fix done.

- [ ] **P9-1 · Font-scale sweep (RF-1)** — every screen at 1.3×, both widths; screenshots; every failure fixed or the handoff patched.
- [ ] **P9-2 · TalkBack sweep** — merged-node sentences (gauge/strip/verdict), slider custom actions, row targets, inert decorations skipped; screen-reader completes create/accept/forfeit.
- [ ] **P9-3 · Performance (§7 budgets)** — cold start `< 2.0s` mid-range, p95 `< 16ms` + no frame `> 32ms` during M-24/25/29, jank `< 1%` on Duels/Stats scroll, poll `< 50ms`, memory `< 180MB`, APK `< 20MB`, Room p95 `< 5ms`; record the three release-note numbers.
- [ ] **P9-4 · OLED + haptics on hardware** — gradient banding eyeball with/without grain; MIUI haptic flag; wake/dismiss-keyguard dance + `appops` re-apply documented.

**Complete when:** all recorded; fixes closed. **Evidence:** measurement tables.

---

# Phase 10 — Extras

**Gate per sub-phase.**

### Notifications (§14)

- [ ] **P10-1 · Channels + soft ask** — three channels (`duel_events`, `limit_warnings`, `counting`); pre-ticked "Notify me when @alex accepts" row at Sent; OS prompt fires only on tick; never at launch; never re-prompt after denial; badge = open invites.
- [ ] **P10-2 · The six notifications** — accepted / 20%-left (once per duel per day) / settled (win names their stop; loss names your limit first) / invite arrived / declined-or-expired (shade-only, no sound) / counting paused (persistent until fixed); routing per §14 (invite tap → Duels highlighted, **never** the accept sheet; result links fall back to duels; tap clears; stack as "N updates").
- [ ] **P10-3 · No-dark-patterns check** — no fake urgency, no count lies, sounds only where specced; verified on device.

### Share cards (§17)

- [ ] **P10-4 · Five variants** — Won/Invite/Steady/Locked/Streak; field map from §17.1 (volt 24% #A6CC00, sky 15% #7CB9DD, turf2/pitch untouched; faintest line ≥4.5:1 checked per field); fixed-height five-row layout, number leading 46/44; wordmark solid ink (no volt EE on cards); rendered off-screen → PNG.
- [ ] **P10-5 · Share integration** — offered once on Won only; OS share sheet with the 1080×1080 card; caption template "I kept TikTok under 30 min and took the $10 pool on Nopeek."; never re-prompted; **no tx hash, no opponent handle**; link-preview image drawn server-side from the same token table (with P10-6).

### Invite landing

- [ ] **P10-6 · `nopeek.page/d/{id}`** — wordmark, duel summary card, "Open in Nopeek"; installed → deep link to Duels highlighted (M-20, never the accept sheet); not installed → Play → invite waits highlighted after first launch; desktop → card + QR; **nothing asks for money or a wallet**.
- [ ] **P10-7 · Deep links in app** — intent handling + highlight + badge; verified both paths.

### Events

- [ ] **P10-8 · Event wiring verification** — walk build-ops §6.2's list: every event fires with documented props; the never-log list holds (no stake to Logcat, no address/signature, nothing about apps that aren't staked); gaps closed.

### Performance & release notes

- [ ] **P10-9 · Budget recording** — the three numbers in release notes per build; template + first entry.

### Store

- [ ] **P10-10 · Listing assets** — 8 screenshots at 3× from the real app in the §8.2 order (Home live → Create 4 → live duel → Won → Duels → Stats → Profile → onboarding 1); 512×512 icon (full bleed, no rounding); feature graphic 1024×500 wordmark-led, one number, no screenshot.
- [ ] **P10-11 · Store paperwork** — data-safety answers per §8.2; content-rating answer (simulated gambling, test funds only, honestly); real-money memo positioning in description's first two lines; web-deletion URL + live privacy-policy URL (checklist items before listing).

---

# Phase 11 — Close-out

- [ ] **P11-1 · Grep audits** — `pot` absent in `res/` and identifiers · SourceHygiene allowlist **empty** · no unreachable routes/screens · `NopeekColors`/`NopeekType` legacy aliases gone · `OpenDuelsScreen`/`ChoiceScreen`/`DemoSeeder`/old `Pot` gone · no raw hex outside `NopeekRef`.
- [ ] **P11-2 · §16 six rehearsal passes on the real app** — clean first run · both money doors · every outcome · broken day · edge corners · the exits — each recorded, surprises filed as revisions not patches.
- [ ] **P11-3 · Docs refresh** — README + runbook (R-01…R-08 first moves still true), release notes, `DEPLOYED_PROGRAM_ID.txt` final.
- [ ] **P11-4 · Handoff revision** — every deviation encountered during the build recorded back into the handoff as a revision; nothing lives only in code.
- [ ] **P11-5 · Release build** — `:app:assembleRelease` (minified + shrunk), APK `< 20MB`, licence files present in the APK, version + three perf numbers in release notes.

**Campaign done when:** every phase gate ticked; T-01…T-15 green across contract/oracle/repo with mutation teeth where required; deploy + schema + oracle evidence recorded; device pass recorded; extras complete; audits clean.

---

# Carried Decisions — resolve at the named gate

| # | Decision | When |
|---|---|---|
| A | Onboarding scene count: 2.4 changelog + build-ops say **seven scenes** (1–4 story, 5 wallet, 6 identity, 7 all-set); §5/§7.2 route line says `1…6`. Resolve against §7.2 specimens; patch the handoff if it disagrees with itself. | 7i phase open |
| B | `join_window` default: **6h** chosen to match the accept-sheet copy ("Expires in 6 hours") — confirm at the gate, it is config, not a constant. | P3-4 gate |
| C | Void-abuse rate limiting (repeat void-signalling to escape losses): mitigation params undecided; NOT implemented in v1 unless promoted; oracle monitors UNVERIFIED %. | P3-6 gate |
| D | §6 entry numbering quirks (two `9`s, no `31`): reference components by name in tickets and code comments. | now, forever |
| E | Stats scope-dropdown surface + custom-limit keypad surface: triggers specced, surfaces deferred (handoff §12); confirm still deferred at each of their gates. | 7d-2, 7g-1 gates |

# Cutover Register — old → new, delete-on-replacement

| Old | New | Dies at |
|---|---|---|
| `SplashScreen` splash half | cold-start loader 7l-1 | 7l-1 |
| `SplashScreen` `ChoiceScreen` | `WelcomeScreen` 7i-1 | 7i-1 |
| `OpenDuelsScreen` + route | deleted | P2-2 (done) |
| old `OnboardingScreen` (5 steps) | onboarding 1…7 (7i-2…7i-6) | 7i-6 |
| `create_challenge` flow | `create/1…7` (7d) | 7d-7 |
| `active_challenge/{pubkey}` | `duel/{id}` (7c) | 7c |
| `recovery_lock/{pubkey}` | `duel/{id}/result` locked (7f-2) | 7f-2 |
| `WonScreen`/`LostScreen` (unrouted) | `ResultScreen` template (7f) | 7f-2 |
| `DemoSeeder` + `demo-config.json` | deleted — real usage/empty states | P5-11 (done) |
| `ChallengeRepository` | `DuelRepository` | P5-3 (done) |
| `MockSeekerProvider` + `.skr` validation | `ProfilesApi` + `@handles` | P5-10 (done) |
| `Pot` component | Pool mark (§6.9) | P6-5 |
| `NopeekBottomBar` | `NopeekNavPill` | P2-3 (done) |
| `NopeekColors`/`NopeekType` legacy aliases | `Nopeek.colors`/`Nopeek.text` only | per-screen + P11-1 |
| "pot" in copy/identifiers | "pool" | per-screen + P11-1 |
