# Volo UI Design Specification

**Status:** Draft for review
**Scope:** Phase 1 visual foundation and the screen language used by later phases
**Platform:** Native Android, Kotlin, Jetpack Compose, Seeker-oriented APK

## Design Read

Volo is a native mobile accountability app for people who want a financial consequence attached to their screen-time commitments. The visual language combines a light editorial fintech shell with a darker immersive duel state. It should feel considered, trustworthy, and emotionally clear without resembling a crypto trading terminal or a generic wellness tracker.

The inspiration direction contributes three qualities: soft rounded cards, editorial typography, and purposeful motion. Volo uses those qualities to clarify commitment, progress, and settlement rather than to decorate the interface.

## Design Dials

- `DESIGN_VARIANCE`: 7. Use asymmetric emphasis and editorial type, but keep navigation and actions predictable.
- `MOTION_INTENSITY`: 6. Animate hierarchy, progress, confirmation, and settlement. Avoid perpetual motion.
- `VISUAL_DENSITY`: 4. Give daily status and active duels room to breathe while keeping repeated actions scannable.

## Product Principles

1. **Commitment before complexity.** The user should understand the target app, daily limit, stake, opponent, and current state at a glance.
2. **Crypto stays in the infrastructure layer.** Use plain language such as stake, locked, verified, and settled. Expose wallet details when trust requires them.
3. **Failure is legible.** PASSING, AT RISK, FAILED, UNVERIFIED, and RECOVERY LOCKED must never rely on color alone.
4. **The phone is the product surface.** Usage progress, permission state, wallet approval, and challenge status are designed for one-handed mobile use.
5. **Every important number has context.** Show `18m of 30m`, not only `60%`; show `5.00 USDC`, not raw token units.

## Visual System

### Color Tokens

The palette is mineral ink and oxidized copper. It avoids the default fintech blue, neon-lime, purple-gradient, and bright-coral product language.

#### Light shell

- `paper`: `#F4F5F1` — primary app background, a cool chalk rather than warm cream
- `surface`: `#FFFFFF` — cards and sheets
- `surface-muted`: `#E9ECE7` — secondary grouping and disabled surfaces
- `ink`: `#172126` — primary text
- `ink-muted`: `#607076` — secondary text
- `line`: `#D7DEDA` — borders and dividers
- `copper`: `#B7613F` — primary action, active progress, selected navigation state
- `copper-deep`: `#87432F` — pressed and high-contrast copper state
- `celadon`: `#C9DCCF` — successful or safe supporting surface
- `clay`: `#A34F46` — failure and destructive status only

#### Dark duel state

- `duel`: `#10191D` — full-screen active duel background
- `duel-surface`: `#1A272B` — raised duel surfaces
- `duel-line`: `#334247` — borders and separators
- `duel-text`: `#F2F4EE` — primary text
- `duel-muted`: `#A4B0AF` — secondary text
- `duel-copper`: `#D17A54` — active progress and primary duel action
- `duel-safe`: `#B9D5C5` — passing state
- `duel-risk`: `#E0A268` — at-risk state
- `duel-fail`: `#E08373` — failed state

Color is never the only status signal. Pair it with a label, icon, progress shape, or supporting sentence.

### Typography

- **Display and product voice:** Azeret Sans. Use for screen titles, challenge names, status headlines, and primary buttons.
- **Utility data:** Fragment Mono. Use only for usage values, countdowns, wallet fragments, transaction signatures, and compact metadata.
- Do not render the entire app in mono. Long-form labels, explanations, and navigation use Azeret Sans.
- Minimum body size: 15sp. Minimum supporting text: 12sp with sufficient contrast.
- Usage values use tabular numerals where available.
- Avoid all-caps paragraphs. Use small caps only for short status labels.

### Shape, Elevation, and Spacing

- Major cards: 18dp radius.
- Inputs, buttons, and controls: 12dp radius.
- Status chips: full pill radius.
- Use soft tinted elevation rather than black shadows. Light shell cards use a low-contrast shadow plus a 1dp line.
- Base spacing unit: 4dp. Primary layout rhythm uses 8, 12, 16, 24, and 32dp.
- Minimum touch target: 48dp.
- Content horizontal padding: 20dp on compact phones, 24dp on larger screens.
- Keep the bottom navigation and center create action clear of system gesture insets.

## Navigation

Use a bottom navigation bar with five positions:

1. **Home** — daily status and the current accountability loop
2. **Challenges** — active, pending, and completed duels
3. **Create** — center action, visually elevated above the bar
4. **Activity** — history, settlements, streak changes, and verification events
5. **Profile** — wallet, Seeker status, SKR eligibility, and settings

The center Create action is a filled circular control with a plus icon and an accessible label, not a text-heavy tab. It opens a focused create-duel flow. The center position remains visually distinct from the four navigation destinations.

Navigation labels use short, stable nouns. Do not rename the same destination between screens.

## Screen Specifications

### Home

**Purpose:** Make today's accountability state immediately understandable.

Order from top to bottom:

1. Compact greeting and date context.
2. Active duel card as the first major content block.
3. Daily usage ring for the selected app.
4. Streak and verification summary.
5. Secondary action for the next required step.

