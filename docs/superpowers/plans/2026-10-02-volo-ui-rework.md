FALLBACK: hand-vendored Phosphor ImageVectors from composables.com (icons-phosphor 0.1.0 not on Maven Central).
FALLBACK: hand-built primitives per spec (shadcn-compose 0.2.7 pulls Compose 1.12.0 + Kotlin 2.4.0 stdlib, incompatible with AGP 8.6.1 / compileSdk 35 / Kotlin 2.2.21; not used in spike).
FALLBACK: hand-rolled charts per spec (vico 3.3.1 transitively requires Compose 1.12.0 + Kotlin 2.4.10 + AGP 9.1.0 / compileSdk 37, incompatible with AGP 8.6.1 / compileSdk 35 / Kotlin 2.2.21; not used in spike).

# Volo UI Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Volo's entire UI on a dark-only design system (Satoshi + JetBrains Mono, Phosphor icons, shadcn-compose primitives, Vico charts) across all 8 screens.

**Architecture:** Foundation tasks establish tokens/typography/status logic as pure, JVM-tested Kotlin; component tasks build Volo wrappers over shadcn-compose primitives (fallback: hand-built, same tokens); screen tasks migrate one screen per task onto the component layer; final tasks wire navigation, sweep out dead code, and run the design review.

**Tech Stack:** Kotlin 2.2.21, AGP 8.6.1, Compose BOM 2024.12.01, Material3 (infrastructure only), shadcn-compose 0.2.7, Vico 3.3.1, compose-icons Phosphor 0.1.0, JUnit 4 (JVM tests only — project has no androidTest/emulator harness).

**Spec:** `docs/superpowers/specs/2026-10-02-volo-ui-rework-design.md` (rev. 2)

## Global Constraints

- Dark-only: no light theme; all new colors come from `VoloColors`.
- Satoshi = display/body; JetBrains Mono = numbers only (never sentences/navigation).
- Minimum body 15sp, minimum supporting 12sp, touch targets ≥ 48dp.
- Status never color-only: every status chip has a non-empty label.
- Copper `#D17A54` is the only accent (`VoloColors.copper`).
- Radii: controls 12dp, cards 18dp, sheets 24dp, chips pill. Spacing: 4/8/12/16/20/24/32dp.
- No component derives its look from Material defaults; Phosphor for all icons (Regular default, Fill active, Bold feature/empty states).
- UI layer renders demo/preview data only — no wallet/usage/oracle wiring.
- Every task ends with `gradlew assembleDebug` AND `gradlew testDebugUnitTest` green, then one commit.
- Stage only that task's files (`git add <paths>`) — never `git add -A`: the repo has untracked `inspos/*` and spec files that are not part of this work.
- Commit messages: plain sentences in existing history style, e.g. `UI rework: <what changed>`.

## Review Focus

1. **shadcn-compose vs project toolchain** (Compose Styles API, experimental) — Task 0 compiles a minimal `ShadcnTheme` + button + Vico chart + Phosphor icon spike; expected: build green, otherwise documented fallback (hand-built primitives / hand-rolled charts / vendored ImageVectors) with zero design change.
2. **Contrast failures on dark surfaces** — Task 2 `VoloTokensTest` asserts WCAG ≥ 4.5:1 for `text/bg`, `textMuted/bg`, and each chip pair (dark text on safe/risk/fail/copper containers).
3. **Status chips losing labels (color-only)** — Task 3 `StatusTest` pins exact label+colors for all 6 lifecycle strings and 4 usage states, and asserts every variant label is non-empty; unknown status falls through to a neutral variant, never a crash.
4. **Mono misuse / wrong font weights** — Task 1 `VoloTypographyTest` pins every style's size, weight, and family against the spec table; Task 16 grep gate: zero `FontFamily.Monospace` and zero raw `fontSize =` outside `theme/Type.kt`.
5. **Migration leftovers** (light tokens, Material icons, old UiKit) — Task 16 grep gates: zero matches for `Icons\.`, light token names (`Paper|SurfaceWhite|SurfaceMuted|Celadon|Clay|\bInk\b|\bMuted\b|\bCopper\b|CopperDeep`), `statusLabel|statusColors`, and `import com.volo.app.ui.StatusChip` style imports; then full clean build.

## File Structure

