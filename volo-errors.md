# 🔴 ERROR LOG — Volo Build

> **How to use this file**: Every time you hit an error during the Volo build, add the error message, what you tried, and the solution below. This way we build a troubleshooting reference that saves future headaches (and hackathon stress).

---

## ✅ Status pass (plan 12.2 Step 1 — 2026-10-01)

| Error / issue | Status |
|---|---|
| `PACKAGE_USAGE_STATS` permission denial | **✅ verified + fixed** — review showed the manifest declared no permissions at all; `<uses-permission>` added, grant flow works via Usage Access settings |
| Unable to resolve Activity for Intent | ⏳ reference — not encountered (deep links use Activity context) |
| `BadTokenException` (toast after destroy) | ⏳ reference — not encountered |
| Fragment/view not attached | ⏳ reference — not applicable (pure Compose, no fragments) |
| Anchor Rust version mismatch | ⏳ deferred — local `cargo check` blocked by disk (Row 6); Playground deploy is the user's check |
| `invalid account provided` (PDA mismatch) | ⏳ pending on-device — seed math unit-tested (RFC 8032 on-curve check) |
| IDL not found for program | ⏳ reference — IDL hand-authored locally (Row 4 ruling), `anchor idl` not run |
| `insufficient funds for fee` | ⚠️ encountered as environment issue — devnet airdrop rate-limited (ledger 0.2/0.3) |
| Supabase `permission denied` (RLS) | ⚠️ updated — RLS now: no client policies except a **public read** on `settlement_results` (8.2); writes are service-role only inside the function |
| Edge Function 504 | ⏳ reference — oracle handlers are single queries, well under 10 s |
| `NoClassDefFoundError: web3.jvm` | **⚠️ obsolete** — web3j dropped (ledger Row 1); txs built by hand per plan 7.1 |
| `Cannot fit requested classes in single dex` | ⏳ reference — not encountered (debug + release both dex fine) |
| `NullPointerException` UsageStatsManager | **✅ verified + fixed** — same root cause as item 1 (missing manifest permission) |
| `403 Forbidden` Solana devnet RPC | ⏳ pending — public devnet endpoint until a dedicated provider is configured |
| APK won't install | ⏳ reference — not encountered; release APK signature verified with apksigner |

---

## 🆕 New errors encountered (this build)

### Error: MWA `transact` lambda "expected 2 parameters" (Phase 7.2)
- **Cause**: `adapter.transact { ... }` closure is `AdapterOperations` receiver + **one** `authResult` parameter (MWA 2.x API).
- **Fix**: `adapter.transact<ByteArray?>(sender) { signTransactions(arrayOf(tx)).signedPayloads.firstOrNull() }`. A 2-param lambda fails compilation.

### Error: `Suspend function 'updateStatus' can only be called from...` (Phase 8.1)
- **Cause**: `Worker.doWork()` is synchronous; Room DAO methods are `suspend`.
- **Fix**: wrap in `runBlocking { ... }` inside `doWork()` (already on a background thread).

### Error: unit tests throw `org.json` "not mocked" (Phase 7.1)
- **Cause**: android.jar stubs replace `org.json` in local unit tests.
- **Fix**: `testImplementation("org.json:json:20240303")`.

### Error: `lintVitalAnalyzeRelease` fails with `IOException: There is not enough space on the disk`
- **Cause**: C: drive nearly full — lint downloads extra analysis jars.
- **Fix**: free disk (delete temp logs etc.) and retry. Local `cargo check` is blocked by the same condition (ledger Row 6).

### Error: `keytool -printcert -jarfile` → "Not a signed jar file" on a signed APK
- **Cause**: keytool only reads v1 (JAR) signatures; minSdk 26 releases are v2/v3 only.
- **Fix**: verify with `apksigner verify --print-certs app-release.apk`.

### Error: Release APK would ship with **no INTERNET permission**
- **Cause**: AGP injects `INTERNET` only into debug manifests.
- **Fix (found by this review):** declare `<uses-permission android:name="android.permission.INTERNET" />` in the main manifest.

