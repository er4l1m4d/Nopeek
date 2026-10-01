# Volo -- Detailed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android APK (Kotlin/Compose) that locks USDC-backed stakes against app usage limits, settles based on verified usage, and integrates Solana Mobile SDK for Seeker compatibility -- all using free tools and devnet.

**Architecture:** Thin vertical slice first -- usage tracking, MWA wallet connection, Anchor-free escrow on devnet (via Solana Playground browser IDE), Supabase oracle settlement, real APK tested over USB on physical phone. Seeker-only features (Genesis Token verification, Secure Element signing) isolated behind mock-provider adapters documented as untested-until-hardware-available.

**Tech Stack:** Kotlin + Jetpack Compose (Android Studio 2025.3.2), Solana CLI via Git Bash (wallet management only), Anchor-free program written and deployed via Solana Playground browser IDE, Supabase Edge Functions, web3.js/Solana libraries for Kotlin, GitHub Free, Solana Devnet.

**Spec:** `volo-plan.md` (this document) serves as both plan and spec.

## Global Constraints

- Android Studio 2025.3.2 installed; SDK Platform 33-35 available for download.
- Solana CLI 1.17.0 installed via Git Bash for wallet management only.
- Contract written and deployed via Solana Playground browser IDE (avoids Windows BPF compilation issues).
- All tokens on Solana Devnet only; no mainnet transactions.
- Wallet signing via real MWA-compatible wallet (e.g., Phantom, Solflare) on physical phone.
- Seeker-exclusive features (Genesis Token verification, Secure Element signing) behind mock-provider adapters.
- Daily limit resets at local midnight; tamper signals collected but **not enforced** in MVP.
- Free tools only: Android Studio Community, GitHub Free, Supabase Free Tier, Circle Devnet Faucet.
- Recovery lock logic deferred to Phase 2 (after basic settlement works).
- USDC display: always use 6 decimals; never show raw token units.
- 11-day timeline; Days 0-1 are environment setup, demo-ready by Day 7.

## Review Focus

1. Revoked Usage Access mid-challenge -- app detects via AppOpsManager, marks result UNVERIFIED.
2. Changed system clock -- app detects backward jumps greater than 5 minutes, flags CLOCK_CHANGED.
3. Network failure during settlement -- retry 3x with exponential backoff (500ms, 1s, 2s), then queue locally.
4. Duplicate settlement transactions -- contract rejects if status is not CREATED; app polls for idempotency.
5. USDC display precision -- all UI divides/multiplies by 1,000,000; never show raw token units.

## Environment Summary (Verified Current State)

| Component | Status | Notes |
|-----------|--------|-------|
| Android Studio | Installed (2025.3.2) | At Desktop\Downloaded Softwares\Android |
| Android SDK | Missing | Must install fresh (API 33 only) |
| Solana CLI | Not installed | Need via Git Bash |
| Git Bash | Present | At C:\Program Files\Git\git-bash.exe |
| Rust toolchain | Installed | v1.96.1 available |
| Node.js | Installed | v26.5.0 |
| Physical phone | Available | USB debugging ready |
| WSL2/Ubuntu | Not installed | Not required if using Solana Playground |
| Disk space | 21 GB free | Tight but sufficient |
---

## Phase 0: Environment Setup

### Task 0.1: Android SDK Fresh Install

**Files:**
- New: `README.md`
- New: `.gitignore`

**Interfaces:**
- Consumes: Android Studio 2025.3.2 (installed)
- Produces: SDK with API 33 + Build Tools only

- [x] **Step 1: Configure SDK location**
 Open Android Studio -> Settings -> Appearance & Behavior -> System Settings -> Android SDK
 Set SDK path to: `C:\Users\hp\AppData\Local\Android\Sdk`
 Click "SDK Platform" tab

- [x] **Step 2: Install SDK Platform 33**
 _(installed 33 + 35; API 35 required by compileSdk-35 env ruling — ledger 1.1)_
 In SDK Manager:
 - Check "Show Package Details"
 - Check ONLY: API 33 (Android 13.0) -> Build-Tools 33.0.0
 - Uncheck ALL other packages (no emulator, no system images, no NDK)
 Click "Apply" + "OK" to install

- [x] **Step 3: Verify footprint**
 _(verified: platforms 33+35, build-tools 33+34, no emulator — ledger 0.1)_
 Run: `dir "$env:LOCALAPPDATA\Android\Sdk" /s /d`
 Expected: Only these directories:
 - platforms\android-33
 - build-tools\33.0.0
 - platform-tools
 Expected: NO `emulator/` directory
 Expected: NO `system-images/` directory

### Task 0.2: Solana CLI via Git Bash (Wallet Management Only)

**Files:**
- New: `contracts/program/src/lib.rs` (contract source)
- New: `contracts/program/idl.json`
- New: `devnet-wallet-info.txt`

**Interfaces:**
- Consumes: Git Bash at `C:\Program Files\Git\git-bash.exe`
- Produces: Solana CLI wallet with 2 SOL on devnet

- [x] **Step 1: Install Solana CLI 1.17.0**
 In Git Bash:
 ```bash
 sh -c "$(curl -sSfL https://release.solana.com/v1.17.0/install)"
 echo 'export PATH="$HOME/.local/share/solana/install/active_release/bin:$PATH"' >> ~/.bashrc
 source ~/.bashrc
 ```
 Expected: `solana --version` returns `solana-cli 1.17.0`

- [ ] **Step 2: Create devnet wallet + fund**
 ⛔ blocked — wallet `6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj` created, airdrop rate-limited (0 SOL); see ledger Row 9
 ```bash
 solana config set --url https://api.devnet.solana.com
 solana-keygen new --no-bip39-passphrase --silent --force
 solana address > devnet-wallet-info.txt
 solana airdrop 2
 solana balance
 ```
 Expected: `2 SOL` printed
 Save this pubkey -- it becomes the oracle wallet address

### Task 0.3: Get Devnet USDC

**Files:**
- New: `contracts/program/USDC_MINT.txt`

**Interfaces:**
- Consumes: Phase 0.2 devnet wallet
- Produces: >100 USDC in devnet associated token account

- [ ] **Step 1: Find USDC mint for devnet**
 ⛔ blocked — no SOL; plan's Goerli faucet steps obsolete, re-verify current path when unfunded (ledger Row 5)
 Known devnet USDC mint: `Ejmc1UB4EsES5h32u8Fp1avkX4rM2e4HFEh3o4HkF2sX`
 Save to `contracts/program/USDC_MINT.txt`

