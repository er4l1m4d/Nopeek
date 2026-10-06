# Nopeek Device-Attested Compliance Proof — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make one real USDC duel settle on Solana devnet against hardware-attested, publicly re-verifiable usage evidence, and record a portable kept-promise reputation on-chain.

**Architecture:** Native Android (Kotlin/Compose) collects usage and signs a canonical evidence payload with a key generated inside the Android KeyStore, attested by a Google-provisioned certificate chain. A Supabase Edge Function verifies that chain against a pinned Google root and publishes the exact signed bytes at a public URL whose SHA-256 the Anchor program stores at settlement. Fail-closed policy — an absent, late, or unverifiable report is a loss — already exists in code and is retained unchanged.

**Tech Stack:** Kotlin 2.2.21 / Jetpack Compose / WorkManager / Android KeyStore, Anchor (Solana Playground browser IDE), Supabase Edge Functions in TypeScript tested with `node:test`, Solana devnet, USDC devnet mint.

**Spec:** `docs/superpowers/specs/2026-10-06-nopeek-colosseum-architecture-design.md` — read it alongside every task. Field orders, byte sizes, seed layouts, check tables, and error codes are defined there and referenced here by section number rather than repeated.

## Global Constraints

- **$0 budget.** No paid service, no Apple account, no mainnet capital, no emulator (3–4 GB free disk; physical devices only).
- **Solana devnet only.** RPC `https://api.devnet.solana.com`, USDC mint `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU`. No `SKR_*` / Seeker / MWA genesis-token paths.
- **No React Native, no Swift, no iOS code.** Roadmap only (spec §12).
- **Android only**, `minSdk 26`, `compileSdk/targetSdk 35`, JVM 17, Kotlin 2.2.21, KSP 2.2.21-2.0.5.
- Program is built and deployed **only** through the Solana Playground browser IDE. No local cargo/Anchor build.
- Kotlin build gate: `./gradlew.bat :app:compileDebugKotlin --console=plain -q`
- Kotlin unit gate: `./gradlew.bat :app:testDebugUnitTest --console=plain -q`
- Oracle gate: `node --test "supabase/functions/oracle/*.test.ts"` — Node 26 type-stripping, glob form required. The bare-directory form (`node --test supabase/functions/oracle/`) **fails on this machine**: Node 26 on Windows resolves a directory positional as an entry module and throws MODULE_NOT_FOUND. Verified 2026-10-06.
- USDC is always displayed with 6 decimals; never raw token units.
- **The device signs the exact bytes it transmits and the oracle hashes the exact bytes it receives.** No re-serialization on either side (spec §4.1).
- Fail-closed rules may be tightened, never weakened: no valid report ⇒ `UNVERIFIED` ⇒ loss.
- No signing keys, `.env`, `keystore.properties`, or oracle secrets in any commit.
- New UI uses `NopeekTokens` / `NopeekTypography` (dark-only design system); no new color roles without adding them to `design system/tokens.json` first.

## Review Focus

Failure modes the spec implies that no single happy-path test would catch. Each is pinned by a named test in the task listed:

1. **Cross-bound nonce reuse** — a valid nonce issued for wallet A is presented on wallet B's report. Must reject. → Task 8
2. **Replay of a previously valid payload** — same bytes resubmitted after settle, and after the nonce was consumed. Must reject, and must not create a second report row. → Task 8
3. **Chain not rooted at Google** — a self-signed leaf, or a chain rooted at any other CA, presents as a well-formed report. Must reject before any other check. → Task 8
4. **Unlocked / non-verified device** — `rootOfTrust.deviceLocked == false` or `verifiedBootState != VERIFIED` with an otherwise perfect chain. Must reject. → Task 8
5. **Silent MIUI kill** — the one-shot worker never fires, so no report exists and the user loses without knowing why. The app must show the reason, and the eager foreground resubmit must recover it when the app is opened inside the grace window. → Tasks 13, 18
6. **Forward clock jump** — the existing detector only catches backward jumps; moving the clock forward shortens the measured window and under-reports usage. → Task 12
7. **Second duel between the same pair** — PDA seeds collide and a funded escrow account from duel #1 is silently reused. → Tasks 10, 11
8. **Internally contradictory evidence** — `usedSeconds > deviceActiveSeconds`, or `eventCount` not matching `eventSetDigest`. → Task 8

---

## File Structure

**Created — Kotlin** (`app/src/main/java/com/nopeek/app/`)
`data/attest/CanonicalPayload.kt` (byte format + hashing) · `data/attest/KeyStoreEnroller.kt` (keygen, attestation, PEM) · `data/attest/EvidenceReporter.kt` (nonce → sign → submit) · `data/usage/UsageEventCollector.kt` (event set, digest, deviceActiveSeconds) · `data/usage/ReportScheduler.kt` (one-shot + eager triggers) · `data/verify/CommitmentVerifier.kt` (interface + oracle-verdict implementation) · `ui/screens/EvidenceScreen.kt`

**Created — tests** `app/src/test/java/com/nopeek/app/data/attest/{CanonicalPayloadTest,CommitmentVerifierTest}.kt` · `app/src/test/java/com/nopeek/app/data/usage/{UsageEventCollectorTest,ReportSchedulerTest}.kt` · `app/src/androidTest/java/com/nopeek/app/data/attest/KeyStoreAttestationTest.kt` · `app/src/androidTest/java/com/nopeek/app/data/attest/EnrollRoundTripTest.kt`

**Created — oracle** (`supabase/functions/oracle/`) `evidence.ts` · `x509.ts` · `checks.ts` · `store.ts` · plus `evidence.test.ts`, `checks.test.ts`, `store.test.ts` · `supabase/migrations/20261006_nopeek_attestation.sql` · `supabase/functions/oracle/fixtures/` (PEM chain captured in Task 2)

Spec §7 names one module, `attest.ts`. This plan splits it: `x509.ts` is the runtime-bound DER/chain reader (the part Task 3 exists to de-risk) and `checks.ts` is the pure check-ordered verifier. Same contract, one extra file, and the split is what makes all twelve rejection reasons testable without a phone attached. Where the spec says `attest.ts`, read these two.

**Created — contracts** `contracts/DEPLOYED_PROGRAM_ID.txt` · `contracts/program/idl-from-playground.json` · `contracts/ATTESTATION_DEPLOY_NOTES.md`

**Modified** `data/Models.kt` · `data/usage/{UsageTracker,UsageWorker,OracleReportApi}.kt` · `data/solana/{Pda,ProgramInstructions,TransactionCompiler,SettlementClient,SolanaConfig}.kt` · `data/repositories/ChallengeRepository.kt` · `data/local/{AppDatabase,ChallengeDao,ChallengeEntity}.kt` · `navigation/{NavGraph,NavRoutes}.kt` · `contracts/program/src/lib.rs` · `contracts/tests/manual-test-cases.md` · `supabase/functions/oracle/index.ts` · `app/build.gradle.kts` (androidTest only)

---

## Day 1 — Retire the two risks before any feature code

### Task 1: Working-tree safety, branch, and devnet war chest

**Files:**
- Create: `contracts/DEPLOYED_PROGRAM_ID.txt` (placeholder line, filled in Task 4)
- Create: `contracts/ATTESTATION_DEPLOY_NOTES.md`