### Issue: `androidx.navigationevent` breaks Kotlin 2.0.21 analyzer
- **Fix**: `exclude(group = "androidx.navigationevent")` + forced androidx activity/core pins (see `app/build.gradle.kts` comments).

### Error: `IllegalStateException: LifecycleOwner ... attempting to register while current state is RESUMED` (crash at app launch, release build)
- **Cause**: MWA `ActivityResultSender.<init>` calls `ComponentActivity.registerForActivityResult(...)` (verified by decompiling clientlib-ktx 2.1.1) — androidx forbids registering once the lifecycle is ≥ STARTED. `MainActivity` created it with `by lazy`, so first access happened during the **first composition** (stack: `onAttachedToWindow` → `getValue` → `<init>`) when the activity was already RESUMED.
- **Fix**: construct eagerly in `onCreate` right after `super.onCreate()` (state = CREATED), matching Solana Mobile's official `solana-kotlin-compose-scaffold`.

---

## ⚡ Android Errors

### Error: `Permission Denial: getRunningAppProcesses requires android.permission.PACKAGE_USAGE_STATS`
- **What caused it**: Tried to read usage stats without granting permission.
- **Solution**: 
  1. Add `<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />` to `AndroidManifest.xml`.
  2. In app settings, manually enable "Usage Access" for Volo.
  3. Optionally, deep-link to usage access settings with:  
     `Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)`

---

### Error: `java.lang.IllegalArgumentException: Unable to resolve Activity for [Intent]`
- **What caused it**: Started Activity before `onCreate` finished, or missing flag.
- **Solution**: Add `Intent.FLAG_ACTIVITY_NEW_TASK` if starting from non-Activity context. Or wait until Activity context is available.

---

### Error: `android.view.WindowManager$BadTokenException: Unable to add window`
- **What caused it**: Tried to show a toast/dialog after Activity was destroyed.
- **Solution**: Check `isFinishing` / lifecycle state before showing UI. Prefer `Lifecycle`-aware observers.

---

### Error: `java.lang.IllegalStateException: Fragment/view not attached`
- **What caused it**: Tried to update UI from coroutine/thread after fragment detach.
- **Solution**: Wrap in `lifecycleScope.launchWhenStarted { }`. Or use `isAdded` check.

---

## ⚡ Solana / Anchor Errors

### Error: `Program <pubkey> was deployed with a different version of Rust`
- **What caused it**: Mismatch between anchor version used during testing vs. deployment.
- **Solution**: Pin Anchor version across dev + prod. Use `solana cluster -v` to inspect. Redeploy with matching toolchain if needed.

---

### Error: `failed to send transaction: Transaction simulation failed. Error: invalid account provided`
- **What caused it**: PDA derivation mismatch or account not passed correctly.
- **Solution**: Double-check seed derivation:  
  `Pubkey.findProgramAddress(listOf("challenge".toByteArray(), creator.toByteArray()), programId)`

---

### Error: `Error: IDL not found for program`
- **What caused it**: Running client against program that wasn’t properly IDL-synced.
- **Solution**: After deployment, run:  
  `anchor build && anchor idl init --provider.cluster https://api.devnet.solana.com -- wallet.json`

---

### Error: `insufficient funds for fee` / `Transaction simulation failed: insufficient funds`
- **What caused it**: Devnet wallet has no SOL.
- **Solution**: Run:  
  `solana airdrop 2`  
  And check balance with `solana balance`.

---

## ⚡ Supabase Errors

### Error: `PostgrestError: permission denied for table`
- **What caused it**: Trying to write from client without proper Row Level Security (RLS).
- **Solution**: Disable RLS temporarily for testing, or define explicit policies:
  ```sql
  create policy "Allow insert" on "results"
  for insert using (auth.uid() IS NOT NULL);
  ```

---

### Error: Edge Function timed out (504)
- **What caused it**: Long-running logic in Supabase function (max 10 sec).
- **Solution**: Offload heavy work to background job (queue pattern), or optimize function runtime.

---

## ⚡ Kotlin / Compose Errors

