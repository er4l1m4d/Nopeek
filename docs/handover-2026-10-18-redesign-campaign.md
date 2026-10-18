# Handoff — Nopeek Handoff-2.4 Redesign Campaign

**Date:** 2026-10-18 · **For:** a fresh session/chat · **Reply language:** English

## 1 · Mission

Take the Nopeek Android app (`com.nopeek.app`, Kotlin/Compose), its Solana **devnet** program, and its Supabase oracle from "old-style UI, pre-2.4 spine" to the **Handoff 2.4 design** end-to-end — every screen, component, motion, and money path, plus notifications, share cards, invite landing, analytics events, store assets — through a 12-phase plan.

The repo owner approved: **full revamp scope**, delete-on-replacement screen cutover (each old screen dies in the same task its replacement lands), and **native execution** (the agent executes task-by-task in this repo; the owner reviews at phase gates).

## 2 · Repo state (verified 2026-10-18)

- Working dir: `C:\Users\hp\Documents\my builds\lokkin for now` (Windows · PowerShell · temp dir: `C:\Users\hp\AppData\Local\Temp\opencode`)
- Remote: `https://github.com/er4l1m4d/Nopeek.git`
- **Trunk: `nopeek-colosseum` @ `a4bfd47`** — pushed to origin. Contains *everything* (v1.0 lineage, dark-UI rework, Nopeek rename, Colosseum devnet execution, redesign spec + checklist).
- `main` @ `6bf071c` — GitHub default branch; `v1.0` tag lineage; intentionally untouched.
- Housekeeping done: `plan-execution`, `volo-system-port`, `build/sbf-ci` deleted from local; `origin/plan-execution` + `origin/build/sbf-ci` deleted on GitHub. All content was verified contained in the trunk first.
- Leftover: `.github/workflows/build-program.yml` sits **untracked** in the tree (owner chose "no import"; leave it alone unless asked).
- **A parallel session works in this tree** (e.g. `nopeek-film/`). NEVER `git add -A`; stage explicit paths only.
- Next branch to create: **`nopeek-v1`** from `a4bfd47` (Phase 0, task P0-1).

## 3 · Governing documents (read before any work)

1. **`docs/superpowers/plans/2026-10-18-nopeek-redesign-checklist.md` — the plan and tracker.** 12 phases, ~105 tasks; every task = Files · Produces · steps · **"Complete when"** · Evidence. Also contains: Global Constraints (all build pins + spec values), Review Focus (RF-1…RF-5, each with its test pinned to an owning task), Carried Decisions A–E, Cutover Register.
2. `docs/superpowers/specs/2026-10-18-nopeek-redesign-design.md` — the bridge spec (why each decision was made).
3. Design + process sources: `nopeek new design handoff/nopeek-master-handoff.html` (**Handoff 2.4 — wins all user-visible questions**) · `nopeek-build-ops.html` (process: DoD D1–D8, tests T-01…T-15, per-screen §2.1 rows, §4 cutover, §6 events, §7 budgets, §8 store) · `nopeek-mark.html` (mark rules) · `nopeek-build-spec.md` (superseded rev-2, reference only) · `archive/` (frozen old mockups, not sources).
4. Do **not** redo brainstorming/spec/plan — they are committed. When starting work, load the **superpowers:executing-plans** skill (the checklist was produced by writing-plans).

## 4 · Execution rules (locked)

- Native mode: agent executes task-by-task here; owner reviews at **phase gates**; a gate passes only when its tasks + gate line are ticked with evidence. Do not cross a gate uninvited.
- Every phase opens with its **"Phase open"** step: re-verify each path/signature named in that phase against HEAD; if drift, fix the checklist first, then code.
- Commits cite the task ID (`feat(ui): nav pill (P2-1)`); stage explicit paths only.
- Tests: `.\gradlew.bat :app:testDebugUnitTest --rerun` (rerun mandatory — without it Gradle lies) · oracle: `node --test "supabase/functions/oracle/*.test.ts"` · tsc gate for the edge function.
- The app builds at every task (`:app:assembleDebug`).
- When implementation contradicts the handoff, the handoff gets a revision (closed at P11-4) — never silent divergence in code.

## 5 · Deploy / discipline facts

- Program (devnet): `DJftv46AvjrbkbUCHR74ntWMXb5r7eHxHo3SXZkox2NM`; crate renamed in place `volo_challenge` → `nopeek_duel` (P3-1).
- **Only accepted deploy proof:** `lastDeploySlot` (ProgramData bytes 4–12, LE u64, read over RPC) **increases**. Baseline from the last deploy: **511960495**. Never trust the Playground UI's "Deployed" text.
- Playground CLI is **0.29.0** (older anchor-lang; `init_if_needed`, not `init_if_missing`).
- IDLs — `contracts/program/idl.json` and `app/src/main/assets/idl.json` — must ship structurally identical in the deploy commit.
- Secrets (service_role, `ORACLE_API_SECRET`, deploy keypair) never in the app, screenshots, or docs.
- Documented sync gap: after P3-9's contract deploy, the app's chain layer is stale until Phase 5 completes — don't demo chain flows in that window.