**Interfaces:**
- Produces: branch `nopeek-colosseum`; a devnet wallet whose keypair path is recorded in `contracts/ATTESTATION_DEPLOY_NOTES.md` and whose file is gitignored; 2–3 devnet SOL.

- [ ] **Step 1: Commit the 22 modified source files as-is** — they are a day of design-system work sitting in the working tree.

```bash
git add -A ':!keystore.properties' ':!local.properties' ':!.copilot-tmp'
git commit -m "wip: design system and screen updates preceding Colosseum rework"
```
Expected: clean `git status` except gitignored paths. Verify `git log --stat -1` shows no `.properties`, `.keystore`, or secret file.

- [ ] **Step 2: Create the working branch** `git switch -c nopeek-colosseum`

- [ ] **Step 3: Clear build disk** — `app/build`, `.gradle`, `.kotlin/sessions`. Confirm ≥ 2.5 GB free (`Get-PSDrive C | Select-Object Free`). Below that, stop and report before continuing.

- [ ] **Step 4: Create and fund the deploy/oracle wallet.** `solana-keygen new --no-bip39-passphrase -o "$HOME/nopeek-oracle.json"` (Git Bash), then airdrop to 2–3 devnet SOL across several small calls, retrying across [faucet.solana.com](https://faucet.solana.com) and the Playground airdrop button if rate-limited. Record the pubkey and keypair path in `contracts/ATTESTATION_DEPLOY_NOTES.md`; the keypair file stays outside the repo and is added to `.gitignore`.

- [ ] **Step 5: Verify** `solana balance <pubkey> --url devnet` reports ≥ 2 SOL. If the faucet will not yield 2 SOL after three spaced attempts, **stop and escalate** — Task 4 is blocked and the schedule slips.

- [ ] **Step 6: Commit** `git add contracts/ATTESTATION_DEPLOY_NOTES.md && git commit -m "chore: devnet oracle wallet and deploy notes"`

### Task 2: S1 spike — capture a real hardware attestation from the Redmi

**Deliverable is evidence, not a feature.** This is the plan's biggest technical risk: some Xiaomi SKUs ship without provisioned attestation keys. Nothing else in this plan may proceed on assumption.

**Files:**
- Modify: `app/build.gradle.kts` (add `testInstrumentationRunner` + `androidx.test.ext:junit`, `androidx.test:runner`, `androidx.test:rules` to `androidTestImplementation`)
- Create: `app/src/androidTest/java/com/nopeek/app/data/attest/KeyStoreAttestationTest.kt`
- Create: `supabase/functions/oracle/fixtures/device-chain.pem`, `device-spki.b64`, `device-cert-digest.b64`, `FINDINGS.md`

**Interfaces:**
- Produces: the PEM chain and leaf SPKI that Task 3 verifies and Task 8 tests against; the answer to "is spec §4 available on this hardware."

- [ ] **Step 1: Write the failing instrumentation test** `KeyStoreAttestationTest.attestsHardwareBackedEcKey()` — generate an EC P-256 non-exportable signing key in `AndroidKeyStore` with `setAttestationChallenge(bootstrapNonce)` (bootstrap nonce = the fixed ASCII string `"nopeek-s1-probe"`), then assert: chain length ≥ 3; the attestation extension OID `1.3.6.1.4.1.11129.2.1.17` is present; `keyMintSecurityLevel` (or `keymasterSecurityLevel`) is `TEE` or `STRONGBOX`, not `Software`; `rootOfTrust.deviceLocked == true`; `rootOfTrust.verifiedBootState == VERIFIED`; `attestationChallenge` equals the bootstrap nonce; `attestationApplicationId.packageName == "com.nopeek.app"`.
- [ ] **Step 2: Run it to verify it fails** `./gradlew.bat :app:connectedDebugAndroidTest --console=plain` — Expected: FAIL, class not found / no runner configured.
- [ ] **Step 3: Configure the runner** `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `defaultConfig`, add the three androidTest deps, and implement the test body using `KeyPairGenerator.getInstance("EC", "AndroidKeyStore")`. Decode the extension with `BouncyCastle`-free parsing: read `cert.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")` and walk it with `org.bouncycastle` **only if already available**; otherwise dump the raw DER to a file and assert on `BigInteger`/byte scans for the challenge and package strings. Prefer the raw-DER dump: it is enough for a spike and avoids a new dependency.
- [ ] **Step 4: Run on the Redmi** `./gradlew.bat :app:connectedDebugAndroidTest --console=plain` — Expected: PASS. If the device has no hardware keystore, the security level assertion fails red — **that is a valid, reportable result.**
- [ ] **Step 5: Extract the fixtures** `adb exec-out run-as com.nopeek.app cat /data/data/com.nopeek.app/files/device-chain.pem > supabase/functions/oracle/fixtures/device-chain.pem` (likewise the SPKI and cert-digest files). Write `fixtures/FINDINGS.md`: security level actually returned, chain length, issuer DN of the root, whether `deviceLocked`/`verifiedBootState` held, and which SKU/MIUI version.
- [ ] **Step 6: Record the gate in `contracts/ATTESTATION_DEPLOY_NOTES.md`** — S1 PASS ⇒ spec §4 proceeds. S1 FAIL (no hardware keystore, or `Software` level) ⇒ **switch to fallback V2**: keep the whole pipeline (nonce-bound signature, enrolled key, publish-and-pin) but drop claims 1, 6, 7 from spec §4.3, retitle the deck claim from "hardware-attested" to "device-key-signed, nonce-bound", and note the downgrade in `FINDINGS.md`. Do not silently continue as if hardware attestation existed.
- [ ] **Step 7: Commit** `git add -A && git commit -m "test: capture Redmi hardware attestation chain and fixtures"`

### Task 3: S2 spike — verify an X.509 chain in the Edge Function runtime

**Files:**
- Create: `supabase/functions/oracle/x509.ts` (initially `verifyChainToRoot`, `readAttestationExtension`, `verifyEcdsaSignature`)
- Create: `supabase/functions/oracle/x509.test.ts`
- Create: `supabase/functions/oracle/fixtures/google-attestation-root.pem`

**Interfaces:**
- Consumes: `fixtures/device-chain.pem` from Task 2.
- Produces: `readAttestationExtension(chainPem: string): Promise<AttestationInfo>` where `AttestationInfo = { attestationChallengeB64: string, deviceLocked: boolean, verifiedBootState: string, applicationPackageName: string, signingCertDigestB64: string, securityLevel: string, leafSpkiB64: string, rootIssuerDn: string }`; `verifyEcdsaSignature(spkiB64: string, bytes: Uint8Array, signatureB64: string): Promise<boolean>`; `verifyChainToRoot(chainPem: string, rootPem: string): Promise<boolean>`.

- [ ] **Step 1: Write the failing test** `x509.test.ts > reads the attestation extension from the captured device chain` — assert `applicationPackageName === "com.nopeek.app"`, `attestationChallengeB64` decodes to `"nopeek-s1-probe"`, `deviceLocked === true`, `verifiedBootState === "Verified"`. Then the pinning test, which is the one that carries the whole claim: `verifyChainToRoot(chain, pinnedRoot)` is `true` only when the chain's terminal issuer equals `fixtures/google-attestation-root.pem`, and that file is **pinned out of band** — fetched from Google's published Android KeyStore attestation CA and matched by subject DN and SPKI fingerprint against what the device chain reports, with both recorded in `fixtures/FINDINGS.md`. Never copy the root out of the device's own chain: a self-signed chain is equally self-consistent, so that would pass an attacker-controlled CA. The negative test asserts `false` for a valid chain presented against a pinned root from a different CA.
- [ ] **Step 2: Run to verify failure** `node --test supabase/functions/oracle/x509.test.ts` — Expected: FAIL, module missing.
- [ ] **Step 3: Decide the mechanism, in this order, and record the choice at the top of `x509.ts` as a comment.** (a) `@peculiar/x509` + `asn1js`, with `import { X509Certificate } from "npm:@peculiar/x509@1.4.1"` for the deployed Deno function and a local `npm install` copy for `node --test`; (b) if the `npm:` specifier cannot serve both runtimes, keep `x509.ts` runtime-neutral by injecting a `CertParser` and bind it in `index.ts` (Deno) and `x509.test.ts` (Node); (c) last resort, verify the chain in a small free-tier Node service and have the Edge Function call it. **Do not** hand-roll ASN.1 DER parsing beyond a byte-scan fallback.
- [ ] **Step 4: Implement `verifyEcdsaSignature`** using `globalThis.crypto.subtle` — `importKey("spki", …, { name: "ECDSA", namedCurve: "P-256" }, …)` then `verify({ name: "ECDSA", hash: "SHA-256" }, …)`. Available identically in Deno and Node, so this part carries no risk.
- [ ] **Step 5: Run tests** `node --test supabase/functions/oracle/x509.test.ts` — Expected: PASS.
- [ ] **Step 6: Deploy-proof the choice** — paste `x509.ts` + `fixtures/` into Solana Playground? No. Instead deploy the current oracle shell with `x509.ts` wired to a `GET /verify-probe` route (temporary, returns the parsed `AttestationInfo` for a posted chain) to the Supabase function, `curl` it with the device chain, and confirm the deployed runtime parses what Node parsed. Delete the probe route in Task 14.
- [ ] **Step 7: Commit** `git add -A && git commit -m "feat: x509 attestation chain reader verified against device fixture in both runtimes"`

### Task 4: Deploy the program and run the existing 16 manual cases

**Files:**
- Modify: `contracts/program/src/lib.rs:7` (`declare_id!`), `:11` (`ORACLE_AUTHORITY`)
- Create: `contracts/DEPLOYED_PROGRAM_ID.txt`, `contracts/program/idl-from-playground.json`
- Modify: `contracts/USER_ACTIONS.md` (tick Phase 5 and Phase 6 boxes)

**Interfaces:**
- Produces: a live devnet program id and an IDL that `IdlParser` consumes; the oracle signer pubkey used by every later task; the baseline that proves the 16 existing cases pass **before** any program change, so a later failure has a known-good comparison.

- [ ] **Step 1: Deploy via Solana Playground** (devnet): New Project → paste `lib.rs` → keep Playground's generated `declare_id!` → set `ORACLE_AUTHORITY` to Task 1's pubkey → Build → Deploy. Write the id to `contracts/DEPLOYED_PROGRAM_ID.txt` and the pubkey into `ATTESTATION_DEPLOY_NOTES.md`.
- [ ] **Step 2: Export the IDL** to `contracts/program/idl-from-playground.json` and diff against `contracts/program/idl.json`. The Playground export wins; if they differ, replace the hand-authored file and note the delta in `ATTESTATION_DEPLOY_NOTES.md`.
- [ ] **Step 3: Run all 16 cases** in `contracts/tests/manual-test-cases.md` via the Call tab, funding two devnet Phantom accounts with USDC from the Playground/SPL faucet. Record each PASS/FAIL. Cases 7–10 are the settlement matrix — those must match before going further.
- [ ] **Step 4: Wire the app's program id** into `SolanaConfig` / `idl.json` as used, remove `SKR_MINT`, and point `NopeekBackend.SUPABASE_URL` at the real project ref (`app/src/main/java/com/nopeek/app/data/usage/OracleReportApi.kt:18`), confirming `isConfigured` becomes true.
- [ ] **Step 5: Verify** `./gradlew.bat :app:testDebugUnitTest --console=plain -q` — Expected: PASS. Any test asserting the old placeholder id is updated to the new value in this task, not later.
- [ ] **Step 6: Commit** `git commit -am "feat: deploy challenge program to devnet with real oracle authority"`

**Checkpoint before Day 2:** a stake can be escrowed and refunded by hand in the Playground. If it cannot, do not proceed — the money flow is the deliverable.

**If the plan slips,** cut in spec §10's order: Evidence screen (Task 18) → reputation endpoint (Task 17) → `Reputation` PDA (Task 10 Step 2's last part) → `resolve_lock` replaced by a plain refund. Never cut Task 8, Task 15, or Task 20.

