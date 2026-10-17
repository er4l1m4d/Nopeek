# Nopeek — Attestation & Deploy Notes

Running record for the Colosseum build. Every gate result, pinned value, and
device fact that a later task has to trust goes here, because conversation
memory does not survive a long session.

Plan: `docs/superpowers/plans/2026-10-06-nopeek-colosseum-compliance-proof.md`
Spec: `docs/superpowers/specs/2026-10-06-nopeek-colosseum-architecture-design.md`

## Environment verified 2026-10-06

| Item | Value | Note |
| --- | --- | --- |
| Free disk on C: | **13.2 GiB** | Better than the 3–4 GB assumed; Task 1's ≥2.5 GB gate passes untouched |
| Node.js | v26.5.0 | Type-stripping works, but **a directory argument does not** on Windows. Run `node --test "supabase/functions/oracle/*.test.ts"`; the bare-directory form throws MODULE_NOT_FOUND. Verified 2026-10-06 |
| Git Bash | `C:\Program Files\Git\bin\bash.exe` | The sdd scripts are bash and will not run under PowerShell directly |
| Shell | PowerShell — use `;` as separator, never `&&` | |
| Branch | `nopeek-colosseum` cut at **`e030d88`** (= tip of `volo-system-port`); first commit on the branch is `d2a0f20` | |
| Test device A | Redmi `23106RN0DA` (`gale_global`), Android 15, Global ROM with Play Services — **not** a Seeker. Connected and verified working 2026-10-07 | |
| Test device B | second Android, available on loan | |
| **Solana CLI** | **NOT INSTALLED** — and not needed. Path B (Playground) chosen below; Task 1 Steps 4–5 are superseded, Step 6 was done in-place |
| `adb` | Not on `PATH`; absolute path `C:\Users\hp\AppData\Local\Android\Sdk\platform-tools\adb.exe`. Task 2 Step 5's fixture pull must use the full path |
| Unit-test baseline | **141 tests, 0 failures, 0 errors** at `7b014d1`, run with `--rerun` so it is not an up-to-date cache artifact. Every later task compares against this number |

## Deploy wallet

**Path chosen 2026-10-06: B — Solana Playground browser wallet.** No Anza CLI is
installed and none will be. Playground mints its own keypair, airdrops devnet SOL
from its own button, and deploys from the same place, so the wallet and the
deploy stop being two separate human procedures.

Consequence to remember for Task 14: `settle_challenge` requires the
`ORACLE_AUTHORITY` signature, so the Playground keypair must be **exported** and
loaded as a Supabase Edge Function secret. The oracle key therefore lives in a
`.env`-class store, never in git and never in the client. Export it once, into
Supabase, and record that it happened here. `.gitignore` now covers `.env*`,
`*-wallet-info.txt`, `nopeek-oracle.json`, `submission-links.txt` and
`.copilot-tmp/`, because later tasks commit with `git add -A`.

**A devnet wallet already exists** — `6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj`,
created during the earlier Volo plan and left at **0 SOL** because the airdrop was
rate-limited (`volo-plan.md:106`). Task 4 should fund *this* wallet rather than
minting a second one and orphaning the first.

Recorded here when resolved: pubkey, keypair export location (never its
contents), balance, and the date the airdrop landed.

- Oracle pubkey: `6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj` — **funded and imported into
  `beta.solpg.io` on 2026-10-09.** Balance verified at **10 SOL** by a `getBalance` RPC call
  (`https://api.devnet.solana.com`, lamports `10000000000`), not by Playground's own display.
  The keypair is `~/.config/solana/id.json`, outside the repo; its public half was decoded to
  this exact address, so the funded wallet and the wallet we can sign with are the same one.
  **Playground keeps wallets in browser localStorage — clearing browser data deletes the
  wallet, and `id.json` is the only backup.**
- Program id: `DJftv46AvjrbkbUCHR74ntWMXb5r7eHxHo3SXZkox2NM` — **v2 deployed 2026-10-13 as an IN-PLACE UPGRADE; the id did NOT change** (Playground reused the program account and its upgrade authority). Full verify in `contracts/DEPLOYED_PROGRAM_ID.txt`.
- Treasury pubkey: `6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj` — the oracle wallet doubles as TREASURY for the both-fail sweep (deliberate, see lib.rs comment; production must choose a real treasury).
- Devnet balance: **10 SOL**, confirmed on-chain 2026-10-09. Program rent is refundable with
  `solana program close <id>`, so the same balance survives repeated deploy cycles.
- Keypair exported to Supabase as: _tbd_ (Task 14)

## Two things measured instead of remembered (2026-10-09)

Both were about to be "fixed" on the strength of recall, and recall was wrong both times.

- **`program/USDC_MINT.txt` is correct.** `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU`
  returns a real `getTokenSupply` on devnet: 6 decimals, live supply. The address I
  remembered as canonical (`...PgZJ...`, differing at base58 index 35) **does not exist**.
  Task 4 Step 3 funds Phantom accounts with the recorded mint; it needed no correction.
- **`Cargo.toml` needs no `features` line.** `anchor-spl = "0.31.1"` with no explicit
  features was read as "will not compile", but this version ships **11 of 21 features on by
  default, `token` and `associated_token` among them** (docs.rs feature list). Adding a
  features line would have been redundant churn on a file that was already right.

## S1 gate — does the Redmi produce a hardware attestation chain

**RESULT: PROVISIONAL PASS (2026-10-07).** Device `23106RN0DA` / `gale_global` /
Android 15 produced a 4-certificate Android Key Attestation chain for a
non-exportable EC P-256 key the test generated. `am instrument` reported
`OK (2 tests)`.

