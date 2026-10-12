# Nopeek — Colosseum Architecture and Compliance-Proof Design Specification

**Status:** Approved in conversation 2026-10-06; written for review
**Event:** Crypto World's Fair — Solana track. Submission closes Oct 12 11:59 PM PT / Oct 13 06:59 UTC
**Platform:** Native Android (Kotlin + Jetpack Compose), Solana devnet, Supabase Edge Functions
**Budget:** $0. No paid services, no Apple Developer account, no mainnet capital
**Team:** Solo developer, ~6 focused hours/day ≈ 36h gross. Day 6 is reserved for buffer, final live verification, and submission; the plan schedules 30h across Days 1–5 (see §10 for the arithmetic)
**Inputs:** `docs/nopeek-colosseum-preflight.md` (Colosseum Copilot pre-flight), `volo-plan.md` (the build that produced the current codebase)
**Supersedes:** the Seeker-first framing and the Volo name in `volo-plan.md`. Product principles, state model, and the design system from `2026-10-02-volo-ui-rework-design.md` remain in force.

---

## Locked Decisions

| Decision | Choice | Decided by |
| --- | --- | --- |
| Wedge | **D** (non-custodial commitment escrow + portable kept-promise reputation) as the anchor, **B** (P2P duels) as the demo surface | User, Q6 |
| Platform | Android only. iOS descoped from the deliverable | User, this session |
| Language | **Keep Kotlin.** No React Native rewrite inside the hackathon window | User, Q4 → Approach 1 |
| Network | Devnet only; chain config stays a swappable constant | User, Q7 |
| Compliance anchor | **Android Key Attestation** (hardware keystore, X.509 chain to a pinned Google root). Play Integrity rejected | Design, §Compliance proof |
| Settlement policy | **Fail-closed** — already implemented and retained unchanged | Existing code |
| Evidence placement | SHA-256 of the signed canonical bytes written on-chain at settle; payload published publicly | Design |
| Both-fail pot | `resolve_lock` moves the pot to a treasury PDA after the lock window | Design; refund alternative documented |
| Reputation primitive | Per-wallet `Reputation` PDA. **No** token mint, no reward token | Design |
| Trust inversion | `/enroll`, `/nonce`, `/submit-report`, `/evidence/*` are public endpoints; the signature is the authentication | Design |
| Free tier | Supabase free, Solana Playground browser IDE, Helius free RPC, physical devices only, no emulator | User constraint |

---

## 1. Problem and positioning

**What Nopeek is, on the submission form:**

> Nopeek puts money where your attention is. Two people stake USDC against a screen-time limit; a neutral Solana program holds the escrow and settles it against **device-attested evidence that anyone can re-verify** — not a company's ruling. Every kept promise mints a permanent, readable record on the wallet itself.

**The one-line answer to "why does this need crypto":** a fiat incumbent holds your card and decides whether you cheated. Nopeek holds nothing and proves everything. Non-custodial escrow, deterministic settlement, and a portable record of kept promises that no company can freeze, edit, or take with it.

**Why this is not the 21st clone.** The pre-flight found ~20 near-identical projects at Colosseum, none of which won, and one consistent fatal gap: **tamper-proof compliance verification, not trusting the project's own server.** WebLock was logged as *server-managed escrow, self-reported breaks*; *commit* showed "verification failed" live on camera. This design spends its budget on exactly that gap.

**Named incumbent contrast (Forfeit):** $8.7M staked, 94.2% success, real money — and it is custodial, unverifiable, and card-gated. Nopeek's claim is not "better screen-time tracking." It is *neutral, re-verifiable proof and self-custody*.

---

## 2. Scope

**In scope (all of it, in this order of priority):**
1. Hardware-attested usage reports: enroll → nonce → sign → verify → publish.
2. One live devnet duel between two physical Android devices that settles with an evidence hash on-chain.
3. Multiple duels per opponent pair (PDA nonce).
4. `resolve_lock` + treasury (un-strands escrow, states the revenue point).
5. Per-wallet `Reputation` PDA, bumped at settle, readable by anyone.
6. `CommitmentVerifier` seam + transport-neutral `UsageReport` (the RN port preparation, ~1h).
7. Rename cleanup (Volo → Nopeek, stale paths, drop Seeker/SKR) and a clean public repo.
8. Demo, deck, submission.

