# Nopeek — Handoff 2.4 revamp: bridge design

**Date:** 2026-10-18 · **Status:** awaiting review
**Baseline:** `nopeek-colosseum` @ `5ced56d`

## 0 · Sources of authority

| Document | Owns |
|---|---|
| `nopeek new design handoff/nopeek-master-handoff.html` (2.4) | Everything a user can see, touch or read: tokens, type, layout, navigation, 32 components, every screen, copy doctrine, motion ledger M-01…M-45, failure states, exits, architecture rulings (§15), notifications (§14), share cards (§17) |
| `nopeek new design handoff/nopeek-build-ops.html` (1.0) | Everything around the app: pins, Definition of Done D1–D8, per-screen checklist, the fifteen tests, cutover rules, runbook, events, performance budgets, licences, store |
| `nopeek new design handoff/nopeek-mark.html` | Icon and wordmark rules |

Where the two disagree, the design handoff's §Precedence decides. `nopeek-build-spec.md` (rev 2) and everything in `archive/` are superseded references, not sources. Implementation deviations go back into the handoff as a revision — never silently into code (handoff §13).

## 1 · What this document is

A bridge between the handoff and this repository: the verified repo baseline, the decisions taken, the phase skeleton with gates, the definition of "complete" per task type, the explicit out-of-scope list, and the carried risks. It is **not** the checklist. The checklist (implementation plan) is produced with the writing-plans process after this document is approved, and is where every task, file, step and "complete when" list lives.

## 2 · Verified baseline (2026-10-18, `nopeek-colosseum` @ `5ced56d`)

### 2.1 App UI

| Area | State |
|---|---|
| Shell | Docked Material `Scaffold` bottom bar with volt pill behind the selected icon — **not** the floating nav pill (§5) |
| Routes | `splash · choice · onboarding · home · duels · create_challenge · stats · profile · open_duels · active_challenge/{pubkey} · recovery_lock/{pubkey}`. Won/Lost screens exist but are **unroutable**. §5's route table replaces all of this; splash, choice, open_duels retire |
| Home | Hardcoded demo card + 3 `DemoRecent` rows. No gauge, no stats strip, no today rows, no verdict |
| Duels | 3 tabs over real Room `observeActive()` — closest to target, but old fixture-row format, `.skr` handles via `MockSeekerProvider`, no invite card, no per-tab empties |
| Live duel | Real own-usage via `UsageEventCollector`, scoreboard + shared tape + 1s countdown; opponent forever "–:––"; "Lock" is a no-op; forfeit pops nav |
| Create | 6 stepper steps + "Sent." state; approve writes Room with a fake tx hash; opponent is a raw paste field |
| Results | `WonScreen` (volt, pot glass) and `LostScreen` exist unrouted; `RecoveryLockScreen` routed but its copy is false vs the chain; no Steady, Forfeited, Voided |
| Stats | Fully hardcoded; no tabs, no scope selector, no history |
| Profile | Static; no identity row, no edit, no avatar, no sign-out, no delete |
| Onboarding | 5 steps, hardcoded demo state; no identity step, no wallet connect; "Open settings" is a no-op |
| Missing entirely | Accept sheet · interstitial · wallet connect · profile/edit · notifications · delete/sign-out/restore/keystore-check surfaces |
| Theme | Tier-1/2 tokens exist with legacy aliases (`NopeekColors`, `NopeekType`); **no** field gradients, no grain, no `xl 24` / `lg 28` shapes, no nav-pill/gauge dims; motion durations partial, one easing in `Motion.kt` |
| Design-system copies | `design system/tokens.json` + `design system/kotlin/*` drift from the app theme — Tokens v1.2 reconciles (app is the shipped source) |
| Copy | **No `strings.xml`** anywhere; copy hardcoded in composables; "pot" terminology throughout; `.skr` handle validation present |
| Icons | Phosphor subset (~23 vectors); new surfaces (wifi-off, chart, gauge glyphs…) need additions |

### 2.2 Architecture and backend (handoff §15 rulings)