## 6 · Money-spine corrections baked into the plan (enforce them)

- `resolve_lock → treasury` (old: sweeps escrow to treasury after 1h) becomes: LOCKED → after **7-day** cooldown, each side withdraws its **own** stake once (T-04).
- UNVERIFIED must never settle as a loss: **PAUSED → 24h grace → VOIDED** (both stakes returned) (T-05/T-06).
- Clock starts at **join**, not create (T-10).
- Reports are **day-indexed**; PASS only if every UTC day passed (T-11).
- `BOTH_PASS_REFUND` renamed **STEADY**; code set = WON / LOST / STEADY / LOCKED / FORFEITED / VOIDED / DECLINED / EXPIRED.
- Handles are `@handle` (not `.skr`); profiles + public avatars bucket are new.
- "pool", never "pot" (copy and identifiers).
- Gauge: 270° sweep from 135°, 196dp diameter / 14dp stroke / radius 80; opponent marker = **their 20dp avatar** on the track (amended §6.9 — supersedes §4's stale "7dp disc"; P11-4 records the handoff revision).
- Field gradients recipe (§2): radial `135% 86% at (50%, −14%)`, stops `Hi 0% → Lo 74%`, grain 140dp @ 0.18 overlay — mandatory on every gradient (OLED banding).
- Slider (the system's only sanctioned danger fill): completed danger label turns **coral** (permitted only because the completed control is disabled); stake-variant completed label stays volt.

## 7 · Traps

- Parallel session in the tree → explicit-path staging, always.
- MIUI/Redmi (Phase 9 device passes): `KEYCODE_WAKEUP` + `wm dismiss-keyguard` before install; re-apply `appops set com.nopeek.app GET_USAGE_STATS allow` after every reinstall.
- `docs/handover-2026-10-12-nopeek-colosseum.md` is stale — trust git log over it.
- §6 component numbering quirks (two "9"s, no "31") — reference components by name.
- AUTH-1 lands in two halves: server P4-7, client P5-9. `ORACLE_API_SECRET` enforcement: P4-8 (server) + P5-9 (app header).
- Two money inversions were found in pre-flight (see §6 bullets 1–2) — don't reintroduce them via "old habits".

## 8 · Carried decisions (owner gates)

- **A** — Onboarding scene count: 2.4 changelog says *seven* scenes; §5 route line says `1…6`. Resolve at 7i phase open against §7.2 specimens; patch the handoff if it disagrees with itself.
- **B** — `join_window` default **6h** (matches the accept-sheet copy) — confirm at the P3-4 gate; it is config, not a constant.
- **C** — Void-abuse rate limiting: parameters undecided → NOT implemented in v1 unless promoted; oracle monitors UNVERIFIED %.
- **D** — §6 components referenced by name (numbering quirks).
- **E** — Deferred surfaces (Stats scope-dropdown, custom-limit keypad) stay deferred unless promoted at their gates (7d-2, 7g-1).

## 9 · What is already done (this campaign)

- Explored handoff + repo; recorded baseline gaps and the two money inversions (§6).
- Owner approved ordering 1 (tokens → shell → contract → schema/oracle → app data → components → screens → motion → device → extras → close-out), full scope, native execution, delete-on-replacement cutover.
- Committed spec (`ddce99e`) and checklist (`a4bfd47`), with self-review corrections applied (field-gradient recipe, gauge avatar marker, slider coral rule, deploy baseline 511960495, CLI 0.29.0).
- Branch cleanup + push to GitHub (see §2).

## 10 · Immediate next step

Owner says **go** → **Phase 0** (details in the checklist):

- **P0-1** — create branch `nopeek-v1` from `a4bfd47`; run the four baseline gates (`:app:assembleDebug`, `:app:testDebugUnitTest --rerun`, oracle `node --test`, tsc); record counts + HEAD hash.
- **P0-2** — `app/src/main/res/values/strings.xml` (default locale, `app_name` only) + i18n convention.
- **P0-3** — ship OFL licence texts into `app/src/main/assets/licenses/`; verify Satoshi / JetBrains Mono unreferenced.
- **P0-4** — `SourceHygieneTest` ratchet (raw-hex + "pot" scan, shrink-only allowlist; mutation-check it).

Then Phase 1 (Tokens v1.2), one task per working sitting, evidence per task, stop at each phase gate for owner review.