**Explicitly out of scope — roadmap only, do not build:**
React Native, iOS, Apple entitlement requests, mainnet, group challenges/pools, streak-based products, stats screens, app-blocking "hard mode", dispute windows, reward/protocol tokens, Privy, LLC formation, backend-as-a-service beyond Supabase, emulators, CI beyond what a free public repo provides.

---

## 3. Architecture

```
                        NOPEEK (Android, Kotlin/Compose)
  UI (existing 8 screens) ──> domain services ──> CommitmentVerifier (interface)
                                                        │
              ┌─────────────────────────┬───────────────┴──────────────┐
              │                         │                              │
     UsageEvidenceSource        EvidenceReporter              SolanaClient
  (UsageStatsManager,         (KeyStore sign,              (Pda, ProgramInstructions,
   MIUI-tolerant worker)       WorkManager one-shot)        TransactionCompiler,
                                                            SettlementClient)
              │                         │                              │
              └────────────┬────────────┘                              │
                           ▼                                           ▼
                    Nopeek oracle (Supabase Edge)  ─── verify ───>  Solana devnet
                    /enroll /nonce /submit-report                   Challenge PDA
                    /evidence/<hash> /reputation/<wallet>           Reputation PDA
                    /settle (secret-guarded)                        Escrow ATA
                                                                    Treasury PDA
```

Layers stay separated so the product layer can move to React Native without touching the proof layer: **the verifier, the evidence format, and the settlement protocol are the product.** The UI is a client of them.

---

## 4. The compliance proof

### 4.1 Protocol

1. **Bind first.** Opening a commitment window, the app calls `/nonce` with `challengePubkey` + `walletPubkey`. The oracle returns a fresh random nonce, stored with `expires_at = windowEnd + grace` and `consumed = false`.
2. **Mint and enroll, per window.** The device generates an EC P-256 key pair *inside the Android KeyStore*, non-exportable, with `setAttestationChallenge(thatNonce)`, then registers the full cert chain and leaf public key against the connected wallet at `/enroll`.
3. **Sign.** At window end the app builds the canonical byte payload (§4.2), signs it with the KeyStore key (`SHA256withECDSA`), converts the signature from the DER form AndroidKeyStore emits to the fixed-width raw `r||s` the oracle pins (the one-encoding rule below), and POSTs payload bytes + signature + certificate chain.
4. **Verify.** The oracle runs every check in §4.3 and rejects on any failure.
5. **Publish and pin.** On success the oracle stores the row and computes `evidenceHash = sha256(canonicalBytes)`, serves the raw evidence publicly at `/evidence/<hash>`, and passes the hash into `settle_challenge`. The program stores both parties' hashes on the `Challenge` PDA.

**Why the key is minted per window and not once at install.** This section originally specified enrollment "on first grant of Usage Access" *and* check 4 as `attestationChallenge == issued nonce`. Those two cannot both hold: `setAttestationChallenge()` writes the value into the certificate at key creation, where it stays for the key's whole life, while `/nonce` issues a new value every window. A persistent enrolled key therefore mismatches check 4 for every report after the first — which under the fail-closed rule forfeits an honest user's stake. Per-window minting is the fix that keeps check 4 in its strongest form, and it makes a better claim than the one it replaced: the oracle is handed a Google-signed attestation created for *this* window, not a certificate quoted from install day. The cost is one key generation and one `/enroll` POST per report, and `install_keys` therefore holds each wallet's *current* key — already-stored reports are unaffected, since they were verified at submission and settle from their own row.

**Byte-exact rule.** The device signs the exact bytes it transmits; the oracle hashes the exact bytes it received. Neither side re-encodes or re-serializes. Cross-language canonicalization (Kotlin vs Deno) is not attempted — it is a known time sink with no security upside here.

**One encoding per claim.** The same rule applies to the signature itself. Measured on the Redmi, AndroidKeyStore's `SHA256withECDSA` emits a DER `SEQUENCE{INTEGER r, INTEGER s}`, while the oracle verifies fixed-width raw `r||s` (P-1363), which is what WebCrypto and `@noble/curves` require for P-256. The device converts. The oracle deliberately refuses DER rather than accepting both: one claim about who signed a payload should correspond to one byte form, and widening the accepted set would silently double the inputs that satisfy check 3 while breaking the single-format discipline the Kotlin and TypeScript encoders are pinned to by a shared fixture.

### 4.2 Canonical payload