| # | Ruling | Status in code |
|---|---|---|
| 1 | Nonce PDA `[challenge, nonce]` | **Not done** — seeds are still `[challenge, creator, opponent, nonce]`; opponent required at creation |
| 2 | Clock starts at join | **Not done** — times set in `create_challenge` |
| 3 | Decline + expire (config window) | **Not done** — only `refund_unjoined` after 3600 s, unwired |
| 4 | Forfeit (pool pays opponent) | **Not done** — no instruction; UI callback only pops nav |
| 5 | Void after 24 h grace; UNVERIFIED never loses | **Inverted** — UNVERIFIED currently counts as non-PASS; a lone UNVERIFIED side loses the pool |
| 6 | BOTH_PASS_REFUND → STEADY | Behavior exists (`BOTH_PASS_REFUND`); the name/screen do not |
| 7 | Recover after 7 days, per side | **Inverted** — `resolve_lock` sweeps the whole escrow to a treasury after 1 h |
| 8 | Daily accounting per UTC day | **Not done** — single limit vs single window; one-verified-report-per-side unique index blocks day-indexed reports |
| 9 | Profiles table + avatars bucket | **Not done** — no `profiles`, no bucket; handles are an app-side fiction (`MockSeekerProvider`) |
| 10 | Notifications local-only from polling | **Not done** — no notification code, no `POST_NOTIFICATIONS`; no FCM either (consistent) |
| 11 | Attestation invisible, folds into "Counting paused" | **Partial** — the attestation spine is real and deep; the pause language/state does not exist |
| 12 | Rename in place to `nopeek_duel` | **Not done** — crate, error enum and IDLs still `volo_challenge`; the same program ID (`DJftv46…`) stays |
| 13 | Delete account (money → records → key) | **Not done** — nothing anywhere |
| 14 | Sign out = client-side forget, blocked while funds held | **Not done** — only a raw MWA disconnect primitive, never called |

Program on devnet: `DJftv46AvjrbkbUCHR74ntWMXb5r7eHxHo3SXZkox2NM`, 5 instructions (`create_challenge · join_challenge · settle_challenge · resolve_lock · refund_unjoined`), `assets/idl.json` matches it exactly. Oracle routes live: `/health · /enroll · /nonce · /submit-report · /register-challenge · /settle · /evidence/`.

### 2.3 Test state

~240 JVM tests green (2026-10-17), plus the oracle Node suites. **T-01…T-05 and T-10 do not exist** (the new §15 instructions); T-06/T-11/T-12 exist under the old semantics and change with the void/day-accounting rework. Tests to extend rather than invent: `NopeekTokensTest`, `NopeekTypographyTest`, `ComponentContrastTest`, `InteractiveComponentContractTest`, `ChartMathTest`, `NavRoutesTest`.

## 3 · Decisions

- **D1 — Ordering 1.** `Preflight → Tokens → Shell → Contract v2 → Schema+oracle → App data layer → Components → Screens → Motion pass → Device pass → Extras → Close-out`. Tokens/shell depend on nothing and make the redesign visible early; screens still wait for the money spine, as build-ops §4 argues. Reordering changed only the sequence, never a dependency.
- **D2 — Cutover, screen-by-screen.** The app compiles and stays demoable at every task; each screen task deletes its old counterpart alongside. End state is exactly the docs' clean break: `splash · choice · open_duels · DemoSeeder` retired, "pot" purged (copy and identifiers), legacy theme aliases retired per screen, unreachable routes gone. Final sweep audits in Close-out.
- **D3 — Scope: full revamp.** Contract, schema/oracle, app data layer, all UI, motion, device passes, and every extra: notifications §14, share cards §17, invite landing, analytics events, perf budgets, store assets.
- **D4 — Execution.** Task-per-sitting, each ending with its "complete when" list ticked and evidence attached; the user reviews at **phase gates**, not every task. Full build-ops DoD discipline (D1–D8) on screen tasks and their equivalents elsewhere.
- **D5 — Conventions.** `:app:testDebugUnitTest --rerun` or it didn't run; never trust the Playground "Deployed" text (ProgramData slot must advance); commits stage explicit paths only (a parallel session works here); every motion cites its M-ID in code; copy in `strings.xml` from day one; zero raw hex outside `NopeekRef`; secrets never leave the edge function; when the build discovers a design contradiction, the handoff is patched as a revision.
- **D6 — Docs and branch.** Spec here; checklist at `docs/superpowers/plans/2026-10-18-nopeek-redesign-checklist.md`; execution branch `nopeek-v1` created at Phase 0.