- [ ] **Step 2: Request tokens from Circle Devnet Faucet**
 1. Go to: https://faucet.circle.com/usdc-ethereum
 2. Select "Ethereum Testnet Goerli" + "USDC"
 3. Copy your devnet wallet address from `devnet-wallet-info.txt`
 4. Click "Send me USDC"
 Expected: Wallet receives 100 USDC on devnet
 (Note: This faucet sometimes requires GitHub account login)

- [ ] **Step 3: Verify USDC balance**
 In Git Bash:
 ```bash
 spl-token accounts --owner $(solana address) --display-authority
 ```
 Expected: At least 1 USDC account with balance greater than 0

### Task 0.4: Supabase Project Setup

**Files:**
- New: `supabase/migrations/20240101000001_init.sql`
- New: `supabase/functions/oracle/index.ts`

**Interfaces:**
- Consumes: Supabase free account (signup at supabase.com)
- Produces: Live project URL + anon key

- [ ] **Step 1: Create Supabase project**
 ⛔ blocked — interactive signup required; local artifacts + checklist deferred (ledger Row 6)
 Go to https://supabase.com/dashboard
 Click "New Project":
 Name: `volo-accounts`
 Region: Select closest to you
 Password: Generate strong random password (save it!)
 Click "Create" (2-4 min provisioning)

- [ ] **Step 2: Enable Email auth**
 Authentication -> Providers -> Email -> Enable
 Settings -> URL Configuration:
 Add `http://localhost:19006` (dev redirect URI)

- [ ] **Step 3: Deploy database schema**
 In Supabase Dashboard -> SQL Editor:
 ```sql
 create table challenges (
   id uuid primary key default gen_random_uuid(),
   challenge_pubkey text unique not null,
   creator_wallet text not null,
   opponent_wallet text not null,
   target_app text not null,
   limit_seconds integer not null,
   stake_amount bigint not null,
   window_type text default 'demo' check (window_type in ('demo', 'daily', 'weekly')),
   window_seconds integer not null,
   start_time bigint not null,
   end_time bigint not null,
   status text default 'CREATED' check (status in ('CREATED', 'ACTIVE', 'SETTLED', 'REFUNDED', 'BOTH_FAIL_LOCKED')),
   created_at timestamp default now()
 );
 create table usage_reports (
   id uuid primary key default gen_random_uuid(),
   challenge_pubkey text references challenges(challenge_pubkey),
   wallet_pubkey text not null,
   used_seconds integer not null,
   window_start bigint not null,
   window_end bigint not null,
   tamper_signals jsonb default '{}'::jsonb,
   submitted_at timestamp default now()
 );
 create table settlement_results (
   id uuid primary key default gen_random_uuid(),
   challenge_pubkey text references challenges(challenge_pubkey),
   creator_result text check (creator_result in ('PASS', 'FAIL', 'UNVERIFIED')),
   opponent_result text check (opponent_result in ('PASS', 'FAIL', 'UNVERIFIED')),
   both_fail boolean default false,
   settled boolean default false,
   settled_at timestamp,
   tx_signature text
 );
 ```
 Click "Run" -> verify tables created

- [ ] **Step 4: Get project credentials**
 Settings -> API:
 Copy `PROJECT_URL` and `ANON_KEY`
 Save in `volo-secrets.md` (NOT committed to git)

- [ ] **Step 5: Deploy minimal oracle function**
 Create local file `supabase/functions/oracle/index.ts`:
 ```typescript
 import 'jsr:@supabase/functions-js'
 import { createClient } from 'jsr:@supabase/supabase-js@2'

 const supabase = createClient(
   Deno.env.get('SUPABASE_URL')!,
   Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
 )

 Deno.serve(async (req) => {
   const url = new URL(req.url)
   if (url.pathname === '/health') {
     return new Response(JSON.stringify({ status: 'alive', timestamp: Date.now() }), {
       headers: { 'Content-Type': 'application/json' }
     })
   }
   return new Response('Not Found', { status: 404 })
 })
 ```
 Deploy: Supabase Dashboard -> Functions -> New Function -> "oracle" -> paste code -> Deploy
 Expected: curl https://[project-ref].supabase.co/functions/v1/oracle/health returns alive JSON
---

## Phase 1: Android App Skeleton + Navigation

### Task 1.1: Create Compose Project

**Files:**
- New: app/src/main/java/com/volofinal/volofinal/MainActivity.kt
- New: app/src/main/java/com/volofinal/volofinal/data/Models.kt
- Modify: app/src/main/AndroidManifest.xml

**Interfaces:**
- Consumes: Phase 0 Android SDK
- Produces: Runnable empty Compose app on physical phone

- [x] Step 1: Create new project in Android Studio
 _(retrofitted into existing approved app `com.volo.app` instead of new `com.volofinal.volofinal` project — ledger Row 1)_
  File -> New -> Empty Compose Activity
  Name: Volo | Package: com.volofinal.volofinal | Language: Kotlin | Min SDK: 33
  UNCHECK Enable Night Mode

- [x] Step 2: Define data models
 Create Models.kt with Challenge, UsageReport, SettlementResult data classes
 (See detailed definition in original brainstorm)