A flat, length-prefixed concatenation of UTF-8 fields, versioned, in this fixed order:

`version(1) | challengePubkey | walletPubkey | packageName | targetPackage | windowStart(ms) | windowEnd(ms) | usedSeconds | deviceActiveSeconds | eventCount | eventSetDigest | tamperSignals(joined) | nonce`

`eventSetDigest = sha256` over the sorted, de-duplicated `packageName:eventType:timestamp` triples from `UsageStatsManager.queryEvents` for the whole window across **all** apps — not just the target app. A false report must be internally consistent with the device's entire activity, not merely assert a small number.

`deviceActiveSeconds` = total non-overlapping foreground span across all packages in the window, computed from the same event stream. It is a *consistency* check (a stake of 20 minutes on one app while the device shows 90 minutes of foreground activity invites scrutiny), **not** an independent sensor — Android will not hand a background app screen-on broadcasts, and the spec does not claim otherwise.

### 4.3 Verification checks (all must pass)

| # | Check | Source | Enforcement tier |
| --- | --- | --- | --- |
| 1 | Certificate chain verifies to the pinned Google Android Keyset attestation root | X.509 signatures, WebCrypto | hardware |
| 2 | Leaf public key == the key enrolled for this wallet | `install_keys` | hardware |
| 3 | Signature valid over the exact received bytes | WebCrypto ECDSA | hardware |
| 4 | `attestationChallenge` == issued nonce | attestation extension, `attestationChallenge` field | hardware |
| 5 | Nonce unconsumed and unexpired; marked consumed | `report_nonces` | oracle |
| 6 | `rootOfTrust.deviceLocked == true` | attestation ext `[704]` | **hardware-enforced** |
| 7 | `rootOfTrust.verifiedBootState == VERIFIED` | attestation ext `[704]` | **hardware-enforced** |
| 8 | `attestationApplicationId` package + signing-cert digest match Nopeek | attestation ext `[709]` | software-enforced — corroboration only, see §5 |
| 9 | `usedSeconds <= deviceActiveSeconds` and `eventCount` consistent with `eventSetDigest` | recomputed from the same stream | oracle |
| 10 | `submittedAt <= windowEnd + grace` | row | oracle |

Any failure → the report is not stored as valid → at settle the party is `UNVERIFIED` → the existing fail-closed rule makes that a loss.

### 4.4 Why this answers the judge question

An oracle that only *verifies* is still an authority you must trust. This oracle **publishes**: canonical bytes, signature, and chain are served unauthenticated at `/evidence/<hash>`, and their SHA-256 is written into the settlement transaction. Any judge, any opponent, any future auditor can re-run checks 1–9 themselves against a Google root and a Solana slot. The trust placed in Nopeek's server is *not* the integrity of the verdict — it is only availability.

**The deck line:** *anyone can submit a usage report; nobody has ever submitted a valid one.*

---

## 5. Residual limitations — stated in the submission, not buried

1. **Provenance, not truthfulness.** Attestation proves the report came from an unmodified, locked, verified-boot device holding an enrolled hardware key. It does not prove the number is honest. Mitigations: the whole-device `eventSetDigest` and the `deviceActiveSeconds` consistency bound (check 9) — both derived from the same stream, so they constrain an internal contradiction rather than independently measure. Not solved: a determined liar on their own honest device.
2. **A device, not a person.** Second device, another browser, a friend's phone, and web usage are out of scope. Nopeek attests one device's activity and the escrow terms are written to say so.
3. **Software-enforced identity.** `attestationApplicationId` lives in `softwareEnforced`, and AOSP shows `Tag::APPLICATION_ID` can be supplied to `attestKey`. Check 8 is therefore corroboration, not proof; checks 6–7 (hardware-enforced) carry the device-integrity claim.
4. **The oracle is still operator-run.** Key rotation and treasury keys are single-signer. Decentralized oracle is roadmap.
5. **Regulatory.** Forfeited stakes moving to another user edges toward wagering; the treasury model is chosen partly because it avoids prize-pool economics. Not legal advice; not solvable in six days.

---

## 6. Program changes (`contracts/program/src/lib.rs`)

