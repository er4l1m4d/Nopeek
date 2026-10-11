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
- Program id: _tbd_ (see `contracts/DEPLOYED_PROGRAM_ID.txt`)
- Treasury pubkey: _tbd_ (Task 10)
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
| Revoked-serial list | `https://android.googleapis.com/attestation/status` — available, **not yet checked**. A leaked batch attestation key would keep producing valid-looking chains forever |
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
- Cases 17–24 (nonce, evidence, lock, reputation): **not run** (Task 10).

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

`crypto.subtle.verify` for P-384 throws `DOMException: Not implemented` on Supabase edge runtime 1.77.0 (Deno 2.1.4). Node 26 verifies the same bytes. Google signs the attestation intermediate with a P-384 key, so the deployed oracle rejected every genuine chain at `CHAIN_NOT_ROOTED` while all 12 x509 tests passed locally.

Found by bisecting per link through a temporary `/diag` route (since removed) rather than guessing: link 0 (P-256) verified, link 1 (P-384) threw, link 2 is the root's RSA self-signature. Fix: `x509.ts` takes an injectable `EcdsaVerifier` (default WebCrypto, so behaviour is unchanged everywhere else) and `index.ts` injects `@noble/curves` 2.4.0. All ECDSA links go through the injected path - not only P-384 - so there is one implementation and no host-dependent behaviour left to surprise us.

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

Verified: none of this chain's four serials appear in the live feed under either reading, so the device is genuinely not revoked.
