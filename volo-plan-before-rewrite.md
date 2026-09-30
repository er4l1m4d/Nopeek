# Volo — Detailed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android APK (Kotlin/Jetpack Compose) that locks USDC-backed stakes against app usage limits, settles based on verified usage, and integrates Solana Mobile SDK for Seeker compatibility — all using free tools and devnet.

**Architecture:** Thin vertical slice first — usage tracking, MWA wallet connection, Anchor escrow on devnet, Supabase oracle settlement, real APK tested on phone. Seeker-only features (Genesis Token verification, SE signing) isolated behind mock-provider adapters for later hardware integration.

**Tech Stack:** Kotlin + Jetpack Compose (Android Studio Chipmunk 2021.2.1), Solana CLI via Git Bash (deploy only), hand-written Anchor program (no Anchor CLI), Supabase Edge Functions, web3.js/Solana Kotlin libs, GitHub Free, Solana Devnet.

**Spec:** `volo-plan.md` (this document) serves as both plan and spec.

## Global Constraints

- Android Studio Chipmunk (2021.2.1); SDK Platform 33 only, no emulator images.
- No WSL2 required; Solana CLI via Git Bash on Windows for contract deployment only.
- All tokens on Solana Devnet only; no mainnet transactions.
- Wallet signing via real MWA-compatible wallet; Seed Vault SDK integrated but Seeker-specific checks deferred to mock providers.
- Daily limit resets at local midnight; tamper signals collected but **not enforced** in Phase 1.
- Free tools only: Android Studio Community, GitHub Free, Supabase Free Tier.
- Recovery lock logic deferred to Phase 2 (after basic settlement works).
- USDC display: always use 6 decimals; never show raw token units.

## Review Focus

1. Revoked Usage Access mid-challenge → app detects via `AppOpsManager.checkOpNoThrow()`, marks result UNVERIFIED.
2. Changed system clock → app detects backward jumps >5 min, adds tamper signal `CLOCK_CHANGED`.
3. Network failure during settlement → retry 3x with exponential backoff (500ms, 1s, 2s), then queue locally.
4. Duplicate settlement → contract rejects if status not CREATED; app polls `getChallengeStatus` for idempotency.
5. USDC display precision → all UI multiplies/divides by 1,000,000.

---

## Phase 0: Environment Setup

### Task 0.1: Minimal Android Studio Install

**Files:**
- New: `README.md`
- New: `.gitignore`

**Interfaces:**
- Consumes: Windows 10/11 + 21 GB free disk
- Produces: `local.properties` with SDK path

- [ ] **Step 1: Install Android Studio Chipmunk**
 Download: https://redirect.android.com/androidsdk/Runnable/2021.2.1/android-studio-2021.2.1-windows.exe
 Select "Custom Install" → UNSELECT everything except:
 - Android SDK
 - Android SDK Platform
 - (Leave Android Virtual Device UNCHECKED)

- [ ] **Step 2: Install SDK Platform 33 + Build Tools 33.0.0**
 After install → Tools → SDK Manager:
 - Check "Show Package Details"
 - Install only: SDK Platform 33, Build-tools 33.0.0
 - Skip everything else

- [ ] **Step 3: Verify footprint**
 Run: `dir "$env:LOCALAPPDATA\Android\Sdk" /s /d`
 Expected: No emulator directory. Only `platforms\android-33`, `build-tools\33.0.0`

### Task 0.2: Solana CLI via Git Bash (Deploy-Only)

**Files:**
- New: `contracts/Anchor.toml`
- New: `contracts/Cargo.toml`

**Interfaces:**
- Consumes: Git for Windows (Git Bash)
- Produces: `dev-wallet.json`, `devnet-wallet-info.txt`

- [ ] **Step 1: Install Git for Windows**
 https://git-scm.com/download/win

- [ ] **Step 2: Install Solana CLI 1.17.0**
 In Git Bash:
 ```bash
 sh -c "$(curl -sSfL https://release.solana.com/v1.17.0/install)"
 echo 'export PATH="$HOME/.local/share/solana/install/active_release/bin:$PATH"' >> ~/.bashrc
 source ~/.bashrc
 ```
 Expected: `solana --version` → `solana-cli 1.17.0`

- [ ] **Step 3: Create wallet + fund**
 ```bash
 solana config set --url https://api.devnet.solana.com
 solana-keygen new --no-bip39-passphrase --silent
 echo "Pubkey: $(solana address)" > devnet-wallet-info.txt
 solana airdrop 2
 solana balance
 ```
 Expected: `2 SOL` printed

- [ ] **Step 4: Get devnet USDC**
 Visit Circle Devnet Faucet (free): https://faucet.circle.com/usdc-ethereum
 Mint: `Ejmc1UB4EsES5h32u8Fp1avkX4rM2e4HFEh3o4HkF2sX`
 Expected: >0 USDC in devnet wallet