| Change | Detail |
| --- | --- |
| `declare_id!` | Real Playground-generated id (currently the `1111…` placeholder) |
| `ORACLE_AUTHORITY` | Oracle wallet pubkey generated during Day-1 faucet work |
| `create_challenge(..., nonce: u32)` | Seeds become `[b"challenge", creator, opponent, nonce_le_bytes]`; `nonce` stored on the account. Fixes the one-duel-per-pair-ever collision |
| `settle_challenge(creator_result, opponent_result, creator_evidence_hash: [u8;32], opponent_evidence_hash: [u8;32])` | Both stored on `Challenge`, plus `settled_at: i64` |
| `resolve_lock` | Requires `status == BOTH_FAIL_LOCKED` and `clock.unix_timestamp >= end_time + LOCK_SECONDS`; transfers full escrow to the treasury ATA, sets `status = STATUS_LOCKED_RESOLVED (5)`. Callable by either party |
| `Reputation` PDA | Seeds `[b"reputation", wallet]`; `kept: u32, broken: u32, current_streak: u32, longest_streak: u32, last_settled_at: i64, bump: u8`. Bumped for both parties inside `settle_challenge`; `init_if_missing`, payer = oracle |
| `TREASURY` pubkey constant | Program-level const alongside `ORACLE_AUTHORITY` |
| Remove | Seeker/SKR references and the genesis-token path |

`Challenge::LEN`: **175 → 251** bytes (+4 `nonce`, +64 two evidence hashes, +8 `settled_at`). `target_app` stays `String` capped at 32 bytes.

Streak rule: `PASS` → `current_streak += 1`, `kept += 1`, `longest_streak = max(longest, current)`. `FAIL`/`UNVERIFIED` → `broken += 1`, `current_streak = 0`.

New error codes: `NotLocked`, `TooEarlyToResolve`, `ReputationMismatch`, `EvidenceZero` (evidence hashes must be non-zero for explicit `PASS` results; `FAIL`/`UNVERIFIED` may be zero, since an absent report has no bytes to hash).

Existing 16 manual cases in `contracts/tests/manual-test-cases.md` are re-run after this change, not before submission day.

---

## 7. Oracle changes (`supabase/functions/oracle/`)

**Migration** `20261006_nopeek_attestation.sql`, additive to `20261001_volo_init.sql`:
- `install_keys(wallet_pubkey pk, keystore_pubkey_spki, cert_chain_pem, enrolled_at, revoked_at)`
- `report_nonces(id, challenge_pubkey, wallet_pubkey, nonce unique, issued_at, expires_at, consumed_at)`
- `usage_reports` + `signature text, cert_chain_pem text, canonical_bytes bytea, evidence_hash text, package_name text, screen_on_seconds int, event_count int, event_set_digest text, verified bool, reject_reason text`
- `settlement_results` + `creator_evidence_hash, opponent_evidence_hash`
- Public read on `usage_reports` is removed; `/evidence/<hash>` is the public surface instead (it serves the stored bytes, not the table).

**Endpoints**
| Route | Auth | Purpose |
| --- | --- | --- |
| `GET /health` | none | liveness |
| `POST /enroll` | none | register chain + leaf key for a wallet |
| `POST /nonce` | none | issue single-use window nonce |
| `POST /submit-report` | **none — signature is the auth** | verify (§4.3), store, return `evidenceHash` |
| `GET /evidence/<hash>` | none | publish canonical bytes + signature + chain + verification summary |
| `GET /reputation/<wallet>` | none | read the PDA + last N settlements |
| `POST /settle` | `x-oracle-secret` | compute verdicts, return codes + both evidence hashes for the client to submit |
| `POST /register-challenge` | `x-oracle-secret` | unchanged |

**New module `attest.ts`** — parses the chain (`@peculiar/x509` + `asn1js`), verifies each signature with its issuer's public key via WebCrypto, decodes OID `1.3.6.1.4.1.11129.2.1.17`, asserts checks 4–8, and exports a pure `verifyAttestation(input): {ok, reason?}` so it is unit-testable without Deno's HTTP layer. `determine_result.ts` keeps its existing matrix and gains the "no valid report ⇒ UNVERIFIED ⇒ loss" path it already implements.

---

## 8. Android changes (`app/src/main/java/com/nopeek/app/`)