### Error: `java.lang.NoClassDefFoundError: web3.jvm... `
- **What caused it**: Missing native Solana dependency in `build.gradle`.
- **Solution**: Ensure all dependencies are in `build.gradle`:
  ```gradle
  implementation 'org.solana:spl-token-async:1.+'
  implementation 'org.solana:web3-solana:1.+'
  implementation 'org.solana:rpc-core:1.+'
  ```

---

### Error: `Cannot fit requested classes in a single dex file`
- **What caused it**: Too many methods (method count > 65k).
- **Solution**: Enable multidex:
  ```gradle
  android {
      defaultConfig {
          multiDexEnabled true
      }
  }
  ```

---

### Error: `java.lang.NullPointerException: Attempt to invoke virtual method on null object reference`
- **What caused it**: UsageStatsManager returned null (no permission).
- **Solution**: Always check for permission before calling APIs. Return gracefully if denied.

---

## ⚡ Network/API Errors

### Error: `403 Forbidden` when querying Solana Devnet
- **What caused it**: RPC rate-limited or wrong cluster.
- **Solution**: Use a reliable RPC provider like Alchemy or QuickNode devnet endpoints, or retry logic.

---

## ⚡ Common Build Gotchas

### Issue: APK won’t install on device
- **Cause**: Debug key mismatch or architecture incompatibility.
- **Fix**: Clean project (`Build > Clean Project`) and rebuild. Ensure target architecture matches device.

---

## Notes for Future Debugging

- Always check `adb logcat` for uncaught exceptions.
- Use `solana confirm -v <tx_signature>` to track transactions.
- Supabase logs are available under `Project Settings > Logs` — super useful.

---

## 🆕 Errors — 2026-10-13 (Nopeek Colosseum crunch)

### Error: `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` (intermittent)
- **Cause**: MIUI blocks USB installs and auto-denies its confirm dialog when the **screen is off**. `adb_install_need_confirm 0` + `verifier_verify_adb_installs 0` help, but MIUI's security app reverts them between runs — they are NOT durable.
- **Fix (measured)**: wake + dismiss keyguard before every install:
  `adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard`
  then `adb install -r ...`. No Developer-options toggle needed this way.
- **Also**: Gradle `connectedDebugAndroidTest` uses a split-APK install path that hits the same wall more often — prefer `adb install` of both APKs + `adb shell am instrument -w` (ledger Task 2 ruling). After ANY reinstall: `adb shell appops set com.nopeek.app GET_USAGE_STATS allow`.

### Error: `Unable to find instrumentation info for: ...AndroidJUnit4`
- **Cause**: passed the runner CLASS instead of the runner NAME.
- **Fix**: the runner is `androidx.test.runner.AndroidJUnitRunner` (check `adb shell pm list instrumentation`). Use `com.nopeek.app.test/androidx.test.runner.AndroidJUnitRunner`.

### Error: `java.net.BindException: Address already in use` (Gradle FileLockContentionHandler)
- **Cause**: stale daemon holding the port.
- **Fix**: `gradlew --stop`, rerun.

### Error: `AssertionError: challenge must stay under 128 bytes expected:<32> but was:<33>`
- **Cause**: test's own assertion was wrong — challenge string is 33 bytes; requirement is `<128` (short-form DER), not exactly 32.
- **Fix**: assert `size < 128` in `EnrollRoundTripTest.kt`.

### Error: `Rejected(reason=NONCE_CONSUMED)` on every genuine report (nonce burned by its own verification)
- **Cause**: `index.ts consumeNonce` judged the conditional consume update by `(count ?? 0) > 0` from `.update(..., {count: "EXACT"})`. In Supabase's edge runtime, supabase-js returned **no usable count on success** → a SUCCESSFUL consume read as a lost race → returned `NONCE_CONSUMED` **and had already set consumed_at**. The nonce was burned by its own verification. Never seen before because check 8 (`BAD_SIGNATURE`) always fired before check 9 on real chains — the first genuinely-signed report ever is what exposed it.
- **Fix**: judge success by the RETURNING row, not a count option:
  `.update({consumed_at}).eq("id", data.id).is("consumed_at", null).select("id").maybeSingle()` → row present = "ok", null = "consumed". Shape-stable across supabase-js versions; keeps lost-race semantics for real concurrency.