---

## Day 2 — The trust anchor

### Task 5: Attestation migration

**Files:**
- Create: `supabase/migrations/20261006_nopeek_attestation.sql`
- Modify: `supabase/migrations/20261001_volo_init.sql` (leave untouched; the new file is additive)

**Interfaces:**
- Produces: tables `install_keys(wallet_pubkey text pk, keystore_spki_b64 text, cert_chain_pem text, cert_digest_b64 text, enrolled_at timestamptz, revoked_at timestamptz)`, `report_nonces(id bigserial pk, challenge_pubkey text, wallet_pubkey text, nonce_b64 text unique, issued_at timestamptz, expires_at timestamptz, consumed_at timestamptz)`, and on `usage_reports`: `signature_b64 text, cert_chain_pem text, canonical_bytes bytea, evidence_hash text, package_name text, device_active_seconds bigint, event_count int, event_set_digest text, verified boolean, reject_reason text`. Unique index on `(challenge_pubkey, wallet_pubkey) where verified`.

- [ ] **Step 1: Write the migration** exactly as above; `evidence_hash text unique` where not null; grant public `SELECT` on nothing new (evidence is served by the function, not PostgREST).
- [ ] **Step 2: Run it** in the Supabase SQL Editor against the project created in Task 4 Step 4.
- [ ] **Step 3: Verify** `select count(*) from install_keys;` and `select count(*) from report_nonces;` both return 0 without error, and `\d usage_reports` shows the new columns.
- [ ] **Step 4: Commit** the migration file.

### Task 6: Canonical evidence payload — one byte format, two languages

**Files:**
- Create: `app/src/main/java/com/nopeek/app/data/attest/CanonicalPayload.kt` + `app/src/test/java/com/nopeek/app/data/attest/CanonicalPayloadTest.kt`
- Create: `supabase/functions/oracle/evidence.ts` + `evidence.test.ts`
- Create: `supabase/functions/oracle/fixtures/evidence-vector.json`

**Interfaces:**
- Consumes: spec §4.2 field order.
- Produces: `data class EvidenceFields(version: Byte, challengePubkey: String, walletPubkey: String, packageName: String, targetPackage: String, windowStartMs: Long, windowEndMs: Long, usedSeconds: Int, deviceActiveSeconds: Long, eventCount: Int, eventSetDigestHex: String, tamperSignals: List<String>, nonceB64: String)`; `object CanonicalPayload { fun encode(f: EvidenceFields): ByteArray; fun digestHex(bytes: ByteArray): String }` on Kotlin, and `interface EvidenceFields {...}` / `encodeEvidence(f): Uint8Array` / `decodeEvidence(bytes): EvidenceFields` / `evidenceHash(bytes): Promise<string>` (hex, sha256) in TS. Each field is encoded as `u32` big-endian byte length then raw UTF-8, in spec order, prefixed by the `version` byte `1`.