## 4 · Phase skeleton and gates

| # | Phase | Builds | Phase gate — complete when |
|---|---|---|---|
| 0 | Preflight & guardrails | Branch, baseline test numbers recorded, `strings.xml` + i18n convention, OFL licence files into assets, cutover register (old → new, deletion-on-replacement), conventions doc | Baseline suites green and recorded; conventions in repo; branch created |
| 1 | Tokens v1.2 | Six field-gradient tier-1 values + 140dp grain drawable, nav/gauge/pool-mark/scene dims, `xl 24`/`lg 28` shapes, 4 easings + 6 durations, refreshed `tokens.json` + design-system kotlin export | Token/typography/contrast tests extended and green; design-system exports match the app; no raw hex outside `NopeekRef` |
| 2 | Shell | Floating nav pill (M-05), §5 route table, FAB at 92dp, edge-to-edge insets, transitions M-10/M-06, predictive back; old screens mounted provisionally in the new frame | App runs at 360 & 412dp on the new frame; route tests updated; no dead destination |
| 3 | Contract v2 (`nopeek_duel`) | Nonce PDA, clock-at-join, decline, expire (config window), forfeit, void (24h grace), recover (7-day, per side), rename in place, treasury sweep retired; T-01…T-05 + T-10 + suite + mutation teeth | New suite green; in-place upgrade proven by `lastDeploySlot` increase; `idl.json` + `assets/idl.json` updated in the same commit |
| 4 | Schema + oracle | Day-indexed reports, profiles + avatars bucket, events table, settle codes (STEADY/FORFEITED/VOIDED/DECLINED/EXPIRED), void/grace paths, delete-account endpoint (T-15 order), `ORACLE_API_SECRET` closed; T-06/T-11/T-12 reworked | Oracle suites green; migration applied and verified via PostgREST; deployed with upload-count proof |
| 5 | App data layer & identity spine | §7.0 model verbatim (`DuelState` ×9, `Duel`, `Profile`, `DayVerdict` + `verdictFor`), Room + repository, client instructions for all new ops, day-indexed report payload, keystore check (T-13, local-only), session/sign-out logic (T-14), wallet service (keystore keypair create/import + signing), profiles API + avatar upload + handle validation, **AUTH-1** (wallet-signed enroll/register writes), notification seams | Model/verdict/repository tests green including T-13/T-14 logic; no UI yet |
| 6 | Components (§6, 32 entries, by family) | The catalogue with semantics tests written first | `InteractiveComponentContractTest` + contrast tests green for the whole catalogue |
| 7 | Screens, verdict order | 7a Home · 7b Duels · 7c live duel · 7d Create 1–7 · 7e commit surfaces (accept sheet, forfeit dialog) · 7f results family (Won · Steady · Lost · Locked+recovery · Forfeit · Voided) · 7g Stats/History (+ scope trigger) · 7h Profile + edit · 7i welcome + onboarding 1–6 + wallet connect + identity · 7j interstitial · 7k failure states · 7l exits (splash, keystore check, restore sheet, permission step, before-you-go ×2, signed-out, delete ×4) | Each screen task: its build-ops §2.1 row + D1–D8 ticked, old counterpart deleted. Sub-phase gate: all its screens pass + device spot-check |
| 8 | Motion pass | Full ledger audit M-01…M-45, orchestrated moments, reduced-motion + haptics sweep | Every screen cites its M-IDs; reduced-motion and haptics behaviours verified |
| 9 | Device pass | 1.3× font sweep, TalkBack sweep, 60fps traces of §10.5 beats, OLED banding eyeball, 360/412dp sweep | Results recorded in the checklist; fixes filed and done |
| 10 | Extras | Notifications §14 · share cards §17 · invite landing · analytics events verification · perf budgets §7 · store listing §8 | Per sub-phase: §14 routing rules verified; cards render at all formats; budgets recorded in release notes; listing deliverables complete |
| 11 | Close-out | Grep audits ("pot" empty, no unreachable screens, no legacy aliases, no demo seeders, no raw hex), demo-data removal, README/runbook refresh, release notes | All audits clean; release build; handoff patched where reality forced deviations |