**Root trust is now PROVEN outright**, not deferred: the chain's terminal certificate
is byte-identical (sha256 `cedb1cb6dc896ae5ec797348bce92867`, 1312 bytes) to the root
Google publishes at `https://android.googleapis.com/attestation/root`. That copy was
fetched independently and compared by full DER hash — never taken from the device's own
chain, which would have proven only that the chain vouches for itself.

**Still unproven, and it gates the headline claim:** `keyMintSecurityLevel` and
`rootOfTrust` (deviceLocked / verifiedBootState). A **software** KeyMint attestation
satisfies every assertion currently made on device — chain depth, challenge embedded,
exact package, leaf-key identity, and `getEncoded()` yielding no bytes all remain true.
Until Task 3 decodes `device-attest-ext.der` and returns `TrustedEnvironment`:

- ✅ safe to say: *"attested chain, rooted at Google, key never exportable"*
- ❌ not yet: *"hardware-attested"*, *"TEE-backed"*, *"verified boot"*

Full detail and reproduce procedure: `supabase/functions/oracle/fixtures/FINDINGS.md`.

| Fact | Value |
| --- | --- |
| Chain depth | 4 (leaf + 2 TEE intermediates + self-signed root) |
| Leaf subject | `CN=Android Keystore Key` |
| Root serial | `f92009e853b6b045` (self-signed) — **matched byte-for-byte to Google's published root** |
| Revoked-serial list | `https://android.googleapis.com/attestation/status` — **now fetched and enforced by the oracle** (see the feed-shape section below: top-level `entries`, mixed decimal/hex serial keys, no issuer field). Our own chain's four serials appear in it under neither reading |
| StrongBox | **absent** — `TEE-only: Failed to generated key pair.` Say *TEE-backed* only if/when the security level is decoded; never "dedicated secure element" |
| `keyMintSecurityLevel` | **not decoded** — Task 3, from `device-attest-ext.der` |
| Boot state / `deviceLocked` | **not decoded** — Task 3 |

Do not let the `TEE` string in the intermediate DNs stand in for the authoritative
`keyMintSecurityLevel` field. It is a name, not proof, and asserting it as proof is the
soft-claim pattern the pre-flight says judges caught in the prior clones.

Two traps found while closing this gate, both now documented in FINDINGS.md:

1. **Gradle uninstalls after `connectedDebugAndroidTest`**, which deletes
   `/data/data/com.nopeek.app/files` and the fixtures with it. Capture by installing both
   APKs and running `am instrument` manually, then pulling.
2. **The first "passing" run was not trustworthy.** `com.nopeek.app` was not installed
   (`INSTALL_FAILED_USER_RESTRICTED` from MIUI), yet 2 tests reported green — and the
   package assertion was a *substring* match, which `com.nopeek.app.test` also satisfies.
   It could not distinguish the app from its own test APK. Now asserted as an exact-length
   DER OCTET STRING, plus leaf-public-key identity. MIUI requires Developer options →
   **Install via USB** (Mi account + SIM) for installs to succeed at all.

## S2 gate — Google attestation root pinning

Per ledger Row 9, `fixtures/google-attestation-root.pem` is pinned **out of band**
from Google's published Android KeyStore attestation CA. Its subject DN and SPKI
fingerprint are recorded here and cross-checked against what the device chain
reports. It is never copied from the device chain — a self-signed chain is equally
self-consistent, and pinning that would certify an attacker-controlled CA.

- Pinned root subject DN: _tbd_
- Pinned root SPKI SHA-256: _tbd_
- Runtime mechanism chosen (`npm:` import / injected parser / sidecar service): _tbd_

## MIUI checklist (Task 13, no server needed)

| Setting | Verified on device |
| --- | --- |
| Usage Access granted | ☑ via `adb shell appops set com.nopeek.app GET_USAGE_STATS allow` (MIUI Settings tap not needed; re-apply after each reinstall) |
| Battery saver off | ☐ |
| Nopeek excluded from per-app battery optimization | ☐ |
| Autostart enabled | ☐ |
| Allowed on lock screen | ☐ |

## Usage reading sanity (Task 12 Step 3)

Known interval vs reported, recorded before anyone trusts a number.

**Device:** Redmi, global ROM, `GMHAFAPRYTHUTGAU`. **Run:** 2026-10-08, `.superpowers/sdd/2026-10-06-nopeek-colosseum-compliance-proof/step3.sh`, results under `NOPEEK_STEP3` in logcat.

| App | Expected foreground | `usedSeconds` | `deviceActiveSeconds` | `eventCount` |
| --- | --- | --- | --- | --- |
| `com.instagram.android` | 95 s wall-clock interval (launch → HOME, driven by adb) | **89** | **92** | 7 |

- 89 s against a 95 s interval is **93.7 %**, and the 6 s deficit is the launch animation:
  `monkey` returns before the activity is resumed, so the phone was honestly not showing
  Instagram for those seconds. The direction is right — it under-reports rather than up.
- `usedSeconds (89) <= deviceActiveSeconds (92)` holds on real data, so the oracle's
  `USAGE_EXCEEDS_ACTIVE` bound is satisfiable by an honest report and not vacuous.
- Digest for that window: `201f31212c593c80ab79b096e0f160eada8c860852e83e6176e0109a27144dd6`.

**Event-type histogram over a real 6 h window** — `eventCount=1398`, `deviceActiveSeconds=9178`
(2.55 h of 6 h active, plausible for a phone in daily use), digest
`66ef3eb1e36d53a5a7627f83e8c9b863fd6688da9275cb3958fda6a7d1162061`:

```
typeHistogram = {1=552, 2=552, 5=16, 7=258, 10=635, 11=461, 12=1364,
                 15=69, 16=69, 17=32, 18=32, 19=191, 20=190, 23=499}
```