| Unit | Responsibility | Depends on |
| --- | --- | --- |
| `data/attest/KeyStoreEnroller` | generate non-exportable EC P-256 key, request attestation, hold alias | Android KeyStore |
| `data/attest/CanonicalPayload` | build the §4.2 bytes, `sha256`, sign, expose `evidenceHash` | nothing |
| `data/attest/EvidenceReporter` | fetch nonce, submit payload+signature+chain, retry/backoff | `CanonicalPayload`, `OracleReportApi` |
| `data/usage/UsageEventCollector` | full-window event set, `eventSetDigest`, `deviceActiveSeconds` | `UsageStatsManager` |
| `data/verify/CommitmentVerifier` **(interface)** | `verify(report, limitSeconds): VerificationStatus`; default `AttestedCommitmentVerifier` | oracle result, not device opinion |
| `data/usage/ReportScheduler` | two triggers for the same one-shot report: a unique `OneTimeWorkRequest` with `initialDelay = windowEnd - now`, **and** an immediate generate-and-submit whenever the app is opened inside the grace period | WorkManager |
| `data/solana/Pda` | add nonce to challenge seeds; derive `Reputation` | existing |
| `data/solana/ProgramInstructions` / `TransactionCompiler` / `SettlementClient` | new args, `resolve_lock`, reputation accounts | existing |
| `data/demo/DemoSeeder`, `data/identity/*` | delete Seeker/MWA mock providers and `SKR_MINT` | — |