- [ ] **Step 1: Write `fixtures/evidence-vector.json`** by hand: one `EvidenceFields` object with fixed values (`challengePubkey: "7Nb…probe"`, `targetPackage: "com.instagram.android"`, `windowStartMs: 1759000000000`, `windowEndMs: 1759000060000`, `usedSeconds: 42`, `deviceActiveSeconds: 180`, `eventCount: 6`, `eventSetDigestHex` a fixed 64-char hex, `tamperSignals: []`, `nonceB64: "bm9wZWVrLXNxb25l"`) and the expected `evidenceHash` filled in as `""` for now.
- [ ] **Step 2: Write the failing Kotlin test** `CanonicalPayloadTest.encodesSharedVectorByteForByte()` — encode the vector and assert the hex of the result equals a literal taken from the JSON `expectedHex` you add in step 4; and `CanonicalPayloadTest.lengthPrefixedFieldsRoundTrip()` asserting `tamperSignals` of 0, 1, and 3 entries each encode and decode.
- [ ] **Step 3: Write the failing TS test** `evidence.test.ts > encodeEvidence matches the shared vector` and `> decodeEvidence round-trips encodeEvidence` and `> evidenceHash is sha256 of the exact bytes passed in` (assert against `node:crypto` computing sha256 of the same array, **not** a re-encode).
- [ ] **Step 4: Implement both encoders**, then compute the real `expectedHex` and `expectedHash` once, paste them into the vector, and re-run. Both suites must read the same vector file. This cross-language byte agreement is the point of the task; do not let either side special-case its own output.
- [ ] **Step 5: Verify** `node --test "supabase/functions/oracle/*.test.ts"` PASS and `./gradlew.bat :app:testDebugUnitTest --console=plain -q` PASS.
- [ ] **Step 6: Commit**

### Task 7: Key enrollment and nonce issuance

**Files:**
- Create: `app/src/main/java/com/nopeek/app/data/attest/KeyStoreEnroller.kt`
- Create: `app/src/androidTest/java/com/nopeek/app/data/attest/EnrollRoundTripTest.kt`
- Modify: `supabase/functions/oracle/index.ts` (add `POST /enroll`, `POST /nonce`), `store.ts` (create; wraps Supabase inserts) + `store.test.ts`

**Interfaces:**
- Consumes: Task 3 `x509.ts`, Task 5 tables.
- Produces: `class KeyStoreEnroller(context: Context) { fun enrollOrLoad(bootstrapChallengeB64: String): EnrolledKey }` with `data class EnrolledKey(alias: String, spkiB64: String, chainPem: String, certDigestB64: String)`; key alias constant `NopeekAttest.v1`. Oracle: `POST /enroll { walletPubkey, spkiB64, chainPem, certDigestB64 } -> { ok }` (upsert, rejects `revoked_at != null`); `POST /nonce { challengePubkey, walletPubkey } -> { nonceB64, expiresAt }` — 32 random bytes, stored with `expires_at = windowEnd + graceSeconds` supplied by the caller as `expiresAt` ISO, single-use.
- **Both endpoints are public: no `x-oracle-secret`** (spec §7 auth inversion). `settle` and `register-challenge` keep the header.

- [ ] **Step 1: Write the failing store tests** `store.test.ts > issueNonce stores an unexpired unconsumed nonce for (challenge, wallet)`; `> issueNonce for wallet B does not invalidate wallet A's nonce` (**Review Focus 1** setup); `> enroll upserts on wallet pubkey and refuses a revoked key`.
- [ ] **Step 2: Run to verify failure**, then implement `store.ts` with an injected client so tests can use a fake, matching how `determine_result.ts` is kept pure.
- [ ] **Step 3: Wire the routes** in `index.ts`: `/enroll` and `/nonce` must be reachable **without** the `x-oracle-secret` header — add them to the exemption list in `authorize(req)` and assert that in a test (`index.test.ts > enroll is reachable without the shared secret`). Move `x-oracle-secret` off these two endpoints for good.
- [ ] **Step 4: Write the failing Kotlin enrollment test** `EnrollRoundTripTest.enrollsOnceAndReusesTheSameAlias()` — call `enrollOrLoad` twice, assert the same alias, same SPKI, and a non-empty chain PEM; assert `KeyStore.isKeyEntry(alias, "AndroidKeyStore")`.
- [ ] **Step 5: Implement `KeyStoreEnroller`** reusing Task 2's key-generation parameters; on API 30+ request `setUserAuthenticationParameters` **off** (the report must be signable by the background worker without a prompt).
- [ ] **Step 6: Verify** `./gradlew.bat :app:connectedDebugAndroidTest --console=plain` PASS, `node --test "supabase/functions/oracle/*.test.ts"` PASS, deploy the function, then `curl -s https://<ref>.supabase.co/functions/v1/oracle/nonce -H 'content-type: application/json' -d '{"challengePubkey":"<valid base58>","walletPubkey":"<valid base58>","expiresAt":"2026-10-07T00:00:00Z"}'` → returns a `nonceB64`. Same call with no auth header must succeed.
- [ ] **Step 7: Commit**

### Task 8: Attestation verifier — the ten checks, in order

**Files:**
- Create: `supabase/functions/oracle/checks.ts` + `checks.test.ts`

**Interfaces:**
- Consumes: Task 3 `readAttestationExtension` / `verifyEcdsaSignature` / `verifyChainToRoot`, Task 6 `decodeEvidence` / `evidenceHash`, Task 5 tables.
- Produces:
```ts
type RejectReason = "CHAIN_NOT_ROOTED" | "KEY_NOT_ENROLLED" | "BAD_SIGNATURE"
  | "NONCE_MISMATCH" | "NONCE_CONSUMED" | "NONCE_EXPIRED" | "BOOT_UNLOCKED"
  | "BOOT_UNVERIFIED" | "PACKAGE_MISMATCH" | "USAGE_EXCEEDS_ACTIVE"
  | "DIGEST_MISMATCH" | "REPORT_LATE";
type VerifyResult = { ok: true; evidenceHash: string; checks: string[] }
  | { ok: false; reason: RejectReason };
interface VerifyDeps { verifyChainToRoot; readAttestationExtension; verifyEcdsaSignature;
  lookupEnrolledKey(walletPubkey): Promise<{ spkiB64 } | null>;
  consumeNonce(nonceB64, challengePubkey, walletPubkey): Promise<"ok"|"mismatch"|"consumed"|"expired">; }
async function verifyReport(input: { canonicalBytes: Uint8Array; signatureB64: string;
  chainPem: string; submittedAtMs: number; windowEndMs: number; graceSeconds: number },
  deps: VerifyDeps): Promise<VerifyResult>
```
`checks.ts` is pure and dependency-injected so all twelve reasons are testable with a fake device chain, without the real Redmi attached.