- **Diagnosis path that found it** (reuse this): a fresh nonce consumed before its single /submit-report + `/settle` showing nothing stored ⇒ the burn happened inside verification (consume is check 9, before checks 10-14). Redirect double-execution was ruled out by logging every hop (`NOPEEK_HTTP`).

### Error: oracle 4xx surfaced as `TransportFailed` (refusals were retryable)
- **Cause**: `OracleEvidenceTransport.post()` threw plain `IOException` on any non-2xx, so `SubmitResult.Refused` was dead code and the worker would re-mint/re-enroll/resubmit an already-judged report.
- **Fix**: `HttpErrorResponse(code, body)` carries the oracle body; `submit()` catches it and returns `Refused(reason())`. A refusal is a verdict, not a network problem.

### Risk fixed: silent POST body resend on redirect
- **Cause**: `HttpURLConnection` with `instanceFollowRedirects=true` silently re-POSTs the body on 307/308 — for `/submit-report` that means double server-side execution (first run consumes the window's nonce).
- **Fix**: `instanceFollowRedirects = false`; follow 307/308 explicitly, once, with the hop logged. 301/302 on a POST = loud failure.

### Error: Gradle via PowerShell script dies with `NativeCommandError` on stderr
- **Cause**: PS 5.1 with `$ErrorActionPreference = "Stop"` converts gradlew.bat's stderr output into a terminating error before the build even runs.
- **Fix**: invoke via `cmd /c "gradlew.bat ... 2>&1"` inside scripts, and judge results from the XML, not the console.

---

## 🆕 Errors — 2026-10-17 (program redeploy: local build → CI pivot)

### Error: `linker link.exe not found` (local `cargo-build-sbf` on Windows)
- **What caused it**: building `contracts/program` locally with the fresh Solana CLI (cargo-build-sbf 4.4.0, platform-tools v1.57) fails while host-linking build scripts/proc-macros (`quote`, `proc-macro2`, `serde_core`). The SBF toolchain's host is `x86_64-pc-windows-msvc`; this machine has no MSVC linker (no VS Build Tools; `vswhere` finds no install; only TDM-GCC).
- **Fix**: build on a Linux runner instead — `.github/workflows/build-program.yml` (branch `build/sbf-ci`, repo `er4l1m4d/Nopeek`): checkout → Anza stable → `declare_id` rewired `1111…` → `DJftv46…` via sed → `cargo-build-sbf` → upload `.so` artifact. CI logs record `solana --version` / `cargo-build-sbf --version` / sha256. Deploy locally with `solana program deploy` + upgrade-authority keypair.
- **Why not WSL**: `wsl.exe` present but **no distro installed**, and C: had only ~2.3 GB free — an install was the riskier path.

### Measurement trap: dummy-account simulations **skip the seeds check** in the deployed binary
- **What happened**: earlier "the canonical v2 PDA was accepted" readings came from simulations with dummy mint/creatorToken. Controlled experiment (`probe.mjs grad`) → G7 (`challenge` = random garbage pubkey, dummy mint): error is still `3012 mint` — the deployed binary evaluates `mint`/`creator_token` BEFORE `challenge`'s seeds constraint, so with a dummy mint the seeds check never runs at all.
- **Rule**: when probing an `init` account's seeds, every account earlier in the binary's evaluation order must be real/initialized — otherwise "no ConstraintSeeds error" proves nothing. G3 (real accounts, `challenge` = `ETxJHWTx…`) simulates to **full success**, proving the deployed binary's seeds genuinely differ from the source's `[challenge, creator, opponent, le4(nonce)]`.

### Issue: deployed binary does not match `contracts/program/src/lib.rs` seeds
- **Evidence**: with all-real accounts the running binary demands `ETxJHWTx…` (creator `6k9TDQ…`, opponent `F6DDz9…`, nonce 0 **and** 1) / `HsD9Xj…` (opponent `79W1jL…`) — invariant to nonce. Exhaustive sweeps (211k canonical layouts × prefixes/pids + all-256-bump create sweeps) reproduce neither address ⇒ not a sibling of any source rule.
- **Fix (superseded — see next entry)**: redeploying alone was NOT the fix: the CI `.so` built from `5d65f60` carries the same bug. The real fix is the `#[instruction(..)]` attr line — one line of source — then rebuild + redeploy.

### ✅ RESOLVED — root cause: `#[instruction(nonce: u32)]` decoded the wrong 4 bytes
- **Root cause (3-way proof)**: `CreateChallenge` declared only `#[instruction(nonce: u32)]`. Anchor borsh-decodes `#[instruction(..)]` args from the instruction-data **prefix in declared order**, so `nonce` was read from the first 4 bytes of the payload — the low half of `u64 stake_amount` = **5,000,000** (`0x004C4B40`). Every create therefore demanded `[b"challenge", creator, opponent, u32le(5_000_000)]`, invariant to the client's nonce — exactly what every probe measured:
  - (6k9TDQ, F6DDz9) → `ETxJHWTx…` == derive(…, u32le(5_000_000)) ✓
  - (6k9TDQ, 79W1jLVv) → `HsD9XjBw…` == derive(…, u32le(5_000_000)) ✓
  - (6k9TDQ, 3cDebcs) → `9Z1qp9iV…` == derive(…, u32le(5_000_000)) ✓
  The "stale/cached artifact" theory is retired: the deployed binary WAS v2 (its bytecode contains every v2-only string); the demanded rule was a decode bug compiled into it.
- **Fix**: `#[instruction(stake_amount: u64, target_app: String, limit_seconds: u32, window_seconds: u32, nonce: u32)]` — every handler arg listed before `nonce` in decode order. Wire format unchanged; no client change needed.
- **Rebuild + redeploy**: local `cargo-build-sbf` (MSVC-shim toolchain, CI still billing-locked) → `volo_challenge.so` **291,000 B** sha256 `CE8ACB413404A283…` (was 291,080 B `7074F9D6…`); devnet upgrade `lastDeploySlot 511856415 → 511960495` (tx `5S55FR1er1me6…`), buffer closed; `solana program dump` byte-verified — on-chain ELF sha256 equals the artifact.
- **Verification ladder (all green)**:
  1. LOCAL cluster (ledger5): full probe — CREATE nonce 0 accepted, duplicate refused, CREATE nonce 1 accepted, both accounts decode nonce 0/1.
  2. DEVNET full probe — same result (mint `BMEqfNzH…`).
  3. DEVNET grad matrix — G2 (`Dh3VGhDh…`, the exact old ConstraintSeeds case) OK(no-err); G4/G5 OK; G3 (`challenge=ETxJHWTx…`, the old demand) now fails **2006**; G0/G7/G6 still 3012 first (evaluation order, unchanged trap).
  4. `contracts/tests/playground-case17.test.ts` run on devnet in a reconstructed Playground env — **RESULT PASS**: nonce 0 accepted, duplicate refused, nonce 1 accepted, both fetch as Created with nonces 0/1.

### Error: the app sent every instruction to the System Program (`idl.address` placeholder)
- **Cause**: `app/src/main/assets/idl.json` and `contracts/program/idl.json` still carried `"address": "11111111111111111111111111111111"`; `ProgramInstructions.programIdFrom(idl) = idl.address`, so every app-built transaction targeted the System Program — no duel could ever be created from the app (Task 11 Step 4 would have failed on device).
- **Fix**: both files rewritten to the v2 playground shape (5 instructions, `nonce` arg, v2 settle args, 13 errors, Challenge(16) + Reputation types) with `"address": "DJftv46…"`; byte-identical (sha256 `FF98FD0D…`).

### Error: the app marked the challenge PDA as a transaction signer
- **Cause**: `ProgramInstructions.createChallenge` built the challenge account with `isSigner = true` — a v1 leftover (v1's challenge WAS a client keypair). v2's challenge is a PDA; it signs via `invoke_signed` on-chain. A signer flag demands a wallet signature that no key can produce — the tx dies at signature verification.
- **Fix**: `isSigner = false` + regression assertions (`ProgramInstructionsTest`), `IdlParserTest` pins the deployed address, v2 arg/account/struct shapes + non-signer flag. Full app unit suite green: **240/240**.

### Error: v2 case24 — second join reused a non-idempotent ATA create
- **What happened**: case24's second duel between the same two wallets reused the classic ATA `Create` (data `[0]`) on an already-initialized account; the join died with "Provided owner is not allowed" from `ATokenGPvbd…`.
- **Fix**: both suites' hand-rolled ATA create now sends `CreateIdempotent` (data `[1]`) — reruns and same-opponent re-joins no-op instead of failing.

### Error: v2 cases 21–23 — the lock challenge could never mature inside case23's search window
- **What happened**: case21 created its challenge with `window_seconds = 3600`, so `resolve_lock` needs `end_time + LOCK_SECONDS` = create + 7200 s, while case23 searched only the previous-hour bucket (< 2 h) and gated on `settled_at` — the two gates could not intersect, and a 1–2 h rerun would have crashed case23 rather than skipped it.
- **Fix**: case21 creates with `window_seconds = 60` (maturity = create + 3660 s); case22/case23 gate on the raw `end_time` at offset 164 `+ 3600 s`; case23 searches BACK through bucket nonces 1..48 and sweeps the first matured `BothFailLocked`. A fresh run reports case23 SKIP-with-clock, never a false PASS.

### Error: v2 case21 rerun collision within one hour bucket
- **What happened**: rerunning the suite inside the same hour bucket re-created the same PDA → `init` collision.
- **Fix**: case21 reuses an existing bucket challenge (`status == LOCKED`) after asserting the escrow of **the challenge's recorded mint** holds 10; case22 derives escrow/treasury from that recorded `token_mint` (offset 72..104), not the current session's mint. Run 2 exercised the reuse path live.

### Error: case11 — `NotOracle` masked by a zero-lamport impostor oracle
- **What happened**: the settle's reputation accounts are `init_if_needed` with `payer = oracle`, and Anchor runs those CPIs **before constraint checks**. A freshly generated impostor (0 lamports) died at `SystemProgram: Transfer: insufficient lamports 0, need 817880` — `custom program error: 0x1`, no anchor name — and `NotOracle` never spoke.
- **Fix (probe-verified)**: fund the impostor 0.02 SOL before the attempt; the identical tx then returns `Error Code: NotOracle. Error Number: 6006`, and a control with the real oracle simulates to success (`.copilot-tmp/local-cluster/probe-case11.txt`).
- **Lesson**: a negative test that reaches an `init_if_needed` account must fund the payer account first, or it measures the payer's balance instead of the constraint.

### Issue: scratch scripts used a typo'd wallet pubkey (`…GfCctsv…` vs `…GfCCTsv…`)
- **What happened**: two ad-hoc probes copied the wallet string from an 8-char-prefix display and lowercased `CCT` → `Cct`. base58 is case-sensitive, so the queries targeted a different key (balance 0, bucket PDAs "not found") and a phantom "PDA derivation mismatch" got chased for a while.
- **Lesson**: copy full pubkeys from files (or re-derive from `id.json`), never from a truncated display; a prefix match proves nothing.

### Issue: PowerShell pipeline exit codes are unreliable through `Tee-Object`
- **What happened**: `node … | Tee-Object …` reports exit code 1 even when the runner printed `RESULT PASS` (429-retry stderr lines surface as NativeCommandError).
- **Rule**: trust the runner's `RESULT PASS/FAIL` line in the transcript, not `$LASTEXITCODE`.

### Note: provenance check — on-chain ELF vs local artifact (2026-10-17)
- **Measured**: ProgramData `77RtiTVT…` body length 365,328 — the pre-fix build's size, because the loader does not shrink the account on a smaller upgrade; the space after the ELF is zero-filled. The ELF's true extent is the first 291,000 bytes: sha256 `ce8acb41…` == local `volo_challenge.so` — byte-for-byte. `lastDeploySlot` 511960495 matches the fix deploy. The full-account sha (`14bd1d0c…`) is NOT the artifact hash; slice `[45 .. 45+291000]` is.

### Error: Task 14's test gate never ran — report storage was inline in index.ts and rejections were never written
- **What happened** (retro review, 2026-10-17): the plan's seven named Task 14 tests did not exist; the new DB logic lived inline in `index.ts`, which tsconfig excludes, so no Node test could reach it. Worse, the handler skipped storing rejected submissions entirely ("Task 14's follow-up") while the ledger claimed the behaviour present — the honesty slide had no denominator data. And `/settle` selected ALL report rows while its comment claimed "latest VERIFIED report": the moment rejections were stored, a forged-but-rejected row could have driven a payout — the exact fund-loss class the project exists to prevent.
- **Fix**: report storage moved into `store.ts` (`insertVerifiedReport` / `insertRejectedReport` / `latestVerifiedReports` / `verifiedEvidenceByHash`) with five new tests (suite 91 → 96); rejections are stored with `verified=false` + `reject_reason` when the payload decoded far enough to attribute them (`wallet_pubkey` is NOT NULL — earlier failures stay log-only); settle reads verified rows only.
- **Lesson**: "deployed and probed" is not "tested" — a handler excluded from the typecheck project is a handler with no test seam; the DI seam the plan named exists precisely for this, and skipping it hid three defects at once.

### Error: duplicate accepted report answered 500 with Postgres text — and the client retried forever
- **What happened**: Task 13's two-chances design (worker + eager path) legitimately double-fires across processes; the second insert hit `usage_reports_one_verified_per_side` and the handler returned a raw 500 carrying the constraint text. `EvidenceReporter` treats 5xx as retryable, so the loser looped re-minting nonces against a permanent condition.
- **Fix**: the store maps the duplicate (23505 / duplicate-key / constraint name) to `InsertConflictError`; the handler answers 409 `REPORT_ALREADY_ACCEPTED`, which the client treats as a verdict. Any OTHER insert error stays a retryable 500 — the first cut wrapped every error as a conflict, which would have told a transient failure to give up.
- **Lesson**: a unique constraint on an expected race is a domain outcome, not a server fault; and an error-class wrapper that does not discriminate turns "retryable" into "permanent" invisibly.

### Error: the eager recovery hook ran in onCreate, so the primary force-stop path never recovered
- **What happened** (Task 13 review): `runEagerRecovery()` was called from `onCreate`, which fires only on a new process. The Review Focus 5 scenario — force-stop, window lapses, user returns via the recents switcher — has a LIVE process, so only `onResume` ran and recovery was skipped. Also the `runCatching` wrapper swallowed every exception with no log: a stranded recovery looked identical to "nothing due".
- **Fix**: moved to `onResume`; `onFailure` now logs to `NOPEEK_RECOVERY`. Kotlin suite 240/240 after.
- **Lesson**: lifecycle hooks are part of a feature's contract; "it works when launched fresh" is the weakest of the launch paths, not proof.

### Note: review-gate sweep (Tasks 13/14, 2026-10-17) — six Criticals found, six fixed, deployed twice
- Full findings + the open Importants/Minors live in `contracts/ATTESTATION_DEPLOY_NOTES.md` § "Review gate (retro)". Oracle suite 91→96, Kotlin 240/240, tsc clean, deployed to `ybcavetnzivwsfnwzvtd`, post-deploy probes green (`/health`, 404, 400).

---

Last updated: `2026-10-17` (ROOT CAUSE fixed: `#[instruction(..)]` prefix decode; rebuilt 291,000 B sha256 `CE8ACB41…`, live on devnet slot 511960495; local + devnet + case17 green; app IDL wired to `DJftv46…`; full contract suites green — v2 cases 17–24 devnet `RESULT PASS`, corrected cases 1–16 `23 PASS / 0 FAIL / 1 time-gated SKIP`; on-chain ELF provenance byte-verified against the local build; retro review gate for Tasks 13/14 run — six Criticals fixed + redeployed)
