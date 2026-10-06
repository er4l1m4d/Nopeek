# Volo UI Rework Design Specification

**Status:** Draft for review — 2026-10-02 (rev. 2 — component/chart architecture revised after initial approval: shadcn-compose base + Vico charts)
**Supersedes:** `2026-09-29-volo-ui-design.md` (its light shell and Azeret Sans / Fragment Mono typography were overturned by user decisions on 2026-10-02; its product principles, state model, and accessibility rules are carried forward here)
**Scope:** Full visual rework of the existing 8 screens — restyle plus usage charts
**Platform:** Native Android, Kotlin, Jetpack Compose

## Locked Decisions

| Decision | Choice |
| --- | --- |
| Theme | Dark-only app (no light variant) |
| Display/body font | Satoshi (Fontshare, free, bundled TTF) |
| Numeric font | JetBrains Mono — numbers only |
| Icon pack | Phosphor Icons (Regular default, Fill for active) |
| Component base | shadcn-compose (`io.github.ronjunevaldoz:shadcn-compose`) + Volo wrappers |
| Charts | Vico (`com.patrykandpatrick.vico`) for bar/line; custom for ring/progress |
| Scope | Restyle existing screens + add usage charts |
| Review flow | All screens reviewed together |
| Driving skills | impeccable, ui-ux-pro-max; final gate UI/UX Design Review |

## Problem Statement

The current UI reads as default: system Roboto with no typography scale (73 scattered hardcoded `fontSize` calls across 8 screens), stock Material icons, a light paper theme fighting the dark duel theme, and no chart visuals despite the app being about screen time. This rework establishes one coherent dark design system and rebuilds every screen on it.

## Product Principles (carried from Sept 29 draft)

1. **Commitment before complexity.** Target app, daily limit, stake, opponent, and current state are legible at a glance.
2. **Crypto stays in the infrastructure layer.** Plain language (stake, locked, verified, settled); wallet details appear where trust requires them.
3. **Failure is legible.** PASSING, AT RISK, FAILED, UNVERIFIED, RECOVERY LOCKED never rely on color alone — always pair color with a label and icon.
4. **The phone is the product surface.** One-handed mobile use; 48dp minimum touch targets.
5. **Every important number has context.** `18m of 30m`, not `60%`; `5.00 USDC`, not raw token units.

## Visual System

### Color Tokens (dark-only)

Built from the existing `Duel` palette; the light Paper/White/Celadon-Clay shell is removed entirely.

| Token | Value | Role |
| --- | --- | --- |
| `bg` | `#10191D` | App background |
| `surface` | `#141E22` | Cards at rest |
| `surfaceRaised` | `#1A272B` | Elevated cards, sheets, inputs |
| `surfaceHigh` | `#213035` | Pressed/selected surfaces, chart wells |
| `line` | `#26363B` | Hairlines, borders, dividers |
| `text` | `#F2F4EE` | Primary text |
| `textMuted` | `#A4B0AF` | Secondary text |
| `copper` | `#D17A54` | Brand accent: primary actions, active nav, progress |
| `copperDeep` | `#B4603C` | Pressed copper |
| `safe` | `#B9D5C5` | PASSING state |
| `risk` | `#E0A268` | AT RISK state |
| `fail` | `#E08373` | FAILED / destructive state |

- Status color is never the only signal; pair with label + icon.
- Copper is the only accent. No secondary brand colors, no gradients except chart fills.
- Charts use copper for the user's data, `textMuted` for baseline/comparison, semantic colors only for state.

### Typography

- **Satoshi** (weights 400, 500, 700, 900) — all display, headings, body, labels.
- **JetBrains Mono** (400, 700) — exclusively for: usage values, countdowns, stakes, balances, wallet addresses, transaction fragments, dates-as-metadata. Never for sentences or navigation.
- One `MaterialTypography` scale in `Theme.kt` replaces every ad-hoc `fontSize`:

| Style | Font | Size / line | Use |
| --- | --- | --- | --- |
| `display` | Satoshi 900 | 34 / 38 | Screen hero statements (Duel title) |
| `headline` | Satoshi 900 | 28 / 32 | Screen titles |
| `title` | Satoshi 700 | 20 / 26 | Card titles, section heads |
| `titleSmall` | Satoshi 700 | 16 / 22 | Row titles |
| `body` | Satoshi 500 | 15 / 22 | Default text |
| `bodySmall` | Satoshi 500 | 13 / 18 | Supporting text |
| `label` | Satoshi 700, tracked | 12 / 16 | Eyebrow labels, chips (uppercase allowed) |
| `monoLarge` | JetBrains Mono 700 | 30 / 34 | Hero numbers (usage, scores) |
| `mono` | JetBrains Mono 500 | 15 / 20 | Stakes, timers, addresses |
| `monoSmall` | JetBrains Mono 500 | 12 / 16 | Metadata, chart ticks |