**Create:**
- `app/src/main/res/font/` — `satoshi_regular.ttf`, `satoshi_medium.ttf`, `satoshi_bold.ttf`, `satoshi_black.ttf`, `jetbrainsmono_regular.ttf`, `jetbrainsmono_medium.ttf`, `jetbrainsmono_bold.ttf`
- `app/src/main/java/com/volo/app/ui/theme/Tokens.kt` — `object VoloColors`, `object VoloDimens`
- `.../ui/theme/Type.kt` — `object VoloType`, `val VoloTypography: Typography`
- `.../ui/components/Status.kt` — `StatusVariant`, `UsageState`, `lifecycleVariant`, `usageVariant`
- `.../ui/components/Buttons.kt` — `PrimaryButton`, `SecondaryButton`, `TapIconButton`
- `.../ui/components/Chips.kt` — `StatusChip`, `SelectChip`
- `.../ui/components/Cards.kt` — `DuelCard`, `MetricCard`, `InfoCard`, `RowCard`
- `.../ui/components/StateViews.kt` — `EmptyState`, `InlineError`, `LoadingSkeleton`
- `.../ui/components/DataDisplay.kt` — `UsageRing`, `OpponentUsageBar`, `SectionHeader`, `MetricRow`
- `.../ui/components/Charts.kt` — `DailyBarChart`, `Sparkline`, `UsageBreakdownBars`, `UsageBarItem`, `fractionOf`, `sweepDegrees`
- `.../ui/components/Shell.kt` — `VoloScaffold`, `VoloBottomBar`, `CreateAction`, `navIcon`
- Tests: `.../ui/theme/VoloTypographyTest.kt`, `.../ui/theme/VoloTokensTest.kt`, `.../ui/components/StatusTest.kt`, `.../ui/components/ChartMathTest.kt`, `.../navigation/NavIconMappingTest.kt`

**Modify:** `app/build.gradle.kts`, `ui/theme/Theme.kt`, `ui/UiKit.kt` (until deleted), `navigation/NavGraph.kt`, all 8 files in `ui/screens/`, `ui/components/{StakeInput,LimitSelector,AppSelector}.kt`, `res/values/styles.xml`.

**Delete (Task 16):** `ui/UiKit.kt`, light color vals in `Theme.kt`, `statusLabel`/`statusColors` in `ChallengesScreen.kt`.

**Status quo note:** screens keep compiling against the untouched old `UiKit.kt` + light tokens until their own migration task; dual systems coexist deliberately mid-plan.

---