- [ ] **Step 1: Write the failing tests**, one per rejection, asserting the exact `reason` and the check order. Include the adversarial ones the spec implies: **Review Focus 1** `rejects a valid nonce issued for another wallet` (the fake `consumeNonce` returns `"mismatch"` when the (challenge, wallet) pair differs); **2** `rejects a replayed payload after the nonce was consumed` and `replaying the same canonical bytes twice yields NONCE_CONSUMED, not a second ok`; **3** `rejects a chain that does not terminate at the pinned Google root` — asserted to be checked **first**, before signature or nonce, with a stub that fails a test when any later dep is touched; **4** `rejects deviceLocked=false` and `rejects verifiedBootState != Verified`; plus `rejects signature over mutated bytes`, `rejects a leaf key not enrolled for this wallet`, `rejects package mismatch`, `rejects expired nonce`, `rejects submittedAt beyond windowEnd + grace` (`REPORT_LATE`), and **8** `rejects usedSeconds greater than deviceActiveSeconds` and `rejects an eventSetDigest that does not match the decoded field`.
- [ ] **Step 2: Run to verify failure** `node --test supabase/functions/oracle/checks.test.ts`
- [ ] **Step 3: Implement `verifyReport`** running spec §4.3 checks 1→10 in that exact order, returning the first failure, and collecting the passing check **names** (e.g. `"chain"`, `"enrolled"`, `"signature"`, `"nonce"`, `"boot"`, `"package"`, `"consistency"`, `"freshness"`) into `checks[]` — that array is what the Evidence screen and `/evidence/<hash>` publish, so name them stably.
- [ ] **Step 4: Run tests** — Expected: PASS.
- [ ] **Step 5: Positive test against the real fixture** — a test that loads `fixtures/device-chain.pem` and a chain whose challenge matches a fixture nonce, asserting `{ ok: true }`. Mark it skipped-but-documented if it can only run when Task 2's fixture is present, and **never let it pass vacuously**.
- [ ] **Step 6: Commit**

## Day 3 — Money and evidence on-chain

### Task 9: `CommitmentVerifier` seam and the evidence-shaped report DTO

A pure refactor with no behavior change, done **before** the verifier rewiring so the diff is reviewable as "moved, not modified."

**Files:**
- Create: `app/src/main/java/com/nopeek/app/data/verify/CommitmentVerifier.kt` + `app/src/test/java/com/nopeek/app/data/verify/CommitmentVerifierTest.kt`
- Modify: `data/Models.kt`, `data/usage/UsageWorker.kt`, `data/repositories/ChallengeRepository.kt`, plus every screen that calls `resolveResult`