- Minimum body 15sp; minimum supporting 12sp with contrast ≥ 4.5:1 on `bg`/`surface`.
- Tabular numerals for all mono usage values.
- No all-caps paragraphs; uppercase only for short `label` eyebrows/chips.

### Spacing, Shape, Elevation

- Spacing scale: 4, 8, 12, 16, 20, 24, 32 dp. Screen horizontal padding 20dp (compact) / 24dp.
- Radii: controls 12dp, cards 18dp, sheets 24dp, chips pill.
- Elevation by surface tone (`surface` → `surfaceRaised` → `surfaceHigh`), not shadow. Hairline `line` border where a card must separate from `bg`.
- Bottom nav and center Create action clear system gesture insets.

## Icon System

- **Phosphor Icons**, replacing all Material icons.
  - `Regular` — default everywhere.
  - `Fill` — active/selected bottom-nav item, favorited/active states.
  - `Bold` — large feature icons only (empty states, hero card glyphs).
- Integration: `io.github.jinghu-moon.composeicons:icons-phosphor` (Maven Central, MIT, R8 tree-shaken — only referenced icons ship). Fallback if dependency is unsuitable: vendor individual ImageVector `.kt` files from composables.com.
- Replacements include at minimum: `Home`, `SportsScore` → `Flag`/`Swords`, `History` → `ClockCounterClockwise`, `Person` → `User`, `Add` → `Plus`, `ArrowForward` → `ArrowRight`, `Shield` → `ShieldCheck`, plus every icon across the 8 screens.
- Every icon-only action keeps a content description and 48dp touch target.

## Components

**Base layer: shadcn-compose.** Interactive primitives (button, input, badge/chip, card, sheet/dialog, skeleton, switch, tabs) come from `io.github.ronjunevaldoz:shadcn-compose`, themed by mapping its tokens (`--background`, `--card`, `--primary`, `--border`, `--muted`, radius scale) onto the Volo palette above. `ShadcnTheme` wraps `VoloTheme` so every primitive renders in Volo colors by default. All components take explicit state parameters and render preview/demo data only — no wallet/usage/oracle wiring in the UI layer.

**Composition rules:**

- Material 3 remains only as low-level infrastructure (Scaffold, Text, Icon, NavigationBar host) — no component derives its look from Material defaults; colors/typography always come from Volo tokens.
- Volo-specific components are thin wrappers that compose shadcn primitives with our content: they hold the spec'd anatomy (status chip + title + mono rows) but delegate button/input/card behavior to shadcn.
- shadcn-compose draws its own icons-free primitives; Phosphor supplies all icon ImageVectors independently.

**Volo component inventory (wrappers + custom):**

- **Shell:** `VoloScaffold`, `VoloBottomBar` (5 positions, active = Phosphor Fill icon + copper label), `CreateAction` (center circular copper FAB with Plus).
- **Actions:** `PrimaryButton` / `SecondaryButton` (shadcn Button + copper/outline variants, Satoshi 700, 12dp), `IconButton` (48dp target).
- **Status:** `StatusChip` (shadcn Badge base; pass/risk/fail variants: tinted bg + label + icon), `SelectChip` (selected = copper).
- **Cards:** `DuelCard` (hero: status + title + opponent + mono usage row + arrow), `MetricCard`, `InfoCard`, `RowCard`, `WalletIdentityRow` — shadcn Card base, Volo anatomy.
- **Data display:** `UsageRing` (custom Canvas progress, mono center), `OpponentUsageBar` (custom compare bar), `SectionHeader` (eyebrow + title), `MetricRow`.
- **Charts:** `DailyBarChart` (Vico columns; copper bars, muted gridline, mono ticks), `Sparkline` (Vico line), `UsageBreakdownBars` (custom rows: label + bar + mono duration).
- **States:** `EmptyState` (Bold Phosphor icon + copy + one action), `InlineError`, `LoadingSkeleton` (shadcn Skeleton, fixed dimensions).
- `SettlementSummary`, `SeekerBadge` (carried from Sept 29 inventory).

## Charts — Placement

| Screen | Charts |
| --- | --- |
| Home | `UsageRing` inside hero duel card (used vs limit) + weekly `DailyBarChart` (Vico) card |
| Activity | `DailyBarChart` (recent days, Vico) + `UsageBreakdownBars` (per-app, custom rows) |
| Duel | Dual `OpponentUsageBar` (you vs Maya, custom) + large mono countdown |

Tooling split: Vico renders Cartesian charts (columns/lines) with its axes/animation; ring, compare bars, and per-app row bars stay custom Canvas/layout composables so they match the tokens exactly. All charts render from demo/preview data; real usage wiring stays in existing view-model/repository seams.

## Screen Specifications (8 routes)

All screens: dark tokens only, typography scale only, Phosphor icons, bottom bar shell.