This line exists because of a bug, and it is the check that would have caught it.
`UsageEventType` declared foreground/background as `100`/`101`; the platform sends `1`/`2`
(`javap -constants` on android-35). The filter matched nothing, so every window produced an
empty event set, `usedSeconds = 0`, and a report the oracle scored **PASS** — while 184 JVM
tests stayed green, because each of them built its events from those same wrong constants.
Fixed in `1f03bcc`; `UsageEventTypeConstantsTest` now pins both values against the real
framework class, and this histogram is the device-side proof.

- **FG and BG counts are exactly equal (552/552)** — every open has a close. A stream where
  they diverge wildly would mean sessions are being cut off and the numbers are not
  trustworthy.
- **13 other event types are present and committed** (notifications, screen, activity
  stopped). The digest covers the whole stream, not just foreground transitions, so "no
  evidence collected" and "a phone that was genuinely idle" are different bytes.
- `eventCount` (1398, distinct in-window triples) is smaller than the histogram sum, because
  the collector deliberately reads a 6 h lookbehind before `windowStart` for span arithmetic
  while the committed set stays window-scoped. Expected, not data loss.
- Busiest packages in the window: `com.whatsapp.w4b`, `com.snapchat.android`, `android`,
  `org.telegram.messenger`, `com.miui.home`, `com.spotify.music`, `com.twitter.android`.
  **MIUI reports third-party usage honestly** — this is the finding the spec's residual
  limitations section needed, and it is the assumption Task 15's duel rests on.

**Standing limitation found here:** clearing app data or a first-ever run leaves no prior
clock observation, so neither clock-jump direction can be evaluated on that run. It is not
flagged, because flagging would forfeit every honest first report; see the §5 write-up owed
in Task 19/20.

## Contract test results

- 16 original manual cases: **not run** (Task 4). A baseline is required before
  Task 10 changes the program, so that a later failure has something to point at.
- Cases 17–24 (nonce, evidence, lock, reputation): **NOT yet run.** v2 is deployed
  and its IDL is validated, but execution is blocked on the harness, not the program.
  Two hard facts found while trying (both measured, not recalled):
  1. **`beta.solpg.io` as loaded here has NO "Call" tab** (confirmed twice: zero
     occurrences in the DOM). The manual-test-cases.md "run in the Call tab"
     instruction cannot be followed literally. The executable fallback is the test
     sandbox (`tests/anchor.test.ts` + `test`), which is where the v2 suite
     (`contracts/tests/playground-cases-v2.test.ts`) runs from.
  2. **The v2 suite compiles past the runner guard and starts, but dies in the
     `before all` setup on a hand-rolled ATA create**: on-chain
     "Program log: Error: Associated address does not match seed derivation"
     from `ATokenGPvbd…` on `create_associated_token_account`. The account order
     used (`funder, ata, owner, mint, TOKEN, SYSTEM, RENT`) matches the
     in-practice `@solana/spl-token` JS layout, yet the deployed program still
     rejects the derived address — so the fix needs a byte-exact diff against the
     working lib's `createAssociatedTokenAccountInstruction`, NOT a recall guess
     (docs.rs's deprecated Rust helper lists System before Token, the two sources
     disagree on indices 4/5). This is the one open bug before cases 17–24 can run.
  No case has passed; the earlier "0 passing / before-all failing" runs are NOT a
  baseline. Wallet spent to date on the deploy + failed setup attempts: ~0.24 SOL
  (deploy) + ~0.005 SOL (partial setups); the failing ATA creates were only
  simulated so cost fees only.

## Deviations from the plan

- **Task 1 Step 1:** staged with `git add -u` plus the pre-flight doc instead of
  `git add -A`. `-A` would have swept `.copilot-tmp/`, `nopeek-film/`, and a
  reference PDF into the branch — scratch and assets whose inclusion is a
  Task 19 public-repo decision, not a Day-1 accident.
- **Task 1 Step 1:** split into two commits — `2953fa6` for the design-system and
  screen work, `e030d88` for the pre-flight doc and the plan's ruling edits.
- **Task 1 Step 3:** `app/build`, `.gradle` and `.kotlin/sessions` **not** deleted.
  The gate was "≥2.5 GB free" and there is 13.2 GiB; clearing them only forces
  Gradle to re-resolve and re-configure. Revisit if disk drops below 3 GB.

## Playground test sandbox - measured constraints (2026-10-11)
Probed by replacing tests/anchor.test.ts with a logging-only script (sends no transactions; balance unchanged at 8.37785684 SOL). Anything written to run in Playground must obey this:
- Globals available: pg, web3, anchor, BN, assert, Buffer, borsh, BufferLayout, sleep, plus mocha (describe/it/before/after/beforeEach/afterEach/context/specify).
- NO token library. A dynamic import() aborts the ENTIRE file at transform time, so mint/ATA/transfer instructions must be hand-laid-out from web3 + Buffer.
- eval and the Function constructor are unavailable. The bare identifiers for the browser object and the global object are rejected even inside comments and string literals.
- pg.provider does not exist. Use pg.connection and pg.wallet only.
- Every test file must define describe(), or the runner refuses it and says to use the run command instead.
- Terminal shows about 20 rows; long console output scrolls away, so keep log lines short.
- **The runner guard is a case-INSENSITIVE SUBSTRING scan over the WHOLE source,
  comments included**, banning tokens `window|top|eval|global|document|navigator|localStorage`
  (measured: a comment word "documented" and a variable `detOpp` (contains "top") each
  aborted the ENTIRE file before mocha ran, 0 SOL). Any test file must avoid these
  substrings EVERYWHERE, including prose and identifiers. Also: `BN.toArrayLike` must
  use the 3-arg `toArrayLike(Buffer,"le",8)` form — the 2-arg form threw a minified
  "e is not a constructor" inside `before all` here.