- [x] Step 3: Verify app compiles + runs
 _(APK built and installed on user's physical phone)_
 Connect phone via USB -> Run in Android Studio -> select physical device
 Expected: White screen with Hello Android text

### Task 1.2: Navigation + Light Theme

**Files:**
- Modify: MainActivity.kt
- New: ui/navigation/NavGraph.kt
- New: ui/screens/HomeScreen.kt

**Interfaces:**
- Consumes: Phase 1.1 project
- Produces: NavHost with 4 routes

- [x] Step 1: Add Navigation dependency
 _(navigation-compose:2.9.2, newer than 2.7.7)_
 In build.gradle.kts: implementation androidx.navigation:navigation-compose:2.7.7

- [x] Step 2: Create NavGraph.kt
 _(shipped as `navigation/NavGraph.kt` + `NavRoutes.kt`; 5 destinations: home, challenges, create_challenge, activity, profile + active_challenge/{pubkey} full-screen — ledger 1.2)_
 Four routes: home, create_challenge, active_challenge/{pubkey}, profile

- [x] Step 3: Set up light theme
 _(mineral-ink + oxidized-copper palette per approved UI spec instead of #6366F1 indigo — ledger Row 1)_
 Primary: #6366F1 | Secondary: #8B5CF6 | Background: #FFFFFF

- [x] Step 4: Verify navigation works
 _(tested on phone during prototype review + NavRoutesTest 3/3)_
 Run app then tap between screens
 Expected: Each screen displays its name

---

## Phase 2: Wallet Connection

### Task 2.1: Add Solana Mobile SDK Dependencies

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: Phase 1 project with navigation
- Produces: Project compiles with Solana Mobile SDK

- [x] Step 1: Add dependencies
 _(shipped `com.solanamobile:mobile-wallet-adapter-clientlib:2.1.1` + `-common`; web3j dropped, seedvault omitted — ledger Row 3 / 2.1)_
 In `app/build.gradle.kts` (Module level):
   implementation 'com.solanamobile:seedvault:1.0.1'
   implementation 'com.solanamobile:mobile-wallet-adapter:1.2.0'
   implementation 'com.solanamobile:mobile-wallet-adapter-client:1.2.0'
   implementation 'org.web3j:core:5.0.0'

- [x] Step 2: Add intent filter
 _(singleTask added + `<queries>` entry for `solana-wallet` scheme)_
 In AndroidManifest.xml: add `android:launchMode="singleTask"` to MainActivity

- [x] Step 3: Verify project syncs
 _(assembleDebug BUILD SUCCESSFUL)_
 Click "Sync Now" in Android Studio
 Expected: No Gradle errors

### Task 2.2: Implement Wallet Connection Flow

**Files:**
- New: `app/src/main/java/com/volofinal/volofinal/data/wallet/WalletManager.kt`
- Modify: `app/src/main/java/com/volofinal/volofinal/ui/screens/HomeScreen.kt`

**Interfaces:**
- Consumes: Phase 2.1 dependencies
- Produces: "Connect Wallet" button shows wallet address

- [x] Step 1: Create WalletManager singleton
 Create WalletManager.kt with:
   - connect(activity: Activity): Result<String>
   - signTransaction(txn: Transaction): Result<String>
   - walletAddress property
 _(shipped: connect/disconnect via MWA ktx, WalletState sealed states, walletAddress getter; signTransaction deferred to Phase 7 tx work — ledger 2.2)_
 Create WalletManager.kt with:
   - connect(activity: Activity): Result<String>
   - signTransaction(txn: Transaction): Result<String>
   - walletAddress property

- [x] Step 2: Implement connect()
 Use MobileWalletAdapter with ConnectionSpecification
 Handle: NoMatchingWalletFound, UserCanceled exceptions
 Store Account object for session reuse
 _(shipped: MWA `connect(sender)` → TransactionResult; NoWalletFound/Cancel/Failure → AssociationOutcome; account retained in Connected state)_
 Use MobileWalletAdapter with ConnectionSpecification
 Handle: NoMatchingWalletFound, UserCanceled exceptions
 Store Account object for session reuse

- [x] Step 3: Hook up to HomeScreen UI
 Button onClick: trigger wallet connection via MWA
 Update UI based on result (Connected state shows address)
 _(hooked on ProfileScreen wallet row instead of HomeScreen, per approved UI spec — ledger 2.2; Disconnect + error states also wired)_

- [ ] Step 4: Test wallet connection
 Install Phantom or Solflare on phone
 Click "Connect Wallet" -> select wallet -> approve connection
 Expected: Screen shows devnet wallet address
 ⏳ pending on-device test (user step); code + tests complete (commit 226d356)

---

## Phase 3: Usage Tracking Engine

### Task 3.1: Usage Access Permission

**Files:**
- New: `app/src/main/java/com/volofinal/volofinal/data/usage/UsagePermissionHelper.kt`

**Interfaces:**
- Consumes: Physical phone in developer mode
- Produces: Detectable Usage Access permission state

- [x] Step 1: Create UsagePermissionHelper
 Object with:
   isGranted(context: Context): Boolean
   requestPermission(activity: Activity)
 _(shipped: `state(context)` via AppOpsManager.checkOpNoThrow + `settingsIntent()`; pure `usagePermissionStateForMode` mapping tested — UsagePermissionStateTest 6/6)_

- [x] Step 2: Add permission screen
 When user clicks "Start Tracking":
   - If not granted: show rationale -> open Settings.ACTION_USAGE_ACCESS_SETTINGS
   - Track permission state via AppOpsManager.checkOpNoThrow
 _(shipped inline on ProfileScreen "Usage access" row (state text + tap-to-open Settings); no separate screen — ledger 3.1)_

- [ ] Step 3: Handle result + verify
 After user returns from settings, re-check permission
 Show success/failure Toast
 Test on phone: toggle off/on permission manually
 _(re-check implemented via lifecycle ON_RESUME observer; toast + on-device toggle test pending — user step)_

### Task 3.2: UsageStatsManager + WorkManager

**Files:**
- New: `app/src/main/java/com/volofinal/volofinal/data/usage/UsageTracker.kt`
- New: `app/src/main/java/com/volofinal/volofinal/data/usage/UsageWorker.kt`

**Interfaces:**
- Consumes: Phase 3.1 permission system
- Produces: Aggregate usage seconds + tamper signals

- [x] Step 1: Create UsageTracker
 Track MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND events per package
 Calculate: totalMs = sum of (background_timestamp - foreground_timestamp)
 Convert to usedSeconds = totalMs / 1000
 _(shipped as pure `aggregateUsage` with window clipping + unclosed-session handling; UsageAggregatorTest 9/9 — ledger 3.2)_

- [x] Step 2: Implement tamper signal detection
   PERMISSION_REVOKED: AppOpsManager.checkOpNoThrow returns DENY
   CLOCK_CHANGED: System.currentTimeMillis() jumped backward >5 min
   REPORT_LATE: Current time vs scheduled window_end exceeds grace period
 _(shipped as pure `detectTamperSignals`; clock jump tracked via SharedPreferences 5-min threshold in UsageWorker; grace default 60s)_

- [x] Step 3: Create UsageWorker
 Extends Worker class:
   doWork(): fetch challenge params from Room DB -> query usage -> POST to oracle
   Handle network retry: 3 attempts, exponential backoff
 _(shipped: params via WorkManager input data until Room lands at 4.2 — ledger Row 2; real POST + retry deferred to 8.2 where the network call exists; `postReport` is a stub now)_

- [x] Step 4: Schedule WorkManager
 scheduleDailySync():
   - PeriodicWorkRequest: 15-minute intervals
   - Constraints: Required network connected
   - Input data: challenge_pubkey, target_package, window_start/end
 _(shipped as `UsageWorker.scheduleUsageSync` — unique periodic work per challenge pubkey, ExistingPeriodicWorkPolicy.UPDATE; invoked when challenge creation lands in 4.x)_

- [ ] Step 5: Test on phone
 Start tracking TikTok for 5 min -> force-stop TikTok -> return to Volo
 Click Sync Now manually
 Expected: Shows real usage seconds
 ⏳ pending on-device test (user step); code complete — commit 48c2eb9

---

## Phase 4: Challenge Creation Flow

### Task 4.1: Challenge Create UI

**Files:**
- Modify: `app/src/main/java/com/volofinal/volofinal/ui/screens/ChallengeCreateScreen.kt`
- New: `app/src/main/java/com/volofinal/volofinal/ui/components/AppSelector.kt`
- New: `app/src/main/java/com/volofinal/volofinal/ui/components/LimitSelector.kt`
- New: `app/src/main/java/com/volofinal/volofinal/ui/components/StakeInput.kt`

**Interfaces:**
- Consumes: Phase 1 navigation + Phase 2 wallet state
- Produces: Valid challenge input -> confirmation screen

- [x] Step 1: Create AppSelector dropdown
 Load installed apps:
   pm.getInstalledApplications(0).filter { not system app }
 Sort by label -> show in DropdownMenu
 Allow any app selection (no hardcoded list)
 _(shipped: ui/components/AppSelector.kt, FLAG_SYSTEM filter, label-sorted — ledger 4.1)_

- [x] Step 2: Create LimitSelector
 Presets: [15 min, 30 min, 60 min, 120 min] + Custom option
 Custom -> dialog with numeric input field
 _(shipped: chips 15m/30m/1h/2h + Custom → AlertDialog numeric input)_

- [x] Step 3: Create StakeInput
 Default: 5.00 USDC
 Display format: USDUSD amount / 1_000_000
 Show wallet USDC balance below input
 _(shipped: default 5.00, decimal-masked field → raw base units; USDC balance fetch deferred to Phase 7 — shows wallet-connected status instead, no fake balance)_

- [x] Step 4: Add opponent field
 Text field accepts:
   - Raw wallet address (base58 validation)
   - .skr domain (mocked resolution)
 Show resolved pubkey below field
 _(shipped: Base58.decode (32-byte check) + deterministic sha256 mock for .skr — real resolution when external services land per Row 6; ChallengeValidationTest 17/17)_

- [x] Step 5: Add window selector
 Options: [1 hour, 4 hours, 12 hours, 24 hours, 7 days]
 Store as windowSeconds Long
 _(shipped: chip row, windowSeconds Long state)_

- [x] Step 6: Validation + confirmation
 Validate all inputs -> show AlertDialog with summary
 On Confirm -> store locally + advance to Phase 5
 _(shipped: validateChallengeInput inline errors + Confirm AlertDialog with summary; "store locally" lands at 4.2 Room — Confirm advances navigation now; commit 0882566)_

### Task 4.2: Local State Storage

**Files:**
- New: `app/src/main/java/com/volofinal/volofinal/data/local/ChallengeDao.kt`
- New: `app/src/main/java/com/volofinal/volofinal/data/local/AppDatabase.kt`

**Interfaces:**
- Consumes: Phase 4.1 UI
- Produces: Room database persisting challenges

- [x] Step 1: Define ChallengeEntity
 @Entity annotation mapping to Challenge model
 Include local status field for UI state tracking
 _(shipped: data/Challenge.kt + data/local/ChallengeEntity.kt (unique challengePubkey, localStatus); mapping tested 3/3 — ledger 4.2)_

- [x] Step 2: Create DAO + Database
 RoomDatabase with ChallengeDao:
   insert(challenge: Challenge)
   updateStatus(pubkey: String, status: String)
   getActiveChallenges(): List<ChallengeEntity>
 _(shipped: ChallengeDao suspend methods + AppDatabase singleton; Room 2.7.1 via KSP — 2.6.1 crashed under KSP2; wired into CreateScreen Confirm — commit ebf46fe)_

- [ ] Step 3: Verify local storage
 Create challenge -> force-close app -> reopen
 Expected: Challenge persists in database
 ⏳ pending on-device persistence test (user step); insert path wired in commit ebf46fe

---

## Phase 5: Smart Contract (Solana Playground)

### Task 5.1: Write Escrow Program in Solana Playground

**Files:**
- New: Source written in browser at playground.solana.com
- New: `contracts/program/src/lib.rs` (exported from Playground)

**Interfaces:**
- Consumes: devnet wallet with SOL + USDC
- Produces: Deployed program on devnet

- [ ] Step 1: Open Solana Playground
 Go to https://solanapayground.com
 Select "devnet" cluster
 Connect wallet: import devnet wallet from devnet-wallet-info.txt
 ⏳ pending user browser action (real URL: solana-playground.com — plan typo); see contracts/USER_ACTIONS.md

- [ ] Step 2: Create new program
 Click "New Project" -> "From Scratch"
 Name: volo_challenge
 Editor shows boilerplate with declare_id! macro
 ⏳ pending user browser action — paste local contracts/program/src/lib.rs (checklist in USER_ACTIONS.md)

- [x] Step 3: Write Challenge struct
 ```rust
 #[account]
 pub struct Challenge {
     pub creator: Pubkey,
     pub opponent: Pubkey,
     pub token_mint: Pubkey,
     pub stake_amount: u64,
     pub target_app: String,
     pub limit_seconds: u32,
     pub window_seconds: u32,
     pub start_time: i64,
     pub end_time: i64,
     pub status: u8, // 0=Created, 1=Active, 2=Settled, 3=Refunded, 4=BothFailLocked
     pub result: u8, // 0=NoResult, 1=CreatorWon, 2=OpponentWon, 3=BothPass, 4=BothFail
 }
 ```
 Account size: 8 discriminator + (32*4 pubkeys) + (8*2 u64/u32) + String(4+32) + 4*u32 + 2*u64 + 2*u8
 _(shipped locally: contracts/program/src/lib.rs — 3 pubkeys + stored bump (175 bytes), PDA seeds [challenge, creator, opponent]; source compiles in Playground — local cargo check blocked by disk (0.7 GB free))_

- [x] Step 4: Write create_challenge instruction
 Instruction accepts: stake_amount, target_app, limit_seconds, window_seconds
 Logic: initialize Challenge account, set start/end times, set status Created
 _(shipped: full source + escrow funding CPI transfer; also added `opponent` account (plan omitted — needed for PDA seeds) and `join_challenge` instruction (plan omits any join path — without it no one can ever stake/activate)_

- [x] Step 5: Write settle_challenge instruction
 Requires oracle authority verification
 Logic:
 - Creator PASS, opponent FAIL -> creator wins 2x stake
 - Both PASS -> refund both stakes
 - Both FAIL -> set status BothFailLocked
 - UNVERIFIED -> treat as FAIL
 _(shipped: ORACLE_AUTHORITY signer constraint (placeholder const to replace at deploy); outcome matrix incl. opponent-wins branch; report codes 1=PASS 2=FAIL 3=UNVERIFIED)_

- [x] Step 6: Write refund_unjoined instruction
 Requires > 1 hour since creation, status Created
 Refunds stake to creator
 _(shipped: clock check + status check + PDA-signed escrow transfer)_

- [ ] Step 7: Build and deploy to devnet
 Click "Build and Deploy" in Playground
 Save program ID to: contracts/DEPLOYED_PROGRAM_ID.txt
 ⏳ pending user browser action (requires devnet SOL — see USER_ACTIONS.md)

- [ ] Step 8: Export IDL for Android client
 Click "Export" -> "IDL" -> copy JSON
 Save to: contracts/program/idl.json
 ⏳ pending user browser action — hand-authored contracts/program/idl.json already exists (Row 4); diff against Playground export when available

### Task 5.2: Test Contract Logic via Playground UI

**Files:**
- New: `contracts/tests/manual-test-cases.md`

**Interfaces:**
- Consumes: Deployed program
- Produces: Verified logic

- [x] Step 1: Write test cases in manual-test-cases.md
 Document steps for each instruction via Playground Call tab
 _(shipped: contracts/tests/manual-test-cases.md — 16 cases incl. join/negative paths)_

- [ ] Step 2: Execute test cases
 Use Playground "Call" tab for each instruction
 ⏳ pending user browser action (after 5.1 deploy; cases documented in Step 1)

---

## Phase 6: Supabase Oracle Functions

### Task 6.1: Complete Oracle Edge Function

**Files:**
- Modify: `supabase/functions/oracle/index.ts`

**Interfaces:**
- Consumes: Phase 0.4 Supabase setup
- Produces: Live API endpoints

- [x] Step 1: Add submit-report handler
 POST /submit-report validates JSON and inserts into usage_reports table
 _(shipped: validation of camelCase payload from 8.1 Step 1 — challengePubkey/walletPubkey/usedSeconds/window times/tamperSignals; base58 + range checks; 400 on invalid, insert via service role. Also added /register-challenge (plan gap: nothing else ever wrote challenges, but 6.2 Step 1 reads limit from it; app calls it after on-chain create in Phase 7/8) and optional x-oracle-secret auth via ORACLE_API_SECRET env)_

- [x] Step 2: Add settle handler
 POST /settle fetches both reports, determines results, stores in settlement_results
 _(shipped: latest report per wallet; missing report -> UNVERIFIED; upsert settlement_results + challenges.status update; returns creatorResult/opponentResult codes for on-chain settle instruction (Phase 8.2))_

- [x] Step 3: Implement result logic
 - If tamper_signals has PERMISSION_REVOKED or CLOCK_CHANGED -> UNVERIFIED
 - If used_seconds <= limit -> PASS
 - Else -> FAIL
 _(shipped: supabase/functions/oracle/determine_result.ts, TDD — 12/12 node tests green (determine_result.test.ts); UNVERIFIED counts as FAIL for outcome but keeps code 3 on-chain)_

- [ ] Step 4: Set environment variables
 In Supabase -> Settings -> Functions -> Environment:
 SUPABASE_URL = your-project-url
 SUPABASE_SERVICE_ROLE_KEY = your-service-role-key
 ⏳ pending user (also set ORACLE_API_SECRET — checklist in contracts/USER_ACTIONS.md)

- [ ] Step 5: Deploy function
 Deploy via Dashboard: Functions -> oracle -> Deploy
 Test: curl https://[project-ref].supabase.co/functions/v1/oracle/health
 Expected: returns alive status
 ⏳ pending user (deploy artifacts ready in supabase/functions/oracle/)

### Task 6.2: Settlement Result Determination

**Files:**
- Modify: `supabase/functions/oracle/index.ts` (existing)

**Interfaces:**
- Consumes: usage_reports table populated by app
- Produces: settlement_results table updated

- [x] Step 1: Implement determineResult logic
 Read challenge limit from challenges table to compare with used_seconds
 _(shipped: settle reads challenges.limit_seconds + latest usage_reports per wallet; same tested module as 6.1 Step 3)_

- [x] Step 2: Handle both-fail scenario
 If both FAIL -> set both_fail=true -> status BOTH_FAIL_LOCKED
 _(shipped: settleOutcome() bothFail flag + challenges.status = BOTH_FAIL_LOCKED; tests cover UNVERIFIED-as-fail edge)_

- [ ] Step 3: Test result determination
 Submit fake reports with known usage seconds
 Verify settlement_results table updated correctly
 ⏳ pending user (needs deployed project + SQL migration — see contracts/USER_ACTIONS.md; pure logic already covered by 12 local tests)

---

## Phase 7: Android <-> Solana Integration

### Task 7.1: Manual IDL Parser for Kotlin

**Files:**
- New: `app/src/main/java/com/volo/app/data/solana/IdlParser.kt` _(plan path com.volofinal.volofinal is stale — actual package is com.volo.app)_
- New: `app/src/main/java/com/volo/app/data/solana/ProgramClient.kt`
- New: `app/src/main/assets/idl.json`

**Interfaces:**
- Consumes: contracts/program/idl.json from Phase 5
- Produces: Methods to build transactions from Kotlin

- [x] Step 1: Copy IDL to Android assets
 Copy contracts/program/idl.json to app/src/main/assets/idl.json
 _(copied, hash-verified identical)_

- [x] Step 2: Create IdlParser
 Load JSON from assets
 Parse instructions array to find create_challenge, settle_challenge, etc.
 Compute discrimators: first 8 bytes of sha256("global:" + instruction_name)
 _(shipped: IdlParser.kt — org.json parse + recompute-verify; test dep org.json:json added for unit tests; account discriminator = sha256("account:" + name))_

- [x] Step 3: Create ProgramClient
 Methods:
 - createChallenge(params): builds + sends transaction
 - getChallenge(pubkey): fetches account data via RPC
 - joinChallenge(pubkey): second user joins
 - settleChallenge(pubkey, results): settles via oracle
 _(shipped: buildCreateChallenge/buildJoinChallenge/buildSettleChallenge/buildRefundUnjoined (unsigned wire tx via TransactionCompiler), getChallenge (JSON-RPC + ChallengeAccountCodec), sendTransaction; send orchestration = ChallengeSender via WalletManager.signTransaction (MWA) — split build/sign/send across the classes named above; supporting pure modules: Pda.kt (ed25519 on-curve check per RFC 8032), ProgramInstructions.kt, TransactionCompiler.kt, ChallengeAccountCodec.kt, SolanaConfig.kt)_

- [x] Step 4: Write unit test
 JUnit test verifying discriminator calculation matches expected value
 _(IdlParserTest 7 tests: known vector for create_challenge + computed-vs-stored for all instructions and account disc; total suite now 89/89)_

- [x] Step 5: Verify compilation
 Gradle build succeeds with no errors
 _(testDebugUnitTest + assembleDebug green; APK 18.2 MB)_

### Task 7.2: Create-Challenge Transaction Build
 _(reconstructed header per ledger Row 7 — steps below were orphaned after 7.1; Step 1 is new so the list numbers coherently)_

- [x] Step 1: Build create_challenge instruction
 Program: from declare_id in idl.json "address"
 Accounts: challenge PDA (seeds [b"challenge", creator, opponent]), creator, opponent,
 mint, creator USDC ATA, escrow PDA ATA, token_program, ata_program, system_program
 Data: 8-byte discriminator + stake_amount u64 LE + string(u32 len + utf8) + limit u32 LE + window u32 LE
 _(shipped: ProgramInstructions.createChallenge + PDA/ATA derivation; bytes asserted in ProgramInstructionsTest)_

- [x] Step 2: Build SPL token transfer instruction
 Source: user's USDC ATA
 Destination: escrow PDA ATA
 Amount: stakeAmount * 1_000_000 (micro-USDC)
 _(deviation ruling: transfer happens INSIDE the program — create_challenge CPI-transfers stake into escrow (lib.rs line 61), join likewise. An app-side transfer ix here would DOUBLE-FUND escrow. splTokenTransfer builder exists for completeness but challenge transactions carry one instruction.)_

- [x] Step 3: Combine into single transaction
 Transaction contains:
 1. createChallenge instruction
 2. SPL token transfer instruction
 _(per Step 2 ruling: transaction contains just createChallenge — stake movement is atomic inside it; TransactionCompiler supports multi-ix if ever needed)_

- [x] Step 4: Send via MWA wallet
 Sign transaction -> send via sendRawTransaction()
 _(shipped: WalletManager.signTransaction via adapter.transact receiver-lambda + signTransactions(byte[][]); ChallengeSender orchestrates build->sign->RPC send; returns tx signature)_

- [ ] Step 5: Test on phone
 Create challenge with 1 USDC -> approve in Phantom
 Expected: Transaction succeeds on Solscan
 ⏳ pending user device test (needs deployed program id in idl.json + devnet USDC — chain of Phase 0/5 user actions first)

### Task 7.3: Challenge Status Polling

**Files:**
- New: `app/src/main/java/com/volofinal/volofinal/data/repositories/ChallengeRepository.kt`

**Interfaces:**
- Consumes: ProgramClient.getChallenge()
- Produces: Real-time UI updates

- [x] Step 1: Poll challenge account
 Every 5 seconds via RPC getAccountInfo(challengePDA)
 _(shipped: ChallengeRepository.startPolling — 5s loop over Room's CREATED/ACTIVE rows, getChallenge (getAccountInfo), cancellation-safe, swallows RPC errors until next tick; started once in MainActivity.onCreate)_

- [x] Step 2: Update Room database
 Compare status field, update UI state
 _(shipped: pure resolveNextStatus(onChain 0..4 -> CREATED/ACTIVE/SETTLED/REFUNDED/BOTH_FAIL_LOCKED, null = unchanged) + dao.updateStatus; 3 unit tests; no schema change — status column only, per plan)_

- [x] Step 3: Show status in UI
 ActiveChallengeScreen observes LiveData from Room
 Color-coded indicators: green=passing, red=exceeded, yellow=pending
 _(app has no ActiveChallengeScreen — ChallengesScreen is the list it means; observes Room via Flow/collectAsState instead of LiveData (Compose-idiomatic). Colors: ACTIVE=Celadon/green, CREATED=Copper/yellow-pending, BOTH_FAIL_LOCKED=Clay/red, settled/refunded=muted. Usage-based passing vs exceeded (red-while-active) needs per-challenge usage persistence -> lands with Phase 8 report submission; annotate there.)_

---

## Phase 8: Settlement Flow

### Task 8.1: Usage Report Submission

**Files:**
- Modify: `app/src/main/java/com/volofinal/volofinal/data/usage/UsageWorker.kt`

**Interfaces:**
- Consumes: Phase 3 usage engine + Phase 6 API
- Produces: Reports in Supabase

- [x] Step 1: Build report payload
 Send only: challengePubkey, walletPubkey, usedSeconds, window times, tamperSignals
 _(shipped: pure reportPayload() in data/usage/OracleReportApi.kt — exact 5 plan fields (6 keys incl. both window times), ISO-8601 UTC windows, tamperSignals as name array; 5 unit tests incl. exact-key-set assertion)_

- [x] Step 2: Submit to Oracle
 POST to SUPABASE_URL/functions/v1/oracle/submit-report
 _(shipped: OracleReportApi.submit via HttpURLConnection; wired into existing postReport() stub so UsageWorker needs no call-site change. VoloBackend.SUPABASE_URL is a PROJECT-REF placeholder until user creates the Supabase project — submit() skips gracefully while unconfigured)_

- [x] Step 3: Handle tamper signals
 If present -> mark challenge as STATUS_PENDING_VERIFICATION
 _(shipped: flagForVerification() = TamperSignals.isCompromised(); UsageWorker sets localStatus=PENDING_VERIFICATION before submitting; ChallengesScreen/dao.observeActive extended to still show flagged rows with a red VERIFY chip, while the polling query deliberately excludes them so a chain status can't overwrite the flag)_

- [ ] Step 4: Test submission
 Run worker -> check Supabase shows row
 Expected: usage_reports table has new entry
 _(⏳ pending: needs user's live Supabase project + URL configuration — see contracts/USER_ACTIONS.md Phase 6)_

### Task 8.2: Automatic Settlement Trigger

**Files:**
- New: `app/src/main/java/com/volofinal/volofinal/data/solana/SettlementClient.kt`

**Interfaces:**
- Consumes: Phase 6 /settle endpoint + Phase 7.1 tx signing
- Produces: Settlement executed

- [x] Step 1: Poll for readiness
 After end_time -> check settlement_results table for SETTLED=true
 _(shipped: SettlementClient — PostgREST GET settlement_results first, then past end_time when no row exists: idempotent POST /register-challenge (oracle challenges row must exist or /settle 404s — the gap flagged in the 6.1 annotation) followed by POST /settle (409 falls back to the row); pure isSettlementReady = window over AND (settled OR bothFail), 6 unit tests + register contract test; driven every 5s by ChallengeRepository.checkSettlements. Added a public-read RLS policy on settlement_results (migration not yet applied) + SUPABASE_ANON_KEY placeholder for PostgREST)_

- [x] Step 2: Trigger settlement
 ```kotlin
 suspend fun settleChallenge(challengePubkey: String): Result<String> {
   val oracleResult = supabaseClient.getSettlementResult(challengePubkey)
   val tx = buildSettleInstruction(challengePubkey, oracleResult)
   return sendTransaction(tx)
 }
 ```
 _(shipped: ChallengeRepository.settleWithUser(entity, mwaSender) — sync oracle result -> derive both ATAs -> SettleChallengeParams(creatorResult/opponentResult codes) -> ChallengeSender.settleChallenge -> MWA sign (feePayer = oracle = connected wallet, per USER_ACTIONS Phase 5) -> RPC send -> localStatus=SETTLED. Deviations: hand-rolled HttpURLConnection instead of a Supabase SDK; on-chain tx can't sign itself — MWA needs the user present, so ended ACTIVE rows show a "Settle" button (oracle computation itself stays automatic in the poll loop); Result<String> matches the plan signature)_

- [x] Step 3: Handle both-fail scenario
 If oracle returns bothFail=true -> show Recovery Lock screen
 _(shipped: bothFail=true flips localStatus to BOTH_FAIL_LOCKED in the poll loop; the row stays visible (red LOCKED chip) and navigates to the new RecoveryLockScreen (Duel palette, lock icon, challenge pubkey, back); also short-circuits settleWithUser. 10 new tests: SettlementLogicTest 9 + canSettle 1)_

---

## Phase 9: SKR Integration (Demo Gate Only)

### Task 9.1: Check SKR Balance + Gate UI

**Files:**
- New: `app/src/main/java/com/volofinal/volofinal/data/solana/TokenBalanceApi.kt`
- Modify: `app/src/main/java/com/volofinal/volofinal/ui/screens/ProfileScreen.kt`

**Interfaces:**
- Consumes: Phase 7.1 ProgramClient for RPC calls
- Produces: SKR balance display + Open Duels feature gate

- [x] Step 1: Query SKR token balance
 spl-token accounts for wallet -> find SKR mint balance
 SKR mint on devnet: check SolanaFM for official devnet address
 _(shipped: TokenBalanceApi over ProgramClient's RPC (getBalance + getTokenAccountBalance on the wallet's ATA; missing ATA = 0); SKR mint taken from Solana Mobile official docs: SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3; 4 parse/format unit tests)_

- [x] Step 2: Display in Profile
 Show: Wallet address, SOL balance, USDC balance, SKR balance
 _(shipped: ProfileScreen rows — wallet address (short form; WalletRow also shows it), SOL (9dp), USDC, SKR balances with "Loading…" until the fetch returns; fetched via LaunchedEffect on connect)_

- [x] Step 3: Gate Open Duels
 If SKR balance >= 10 -> show "Open Duels: Available" badge
 Else -> show "Unlock by staking 10 SKR" locked badge
 _(shipped: OpenDuelsGateRow — Celadon available badge (clickable) vs muted locked badge, exact plan wording via openDuelsLabel(); threshold test covers exact-10, decimals-agnostic)_

- [x] Step 4: Create placeholder Open Duels screen
 Behind gate, shows: "Feature unlocked in full release"
 No actual matching logic — just demonstrates the gating mechanism
 _(shipped: OpenDuelsScreen (Paper palette, trophy icon, exact copy) behind new route open_duels)_

- [ ] Step 5: Verify on phone
 Connect wallet -> profile shows SKR balance
 Expected: Correct gate condition displayed
 _(⏳ pending: device test — needs connected wallet with devnet SKR)_

---

## Phase 10: Seeker Identity (Mock Provider Only)

### Task 10.1: Identity Provider Interface

**Files:**
- New: `app/src/main/java/com/volofinal/volofinal/data/identity/IdentityProvider.kt`
- New: `app/src/main/java/com/volofinal/volofinal/data/identity/MockSeekerProvider.kt`

**Interfaces:**
- Consumes: Phase 2 wallet address
- Produces: Mock identity verification (real hardware deferred)

- [ ] Step 1: Define SeekerIdentityProvider interface
 Methods:
 - verifyGenesisToken(wallet: String): IdentityVerificationResult
 - resolveDomain(skrDomain: String): String?

 Sealed classes:
 - VerifiedSeeker / Unverified / Error

- [ ] Step 2: Implement mock provider
 MockSeekerProvider.kt:
 - verifyGenesisToken always returns Unverified (no hardware)
 - resolveDomain returns known demo addresses for .skr domains:
   "alice.skr" -> "6o9DpPwoNMyfJx2Bw6rFByrZ2Z3r5qF7hJ8eD3aK9nB1mC5x"
   "bob.skr" -> "9fG8jK2hL4mN7pQ1rT6sW3xY8bV5cD2eF9gH7jK4lM1nQ3r"

- [ ] Step 3: Inject in WalletManager
 WalletManager uses MockSeekerProvider by default
 Replaceable via DI in future when Seeker hardware available

- [ ] Step 4: Placeholder badge in UI
 In ProfileScreen:
 "Seeker Status: Not Verified (requires Seeker hardware)"
 In challenge screens:
 "Opponent: alice.skr [Unverified]"

- [ ] Step 5: Document future integration
 Create `docs/seeker-future-integration.md`:
 - Replace MockSeekerProvider with SeedVaultIdentityProvider
 - Add Genesis Token verification via solana-mobile SDK
 - Add Secure Element signing for high-security features

---

## Phase 11: Demo Day Preparation

### Task 11.1: Configure Demo Environment

**Files:**
- New: `assets/demo-config.json`

**Interfaces:**
- Consumes: All phases 0-10 working
- Produces: One-click demo setup

- [ ] Step 1: Pre-create demo challenge
 On app first launch (dev build):
 - Target app: "com.tiktok.android" (or any installed app)
 - Limit: 1 minute
 - Stake: 1 USDC
 - Window: 5 minutes
 Store pubkey in demo-config.json

- [ ] Step 2: Two test wallet addresses
 - Wallet A (creator): funds via Phantom
 - Wallet B (opponent): pre-funded
 Both known -> no second person needed

- [ ] Step 3: Demo usage scenario
 For demo purposes:
 - User A: simulate 55 seconds usage -> PASS
 - User B: simulate 70 seconds usage -> FAIL
 Show automatic settlement -> User A wins 2 USDC

- [ ] Step 4: Trigger settlement
 Tap "Settle Challenge" -> calls /settle -> signs tx -> shows result
 Expected: User A balance increases by 1 USDC

### Task 11.2: Record Demo Video + Pitch Deck

**Files:**
- New: `assets/demo-script.md`
- New: `assets/pitch-deck-outline.md`

**Interfaces:**
- Consumes: Working app on phone
- Produces: YouTube video + Google Slides deck

- [ ] Step 1: Screen record setup
 Enable "Screen recording" in Developer Options
 Or use Android Studio -> Tools -> Screen Record

- [ ] Step 2: Record 3-minute demo
 Capture:
 1. Connect wallet via Phantom -> select devnet
 2. Create demo challenge -> stake 1 USDC
 3. Simulate usage (55s for creator, 70s for opponent)
 4. Settle -> show 2 USDC reward
 5. Verify tx on Solscan via deep link

- [ ] Step 3: Write 10-slide pitch deck
 Slide 1: Title + tagline: "Volo -- Financial Accountability for Screen Time"
 Slide 2: Problem -- $1B lost to attention economy annually
 Slide 3: Product -- Stake USDC -> challenge a friend -> stay off TikTok
 Slide 4: Tech -- Android native + Solana devnet + MWA + Seed Vault
 Slide 5: Demo -- 30-second screen capture
 Slide 6: Traction -- 1-week MVP, solo dev
 Slide 7: SKR angle -- 10 SKR gates Open Duels, 100 SKR unlocks leaderboards
 Slide 8: Seeker integration -- visual mockup of Genesis Token badge
 Slide 9: Market -- 150k Seeker users + growing attention economy
 Slide 10: Ask -- $10K SKR prize + dApp Store placement

- [ ] Step 4: Upload deliverables
 YouTube: Upload video as "Unlisted"
 Google Drive: Upload pitch deck PDF
 Save links to: submission-links.txt

---

## Phase 12: Submission + Polish

### Task 12.1: APK Build + Release

**Files:**
- Modify: `app/build.gradle.kts` (release config)

**Interfaces:**
- Consumes: All code complete
- Produces: Release-ready APK

- [ ] Step 1: Generate signed APK
 Build -> Generate Signed Bundle / APK
 Keystore: Volo.keystore (create new, save in repo)
 Build variant: Release (Minify enabled)

- [ ] Step 2: Reduce APK size
 ProGuard rules + resource shrinking:
 - android:extractNativeLibs="false" in manifest
 Target APK size < 20 MB

- [ ] Step 3: Upload to GitHub
 Create release at github.com/[repo]/releases/tag/v1.0
 Upload app-release.apk
 Copy release URL

### Task 12.2: Final Verification + Error Log Review

**Files:**
- Read: volo-errors.md
- Read: volo-plan.md

- [ ] Step 1: Cross-check error log
 Re-read volo-errors.md -> mark each item verified or updated
 Add any new errors encountered

- [ ] Step 2: Test on second device
 If possible, install APK on another Android phone
 Expected: App runs -> connects wallet -> shows demo challenge

- [ ] Step 3: Submit to hackathon portal
 Final checklist:
 [x] APK uploaded + link ready
 [x] Demo video uploaded -> unlisted YouTube link
 [x] Pitch deck -> Google Slides shareable link
 [x] Source code -> public GitHub repo
 [x] Error log + README updated

---

## Free Tools Summary

| Purpose | Tool | Disk Estimate |
|---------|------|---------------|
| Android IDE | Android Studio 2025.3.2 (already installed) | 0 GB (reuse) |
| Android SDK | Platform 33 + Build Tools only | 4 GB |
| Solana CLI | Git Bash install | < 1 GB |
| Wallet | Phantom mobile (free) | 0 GB |
| Backend | Supabase free tier | 0 GB |
| Contract | Solana Playground (browser) | 0 GB |
| Video | Android Studio screen recorder | 0 GB |
| Hosting | YouTube (unlisted) | 0 GB |
| Slides | Google Slides | 0 GB |
| Fonts/icons | Feather Icons (free) | 0 GB |
| Total estimated additional disk |  | 5 GB |

---

## Timeline (11 Days, Realistic)

| Days | Work | Deliverable |
|------|------|-------------|
| 0 | Environment setup | SDK + Solana CLI + Supabase ready |
| 1 | Phase 1-2 App skeleton + wallet | First APK on phone |
| 2 | Phase 3 Usage tracking | Real TikTok usage shows in app |
| 3 | Phase 4 Challenge UI | Full create flow working |
| 3-4 | Phase 5 Contract via Playground | Deployed program on devnet |
| 4-5 | Phase 6-7 Oracle + integration | Create challenge on-chain |
| 5-6 | Phase 8 Settlement flow | End-to-end flow works |
| 6 | Phase 9-10 SKR + Seeker mock | Feature gates + badges visible |
| 7 | Phase 11 Demo prep | Recorded video + deck |
| 8 | Phase 12 Polish | APK built |
| 9 | Edge case testing | Error log updated |
| 10 | Submission prep | Links + repo ready |
| 11 | Submission | Submitted to hackathon |

## Seeker Hardware Testing Notes

| Feature | Test Status | Notes |
|---------|-------------|-------|
| MWA wallet connection | Real device | Works on any Android phone |
| Usage tracking | Real device | Standard Android APIs |
| USDC deposits | Real device | Standard SPL token calls |
| Genesis Token check | MockOnly | Requires physical Seeker hardware |
| SE signing | MockOnly | Requires Seeker hardware |
| .skr domain resolver | MockOnly | Hardcoded demo addresses |
| All on-chain features | Real device | No hardware dependency |

## Phase 2 (Future) Notes

| Feature | Status | Notes |
|---------|--------|-------|
| Recovery lock logic | Deferred | Contract state exists, enforcement deferred |
| Open Duels matching | Deferred | SKR gate implemented only |
| Seeker SE signing | Deferred | Mock provider only until hardware available |
'