Scale, stated honestly: roughly **100 tasks across 12 phases**. The checklist doubles as the tracker — boxes ticked, evidence attached next to each task.

## 5 · "Complete" — per task type

Every task in the checklist follows one shape: **ID · name · depends-on · files · steps · "complete when" list · evidence to attach**. The gate for each type:

| Task type | "Complete when" is anchored to |
|---|---|
| Screen | Its build-ops §2.1 row (states, motion IDs, test) **and** D1–D8: layout at 360/412dp inside §4 budget · zero raw hex · loaded/empty/error/offline + own failure branch render · contrast test passes on the real composables (lightest gradient point) · motion IDs exist, retarget, collapse under reduced motion · copy in `strings.xml`, no concatenation, pseudo-locale check · TalkBack announces state not decoration, slider custom action, ≥48dp targets · defined events fire |
| Component | Semantics test written first + `InteractiveComponentContractTest` + contrast on its pairs; motion map from §10.4 |
| Contract | The named T-test(s) green + mutation teeth (break the guard once, watch it go red) + deploy proof (slot advance) |
| Schema/oracle | Named suites green + applied-and-verified evidence (PostgREST probe, upload count) |
| Data layer | Named unit tests (incl. T-13/T-14 logic) + no UI assumptions |
| Tokens/infra | The relevant test file extended and green + command output recorded |
| Extras | The sub-phase's own list (routing rules, formats, budgets, listing deliverables) |

Evidence rule, everywhere: commands and their output lines, screenshots at 360/412dp, deploy slot, upload count — **evidence before assertions**.

## 6 · Scope boundaries

**In scope:** everything in §4 above — the full revamp of the app, its contract, oracle, schema, and the listed extras.

**Explicitly out of scope** (per handoff §12 deferred list, unless the user promotes one):
light theme · chart-bar → History interaction · settings sub-pages behind Notifications/Appearance/Help rows · FCM push · real-funds / mainnet gate (including "stake" wording review) · marketing colour · stuck-transaction escape hatch · daily summary notification · notification settings sub-page · profanity filter · themed-icon device check · username reservation changes.

**Untouched:** `nopeek-film/`, `docs/nopeek-ui-ux-audit.md` (historical), `archive/`, the old Colosseum plan.

## 7 · Risks and carried items

- **Solana Playground constraints.** Anchor 0.29-era toolchain, `init_if_needed`; deploys in place of the existing program ID; the slot-advance verification rule from build-ops §1.4 is the only accepted deploy proof.
- **Known inversions to fix, not port.** `resolve_lock → treasury` becomes recover-after-7-days; the UNVERIFIED-loses path becomes pause → void. Both are behavior replacements, not additions.
- **AUTH-1** (unauthenticated `/enroll` + `/register-challenge` writes) lands with the wallet signer in Phase 5; `ORACLE_API_SECRET` gap closes in Phase 4.
- **Wallet model change.** §7.14/§7.15 make a keystore-resident devnet keypair the default wallet (create/import), with Seeker muted "coming soon" — MWA is no longer the walking path.
- **Expiry window is config**, not a constant (the accept sheet's "6 hours" is copy, not the source of truth).
- **Device availability.** Phase-9 and sub-phase spot-checks assume the Redmi; MIUI needs the wake/dismiss-keyguard trick and `appops set … GET_USAGE_STATS allow` re-applied after every reinstall.
- **Parallel session.** Film/design artifacts stay untracked by intent; commit with explicit paths only.
- **Design-file quirks.** The handoff's §6 entry numbering has small internal quirks (two entries commented `9`, no `31`); the checklist references components by name to stay unambiguous.

## 8 · Campaign success criteria

The revamp is done when: every §7 screen passes its §2.1 row and D1–D8; T-01…T-15 are green across contract, oracle and repo (mutation-gated where build-ops §3 requires); the program is upgraded in place with slot proof and the IDLs are in sync; the schema is re-applied with the new shape and the oracle is deployed; the device pass is recorded; all extras are complete; the close-out audits are clean; and a release build ships.

## 9 · Next steps

1. **You review this spec** — approve or request changes (this gate).
2. On approval: writing-plans produces `docs/superpowers/plans/2026-10-18-nopeek-redesign-checklist.md` — every phase, task and "complete when" list — for your approval.
3. On checklist approval: Phase 0 begins.