- Confirmed live: pg.program.programId = DJftv46AvjrbkbUCHR74ntWMXb5r7eHxHo3SXZkox2NM, methods = createChallenge, joinChallenge, settleChallenge, refundUnjoined, web3.Keypair and web3.PublicKey.findProgramAddress both present.

## Oracle deployed - and what that does NOT yet mean (2026-10-11)
Deployed with: npx supabase functions deploy oracle --project-ref ybcavetnzivwsfnwzvtd --no-verify-jwt. Live: GET /functions/v1/oracle/health returns 200 {status:alive}. Login works via the OS keyring, not ~/.supabase/config.yml, so a missing config.yml does NOT mean not-logged-in - check with projects list.
The CLI uploaded only index.ts and determine_result.ts because it follows the import graph, and index.ts imports nothing else. CONSEQUENCE: checks.ts, x509.ts and evidence.ts are not in the deployed artifact. The live oracle settles on the device's self-reported usedSeconds with NO attestation verification - the legacy behaviour. Task 14 is the switch that makes the fourteen checks real; until it is done and redeployed, nothing in this section is load-bearing in production.
Also true and worth stating: --no-verify-jwt is correct for the final design (the attestation chain is the authentication), but today it exposes an unauthenticated endpoint that has no attestation behind it. Devnet and fake USDC only. Do not point anything real at it before Task 14.
Docker is not running on this machine; the CLI used its hosted upload path, so there is no local container verification of the bundle.

## SUPERSEDED the section above: the verifier is live, and two runtime bugs were found on the way (2026-10-11, later the same day)

`index.ts` now imports `checks.ts`, so the deploy uploads five files - `index.ts, x509.ts, checks.ts, evidence.ts, determine_result.ts` - and the live endpoint enforces attestation. Measured against the deployed function, not inferred:

| probe | response |
| --- | --- |
| legacy body, no evidence | `400 EVIDENCE_MISSING` (the fail-open hole is shut) |
| forged chain | `400 CHAIN_NOT_ROOTED` |
| genuine Redmi chain + unsigned payload | `400 PAYLOAD_UNPARSEABLE` |
| genuine Redmi chain + real canonical payload | `400 NONCE_MISMATCH - "attested challenge is not the payload nonce"` |

The last row is the one that matters: checks 1-5 - chain rooted to the pinned Google root, revocation, attestation readable, hardware-backed, payload decoded - all pass **in Deno, on real device data**. What remains before a report can settle is enrollment (Task 7) and a signature only the device's keystore can make (Task 13).

### Bug 1: Deno's WebCrypto cannot verify P-384

`crypto.subtle.verify` for P-384 throws `DOMException: Not implemented` on Supabase edge runtime 1.77.0 (Deno 2.1.4). Node 26 verifies the same bytes. Google's attestation intermediate holds a P-384 key and signs the batch certificate below it, so the deployed oracle rejected every genuine chain at `CHAIN_NOT_ROOTED` while all 12 x509 tests passed locally.

Found by bisecting per link through a temporary `/diag` route (since removed) rather than guessing. The presented chain is four certificates - leaf (serial `01`), batch key (P-256), attestation intermediate (P-384), pinned root (RSA-4096) - so the three links the loop verifies are leaf←batch under the batch key (ECDSA P-256), batch←intermediate under the intermediate's key (ECDSA **P-384**), and intermediate←root under the root's key (RSA-SHA256). Link 1 was the one that threw. The root's own self-signature is never checked, by design: it is trusted by byte-identical identity, and a self-signature proves nothing about who issued it. Fix: `x509.ts` takes an injectable `EcdsaVerifier` (default WebCrypto, so behaviour is unchanged everywhere else) and `index.ts` injects `@noble/curves` 2.4.0. All ECDSA links go through the injected path - not only P-384 - so there is one implementation and no host-dependent behaviour left to surprise us. The RSA link stays on the host and demonstrably works: `NONCE_MISMATCH` is only reachable if every earlier check, that one included, returned true.

Two traps in that fix, both avoided by reading the installed package rather than recalling it:
- `@noble/curves` 2.4.0 has **no `ecdsa.js` or `p384` subpath**; the curves are exported from `nist.js`. The v1-style specifier would have failed at deploy.
- `ECDSAVerifyOpts` has **no `hash` option**, and `prehash` defaults to true, meaning noble hashes with the *curve's* hash. The certificate dictates the hash via its own signatureAlgorithm OID, so the digest is computed here and passed with `prehash: false`. `lowS: false` is set because noble's default rejects high-S signatures, which a CA certificate may legitimately carry.

### Bug 2: Google's revocation feed is not the shape the code guessed

The code read `body.serials`. The live feed (`android.googleapis.com/attestation/status`, captured in `supabase/functions/oracle/fixtures/revoked-feed-shape.json`) is:
- top-level key **`entries`**, not `serials`;
- keys are serial numbers in a **mix of bases**: 979 of 1759 are 17-20 decimal digits, 780 are 30-32 hex characters containing `a-f`;
- values are `{ status, reason }` with **no issuer field at all**.

Each of those was a problem:
1. The wrong key name made `revokedEntries()` return `null`, which failed closed as `CHAIN_REVOKED` for every report, genuine or forged. Fail-closed is the right direction, but it meant nothing could ever be accepted.
2. The old matcher required issuer AND serial. Since the feed publishes no issuer, that comparison could never succeed - **revocation would have been silently disabled for all 1759 leaked keys**. This is the fail-open that the strict-looking rule was hiding, and it is the more dangerous of the two bugs because it would have looked correct: the requirement was added in review precisely to stop false revocations, and satisfying it against this feed was impossible.
3. Reading the feed in one base only would leave the other group permanently un-compared, also fail-open.