**Worker fix (gap #6).** `PeriodicWorkRequest` at 15 minutes cannot report inside a 55–70 second demo window and is throttled by MIUI. The fix is deliberately not "make the scheduler cleverer" — it is **two independent chances to produce the report**, since a late or absent report already costs the user their stake:

1. a unique `OneTimeWorkRequest` per challenge with `initialDelay = windowEnd - now`, **no** `NetworkType.CONNECTED` constraint (queue and retry offline instead), cancelled with the existing `cancelUsageSync`;
2. an eager generate-and-submit on every app foreground while `now <= windowEnd + grace`, idempotent by `evidenceHash`.

This removes the timing fragility instead of tuning it. No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and no always-on foreground service — both would fight MIUI and burn hours.

**MIUI checklist (Day 4, before the end-to-end run).** Usage Access granted; battery saver off; Nopeek excluded from per-app battery optimization; Autostart enabled; lock-screen app allowed. Verified on the Redmi, not assumed.

**UI.** Existing screens stay. One addition: an **Evidence** view on the settled-duel row showing `evidenceHash`, which checks passed, and a "open public evidence" link. `LostScreen`/`WonScreen` gain the fail-closed explanation in plain words: *why* an absent or revoked report cost the stake.

---

## 9. Testing

- **Deno unit tests** for `attest.ts` and `determine_result.ts`: fixture chains — valid, wrong nonce, replayed nonce, non-enrolled key, chain not rooted at Google, `deviceLocked=false`, `verifiedBootState!=VERIFIED`, signature over mutated bytes, digest mismatch, late submission.
- **Contract**: the existing 16 manual cases re-run via Playground `Call`, plus new cases for the nonce-collision fix, evidence hashes recorded, `resolve_lock` moving the pot, `EvidenceZero`, and reputation bumps on both settle paths.
- **Instrumentation (on device)**: KeyStore sign → oracle verify round trip, twice, two devices. JVM unit tests cannot exercise KeyStore; the plan says so rather than faking it.
- **End-to-end gate (Day 4)**: create → join → window → attested reports → settle → Solscan shows the transaction and the stored hashes → `/evidence/<hash>` renders in a browser.
- **Fail-closed gate (Day 4)**: revoke Usage Access mid-window on device B → no valid report → B loses. This is both a test and the demo's best scene.
- **Regression**: USDC always 6 decimals; no raw token units in any new surface.

---

## 10. Schedule — 30 scheduled hours, 6 reserved

36h gross (6 days × 6h). Day 6's 6h is held back for buffer, the final live-verification pass, and submission — **not** for new features. Days 1–5 schedule 30h, and each day sums to exactly 6.

| Day | Hours | Work | Gate / fallback |
| --- | --- | --- | --- |
| 1 | 6 | Faucet deploy wallet to 2–3 devnet SOL (**start today**, rate-limited). **S1**: attestation chain spike on the Redmi. **S2**: X.509 verify in Deno. Program deployed with real ids | S1 fails ⇒ V2 (nonce-bound signature, weaker claim, plan re-cut). S2 fails ⇒ verification moves to a free Node service; protocol unchanged |
| 2 | 6 | `KeyStoreEnroller` + `CanonicalPayload` + sign (2h). `/enroll`, `/nonce`, `attest.ts` (3h). Migration (1h) | |
| 3 | 6 | All program changes (3h). Redeploy + 16 manual cases (1.5h). Kotlin client updates (1.5h) | Nothing downstream starts until a settle tx lands |
| 4 | 6 | Second device installed + enrolled (1h). `ReportScheduler` + MIUI checklist (1.5h). Full two-device duel (2h). Fail-closed duel (0.5h). Reputation PDA + endpoint (1h) | **Hard checkpoint: a duel has settled with an evidence hash on-chain** |
| 5 | 6 | Evidence UI (1.5h). Rename cleanup + clean public repo (1h). Demo rewrite + record (2.5h). Deck + submission draft (1h) | |
| 6 | 6 | Buffer, final live-verification pass, Colosseum draft attack via Copilot, submit | Feature-frozen: fix and record only |

**Cut order if slipping:** Evidence UI → reputation endpoint → `Reputation` PDA → `resolve_lock` replaced by a plain refund. **Never cut:** attestation verification, one live settlement, the demo recording.

**Repo note.** `95de976` records a pending security scrub before the repo goes public. **Create a new clean public repository and push the current tree**; do not scrub history — it is faster and it cannot leak a signing key from an old commit. The submission needs this link.

---

## 11. Demo script (3 minutes, compliance-first)

| Time | Scene | Proof shown |
| --- | --- | --- |
| 0:00–0:20 | Problem + the incumbent contrast: *they hold your card and decide if you cheated* | one sentence, no slides |
| 0:20–0:50 | Duel created on phone A, joined on phone B; USDC into the escrow PDA | wallet balances, escrow ATA on Solscan |
| 0:50–1:20 | Tracked usage across the window; A stays under, B crosses the limit | live in-app counters, both devices in frame |
| 1:20–2:05 | **Money shot:** settle → winner paid → `/evidence/<hash>` open in a browser tab beside the transaction, hash matches | re-verification on camera, in public |
| 2:05–2:35 | **Fail-closed run:** revoke Usage Access, never report, lose automatically | the cheat attempt that every prior clone skipped |
| 2:35–2:55 | `/reputation/<wallet>` — kept, broken, streak, and the account address | wedge D as mechanism, honestly framed |
| 2:55–3:10 | Honest scope: device not person; provenance not truthfulness; iOS behind an Apple entitlement gate; RN migration planned, not pretended | credibility is the deliverable |

Optional, only if Day 5 finishes early: the "hard mode" overlay block as a 10-second teaser labelled roadmap.

---

## 12. Roadmap (specified, not built)

**iOS.** Apple's Screen Time stack (`FamilyControls`, `DeviceActivity`, `ManagedSettings`) can satisfy the same `CommitmentVerifier` contract via `DeviceActivityReport` totals, but it requires a Mac, a paid Developer account, and Apple's per-bundle-identifier approval of the Family Controls (Distribution) entitlement — reported anywhere from a few business days to 4+ weeks, and until granted you cannot even build a dev client. iOS is therefore an architecture claim for now, and the entitlement requests should be filed the day a Mac and account exist.

**React Native.** Migration is a **port, not a rewrite**, because of three seams cut in this spec: `CommitmentVerifier` (domain policy, transport-neutral), `UsageReport`/`CanonicalPayload` as a language-neutral byte format, and no domain logic inside Compose. Target shape: RN + TypeScript product layer; existing Kotlin usage and attestation code wrapped as a native Android module; a Swift module implementing the same interface over Screen Time reports. The oracle and the program are unchanged — they never knew which UI called them.

---

## 13. Submission readiness checklist

- [ ] A duel has settled on devnet, end to end, twice, on two devices, with evidence hashes visible in the transaction
- [ ] `/evidence/<hash>` loads publicly and its hash matches the on-chain value
- [ ] Fail-closed path demonstrated (revoked permission ⇒ loss)
- [ ] Program id, oracle authority, and treasury recorded in `contracts/DEPLOYED_PROGRAM_ID.txt`
- [ ] Clean public repo with no signing secrets; README current with the Nopeek name and this spec linked
- [ ] Demo video uploaded (unlisted), deck PDF, Colosseum draft created
- [ ] Residual limitations (§5) present in the deck, not only in this spec
- [ ] Final Copilot pass over the real submission fields before Oct 12 PT