### Task 0: Dependency spike

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/volo/app/ui/DependencySpike.kt` (throwaway)

**Interfaces:**
- Produces: resolved dependencies `io.github.ronjunevaldoz:shadcn-compose:0.2.7`, `com.patrykandpatrick.vico:compose:3.3.1`, `io.github.jinghu-moon.composeicons:icons-phosphor:0.1.0` (verify latest versions at execution; bump if newer patch exists).

- [ ] **Step 1: Add the three dependencies to `app/build.gradle.kts`** (plain `implementation(...)` lines; `settings.gradle.kts` already has mavenCentral).
- [ ] **Step 2: Write `DependencySpike.kt`** containing one `@Composable fun DependencySpikePreview()` with `@Preview` that renders: a shadcn button inside `ShadcnTheme`, one Phosphor icon (`PhosphorIcons.Regular.House`), and one Vico column chart — exact APIs from each library's README.
- [ ] **Step 3: Build**
  Run: `gradlew assembleDebug` — Expected: green. If shadcn-compose fails (Styles API/Kotlin mismatch): remove that dependency, add a fallback note at the top of this plan file (`FALLBACK: hand-built primitives per spec`), and continue — the spike still keeps Vico + Phosphor. Same pattern if Vico fails (hand-rolled charts) or Phosphor fails (vendor ImageVectors from composables.com).
- [ ] **Step 4: Run unit tests**
  Run: `gradlew testDebugUnitTest` — Expected: PASS (existing 19 test files unaffected).
- [ ] **Step 5: Delete `DependencySpike.kt`, rebuild** — `gradlew assembleDebug` green proves deps resolve without the spike code.
- [ ] **Step 6: Commit**
  `git add app/build.gradle.kts` (+ plan-file fallback note if written) → `git commit -m "UI rework: add shadcn-compose, Vico, Phosphor dependencies after spike"`

### Task 1: Fonts + typography scale

**Files:**
- Create: `app/src/main/res/font/*.ttf` (7 files), `.../ui/theme/Type.kt`
- Modify: `.../ui/theme/Theme.kt`
- Test: `.../ui/theme/VoloTypographyTest.kt`

**Interfaces:**
- Produces: `object VoloType` with styles `display, headline, title, titleSmall, body, bodySmall, label, monoLarge, mono, monoSmall` (each `TextStyle`); `val Satoshi: FontFamily`; `val Mono: FontFamily`; `val VoloTypography: Typography` (Material slots filled from the same styles so Material defaults align).

- [ ] **Step 1: Download fonts**
  - Satoshi: download the free zip from `fontshare.com/fonts/satoshi`; take Regular/Medium/Bold/Black TTFs → `res/font/satoshi_regular.ttf`, `satoshi_medium.ttf`, `satoshi_bold.ttf`, `satoshi_black.ttf` (lowercase filenames, Android resource rules).
  - JetBrains Mono: download the release zip from `github.com/JetBrains/JetBrainsMono/releases`; take static Regular/Medium/Bold → `jetbrainsmono_regular.ttf`, `jetbrainsmono_medium.ttf`, `jetbrainsmono_bold.ttf`.
- [ ] **Step 2: Write the failing test** — `VoloTypographyTest` table-driven over the spec's exact table:

```kotlin
private data class Row(val name: String, val style: TextStyle, val size: Int, val line: Int, val weight: FontWeight?, val mono: Boolean)
@Test fun everyStyleMatchesSpec() {
    val rows = listOf(
        Row("display", VoloType.display, 34, 38, FontWeight.Black, false),
        Row("headline", VoloType.headline, 28, 32, FontWeight.Black, false),
        Row("title", VoloType.title, 20, 26, FontWeight.Bold, false),
        Row("titleSmall", VoloType.titleSmall, 16, 22, FontWeight.Bold, false),
        Row("body", VoloType.body, 15, 22, FontWeight.Medium, false),
        Row("bodySmall", VoloType.bodySmall, 13, 18, FontWeight.Medium, false),
        Row("label", VoloType.label, 12, 16, FontWeight.Bold, false),
        Row("monoLarge", VoloType.monoLarge, 30, 34, FontWeight.Bold, true),
        Row("mono", VoloType.mono, 15, 20, FontWeight.Medium, true),
        Row("monoSmall", VoloType.monoSmall, 12, 16, FontWeight.Medium, true),
    )
    rows.forEach { r ->
        assertEquals("${r.name} size", r.size.toFloat(), r.style.fontSize.value)
        assertEquals("${r.name} line", r.line.toFloat(), r.style.lineHeight.value)
        assertEquals("${r.name} weight", r.weight, r.style.fontWeight)
        assertEquals("${r.name} family", if (r.mono) Mono else Satoshi, r.style.fontFamily)
    }
}
```

- [ ] **Step 3: Run test → FAIL** (compile error: `VoloType` unresolved). Run: `gradlew testDebugUnitTest --tests "*VoloTypographyTest*"`
- [ ] **Step 4: Implement `Type.kt`** — `Satoshi = FontFamily(Font(R.font.satoshi_regular), Font(R.font.satoshi_medium), Font(R.font.satoshi_bold), Font(R.font.satoshi_black))`; `Mono` likewise; the 10 styles with exact values above (`fontWeight = FontWeight.Black` = 900 for display/headline); `label` gets `letterStyle` tracking 1.sp; `VoloTypography` maps styles into Material slots (`displayLarge = display`, `headlineLarge = headline`, `titleLarge = title`, `titleMedium = titleSmall`, `bodyLarge = body`, `bodyMedium = bodySmall`, `labelLarge = label`, …).
- [ ] **Step 5: Run test → PASS**, then wire `Theme.kt`: `MaterialTheme(colorScheme = …, typography = VoloTypography, content = …)`.
- [ ] **Step 6: `gradlew assembleDebug` + `gradlew testDebugUnitTest` green**
- [ ] **Step 7: Commit** — `git add app/src/main/res/font app/src/main/java/com/volo/app/ui/theme app/src/test/java/com/volo/app/ui/theme` → `git commit -m "UI rework: bundle Satoshi + JetBrains Mono and add the typography scale"`

### Task 2: Color/dimension tokens + dark scheme + shadcn mapping

**Files:**
- Create: `.../ui/theme/Tokens.kt`
- Modify: `.../ui/theme/Theme.kt`
- Test: `.../ui/theme/VoloTokensTest.kt`

**Interfaces:**
- Produces: `object VoloColors` — `bg #10191D, surface #141E22, surfaceRaised #1A272B, surfaceHigh #213035, line #26363B, text #F2F4EE, textMuted #A4B0AF, copper #D17A54, copperDeep #B4603C, safe #B9D5C5, risk #E0A268, fail #E08373`; `object VoloDimens` — `spaceXs=4, spaceSm=8, spaceMd=12, spaceLg=16, spaceXl=20, spaceXxl=24, spaceXxxl=32` (dp), `radiusControl=12, radiusCard=18, radiusSheet=24, radiusPill=999` (dp). `VoloTheme` gains a dark `darkColorScheme` built from these + wraps content in shadcn's theme with tokens mapped 1:1 to `VoloColors` (if shadcn present; else no-op wrapper).

- [ ] **Step 1: Write failing test** `VoloTokensTest`:
  - `assertEquals(Color(0xFF10191D), VoloColors.bg)` … all 12 colors exactly as above.
  - All 7 spacing + 4 radius values exactly as above.
  - Contrast helper (WCAG relative luminance) + assertions: `contrast(text, bg) >= 4.5`, `contrast(textMuted, bg) >= 4.5`, and `contrast(VoloColors.bg, container) >= 4.5` for `container in listOf(safe, risk, fail, copper)` (chips render dark text on those containers).
- [ ] **Step 2: Run → FAIL** (`VoloColors` unresolved)
- [ ] **Step 3: Implement `Tokens.kt`** exactly per Interfaces; **modify `Theme.kt`**: keep existing light vals untouched (screens still use them), add `private val VoloDarkScheme = darkColorScheme(...)` from `VoloColors`, change `VoloTheme` to `MaterialTheme(VoloDarkScheme, typography = VoloTypography) { ShadcnThemeMapping { content() } }`.
- [ ] **Step 4: Run test → PASS**; check the existing 19 test files still pass (`gradlew testDebugUnitTest`) — Theme changes must not touch data logic.
- [ ] **Step 5: `gradlew assembleDebug` green**
- [ ] **Step 6: Commit** → `git commit -m "UI rework: add VoloColors/VoloDimens tokens and switch VoloTheme to dark-only"`

### Task 3: Status system

**Files:**
- Create: `.../ui/components/Status.kt`
- Test: `.../ui/components/StatusTest.kt`

**Interfaces:**
- Produces: `data class StatusVariant(val label: String, val container: Color, val content: Color)`; `enum class UsageState { PASSING, AT_RISK, FAILED, UNVERIFIED }`; `fun lifecycleVariant(localStatus: String): StatusVariant`; `fun usageVariant(state: UsageState): StatusVariant`.
- Replaces (in Task 11): `statusLabel` / `statusColors` in `ChallengesScreen.kt`.

- [ ] **Step 1: Write failing test** `StatusTest`:

```kotlin
@Test fun lifecycleLabelsAndColorsMatchSpec() {
    assertEquals(StatusVariant("ACTIVE", VoloColors.safe, VoloColors.bg), lifecycleVariant("ACTIVE"))
    assertEquals(StatusVariant("PENDING", VoloColors.copper, VoloColors.bg), lifecycleVariant("CREATED"))
    assertEquals(StatusVariant("VERIFY", VoloColors.risk, VoloColors.bg), lifecycleVariant("PENDING_VERIFICATION"))
    assertEquals(StatusVariant("LOCKED", VoloColors.fail, VoloColors.bg), lifecycleVariant("BOTH_FAIL_LOCKED"))
    assertEquals(StatusVariant("SETTLED", VoloColors.surfaceHigh, VoloColors.textMuted), lifecycleVariant("SETTLED"))
    assertEquals(StatusVariant("REFUNDED", VoloColors.surfaceHigh, VoloColors.textMuted), lifecycleVariant("REFUNDED"))
    assertEquals(lifecycleVariant("SOMETHING_NEW").label, "SOMETHING_NEW") // passthrough, neutral colors
    assertEquals(VoloColors.surfaceHigh, lifecycleVariant("SOMETHING_NEW").container)
}
@Test fun usageVariantsMatchSpec() {
    assertEquals(StatusVariant("PASSING", VoloColors.safe, VoloColors.bg), usageVariant(UsageState.PASSING))
    assertEquals(StatusVariant("AT RISK", VoloColors.risk, VoloColors.bg), usageVariant(UsageState.AT_RISK))
    assertEquals(StatusVariant("FAILED", VoloColors.fail, VoloColors.bg), usageVariant(UsageState.FAILED))
    assertEquals(StatusVariant("UNVERIFIED", VoloColors.surfaceHigh, VoloColors.textMuted), usageVariant(UsageState.UNVERIFIED))
}
@Test fun noVariantIsColorOnly() {
    UsageState.entries.forEach { assertTrue(usageVariant(it).label.isNotBlank()) }
    listOf("CREATED","ACTIVE","PENDING_VERIFICATION","BOTH_FAIL_LOCKED","SETTLED","REFUNDED","x")
        .forEach { assertTrue(lifecycleVariant(it).label.isNotBlank()) }
}
```

- [ ] **Step 2: Run → FAIL**; **Step 3: implement `Status.kt`** (when-tables; unknown → passthrough label with `surfaceHigh`/`textMuted`).
- [ ] **Step 4: Run → PASS**; **Step 5: assembleDebug green**; **Step 6: Commit** → `git commit -m "UI rework: add tested status-variant system"`

### Task 4: Buttons + chips

**Files:**
- Create: `.../ui/components/Buttons.kt`, `.../ui/components/Chips.kt`

**Interfaces:**
- Consumes: `VoloColors`, `VoloDimens`, `VoloType`, `StatusVariant` (Task 3), shadcn button/badge primitives (Task 0; fallback: Material3 `Surface`/`Text` styled from tokens).
- Produces:
  - `@Composable fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)` — copper container, `bg` text, Satoshi 700 15sp, radius `radiusControl`, height ≥ 48dp.
  - `@Composable fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)` — transparent, 1dp `line` border, `text` label.
  - `@Composable fun TapIconButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit)` — 48dp min target (named to avoid clashing with Material's `IconButton`).
  - `@Composable fun StatusChip(variant: StatusVariant, modifier: Modifier = Modifier)` — pill, `variant.container` bg, `variant.content` text, Satoshi 700 10–12sp, tracked, optional leading Phosphor icon per status (Regular weight).
  - `@Composable fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)` — pill; selected = copper bg + `bg` text; unselected = `surfaceRaised` + `textMuted`.

- [ ] **Step 1: Implement both files with `@Preview`s for every composable.**
- [ ] **Step 2: `gradlew assembleDebug` green** (old `UiKit.kt` untouched — same names live in a different package).
- [ ] **Step 3: `gradlew testDebugUnitTest` green** (status tests cover chip colors).
- [ ] **Step 4: Commit** → `git commit -m "UI rework: add button and chip components"`

### Task 5: Cards + state views

**Files:**
- Create: `.../ui/components/Cards.kt`, `.../ui/components/StateViews.kt`

**Interfaces:**
- Consumes: `StatusVariant`, `StatusChip` (Task 3/4), `VoloColors/Dimens/Type`.
- Produces (all with `@Preview`, radius `radiusCard`, `surface`/`surfaceRaised` containers, hairline `line` where separation is needed):
  - `@Composable fun DuelCard(status: StatusVariant, title: String, opponent: String, usageText: String, stakeText: String, onClick: () -> Unit, modifier: Modifier = Modifier)` — hero card: `surfaceRaised`, status chip + "ACTIVE DUEL" eyebrow, title (VoloType.title), opponent (bodySmall, textMuted), usage + stake in `Mono`, trailing Phosphor `ArrowRight` (copper).
  - `@Composable fun MetricCard(value: String, label: String, modifier: Modifier = Modifier)` — value in `VoloType.title` (Satoshi), label `bodySmall`.
  - `@Composable fun InfoCard(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null)` — tinted `safe.copy(alpha=.15f)` container option, leading Phosphor icon slot.
  - `@Composable fun RowCard(title: String, subtitle: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {})`.
  - `@Composable fun EmptyState(icon: ImageVector, title: String, message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier)` — Bold Phosphor icon.
  - `@Composable fun InlineError(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier)` — `fail`-tinted text.
  - `@Composable fun LoadingSkeleton(height: Dp, modifier: Modifier = Modifier)` — `surfaceHigh` block, fixed dimensions.

- [ ] **Step 1: Implement** (previews for each).
- [ ] **Step 2: assembleDebug + testDebugUnitTest green**; **Step 3: Commit** → `git commit -m "UI rework: add card and empty/error/skeleton components"`

### Task 6: Data display + charts (TDD for chart math)

**Files:**
- Create: `.../ui/components/DataDisplay.kt`, `.../ui/components/Charts.kt`
- Test: `.../ui/components/ChartMathTest.kt`

**Interfaces:**
- Consumes: Vico (Task 0; fallback per plan note), tokens, type.
- Produces:
  - `fun fractionOf(value: Float, max: Float): Float` — `0f` if `max <= 0f || value <= 0f`, else `(value / max).coerceAtMost(1f)`.
  - `fun sweepDegrees(fraction: Float): Float` — `(fraction.coerceIn(0f, 1f)) * 360f`.
  - `@Composable fun UsageRing(usedSeconds: Int, limitSeconds: Int, centerText: String, modifier: Modifier = Modifier)` — Canvas arc (`copper` sweep on `surfaceHigh` track), center text `VoloType.monoLarge`.
  - `@Composable fun OpponentUsageBar(youLabel: String, youText: String, youFraction: Float, opponentLabel: String, opponentText: String, opponentFraction: Float, modifier: Modifier = Modifier)` — two labeled bars: you = copper, opponent = `textMuted`; labels/timestamps in `Mono`.
  - `@Composable fun SectionHeader(eyebrow: String, title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null)` — `label` eyebrow (tracked, textMuted) + `title` style.
  - `@Composable fun MetricRow(title: String, value: String, modifier: Modifier = Modifier)` — mono value right-aligned.
  - `data class UsageBarItem(val label: String, val duration: String, val fraction: Float)`
  - `@Composable fun UsageBreakdownBars(items: List<UsageBarItem>, modifier: Modifier = Modifier)` — row per item: label + `bodySmall`, mono duration, copper bar scaled by `fraction`.
  - `@Composable fun DailyBarChart(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier)` — Vico column chart, copper columns, `textMuted` axes, `Mono` tick labels.
  - `@Composable fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, lineColor: Color = VoloColors.copper)` — Vico line chart, no axes.

- [ ] **Step 1: Write failing test** `ChartMathTest`:

```kotlin
@Test fun fractionNormal() = assertEquals(0.6f, fractionOf(18f, 30f), 0.0001f)
@Test fun fractionClampsAtOne() = assertEquals(1f, fractionOf(45f, 30f), 0f)
@Test fun fractionZeroMaxOrNegativeInputs() {
    assertEquals(0f, fractionOf(10f, 0f), 0f); assertEquals(0f, fractionOf(-1f, 30f), 0f)
}
@Test fun sweepDegrees() {
    assertEquals(216f, sweepDegrees(0.6f), 0.0001f)
    assertEquals(0f, sweepDegrees(-5f), 0f); assertEquals(360f, sweepDegrees(2f), 0f)
}
```

- [ ] **Step 2: Run → FAIL**; **Step 3: implement** `fractionOf`/`sweepDegrees` + all composables (rings/bars use the two functions; Vico wrappers set colors explicitly from `VoloColors`).
- [ ] **Step 4: Run → PASS**; **Step 5: assembleDebug + testDebugUnitTest green**; **Step 6: Commit** → `git commit -m "UI rework: add data-display and chart components with tested math"`

### Task 7: Shell components + nav icon mapping

**Files:**
- Create: `.../ui/components/Shell.kt`
- Test: `.../navigation/NavIconMappingTest.kt`

**Interfaces:**
- Consumes: `Destination` (from `navigation/NavGraph.kt` — make it `internal` if currently private), Phosphor icons, tokens.
- Produces:
  - `fun navIcon(item: Destination, selected: Boolean): ImageVector` — mapping: Home → `House`, Challenges → `Flag`, Activity → `ClockCounterClockwise`, Profile → `User`, Create → `Plus`; `selected=true` returns the `PhosphorIcons.Fill.*` variant, `false` the `PhosphorIcons.Regular.*` variant (verify exact names against the installed library while writing the test — compile is the check).
  - `@Composable fun VoloBottomBar(current: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier)` — 4 destinations + center `CreateAction`; active = Fill icon + copper label; labels `Mono`? No — labels are words: `VoloType.label` Satoshi; 48dp+ targets; sits above gesture insets.
  - `@Composable fun CreateAction(onClick: () -> Unit, modifier: Modifier = Modifier)` — circular copper, Phosphor `Plus` (Bold), 56dp.
  - `@Composable fun VoloScaffold(current: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier, content: @Composable (PaddingValues) -> Unit)` — dark `bg`, hosts `VoloBottomBar`.

- [ ] **Step 1: Write failing test** `NavIconMappingTest`:

```kotlin
@Test fun everyDestinationHasIcons() = Destination.entries.forEach { d ->
    assertNotNull(navIcon(d, false)); assertNotNull(navIcon(d, true)) }
@Test fun selectedUsesFillVariant() = Destination.entries.forEach { d ->
    assertNotSame(navIcon(d, false), navIcon(d, true)) }
@Test fun destinationsAreDistinctInRegularSet() { /* pairwise != for all 5 regular icons */ }
```

- [ ] **Step 2: Run → FAIL**; **Step 3: implement `Shell.kt`** (NavGraph NOT rewired yet — that's Task 16).
- [ ] **Step 4: Run → PASS**; **Step 5: assembleDebug + testDebugUnitTest green**; **Step 6: Commit** → `git commit -m "UI rework: add shell components and tested Phosphor nav-icon mapping"`

### Tasks 8–15: Screen migrations (one task per screen, in order)

**Shared pattern** (each task's steps below only list the screen-specific anatomy):

- [ ] **Step 1: Rewrite `<Screen>.kt`** per the spec's Screen Specifications section, using only components from Tasks 3–7 (`VoloType` styles — no raw `fontSize`/`FontFamily`; `VoloColors` — no light tokens; Phosphor — no `Icons.*`; keep existing callbacks/state wiring signatures unchanged so `NavGraph.kt` still compiles).
- [ ] **Step 2: `gradlew assembleDebug` green**
- [ ] **Step 3: `gradlew testDebugUnitTest` green** (unchanged data logic must still pass)
- [ ] **Step 4: Commit** → `git commit -m "UI rework: migrate <screen> screen to the dark design system"`

**Task 8: Home** (`ui/screens/HomeScreen.kt`) — mono date, Satoshi headline greeting, `DuelCard` hero (from demo state), weekly `DailyBarChart` card, two `MetricCard`s, `InfoCard` (usage access), empty-state branch via `EmptyState` when no duel. Icons → Phosphor.

**Task 9: Duel** (`ui/screens/DuelScreen.kt`) — back + mono challenge id, `display`-style title, `UsageRing` with mono center + mono countdown, `usageVariant(UsageState.…)` chip, `OpponentUsageBar` + last-report time, mono stake/window meta, `PrimaryButton` contextual action. Immersive `bg`.

**Task 10: Create** (`ui/screens/CreateScreen.kt` + `ui/components/{AppSelector,LimitSelector,StakeInput}.kt`) — headline/subline, step sections with `SectionHeader`, limit presets as `SelectChip` row, stake input in `Mono` with USDC suffix + balance hint, address field `Mono` + resolve/error row (`InlineError`), review block, `PrimaryButton` submit. Migrate the three shared selector components to tokens/chips in this task.

**Task 11: Challenges** (`ui/screens/ChallengesScreen.kt`) — filter `SelectChip`s (Active/Pending/Completed), `RowCard` list (app, opponent, window, mono stake, `StatusChip(lifecycleVariant(...))`); **delete `statusLabel`/`statusColors`, switch to `lifecycleVariant`** (StatusTest already covers behavior; update any test imports if present).

**Task 12: Open Duels** (`ui/screens/OpenDuelsScreen.kt`) — open-duel `RowCard`s with app/limit/window + mono stake pool + `PrimaryButton` join; `EmptyState` when none.

**Task 13: Activity** (`ui/screens/ActivityScreen.kt`) — date header, `DailyBarChart`, `UsageBreakdownBars`, chronological event rows (`RowCard`-style, mono timestamps, Phosphor event icons).

**Task 14: Profile** (`ui/screens/ProfileScreen.kt`) — wallet card (mono shortened address; disconnected/connecting/connected/failed branches per spec), mono balance rows, usage-access status `InfoCard`, Seeker badge or honest unavailable `InlineError`, settings `RowCard`s.

**Task 15: Recovery Lock** (`ui/screens/RecoveryLockScreen.kt`) — `fail`-accented treatment, mono lock metadata/countdown, explanation + single `PrimaryButton`.

### Task 16: Navigation wiring + migration sweep

**Files:**
- Modify: `.../navigation/NavGraph.kt`, `.../ui/theme/Theme.kt`, `.../res/values/styles.xml`
- Delete: `.../ui/UiKit.kt`, light color vals from `Theme.kt`

- [ ] **Step 1: Rewire `NavGraph.kt`** — replace the `NavigationBarItem` blocks (≈ lines 146–155) with `VoloScaffold`/`VoloBottomBar` + `CreateAction`; remove `NavIcon`, `navColors()`, and all `Icons.*` imports.
- [ ] **Step 2: Delete `UiKit.kt`** — only after `grep -r "com.volo.app.ui.StatusChip\|com.volo.app.ui.SelectChip\|com.volo.app.ui.PrimaryButton" app/src/main` returns 0 (old package imports); fix any stragglers found.
- [ ] **Step 3: Grep gates** (each expected: 0 matches in `app/src/main` except the allowed file):
  1. `FontFamily\.Monospace` → 0 anywhere
  2. `fontSize\s*=` → 0 outside `theme/Type.kt`
  3. `Icons\.` → 0
  4. `Paper|SurfaceWhite|SurfaceMuted|\bCeladon\b|\bClay\b|\bInk\b|\bMuted\b|\bCopper\b|CopperDeep` → 0 after deleting them from `Theme.kt`
  5. `statusLabel|statusColors` → 0
  Fix every hit, re-run until clean.
- [ ] **Step 4: `res/values/styles.xml`** — remove `android:fontFamily` line (spec technical note).
- [ ] **Step 5: `gradlew assembleDebug` + `gradlew testDebugUnitTest` green**
- [ ] **Step 6: Commit** → `git commit -m "UI rework: wire bottom navigation and sweep out light theme, UiKit, Material icons"`

### Task 17: Final verification + user review

- [ ] **Step 1:** `gradlew clean assembleDebug testDebugUnitTest` — all green from scratch.
- [ ] **Step 2:** Run the **UI/UX Design Review** skill over the final code (spec verification requirement); fix findings in a follow-up commit (`git commit -m "UI rework: design-review fixes"`).
- [ ] **Step 3:** Build installable debug APK and capture screenshots of all 8 screens (device or emulator); present the set to the user for the locked "review all screens together" sign-off. **Stop and wait for approval** — do not proceed past this gate without it.
  - New verification sub-step (from "apps design UI Cheatsheet" reference, *principles only* — values rejected under locked dark/Satoshi/48dp constraints): confirm every settings/data row uses a **uniform height** and **left-aligned icon+label+value columns** (adopted principle = "strong states make settings easier to scan"). Status chips remain labeled pills (color + text), satisfying the scannable-strong-state intent. Badge-switch-color tokens from the reference were rejected as light-theme values.
- [ ] **Step 4:** Apply any requested fixes, re-capture, repeat until approved.

---

## Self-review notes (plan author)

- Spec coverage: tokens/typography/icons/components/charts/screens/nav/motion-note/accessibility/verification → Tasks 1–17. Motion system: carried spec values are interaction-level (220–280ms transitions, crossfades) and land inside screen tasks as Compose animation defaults — visual review (T17) is the check.
- Fallbacks (shadcn/Vico/Phosphor) are explicit branches in Task 0, matching the spec's fallback clause.
- No task depends on unstated names: every cross-task symbol appears in an Interfaces block.