The fix keeps the *protection* the issuer rule was for and drops the impossible half: the device leaf (serial `01`, one of the most common serials in existence) is excluded from comparison because the feed lists CA and batch keys, while a supplied issuer is still honoured when a caller has one. Serials are compared as numbers under whichever readings the key admits. Parsing lives in `x509.ts` as `parseRevokedFeed` so it is tested against the captured real slice, and it returns `null` for an unrecognised envelope versus `[]` for a recognised-but-empty one - the distinction that turns an outage into a refusal instead of a pass.

Verified two ways, because the fixture is only a four-entry sample of a 1759-entry feed and cannot prove absence on its own: the live feed was scanned for all four serials under both base readings (none present), and the deployed oracle - which fetches the real feed - passes the revocation check for this chain on every probe, failing later at `PAYLOAD_UNPARSEABLE` and `NONCE_MISMATCH` instead.

The 979/780 base split and the 1759 total were counted by an ad-hoc script, not by the test suite; the fixture records them in `_capturedCounts` so the numbers are at least attributable to a capture rather than to memory.

## Two unauthenticated writes the verifier trusts - decided, sequenced, NOT forgotten (2026-10-11)

Both /enroll and /register-challenge are public writes into tables the fourteen checks read as authoritative. Consequences, stated plainly:
- /enroll: anyone holding the victim's (public) wallet address can enroll THEIR OWN genuine attested chain under it. The victim's honest report then fails check 7 (enrolled != leaf), fail-closed turns that into UNVERIFIED, and the victim loses the pot. Zero cost to the attacker, no key stolen. This is a fund-loss path, not griefing - I first called it a write-up item and that was wrong.
- /register-challenge: its row dictates target_app / limit_seconds / window_seconds, i.e. the terms every report is measured against. Pre-registering a duel's PDA address with the wrong limit is the same class of attack.
- /nonce and /settle are NOT in that class: flooding nonces costs only storage, and reading verdicts early still runs into the on-chain isSettled / oracle-authority / lock-window guards.

Fix: each write carries an ed25519 signature from the wallet it is about, over 'nopeek-enroll/v1' || walletPubkey || spkiB64 || certDigestB64. The oracle verifies with ed25519 from @noble/curves 2.4.0 - already installed and pinned, measured to export ed25519.verify/sign/getPublicKey from ./ed25519.js, so no new dependency. A leaked enrollment signature is harmless (it can only re-enroll the same key under the same wallet), so enrollment needs no nonce or expiry. x-oracle-secret then gets DELETED rather than enforced: setting it would mean shipping the secret in the APK, and the APK and keystore go into a public repo.

Why not built in that session: ~12h of capacity against a 25h+ task list, and the binding constraint is one duel that actually settles. It is also sequenced rather than merely postponed - a Solana transaction signer IS an ed25519 wallet signer, so this becomes ~20 lines the moment Task 11's signer exists, instead of a separate crypto path today. Trigger to revisit: the first time any code in this repo can sign with a wallet key.

Must be measured, not recalled, before Task 11 is designed: whether the Redmi's platform exposes Ed25519 through JCA/Conscrypt at this app's minSdk. If it does not, the tx signer needs a pure-Kotlin ed25519 dependency, which cuts against this project's no-unaudited-crypto convention and is a decision to make out loud.

## Deploy must be verified by RPC, never by the Playground UI (2026-10-14)

A re-Build + re-Deploy attempt reported "Upgraded program DJftv..." with tx
`4EVUes35...` — **the exact same signature as the 2026-10-13 upgrade**. A new upgrade
cannot reuse a signature, so the UI was replaying a cached result. RPC proof that
nothing was deployed: `ProgramData 77RtiTVT...` still reports `lastDeploySlot =
510566526` (unchanged) and `getSignaturesForAddress(DJftv...)` shows no new
transaction. Rule going forward: after any Deploy, re-read `lastDeploySlot` from
ProgramData over RPC and require it to change. Never accept the button text.

