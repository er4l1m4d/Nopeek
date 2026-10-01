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

Last updated: `2026-10-01` (plan 12.2 Step 1 status pass + new-error entries)