### Task 0.3: Supabase Project

**Files:**
- New: `supabase/migrations/20240101000001_init.sql`
- New: `supabase/functions/oracle/index.ts`

**Interfaces:**
- Consumes: Supabase free account
- Produces: Project URL + anon key (for Android)

- [ ] **Step 1: Create project + enable Auth**
 https://supabase.com/dashboard → New Project → `volo-accounts`
 Enable Auth → Email provider

- [ ] **Step 2: Deploy schema**
 Create migration file with tables: `challenges`, `usage_reports`, `settlement_results`
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

- [ ] **Step 3: Deploy Oracle Function**
 Create `supabase/functions/oracle/index.ts`:
 - `GET /health` → `{"status": "alive"}`
 - `POST /submit-report` → stores usage report
 - `POST /settle` → returns settlement result
 Deploy: `supabase functions deploy oracle`
 Expected: `curl /health` returns alive

---

## Phase 1: Android App Skeleton

### Task 1.1: Create Compose Project

**Files:**
- New: `app/src/main/java/com/volofinal/volofinal/MainActivity.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: Phase 0 Android setup
- Produces: Runnable empty Compose app

- [ ] **Step 1: Create new project**
 File → New → "Empty Compose Activity"
 Package: `com.volofinal.volofinal`, Language: Kotlin, Min SDK: 33

- [ ] **Step 2: Define data models**
 Create `app/src/main/java/com/volofinal/volofinal/data/Models.kt`:
 ```kotlin
 data class Challenge(
 val pubkey: String,
 val creatorWallet: String,
 val opponentWallet: String,
 val targetApp: String,
 val limitSeconds: Long,
 val stakeAmount: Long,
 val windowType: String,
 val windowSeconds: Long,
 val startTime: Long,
 val endTime: Long,
 val status: String,
 val createdAt: Long
 )
 data class UsageReport(
 val challengePubkey: String,
 val walletPubkey: String,
 val usedSeconds: Int,
 val windowStart: Long,
 val windowEnd: Long,
 val tamperSignals: Map<String, Any>
 )
 data class SettlementResult(
 val challengePubkey: String,
 val creatorResult: String,
 val opponentResult: String,
 val bothFail: Boolean
 )
 ```

## Phase 1: Android App Skeleton
- [ ] Create new Android project in Android Studio (Empty Compose Activity)
- [ ] Set up navigation (Jetpack Navigation Compose)
- [ ] Create basic screens: Home, Challenge Create, Active Challenge, Profile
- [ ] Set up light theme with clean aesthetic (Apple Screen Time inspiration)
- [ ] Add basic UI components: buttons, text fields, cards
- [ ] Implement ring + bar progress indicators (use MPAndroidChart or custom canvas)
- [ ] Set up Seeker verification badge UI (placeholder for now)

## Phase 2: Wallet Connection & Identity
- [ ] Add Solana Mobile Wallet Adapter dependency
- [ ] Implement Seed Vault / MWA connection flow
- [ ] Show connected wallet address in Profile
- [ ] Display `.skr` domain resolution UI (if Seeker device connected)
- [ ] Implement "Connect Wallet" button on Home screen

## Phase 3: Usage Tracking Engine
- [ ] Request `PACKAGE_USAGE_STATS` permission
- [ ] Integrate `UsageStatsManager` to query app usage
- [ ] Build target app selection (any installed app)
- [ ] Calculate per-day screen time in minutes (midnight local reset)
- [ ] Store local usage data
- [ ] Set up WorkManager for 15-minute sync intervals

## Phase 4: Challenge Creation Flow
- [ ] Build Challenge Create screen UI:
  - App selection dropdown (from installed apps)
  - Time limit presets + custom (15/30/60/120 + custom minutes)
  - Challenge window presets (1h/4h/12h/24h/7d)
  - Stake amount input (default 5 USDC)
  - Opponent field (free-text for .skr username or wallet address)
- [ ] Validate all inputs before proceeding
- [ ] Show confirmation screen with challenge summary

## Phase 5: Smart Contract Development (Anchor)
- [ ] Scaffold Anchor program structure (`anchor init volo`)
- [ ] Define `Challenge` account struct:
  - creator, opponent (pubkeys)
  - token_mint (USDC devnet)
  - stake_amount (u64)
  - target_app (String)
  - limit_seconds (u32)
  - challenge_window_seconds (u32)
  - start_time (i64)
  - end_time (i64)
  - status (enum: CREATED/JOINED/SETTLED/CANCELLED)
  - result (enum: NO_RESULT/USER_A_WON/USER_B_WON/BOTH_PASS/BOTH_FAIL)
- [ ] Define PDA for escrow vault (derived from challenge pubkey)
- [ ] Implement `create_challenge()` instruction
- [ ] Implement `join_challenge()` instruction
- [ ] Implement `settle_challenge()` instruction (oracle-gated)
- [ ] Implement `refund_unjoined()` instruction (1-hour TTL)
- [ ] Add token transfer logic (CPI to transfer USDC to/from escrow)
- [ ] Build and deploy to devnet (`anchor build && anchor deploy`)
- [ ] Run `anchor test` for local verification

## Phase 6: Backend Oracle (Supabase)
- [ ] Set up Supabase project
- [ ] Create Postgres table for challenge results:
  - challenge_id (foreign key to Solana)
  - creator_result (PASS/FAIL)
  - opponent_result (PASS/FAIL)
  - timestamp
- [ ] Create Postgres table for oracle-auth:
  - oracle_wallet_pubkey (authorized resolver)
- [ ] Deploy Supabase Function (Edge Function) for `/settle` endpoint:
  - Receives usage report from Android app
  - Verifies challenge exists on-chain
  - Stores result in Postgres
  - Returns signed settlement payload (or triggers settlement)
- [ ] Set up Supabase Auth (email/password or anonymous, free tier)
- [ ] Create `/oracle-status` endpoint to check if oracle is live

## Phase 7: Android ↔ Solana Integration
- [ ] Add Solana web3 dependencies (web3-solana, rpc-core, borsh)
- [ ] Implement anchor-client or manual IDL-based interaction from Kotlin
- [ ] Connect Challenge Create → `create_challenge()` on Solana
- [ ] Handle USDC allowance/approval flow before deposit
- [ ] Call `join_challenge()` for opponent
- [ ] Display challenge status from on-chain data
- [ ] Implement claim/refund flows

## Phase 8: Settlement Flow
- [ ] Android app sends usage report to Supabase Oracle
- [ ] Oracle validates report format + timing
- [ ] Oracle stores result in Postgres
- [ ] Oracle (or user-triggered) calls `settle_challenge()` on Solana
- [ ] App listens for settlement event (poll for now, websocket later)
- [ ] Display final result screen with reward/loss

## Phase 9: SKR Integration
- [ ] Check if connected wallet holds/stakes SKR (query SPL token balance)
- [ ] Unlock "Open Duels" feature if user stakes 10 SKR
- [ ] Display SKR tier badge in Profile
- [ ] Create Open Duel flow (random matching — simplified for hackathon)
- [ ] Implement SKR deposit for open duels (deposit 10 SKR, get refund on completion)

## Phase 10: Seeker Identity
- [ ] Query Seeker Genesis Token (if device is Seeker)
- [ ] Show "Verified Seeker" badge (subtle checkmark on profile + challenges)
- [ ] Display `.skr` domain name on challenge screens
- [ ] (Fallback: regular wallet address if not Seeker device)

## Phase 11: Demo Day Preparation
- [ ] Create real 1-minute demo scenario:
  - Create challenge for TikTok with 1-min limit in 5-min window
  - Stake 1 USDC
  - Record 55 seconds of real TikTok usage (PASS)
  - Opponent exceeds 1 min (FAIL)
  - Show automatic settlement (10 USDC reward)
- [ ] Test full flow on Android device (real usage data)
- [ ] Generate APK (`Build > Build Bundle(s) / APK(s)`)
- [ ] Record demo video (3-5 minutes max)
- [ ] Create pitch deck (10-15 slides)
- [ ] Write demo script for judges

## Phase 12: Submission & Polish
- [ ] Final testing on real Android device
- [ ] Edge case testing (app crash, network loss, etc.)
- [ ] Polish UI animations (use built-in Compose animations)
- [ ] Final APK build + GitHub release
- [ ] Submit to Solana Mobile hackathon portal
- [ ] Upload demo video (YouTube unlisted — free)
- [ ] Upload pitch deck (Google Slides — free)

---

## Free Tools Used Throughout

| Purpose | Tool |
|---------|------|
| Android IDE | Android Studio (free) |
| Solana dev | Solana CLI, Anchor, Rust (all free) |
| Backend | Supabase (free tier) |
| Code editor | VS Code (free) |
| Version control | Git + GitHub (free) |
| Video recording | Android Studio screen recorder |
| Video hosting | YouTube (unlisted) |
| Slides | Google Slides (free) |
| Design | Figma (free tier) |
| Devnet USDC | Circle faucet |
| Devnet SOL | `solana airdrop` |

## Timeline (11 Days)

| Days | Phases |
|------|--------|
| 1-2 | Phase 0, 1, 2 |
| 2-3 | Phase 3, 4 |
| 3-5 | Phase 5, 6 |
| 5-6 | Phase 7, 8 |
| 6-7 | Phase 9, 10 |
| 7-9 | Phase 11 |
| 9-10 | Phase 12, polish |
| 10-11 | Final review + submission |