Cost of that non-deploy: wallet **8.12518044 → 6.267061 SOL** (~1.86 devnet SOL).
ProgramData rent is unchanged at 1.85674508 SOL, so the missing SOL is sitting in a
**deploy buffer that the interrupted flow never closed** (the old buffer
`9UoL3bZp...` is gone; a newer one holds the rent and its keypair was generated
inside solpg's flow). It is devnet SOL and the faucet is rate-limited today, but it
is recoverable later — not real money. Do not repeat the mistake.

## Open blocker: on-chain challenge PDA does not match lib.rs seeds (2026-10-14)

Cases 17–24 still have NOT passed. The v2 sandbox harness is now correct in every
part it has reached (5 deterministic bugs fixed, see below), and it gets all the way
to the first on-chain instruction, where **every** `createChallenge` is rejected by
the program with `AnchorError ... account: challenge, Error Code: ConstraintSeeds,
Error Number: 2006`.

Measured, not theorised — a ZERO-TRANSACTION probe (0 SOL, no faucet: pure
`findProgramAddress`, no tx at all) recomputed 60 candidate PDAs for the exact
verified inputs (`creator = 6k9TDQtq...`, `opponent = 79W1jLVvdkBm3wPu5UsDf5eGU6EUBg6p2WxRFE9xGk8e`
deterministic via `fromSeed`, nonces = hour buckets 497756/497757 + the suite's
0/20/30/40/61/62) across 6 seed layouts (`challenge|creator|opponent|nonce_le4`,
no-nonce, `nonce_be4`, swapped creator/opponent, `nonce_le8`, nonce-before-opponent):

- One address in the program's error pair equals layout **1** =
  `seeds = [b"challenge", creator, opponent, nonce.to_le_bytes()]` — which is exactly
  what `contracts/program/src/lib.rs` declares and exactly what the client provides.
- The other address (`HsD9XjBwRX7Nk1dwcbVjAZUATcxk7oGVfw2hXctAF9my`) matches **none**
  of the 60 candidates.
  > **RESOLVED 2026-10-17** — the missing candidate was `u32le(5_000_000)`: the low
  > 4 bytes of `stake_amount`, which a one-arg `#[instruction(nonce: u32)]` attr made
  > the program borsh-decode as `nonce`. `HsD9XjBw…` == derive(creator, opponent,
  > `u32le(5_000_000)`) exactly. See `volo-errors.md` → "RESOLVED — root cause" and
  > the "RESOLUTION 2026-10-17" section at the end of this file.

So the program's seed expectation cannot be produced from the current source with
these inputs. Two live hypotheses, and they must be separated before trusting any
test result — because Task 11 (the app's own PDA derivation) depends on which is true:

1. The deployed binary is not this `lib.rs` (the 2026-10-13 upgrade may itself have
   shipped a cached/intermediate artifact — the UI cannot be trusted, per above).
   Decisive test: a Deploy whose `lastDeploySlot` provably changes, then re-run case 17.
2. The browser's cached IDL mis-encodes the `nonce` argument (the v1 IDL had no
   `nonce` arg), so the value the program puts in the seed is not what the client
   used to derive the PDA.

UPDATE 2026-10-14, after two more measurements — and a correction of my own reasoning:

- **Client auto-derivation is ruled out.** The sandbox client IDL declares NO seeds
  (`IDLSEEDS none declared in client IDL`), so omitting `challenge` fails with
  `Invalid arguments: challenge not provided.` The override is the only path, and the
  chain rejects it. Confirmed Left = the account we provide (case17 printed
  `JS PDAs nonce0=41nUw9XG...` and the error logged `Left: 41nUw9XG...`).
- **A 2,912-layout zero-SOL sweep (5,824 derivation calls, 0 transactions) found NO
  match** for the program's expected address across 13 seed prefixes x 7 orderings x
  8 nonce encodings (le4/be4/le8/be8/u8/decimal-string/BN-32/none) x nonces
  {497756, 497757, 0, 1}, for both `find_program_address` and no-bump
  `create_program_address`. So the expectation is not a re-encoding of these inputs;
  the missing axis is a different *seed source* (mint, an ATA, a hash, an extra seed),
  or the binary was built from a different revision.
- **My "redeploy cannot help, the bytecode is v2" conclusion was WRONG and should be
  reversed.** Finding v2-only strings (reputation seed, EvidenceZero message) in
  ProgramData proves the binary is *some* v2 build — it does NOT prove it is *this*
  v2 build. An intermediate v2 draft would carry the same strings with different
  seeds. So a verified redeploy (require `lastDeploySlot` to change over RPC) is back
  on the table as the decisive test, not a wasted ~0.24 SOL.
- Local hand-rolled `find_program_address` (node crypto + BigInt ed25519) FAILED its
  own self-check (2-3 of 6 known sandbox values reproduced), so never trust PDA
  numbers computed by it — run sweeps in the sandbox where web3's derivation is known
  good. Useful constant: the deterministic opponent pubkey is
  `79W1jLVvdkBm3wPu5UsDf5eGU6EUBg6p2WxRFE9xGk8e`.
- Repo history for the program is shallow: `b1a3b0a` is the only v2 commit of
  `contracts/program/src/lib.rs` (Task 10 steps 1-2), preceded by `e1997d6` (first
  ever successful compile, v1). Nothing after Oct 13 edited it.

Harness bugs already fixed and proven on-chain (keep them, do not re-derive):
runner guard is a case-insensitive substring scan over the whole source **including
comments** (`window|top|eval|global|document|navigator|localStorage` — "documented"
and `detOpp` each killed a whole file); hand-rolled ATA create account order is
`funder, ata, owner, mint, SYSTEM(4), TOKEN(5)`, no rent account, data `[0]`;
`getTokenAccountBalance` has no `uiTokenAmount` (use `uiAmountString`);
`Keypair.fromSecretKey` needs 64 bytes — a 32-byte seed needs `fromSeed`;
`BN.toArrayLike` needs the 3-arg form; and the v2 raw byte offsets
(nonce@175, hashes@179/211, settledAt@243) are only valid when `target_app` is
**exactly 32 bytes**, because Anchor's `String` is variable-length.

## Device signature encoding - measured on the Redmi, 2026-10-12 (Task 7 Step 6)

Task 7's on-device suite passes 3/3 on 23106RN0DA / API 35. It also produced the measurement that matters most for Task 13: logging NOPEEK_SIGN_SHAPE gave len=72 firstByte=0x30 der=true rawP1363=false, i.e. **AndroidKeyStore signs with SHA256withECDSA and emits DER**, while the oracle's erifyEcdsaSignature() feeds the signature directly to crypto.subtle.verify, which only accepts fixed-width raw r||s, under a comment asserting the device produces raw. That assertion is wrong. Left unfixed, every genuine signature is rejected BAD_SIGNATURE and all oracle tests stay green. Decision: the oracle keeps one pinned encoding (raw P-1363) and the device converts DER->raw, left-padding r and s to 32 bytes; plus an oracle test that a DER-shaped signature is refused, so neither side can drift. Task 13 implements the conversion.

## Task 10 Step 4 outcome 2026-10-15: the deployed program does NOT implement the nonce PDA

Differential probe (2 setup txs, ~0.003 SOL; every createChallenge refused in
preflight so it cost nothing) against the program deployed TODAY
(`lastDeploySlot` moved 510566526 -> 511209142, verified over RPC, id unchanged):

| inputs | our PDA (lib.rs rule) | program's expected |
| --- | --- | --- |
| creator=6k9TDQ, oppA=3RFMtc, nonce=0 | `EGh1eV1upcsPSbkAqDXKbL1ExeQcpnu2ozY2GDwGQfqV` | `3MtdtiAnbtsSXZ6P4Rxs5FoQYPU7QNKboDHvxrNX2rdE` |
| creator=6k9TDQ, oppA=3RFMtc, nonce=1 | `DAfyraxckGuCTN4uqhbwndYBvvcbYwkJZei9eN2NCuv6` | `3MtdtiAnbtsSXZ6P4Rxs5FoQYPU7QNKboDHvxrNX2rdE` |
| creator=6k9TDQ, oppB=79W1jLVv, nonce=0 | `FUPLQSLuAVWh8WnhyGfHBqqGC6uuhABjifQRt72mmxcy` | `HsD9XjBwRX7Nk1dwcbVjAZUATcxk7oGVfw2hXctAF9my` |

Measured behaviour of the on-chain derivation:

- **`nonce` is NOT a seed** - nonce 0 and nonce 1 demand the identical address.
- **`opponent` IS a seed.** **`mint` is NOT** (two runs, different mints, same pair,
  same demanded address).
- Our address never equals theirs.
  > **RESOLVED 2026-10-17** — both sides were right and wrong: the program's expected
  > column is `derive(creator, opponent, u32le(5_000_000))` (spot-verified: 79W1jLVv →
  > `HsD9XjBw…` — the table's third row — plus F6DDz9 → `ETxJHWTx…` and 3cDebc →
  > `9Z1qp9iV…` on other probes), i.e. the seeds rule always was
  > `[b"challenge", creator, opponent, u32le(nonce)]` with `nonce` mis-read from the
  > payload prefix. The stale-Playground-artifact hypothesis in item 3 below is
  > RETIRED: the binary WAS v2; a one-line attr fix and a redeploy made every case line
  > up (G2 OK, G3 → 2006, case 17 PASS).

Consequences, stated plainly:

1. **Case 17 (the headline: one pair duelling twice) cannot pass on this deployment**,
   because re-duelability depends on the nonce being part of the PDA. This is a
   program/deployment fact, not a test-harness defect - the harness now reaches the
   on-chain seeds check with real funded inputs.
2. The demanded rule is also NOT v1's `[CHALLENGE_SEED, creator, opponent]`: that
   derives `BFQmZQeb76WzyuZjmoakA1DvWZqZp6HM2rH7C7fTZwwW` for this pair, which the
   program did not ask for either. `git show e1997d6` confirms v1's seeds, and a repo-wide
   grep finds only ONE `.rs` containing `seeds = [CHALLENGE_SEED, ...]` - the current
   file - so no alternative draft on disk explains it.
3. Leading hypothesis, not yet proven: **Playground served a stale/cached build
   artifact.** Today's "Build" finished in 7.69s, which is far too fast for an Anchor
   compile (earlier real builds printed LLVM work and took minutes), and the binary
   DOES contain v2-only strings (`reputation`, EvidenceZero text). So the artifact is
   some v2 revision - plausibly an *intermediate* one from the earlier
   compile-fix iterations, whose seeds lacked the nonce, rather than the current file.
   A cache-busting edit (cosmetic comment in lib.rs) plus a real recompile is the
   cheapest way to test this.
4. Search space already eliminated (all zero-SOL, in-sandbox using web3's own
   derivation, because a local node/BigInt re-implementation FAILED its own self-check
   against 6 known values and cannot be trusted): ~2,912 and 660 layout combinations
   across 11-13 seed prefixes x 6-8 nonce encodings x 7-10 orderings including
   mint/creatorAta as extra seeds, both `findProgramAddress` and no-bump
   `createProgramAddress`.

**Resolved 2026-10-17.** The demanded rule was `derive(creator, opponent,
u32le(stake_amount))` — a one-arg `#[instruction(nonce: u32)]` made `nonce` decode
from the payload prefix (low 4 bytes of `stake_amount` = 5,000,000). The attr fix +
redeploy means the source rule now holds live; "v2 deployed" is true going forward
(see "RESOLUTION 2026-10-17" at the end of this file). The caution above was correct
while it stood.

## A comment-only cache-bust cannot change a deployed program (2026-10-16)

Chosen action: append two comment lines to `src/lib.rs` to force Playground to
recompile, testing the theory that the earlier 7.69s "Build" served a cached
artifact. Two builds then ran in ~5-6s with no retrievable compile output (the
terminal is canvas-rendered and the build rides a pre-existing WebSocket, so the
absence of compile text can only be inferred from timing, not proven). The deploy
did reach chain - `lastDeploySlot` advanced 511632821 -> 511671780, verified over
RPC - but that proves only that the upgrade TRANSACTION executed.

The flaw was in the plan, not the tool: **rustc strips comments, so a comment-only
edit produces a byte-identical ELF even when the compiler genuinely runs.** A
comment can never bust a deployed binary's cache. Answering "is the on-chain
program built from this source?" needs either a *behavioural* change whose effect is
observable (alter a seed and watch the demanded PDA move), or a real local
toolchain - and this machine has neither cargo nor anchor, by the disk-space and
CLI decisions recorded earlier in this file.

The marker was added and then reverted: `git status` is clean for
`contracts/program/src/lib.rs`, so the contract file is untouched.

Related drift, and its explanation: this deploy started from `lastDeploySlot` 511632821,
not the 511209142 the previous session recorded. That was NOT a third party - RPC
tx inspection shows slot 511632821 (2026-10-16T07:17:44Z) and slot 511671780
(09:52:33Z) were both fee-paid by the same oracle wallet and both belong to this
thread's cache-bust attempts: the earlier one succeeded on chain even though its
agent report died on a harness `Signature invalid` error. Lesson: a lost or errored
agent reply does NOT mean the action never happened - re-read chain state before
concluding anything, and never assume a missing report means a missing deploy.

## The browser IDE cannot be trusted to compile the duel PDA seeds (2026-10-16)

Decisive experiment, cheap and self-correcting. Instead of interpreting the two
addresses inside a `ConstraintSeeds` error any further, the seed *literal* was changed
in source (`b"challenge"` -> `b"challeng2"`), rebuilt, redeployed to the same id, and
the deployed `ProgramData` was decoded from its bytes.

Results, all measured:

1. **The build is real.** The uploaded bytecode contained a standalone `challeng2`
   rodata blob and no longer contained the standalone `challenge` seed. A cached
   artifact was impossible. The "5-7s build means cache" heuristic I relied on is
   **wrong** - Playground never forwards rustc stdout to the terminal, so there is no
   compile output to observe, and the seed is the same length, so size is blind too.
   Only *content* discriminates. This kills the stale-cache theory that the previous
   session had authorised a new-project spend against.
2. **Therefore the failure is in the source-to-binary path, not the artifact.** The
   acceptance test then tried six structurally different PDAs against the running
   `challeng2` program - `[challeng2, creator, opponent, nonce_le4]` (what the source
   literally declares), the same without the nonce, and variants substituting
   limit/stake/window for the nonce - and **none was accepted**; every rejection named
   account `challenge`. So a binary compiled from this source does not accept the PDA
   that this source's `seeds` attribute specifies.
3. Combined with the differential probe (demanded address is invariant to `nonce` and
   to `mint`, sensitive to `opponent`), the deployed derivation honours neither the v2
   nor the v1 `seeds` line in this repository. ~4,100 offline candidate layouts
   (zero-SOL, pure derivation) reproduce none of it.

Why this matters beyond a failing test: Task 11's Kotlin client must derive the same
PDA, so whichever rule the compiler actually emitted is what the app has to match -
and it cannot be read off `lib.rs`. **Do not start Task 11 against this toolchain.**

The remaining step that can actually settle it is a real local `anchor build`
(`cargo`/`rustc` are present at `C:\Users\hp\.cargo\bin`; `anchor` and the Solana CLI
are not; C: free was 5.7 GiB at the time of writing, which is tighter than the 13.2
GiB recorded earlier, so re-measure before committing to it). A local build gives a
comparable artifact and a trustworthy deploy loop; nothing else measured so far can.

Operational notes, each learned the hard way:

- Do NOT issue your own RPC calls while a Playground deploy is uploading. Concurrent
  fetches on the same socket pool stalled the deploy ~8 minutes at "202 transactions
  not confirmed". Verify only after "Deployment successful".
- `wallet.automaticAirdrop` is `true` in this project's localStorage, so every Deploy
  first attempts a 5 SOL airdrop, which fails with `Internal error` (faucet quota) and
  is harmless. Disable it with `setting set wallet.automaticAirdrop false` to stop the
  noise.
- A lost or cancelled agent reply is not evidence that nothing happened - two deploys
  in this thread landed on chain while their reports were never delivered. Re-read
  `lastDeploySlot` before concluding.

State after the experiment: corrupted seed reverted, `lastDeploySlot` 511856415
(tx `4P5AD1baevPzexUB...`), deployed bytecode re-confirmed to contain the standalone
`challenge` seed again, program id unchanged, wallet 6.24171456 SOL.

## RESOLUTION 2026-10-17 — attr bug fixed, program live, case 17 green

Root cause (3-way proof, see `volo-errors.md`): `CreateChallenge`'s one-arg
`#[instruction(nonce: u32)]` made Anchor borsh-decode `nonce` from the instruction-data
prefix — the low 4 bytes of `u64 stake_amount` = 5,000,000 — so every create demanded
`derive(creator, opponent, u32le(5_000_000))`, invariant to the client's nonce. The
historical demands in this file re-derive exactly: `ETxJHWTx…` (F6DDz9), `HsD9XjBw…`
(79W1jLVv), `9Z1qp9iV…` (3cDebc).

Fix: full ordered attr `#[instruction(stake_amount: u64, target_app: String,
limit_seconds: u32, window_seconds: u32, nonce: u32)]` — wire format unchanged.
Rebuilt locally (cargo-build-sbf via the MSVC shim; 291,000 B, sha256 `CE8ACB41…`,
was 291,080 B `7074F9D6…`) and redeployed: `lastDeploySlot 511856415 → 511960495`
(tx `5S55FR1er1me6…`), buffer closed, on-chain ELF sha256 byte-verified from a
`solana program dump`.

Verification ladder (all green): local full probe; devnet full probe; devnet grad
(G2 canonical OK — the exact old ConstraintSeeds case; G3 `ETxJHWTx…` demand flips to
2006; G4/G5 OK; G0/G7/G6 still 3012-first, evaluation order unchanged);
`contracts/tests/playground-case17.test.ts` on devnet PASS (create nonce 0 accepted,
duplicate refused, nonce 1 accepted, both accounts Created with nonces 0/1).

App wiring fixed (was silently broken): both IDLs carried `"address": "1111…"` and the
create builder marked the challenge PDA `isSigner = true` (a v1 leftover — v2's PDA
signs via `invoke_signed`). IDLs rewritten to the v2 playground shape with the deployed
id (byte-identical, sha256 `FF98FD0D…`), builder fixed, tests pin the address and the
non-signer flag; app unit suite 240/240.

Rollback path: the pre-fix ELF is kept at `.copilot-tmp/onchain-backup/old-deployed.so`
on the authoring machine; redeploy with the same command form (same program id, same
upgrade authority) if the attr fix ever needs reverting.

Provenance note: `contracts/program/idl.json` was hand-synced from the v2 Playground
export (`idl-from-playground.json`, camelCase old-format) into the new-format shape the
app parser expects; an `anchor build` regeneration remains the recommended provenance
step once CI/toolchain access allows (CI is billing-locked).