**Interfaces:**
- Consumes: `VerificationStatus`, `TamperSignals`, `UsageReport` from `data/Models.kt`.
- Produces: `interface CommitmentVerifier { fun verify(report: UsageReport, limitSeconds: Int): VerificationStatus }`; `class LocalRuleCommitmentVerifier` (delegates to the existing `UsageReport.resolveResult`, i.e. today's behavior); `class OracleVerdictCommitmentVerifier` (reads the status the oracle recorded — used from Task 14 on); `data class UsageReport` gains `evidenceHash: String?`, `canonicalBytesHex: String?`, `verified: Boolean`, `rejectReason: String?`, `deviceActiveSeconds: Long`, `eventCount: Int`, `eventSetDigestHex: String`, all defaulted so existing call sites compile untouched.

- [ ] **Step 1: Write the failing test** `CommitmentVerifierTest.localRuleVerifierMatchesHistoricalResolveResultMatrix()` — table-driven over the same cases `UsageReportTest` already pins, so the refactor is proven behavior-preserving; and `> tamper signals override a passing usage number` (**the fail-closed rule must survive the refactor**).
- [ ] **Step 2: Run to verify failure**, then extend `UsageReport` with the defaulted fields and run `./gradlew.bat :app:testDebugUnitTest --console=plain -q` — Expected: existing suite still PASS (this is the real check that nothing changed).
- [ ] **Step 3: Implement the interface and `LocalRuleCommitmentVerifier`**; replace direct `resolveResult(...)` calls in screens and repository with an injected verifier, defaulting to the local one. **No Compose screen may call `resolveResult` or reason about `TamperSignals` afterwards** — grep `resolveResult` and confirm it appears only in `Models.kt` (definition) and `LocalRuleCommitmentVerifier`.
- [ ] **Step 4: Run tests + compile**, then commit `refactor: extract CommitmentVerifier seam and evidence-bearing UsageReport DTO`.

### Task 10: Anchor program v2 — duel nonce, evidence, treasury, reputation

**Files:**
- Modify: `contracts/program/src/lib.rs`
- Modify: `contracts/tests/manual-test-cases.md` (append cases 17–24)
- Modify: `contracts/DEPLOYED_PROGRAM_ID.txt`, `contracts/program/idl-from-playground.json`

**Interfaces:**
- Produces, exactly (spec §6): `create_challenge(stake_amount: u64, target_app: String, limit_seconds: u32, window_seconds: u32, nonce: u32)`; `settle_challenge(creator_result: u8, opponent_result: u8, creator_evidence_hash: [u8;32], opponent_evidence_hash: [u8;32])`; `resolve_lock()`; challenge seeds `[b"challenge", creator, opponent, nonce.to_le_bytes()]`; `Reputation` account `[b"reputation", wallet]` with `kept, broken, current_streak, longest_streak: u32`, `last_settled_at: i64`, `bump: u8`; `STATUS_LOCKED_RESOLVED: u8 = 5`; errors `NotLocked`, `TooEarlyToResolve`, `ReputationMismatch`, `EvidenceZero`; `TREASURY: Pubkey` const; `Challenge::LEN` 175 → 251.

- [ ] **Step 1: Write the eight new manual cases (17–24)** in `manual-test-cases.md` before coding, each as an expected-result line: 17 second duel, same pair, nonce 1 succeeds where nonce 0 failed with `AccountAlreadyInitialized`; 18 settle stores both evidence hashes and they read back on the account; 19 settle with a nonzero evidence hash for a `PASS` succeeds; 20 settle with a zero evidence hash for a `PASS` fails `EvidenceZero`; 21 settle with zero hashes for `FAIL`/`UNVERIFIED` succeeds; 22 `resolve_lock` before `end_time + LOCK_SECONDS` fails `TooEarlyToResolve`; 23 `resolve_lock` after moves the full escrow balance to the treasury ATA and sets status 5; 24 settle bumps `Reputation` for both parties with the spec's streak rule (PASS increments and raises `longest_streak`; FAIL/UNVERIFIED resets `current_streak` to 0 and increments `broken`).
- [ ] **Step 2: Implement the program changes** with `LOCK_SECONDS: i64 = 3600` (one hour, demo-legible; state in `ATTESTATION_DEPLOY_NOTES.md` that production would use 24h), `REPUTATION_SEED = b"reputation"`, treasury ATA derived off `TREASURY` + mint with `init_if_missing`, and the two `Reputation` accounts passed to `settle_challenge` as `mut` `init_if_missing` (payer = oracle).
- [ ] **Step 3: Redeploy on devnet as a new program**, re-export the IDL, replace `idl-from-playground.json`, diff vs `idl.json`, and update `idl.json` to the export if they differ (Playground export wins).
- [ ] **Step 4: Run cases 1–24** in the Playground Call tab, recording PASS/FAIL against each. Any of the original 16 regressing is a stop-condition, not a follow-up.
- [ ] **Step 5: Commit** `feat(program): duel nonce, on-chain evidence hashes, treasury lock resolution, reputation PDA`

### Task 11: Kotlin Solana client for program v2

**Files:**
- Modify: `data/solana/{Pda,ProgramInstructions,TransactionCompiler,SettlementClient,IdlParser}.kt`, `data/Models.kt` (`SettleChallengeParams`), `data/local/{ChallengeEntity,ChallengeDao,AppDatabase}.kt`, `data/repositories/ChallengeRepository.kt`
- Modify: `app/src/test/java/com/nopeek/app/data/solana/{PdaTest,ProgramInstructionsTest,TransactionCompilerTest,SettlementLogicTest,ChallengeAccountCodecTest,IdlParserTest}.kt`

**Interfaces:**
- Consumes: Task 10's IDL.
- Produces: `Pda.challengePda(creator: String, opponent: String, nonce: Int, programId: String): PdaAddress` (nonce as 4 LE bytes); `Pda.reputationPda(wallet: String, programId: String): PdaAddress`; `ProgramInstructions.createChallenge(idl, params: CreateChallengeParams)` with `nonce: Int`; `ProgramInstructions.settleChallenge(idl, params: SettleChallengeParams)` where `SettleChallengeParams(creatorResult: Int, opponentResult: Int, creatorEvidenceHash: ByteArray, opponentEvidenceHash: ByteArray, challengePda, escrow, creatorToken, opponentToken, creator, opponent, reputationPdas)`; `ProgramInstructions.resolveLock(idl, params: ResolveLockParams)`; `ChallengeAccountCodec.decodeV2Layout(data: ByteArray): ChallengeV2` reads `nonce`, the two 32-byte hashes, and `settledAt` at the spec's offsets, replacing the 175-byte `decode` outright (the v1 layout is unreadable against a redeployed program, so no shim).

- [ ] **Step 1: Write the failing tests** `PdaTest.challengePdaIncludesNonceAsLittleEndianBytes()` asserting two nonces for the same pair give different addresses and that nonce `1` encodes as `01000000` (**Review Focus 7**); `PdaTest.reputationPdaIsStablePerWalletAndIndependentOfPair()`; `ProgramInstructionsTest.settleEncodesTwoEvidenceHashesInOrder()` decoding the built instruction data back to the exact 32-byte arrays; `ProgramInstructionsTest.resolveLockBuildsExpectedAccountList()`; `ChallengeAccountCodecTest.decodesV2Layout()` over a hand-built 251-byte account asserting nonce, both hashes, `settledAt`, and status 5; `SettlementLogicTest.evidenceHashForPassMustBeNonZero()` mirroring `EvidenceZero`.
- [ ] **Step 2: Run to verify failure** `./gradlew.bat :app:testDebugUnitTest --console=plain -q`
- [ ] **Step 3: Implement**, updating `IdlParser` for the new arg types (`[u8;32]` arrays — check how the IDL encodes a fixed byte array; if `IdlParser` has no array support, add a `type: "array", length: 32` case and test it). Store `nonce` and `evidenceHash` per challenge in Room (`ChallengeEntity` + a migration bump in `AppDatabase`), and pass `challengePda` derived with the nonce everywhere it was previously derived without one — grep `challengePda(` and fix every call site, including the now-deleted `decode` call sites that move to `decodeV2Layout`.
- [ ] **Step 4: Run tests** — Expected: PASS. Then install on the Redmi, connect Phantom, and confirm a duel still **creates** on devnet (it may not settle yet; the oracle is not wired). That device check is this task's exit, not a green Gradle run alone.
- [ ] **Step 5: Commit**

## Day 4 — One live, attested, settled duel

### Task 12: `UsageEventCollector` — whole-device digest and consistency bound

**Files:**
- Create: `data/usage/UsageEventCollector.kt` + `app/src/test/java/com/nopeek/app/data/usage/UsageEventCollectorTest.kt`
- Modify: `data/usage/UsageWorker.kt` (delegate to the collector), `data/usage/UsageTracker.kt`

**Interfaces:**
- Consumes: `aggregateUsage` and `detectTamperSignals` in `UsageTracker.kt`.
- Produces: `data class EventSet(events: List<UsageEvent>, deviceActiveSeconds: Long, eventCount: Int, eventSetDigestHex: String)`; `class UsageEventCollector(context: Context) { fun collect(windowStartMs: Long, windowEndMs: Long): EventSet; fun usedSecondsFor(eventSet: EventSet, targetPackage: String): Int }`. `eventSetDigestHex` = sha256 over the sorted, de-duplicated `packageName:eventType:timestamp` triples across **all** packages, joined by `\n`, lowercase hex. `deviceActiveSeconds` = total non-overlapping foreground span across all packages (spec §4.2).

- [ ] **Step 1: Write the failing tests** `UsageEventCollectorTest.digestIsOrderAndDuplicateIndependent()` (same events shuffled and with a duplicate line → identical digest; one changed timestamp → different digest); `> deviceActiveSecondsIgnoresOverlappingSessions()` (A foreground 0–100 and B foreground 50–150 → 150 s total, not 200); `> usedSecondsForTargetsOnePackageOnly()`; `> forwardClockJumpIsDetected()` — **Review Focus 6**: extend `detectTamperSignals` with `clockJumpedForward` (wall clock ahead of `windowEnd + elapsed monotonic allowance`) and assert it produces `CLOCK_JUMPED_FORWARD` in `TamperSignals`, which by the existing rule makes the verdict `UNVERIFIED`.
- [ ] **Step 2: Run to verify failure**, then implement, moving the `queryEvents` loop out of `UsageWorker.doWork()` into the collector and having `UsageWorker` call it. Keep `UsageAggregatorTest` green — if it asserts on the old signature, update the test, not the semantics.
- [ ] **Step 3: Verify** unit tests PASS, then on-device: grant usage access, open Instagram for a known interval, and log `usedSecondsFor`, `deviceActiveSeconds`, `eventCount` on the Redmi. Expected: `usedSeconds <= deviceActiveSeconds`, and seconds within a few percent of reality. Record the numbers in `ATTESTATION_DEPLOY_NOTES.md` — this is also the evidence that MIUI reports usage honestly.
- [ ] **Step 4: Commit**

### Task 13: `ReportScheduler` — two chances to produce the report

**Files:**
- Create: `data/usage/ReportScheduler.kt` + `app/src/test/java/com/nopeek/app/data/usage/ReportSchedulerTest.kt`
- Modify: `data/usage/UsageWorker.kt`, `MainActivity.kt` (foreground hook), `data/attest/EvidenceReporter.kt` (create)

**Interfaces:**
- Consumes: `UsageSyncParams`, `UsageSyncParamsReader`, `UsageEventCollector`, `KeyStoreEnroller`, `CanonicalPayload`, `NopeekBackend.oracleUrl`, Task 8's verifier contract on the server side.
- Produces: `object ReportScheduler { fun schedule(context: Context, params: UsageSyncParams); fun cancel(context: Context, challengePubkey: String); suspend fun reportNowIfDue(context: Context, params: UsageSyncParams): ReportOutcome }`; `sealed class ReportOutcome { data class Accepted(val evidenceHash: String) : ReportOutcome(); data class Rejected(val reason: String) : ReportOutcome(); case object NotYetDue : ReportOutcome(); data class TransportFailed(val cause: String) : ReportOutcome() }`. Worker name pattern `nopeek_report_<challengePubkey>`.

- [ ] **Step 1: Write the failing tests** `ReportSchedulerTest.scheduleUsesOneTimeWorkNotPeriodic()` (assert the WorkManager request is a `OneTimeWorkRequest` with `initialDelay = windowEnd - now` and **no** `NetworkType.CONNECTED` constraint); `> reportNowIfDueRefusesBeforeWindowEnd()` → `NotYetDue`; `> reportNowIfDueGivesUpAfterGraceExpiry()` → `Rejected("REPORT_LATE")` without a network call; `> reportNowIfIsDueIsIdempotentByEvidenceHash()` — a second submission of unchanged bytes does not create a second request (**Review Focus 2**, client half); `> workNameIsUniquePerChallenge()`.
- [ ] **Step 2: Run to verify failure**, then implement `EvidenceReporter` (fetch nonce → `KeyStoreEnroller.enrollOrLoad` → `CanonicalPayload.encode` → sign with the KeyStore alias → POST bytes + `signatureB64` + `chainPem`) and `ReportScheduler`, replacing `scheduleUsageSync`/`cancelUsageSync` call sites (grep both, including `ChallengeRepository`).
- [ ] **Step 3: Hook the eager path** — `MainActivity` `onResume` calls `reportNowIfDue` for each ACTIVE challenge in Room whose window has closed and grace has not expired. Run it on the app's existing coroutine scope; failure is logged, never thrown into the UI.
- [ ] **Step 4: Run tests**, then the **MIUI checklist on the Redmi** (spec §8), recording each in `ATTESTATION_DEPLOY_NOTES.md`: Usage Access granted; battery saver off; Nopeek excluded from per-app battery optimization; Autostart enabled; app allowed on lock screen. Then verify **Review Focus 5**: force-stop the app so the worker cannot fire, let the window lapse, reopen the app, and confirm the eager path submits a valid report inside grace and the user is told what happened rather than silently losing.
- [ ] **Step 5: Commit**

### Task 14: `/submit-report` verifies, and `/evidence/<hash>` is public

**Files:**
- Modify: `supabase/functions/oracle/index.ts`, `store.ts`
- Create: `supabase/functions/oracle/index.test.ts`
- Delete: the temporary `/verify-probe` route from Task 3

**Interfaces:**
- Consumes: Task 8 `verifyReport`, Task 6 `evidenceHash`, Task 5 tables, Task 9's DTO field names.
- Produces: `POST /submit-report { canonicalBytesB64, signatureB64, chainPem, submittedAt }` → `{ ok: true, evidenceHash }` or `{ ok: false, reason }` — the handler decodes `canonicalBytesB64`, then **loads the `challenges` row for the decoded `challengePubkey`** to supply `windowEndMs` and `graceSeconds` to `verifyReport`; those two inputs are never taken from the client's submission. The row is stored **only** when verification passes, and a rejected submission is stored with `verified = false` plus `reject_reason` for the deck's honesty slide. `GET /evidence/<hash>` → `{ canonicalBytesB64, decodedFields, signatureB64, certChainPem, checksPassed: string[], challengePubkey, walletPubkey, verifiedAt }`, **no auth**. `POST /settle` now returns `creatorEvidenceHash` / `opponentEvidenceHash` hex alongside the codes.

- [ ] **Step 1: Write the failing tests** `index.test.ts > submit-report is reachable without x-oracle-secret` and `> settle still requires x-oracle-secret`; `> a verified submission stores the row with verified=true and its computed evidence_hash`; `> a rejected submission stores reject_reason and never sets verified=true`; `> submitting the same canonical bytes twice does not insert twice` (unique index from Task 5 — **Review Focus 2**, server half); `> evidence endpoint returns the exact bytes whose hash was requested, and a hash that was never stored returns 404`; `> settle returns evidence hashes matching the stored reports`.
- [ ] **Step 2: Run to verify failure** `node --test "supabase/functions/oracle/*.test.ts"`, then implement against `verifyReport` with `store.ts` as the real dependency-injection seam, and deploy the function.
- [ ] **Step 3: Live proof from the command line** — with a chain from the device: request a nonce, sign on-device, submit, then `curl https://<ref>.supabase.co/functions/v1/oracle/evidence/<hash>` with **no headers at all** and confirm the published bytes' sha256 equals the URL. Record the URL and hash in `ATTESTATION_DEPLOY_NOTES.md`.
- [ ] **Step 4: Verify the fail-closed path end-to-end at the oracle level** — `POST /settle` for a challenge where one wallet has no verified report returns `creatorResult`/`opponentResult` where that party is `3` (UNVERIFIED). That is spec §4.3's "any failure ⇒ loss", proven without a program call.
- [ ] **Step 5: Commit**

### Task 15: Two-device end-to-end duel — the checkpoint

No new unit tests: this task's deliverable is a working system and the artifacts that prove it.

**Files:**
- Modify: `data/usage/OracleReportApi.kt` (send the attested envelope), `data/repositories/ChallengeRepository.kt` (persist evidence hashes, pass them to settle), `data/local/ChallengeDao.kt`
- Modify: `contracts/USER_ACTIONS.md` (record real, dated results for every box it still shows unchecked)

- [ ] **Step 1: Swap the injected verifier** — bind `OracleVerdictCommitmentVerifier` (Task 9) as the app's implementation, so the device shows the verdict the oracle recorded rather than re-deriving one from its own numbers. Grep for `LocalRuleCommitmentVerifier` and confirm it remains only as the offline/no-network fallback path, never as the settle input.
- [ ] **Step 2: Create and join the duel across two devices** — Device A creates (target `com.instagram.android`, limit 60 s, window 120 s, stake 1_000_000 units = 1.00 USDC, nonce 0); Device B joins with its own wallet and its own enrolled key. Expected: both stakes in the escrow ATA, readable on Solana Explorer for devnet.
- [ ] **Step 3: Run the window honestly** — A does not open Instagram; B opens it past 60 s. At window end each device submits an attested report. Expected: two rows with `verified = true` and distinct evidence hashes.
- [ ] **Step 4: Settle from the app** — tap Settle, approve in wallet. Expected: transaction lands; the explorer shows the `Challenge` PDA account data containing **both evidence hashes** (decode with `ChallengeAccountCodec.decodeV2Layout` via a one-off debug print, or dump the account base64 and decode in Node); the winner's ATA balance rises by 2.00 USDC.
- [ ] **Step 5: Capture the proof artifacts** — screenshot the explorer transaction and the decoded account data; save the `/evidence/<hash>` page as HTML; save all of it to a `docs/evidence/` folder committed to the repo. These go straight into the demo and the deck.
- [ ] **Step 6: Re-verify independently** — in a browser tab next to the explorer, recompute sha256 of the published canonical bytes (a `node -e` one-liner is fine) and confirm it equals both the URL and the on-chain value. If these three do not agree, the claim is not yet true; do not proceed to Task 16.
- [ ] **Step 7: Commit** `feat: end-to-end attested duel settles on devnet with on-chain evidence hashes`

### Task 16: Fail-closed duel — the demo's best scene

**Files:**
- Create: `docs/evidence/fail-closed.md`

- [ ] **Step 1: Run the revocation scenario** — start a duel on both devices; on B, revoke Usage Access mid-window. Expected: B's next report carries `PERMISSION_REVOKED` (or B produces no valid report at all), and settle returns B = UNVERIFIED.
- [ ] **Step 2: Settle and confirm money moved to A** — 2.00 USDC to A, B = 0. Record the transaction signature.
- [ ] **Step 3: Run the silence scenario** — a duel where B never opens the app and never reports. Expected: missing report ⇒ UNVERIFIED ⇒ B loses. This proves spec §4.3's last line and needs no code.
- [ ] **Step 4: Write `docs/evidence/fail-closed.md`** with both signatures, timestamps, and what each shows. This document is the source of the demo's 2:05–2:35 scene.
- [ ] **Step 5: Commit**

### Task 17: Reputation PDA and `/reputation/<wallet>`

**Files:**
- Modify: `supabase/functions/oracle/index.ts`, `store.ts`, `store.test.ts`; `data/solana/ProgramClient.kt` (account fetch)
- Create: `app/src/test/java/com/nopeek/app/data/solana/ReputationCodecTest.kt`

**Interfaces:**
- Produces: `GET /reputation/<wallet>` → `{ walletPubkey, account: <PDA address>, kept, broken, currentStreak, longestStreak, lastSettledAt, sourceTransaction: <signature> }`; `ReputationCodec.decode(accountData: ByteArray): Reputation?`.

- [ ] **Step 1: Write the failing test** `ReputationCodecTest.decodesDiscriminantAndFields()` over a hand-built account (8-byte Anchor discriminator then the spec §6 fields) and `> returns null for a short buffer`; `store.test.ts > reputation endpoint reads the PDA rather than trusting the Postgres row` — assert the handler fetches the account and that the response's numbers come from the decode, not from a table.
- [ ] **Step 2: Run to verify failure**, then implement the codec and the endpoint. The endpoint must be **public and header-free** like `/evidence`; the honest framing is that the PDA is the source of truth and Postgres is a cache.
- [ ] **Step 3: Run tests + deploy**, then curl `/reputation/<wallet A>` and `/reputation/<wallet B>` and confirm A shows `kept: 1, currentStreak: 1` and B shows `broken: 1, currentStreak: 0` after Tasks 15–16.
- [ ] **Step 4: Commit**

## Day 5 — Make it win

### Task 18: Evidence screen

**Files:**
- Create: `ui/screens/EvidenceScreen.kt` + `app/src/test/java/com/nopeek/app/ui/screens/EvidenceViewTest.kt`
- Modify: `navigation/{NavGraph,NavRoutes}.kt`, `ui/screens/{WonScreen,LostScreen}.kt`, `data/local/ChallengeDao.kt`

**Interfaces:**
- Consumes: `ChallengeEntity.evidenceHash`, Task 8's `checksPassed` names, `NopeekBackend.oracleUrl("evidence/$hash")`, `NopeekTokens`.
- Produces: route `evidence/{challengePubkey}`; `data class EvidenceUiState(hash, targetPackage, usedSeconds, limitSeconds, verdict, checksPassed: List<String>, publishedUrl, rejectReason: String?)`; the client-side sentinel constant `REPORT_ABSENT` (a string `reject_reason` used when no report row exists at all — the server has no row to derive a reason from). `LostScreen` shows *why* the stake was lost in words, from `rejectReason` or `REPORT_ABSENT`.

- [ ] **Step 1: Write the failing test** `EvidenceViewTest.rejectReasonMapsToPlainLanguage()` asserting every `RejectReason` string from Task 8 plus `REPORT_ABSENT` maps to a non-empty user-facing sentence containing neither "UNVERIFIED" nor any code identifier; and `> checksRenderAsLabelsNotColorAlone()` (design-system rule: status pairs color with a label and icon).
- [ ] **Step 2: Run to verify failure**, then implement the screen using existing `NopeekTokens`/`NopeekTypography` and the component set in `ui/components/Cards.kt` and `StateViews.kt`. It lists the hash, the decoded fields, each passed check as a labelled row, and a "open public evidence" action via `Intent.ACTION_VIEW`. **Review Focus 5**: `LostScreen` and `WonScreen` each name the reason for the outcome.
- [ ] **Step 3: Run tests + compile**, install, and read the real evidence for Task 15's duel on device. Screenshot it for the deck.
- [ ] **Step 4: Commit**

### Task 19: Rename, Seeker removal, clean public repo

**Files:**
- Modify: `README.md`, `.gitignore`, `contracts/**` docs, any `com.volo.app` / "Volo" strings; delete `data/identity/{IdentityProvider,MockSeekerProvider}.kt` and `data/demo/DemoSeeder.kt` plus their tests
- Create: the new public repository (name `nopeek`)

- [ ] **Step 1: Grep for leftovers** — `grep -rn "volo\|Volo\|VOLO\|SKR\|Seeker\|MWA\|mobile-wallet-adapter"` across `app/src`, `contracts`, `supabase`, `README.md`. Every hit is either renamed or deleted in this task; keep `mobile-wallet-adapter` deps only if `WalletManager` still needs a wallet connection, and say which in `ATTESTATION_DEPLOY_NOTES.md`.
- [ ] **Step 2: Delete the Seeker mock layer** (`MockSeekerProvider`, its test, `DemoSeeder` and its test) and any navigation route that only existed for demo data; wire nothing to a Seeker-specific assumption.
- [ ] **Step 3: Verify** `./gradlew.bat :app:compileDebugKotlin --console=plain -q` and `./gradlew.bat :app:testDebugUnitTest --console=plain -q` — Expected: PASS with a smaller suite. Update `NopeekTokensTest`/`NavRoutesTest` if they asserted on deleted routes.
- [ ] **Step 4: Create a new clean public repo and push this tree** — not a history scrub (spec §10). Before pushing: `git log --all --stat | Select-String -Pattern "keystore|\.properties|secret|BEGIN PRIVATE"` returns nothing; `keystore.properties` and `local.properties` are in `.gitignore` and absent from `git ls-files`. Confirm the APK from the release build installs on the Redmi.
- [ ] **Step 5: Commit + push**, then record the repo URL and the release/APK link in `contracts/ATTESTATION_DEPLOY_NOTES.md`.

### Task 20: Demo, deck, Colosseum draft

**Files:**
- Create: `assets/demo-script-nopeek.md`, `docs/evidence/README.md`
- Modify: `assets/pitch-deck-outline.md`

- [ ] **Step 1: Rewrite the demo script** to spec §11's seven scenes, replacing `assets/demo-script.md`'s Volo/Seeker scenarios. Every claim maps to an artifact already in `docs/evidence/` — nothing is said on camera that was not already run in Task 15 or 16.
- [ ] **Step 2: Rehearse once with the phone screen mirrored**, and cut any scene that cannot be run twice in a row without a stumble. Two clean takes minimum, including the money shot where `/evidence/<hash>` and the explorer sit side by side.
- [ ] **Step 3: Record and upload** unlisted; save links to `submission-links.txt` (gitignored).
- [ ] **Step 4: Build the deck** from `assets/pitch-deck-outline.md`, leading with wedge D and demoing wedge B, and including spec §5's limitations as a slide rather than silence. State the revenue point at the `resolve_lock` treasury screenshot.
- [ ] **Step 5: Create the Colosseum project draft** with repo, video, and deck links, then run the Copilot submission attack named in the preflight's step 5 and fix what it finds.

---

## What This Plan Does Not Do

No iOS or Swift code; no React Native; no mainnet; no app-blocking; no group challenges, streaks-as-a-product, stats screens, disputes, or reward token. Each is a roadmap item in spec §12 or an explicit §2 cut, and the deck should say so rather than the code base trying to imply it.