1. **Home** — mono date + Satoshi headline greeting; hero `DuelCard` (status chip, title, opponent, mono `18m / 30m`, stake, arrow); weekly bar chart card; metric tiles (usage, streak); usage-access `InfoCard`. Empty state when no active duel.
2. **Create** — headline + subline; stepwise form: app selector sheet, limit presets (15/30/60/120m + custom), duration presets, stake input (mono, USDC suffix, balance hint), opponent address (mono, resolve/error row); review block; primary submit. Validation errors in `fail`.
3. **Challenges** — filter chips (Active/Pending/Completed); list of `RowCard`s: app, opponent, window, mono stake, `StatusChip`. One framed item per row — no cards-in-cards.
4. **Activity** — date header; `DailyBarChart`; `UsageBreakdownBars` per app; chronological event list (icon, plain label, mono time, optional tx fragment).
5. **OpenDuels** — list of open challenge cards: app, limit, window, mono stake pool, join `PrimaryButton`; empty state.
6. **Duel (active)** — immersive peak: back + mono challenge id; Satoshi display title, opponent; big `UsageRing` with mono center `18m / 30m` plus mono countdown below; `StatusChip`; `OpponentUsageBar` with last-report time; mono stake/window meta; contextual primary action (Check usage / Submit report / Approve settlement).
7. **Profile** — wallet card (`WalletIdentityRow`, mono shortened address, connect/disconnect states); mono balance rows; usage-access status; Seeker badge (or honest unavailable state); privacy note; settings rows.
8. **RecoveryLock** — `fail`-accented warning treatment; mono countdown/lock metadata; explanation + single action.

### States to design per screen

- Home: no duel / passing / at risk / awaiting verification / settlement available / error+retry.
- Create: validation errors, resolving address, submitting, wallet prompt.
- Duel: passing / at risk / failed / unverified; opponent stale-data notice.
- Profile: wallet disconnected / connecting / connected / failed.
- All: loading skeleton, empty, inline error.

## Motion (carried from Sept 29 draft, trimmed)

- Screen transitions 220–280ms fade + directional slide; progress animates only on data change (500–700ms ease); status changes crossfade — no infinite pulses; wallet approval shows explicit pending state; respect reduced-motion (instant state changes only).

## Accessibility & Trust (carried)

- 48dp targets; icon-only actions described; status never color-only; addresses/signatures copyable; errors state the next action; loading preserves layout; no success shown before confirmation; no claims of verification/trustlessness while providers are mocked.

## Technical Notes

- **Dependencies to add** (verify latest versions + repo availability at implementation time):
  - `io.github.ronjunevaldoz:shadcn-compose` (KMP component base; uses the Compose Styles API — see risk below)
  - `com.patrykandpatrick.vico:compose-m3` (charts; Apache-2.0)
  - `io.github.jinghu-moon.composeicons:icons-phosphor` (Phosphor; MIT, R8 tree-shaken)
  - **Compatibility spike first:** shadcn-compose is young (0.2.x, experimental Styles API). Step 0 of implementation is a compile spike — add it, wrap one button in `ShadcnTheme`, run `assembleDebug`. If incompatible with the project's Kotlin/Compose versions, fall back to hand-built primitives styled identically per this spec (no design change, only implementation).
- Fonts: `res/font/` — Satoshi-Regular/Medium/Bold/Black (Fontshare TTF), JetBrainsMono-Regular/Medium/Bold (Google Fonts TTF). Wire via `FontFamily` in `Theme.kt`; remove `android:fontFamily=sans` from `styles.xml`.
- Replace `lightColorScheme` with `darkColorScheme`; `VoloTheme` = ShadcnTheme token mapping + `MaterialTypography` + shape, single wrapper composable.
- Delete stale light tokens after all screens migrate (no dead palette).

## Execution Order

0. Dependency spike: add shadcn-compose + Vico + Phosphor, confirm `assembleDebug` (fallback: hand-built primitives per spec).
1. Fonts + `Theme.kt` tokens + ShadcnTheme mapping + `MaterialTypography`.
2. Component layer: Volo wrappers over shadcn primitives + custom data-display components + chart components.
3. Screen-by-screen migration: Home → Duel → Create → Challenges → Activity → OpenDuels → Profile → RecoveryLock.
4. Bottom nav + icons migration.
5. Remove dead light-theme tokens and stray `FontFamily.Monospace`/hardcoded `fontSize`.

## Verification

- `gradlew assembleDebug` compiles after each phase.
- Visual QA per screen against this spec (states checklist above).
- Final audit with **UI/UX Design Review** skill (accessibility, contrast, consistency).
- User reviews all screens together before sign-off.

## Out of Scope

- Light theme variant; new routes beyond the existing 8; wallet/Solana/usage logic changes; i18n; feature work beyond the agreed charts; onboarding/marketing screens.