The active duel card includes target app, opponent identity, stake amount, remaining daily allowance, challenge state, and a clear `View duel` action. If there is no active duel, show a composed empty state with one primary `Create duel` action and a short explanation.

States to design:

- No active duel
- Active and passing
- Near limit / at risk
- Awaiting opponent
- Awaiting usage verification
- Settlement available
- Network error with retry

### Challenges

Use segmented views or a restrained top filter for `Active`, `Pending`, and `Completed`. Repeated challenge rows show the target app, opponent, date/window, stake, and status. Avoid a large card inside another card: the screen is a list surface, while each duel is one framed item.

### Create Duel

Use a focused sheet or full-screen route with a clear step sequence:

1. Choose app
2. Set daily limit
3. Choose challenge duration
4. Enter stake
5. Enter opponent wallet address
6. Review and approve

Use presets first: 15, 30, 60, and 120 minutes. Custom values are available through a numeric field. Challenge duration uses 1h, 4h, 12h, 24h, and 7d presets. The first MVP uses calendar-day limits; a separate demo mode may use a short controlled window.

The review state should show exactly what the user is approving: app, daily limit, duration, opponent, stake, and the fact that the stake will be locked on devnet. Wallet terminology appears at the approval boundary, not throughout the form.

### Active Duel

Use the dark immersive duel state. This is the emotional peak of the product.

Layout:

1. Back control and compact challenge identity.
2. Large target-app title and opponent identity.
3. Large circular user progress ring with numeric center: `18m / 30m`.
4. Explicit status label: `PASSING`, `AT RISK`, `FAILED`, or `UNVERIFIED`.
5. Opponent comparison bar with their reported or last-known usage.
6. Stake and time-window metadata.
7. Contextual action: `Check usage`, `Submit report`, `Approve settlement`, or `View transaction`.

The screen must explain when opponent data is last reported. Never imply live surveillance if the value is only an oracle report.

### Result and Settlement

Return to the light shell after settlement. Use a large result statement, a concise explanation, and a transaction detail row.

- Winner: show the amount received and the opponent result.
- Both pass: show both stakes returned.
- Both fail: show `Recovery lock activated` with a clear explanation that the funds remain locked until the recovery challenge is completed in the later protocol phase.
- Unverified: show why verification failed and the resulting settlement treatment.

The transaction signature is secondary but available in a copyable utility row.

### Activity

Use a chronological, scannable event list. Events include duel created, opponent joined, usage submitted, permission issue, settlement approved, and funds returned or awarded. Each row has an icon, plain-language label, date/time, and optional transaction fragment.

### Profile

Sections:

- Wallet connection and shortened address
- Devnet USDC balance
- Seeker status with a subtle verification badge
- SKR balance and Open Duels eligibility
- Usage Access permission status
- Privacy and data explanation

When Seeker hardware is unavailable, show `Seeker verification unavailable on this device` rather than a fake verified state. Mock `.skr` identity resolution must be visually marked as unverified.

## Motion System

Motion is purposeful and medium-intensity.

- Screen transitions: 220-280ms, fade plus small horizontal movement based on navigation direction.
- Create flow step changes: 240ms with shared progress indicator movement.
- Progress ring: animate only when reported usage changes; use a 500-700ms eased transition.
- Wallet approval: compress the approval button on press and show a clear pending state; never leave a user guessing whether signing started.
- Settlement: use one deliberate result reveal with the amount and status appearing in sequence.
- Status changes: use content replacement with a short crossfade, not an infinite pulse.
- Respect reduced-motion settings by removing movement and retaining only instant state changes and clear labels.

## Component Inventory for Phase 1

- `VoloScaffold`
- `VoloBottomBar`
- `CreateAction`
- `PrimaryButton`
- `SecondaryButton`
- `StatusChip`
- `DuelCard`
- `UsageRing`
- `OpponentUsageBar`
- `MetricRow`
- `WalletIdentityRow`
- `SeekerBadge`
- `EmptyState`
- `InlineError`
- `LoadingSkeleton`
- `SettlementSummary`

Build these as focused Compose components with explicit state parameters. Do not connect them directly to wallet, usage, or oracle code in Phase 1; use preview/demo data first.

## Accessibility and Trust Requirements

- Every icon-only action has a content description and a 48dp touch target.
- Status color is always paired with text and, where useful, an icon.
- Dynamic usage values are announced when they change in an active challenge.
- Wallet addresses and transaction signatures are copyable and never the only identity label.
- Error messages explain the next action.
- Loading states preserve layout dimensions to avoid jumps.
- Never show a success state before the relevant local or on-chain operation is confirmed.
- Do not claim Seeker verification, hardware-backed signing, or trustless usage measurement when the current provider is mocked or oracle-resolved.

## Phase 1 UI Deliverable

Phase 1 should produce a navigable Compose prototype with preview/demo data for:

- Home with active, empty, and error states
- Challenges list
- Create flow with validation and review
- Active dark duel state
- Result screen for pass, fail, both-pass, both-fail, and unverified
- Activity list
- Profile with mocked wallet, SKR, and Seeker states

The prototype is complete when the visual hierarchy and state transitions can be reviewed on a physical Android phone without requiring Supabase, Solana, or a Seeker device.
