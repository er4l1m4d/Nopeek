# S1 findings — hardware attestation on the test device

**Device:** Redmi model `23106RN0DA`, product `gale_global` (Global ROM), Android 15 (`23106RN0DA - 15`)
**Captured:** 2026-10-07, package `com.nopeek.app`, debug build
**Test:** `KeyStoreAttestationTest` run via `am instrument` — **OK (2 tests)**, zero failures

## Verdict: GATE PASSES — hardware-backed attestation confirmed

| Claim | Status | Evidence |
| --- | --- | --- |
| Chain is 4 deep, carries our challenge + exact package | **asserted on device, passing** | `am instrument` OK (2 tests) |
| Chain terminates at **Google's** published root | **PROVEN** | terminal cert byte-identical (sha256 `cedb1cb6dc896ae5…`, 1312 bytes) to the root fetched from `https://android.googleapis.com/attestation/root` — never copied from this chain |
| **`attestationSecurityLevel` = TrustedEnvironment** | **PROVEN** | decoded from `device-attest-ext.der`: ENUMERATED `01`; AOSP `SecurityLevel ::= ENUMERATED { Software(0), TrustedEnvironment(1), StrongBox(2) }` — same table in every schema version incl. Keymaster 4.1, which is what this device emitted |
| `keymasterSecurityLevel` = TrustedEnvironment | **PROVEN** | second ENUMERATED, also `01` |
| `rootOfTrust.deviceLocked = true` | **PROVEN** | tag `[704]` inside **hardwareEnforced**: BOOLEAN `ff` |
| `rootOfTrust.verifiedBootState = Verified` | **PROVEN** | ENUMERATED `00` = Verified (SelfSigned=1, Unverified=2) |
| RootOfTrust is hardware-enforced | **PROVEN** | `[704]` appears in the second (hardware) AuthorizationList, not the software one |

Schema identity: `attestationVersion` INTEGER `04`, `keymasterVersion` INTEGER `41`
(0x29) → **Keymaster 4.1**. `os_version` `[705]` = `0x0249f0` = 150000 → Android 15.0.0.
`attestationApplicationId` `[709]` decodes to a 14-byte `com.nopeek.app`, confirming the
exact-length DER assertion on device.

**Wording now permitted:** "hardware-attested", "TEE-backed", "locked bootloader, verified
boot". **Still not permitted:** "dedicated secure element" (no StrongBox — value 2), and
"the extension bytes are cryptographically verified" — the chain's **signature**
verification (`verifyChainToRoot` walking issuer→subject signatures) is the remaining
part of Task 3. Root *identity* is proven; signature *checking* is not yet implemented.

Method note, because it nearly cost the claim: the decoded value `1` was first read as
`Software` from memory of a different enum table, which would have forced a downgrade of
the headline claim. It was checked against AOSP before being acted on. Any future
security-level interpretation must cite the table, not recall it.

## Root pinning (was ledger Row 9)

`google-attestation-root.pem` was obtained from Google's published root list at
`https://android.googleapis.com/attestation/root` and matched by full DER hash
against the device chain's terminal certificate. Verification script:
`.superpowers/sdd/…/verify-root.mjs`; it exits non-zero on mismatch.

Also available and not yet wired in: Google's revoked batch-attestation-key serial
list at `https://android.googleapis.com/attestation/status`. A leaked attestation key
would otherwise keep producing apparently-valid chains forever, so Task 3/8 should
check the intermediates' serials against it.

## Chain structure (as recorded by the device in `chain-dns.txt`)

| # | Role | Subject | Issuer |
| --- | --- | --- | --- |
| 0 | leaf | `CN=Android Keystore Key` | TEE batch key `138253d1236621268a1262ff9f466576` |
| 1 | intermediate | TEE `138253d1…6576` | TEE `aa3d661ed0862f74108b03a8dd8abb3e` |
| 2 | intermediate | TEE `aa3d661e…bb3e` | `f92009e853b6b045` |
| 3 | root | `f92009e853b6b045` | **self-signed** (subject == issuer) |

Every non-leaf DN carries `2.5.4.12 = TEE`. The chain is 4 deep and terminates at a
self-signed CA whose serial is `f92009e853b6b045`.

**Not yet established, and deliberately not claimed here:** the `TEE` string is a
*Distinguished Name* label, not the authoritative `keyMintSecurityLevel` field, and
the self-signed terminal cert is only trustworthy as Google's root once it is
matched against a copy obtained out of band. Both belong to Task 3 (ledger Row 9),
which decodes `device-attest-ext.der` and pins `google-attestation-root.pem`.
Recording a DN that says "TEE" as if it proved the enforcement level is exactly the
kind of soft claim that got the ~20 prior clones questioned by judges.

## What the on-device test actually asserted, and why each one matters

- Chain length >= 3 — a one-cert self-signed chain would mean no certified attestation key.
- Extension OID `1.3.6.1.4.1.11129.2.1.17` present.
- `attestationChallenge` contains the probe challenge `nopeek-s1-probe`.
- **Package name matched as an exact-length DER OCTET STRING (tag `0x04`, length `14`),
  not as a substring.** A substring match is satisfied by `com.nopeek.app.test`, which
  would let a self-instrumenting test APK masquerade as the app. That weakness was real:
  the first passing run reported green while `com.nopeek.app` was not even installed, and
  could not tell the two packages apart. The strengthened assertion closes it.
- Leaf subject is `CN=Android Keystore Key`.
- The chain's leaf public key equals the keypair the test just generated (compared as an
  EC point, since the keystore handle and the parsed certificate key are different classes).
- Private key material is not extractable — `PrivateKey.getEncoded()` yields no bytes.

## StrongBox

`strongbox.txt`: `TEE-only: Failed to generated key pair.`
This device has no StrongBox secure element; keys are generated in the TEE. That is
sufficient for the design — StrongBox is a bonus, not a requirement — but it means
`isInsideSecureHardware()` style claims should be phrased as *TEE-backed*, never as
"dedicated secure element".

## Files captured for Task 3 and Task 8

| File | Bytes | Used by |
| --- | --- | --- |
| `device-chain.pem` | 4789 | Task 3 chain verification; Task 8 positive fixture |
| `device-attest-ext.der` | 267 | Task 3 decodes security level + `rootOfTrust` |
| `device-spki.b64` | 125 | Task 7 enrollment, Task 8 check 2 |
| `device-cert-digest.b64` | 45 | Identifies **this leaf certificate** — enrollment bookkeeping. **Not** Task 8 check 8: that compares the *APK signing-cert* digest inside `attestationApplicationId`, which must be read out of `device-attest-ext.der` |
| `chain-dns.txt` | 757 | Task 3 root pinning — **now done, see Root pinning above** |
| `google-attestation-root.pem` | ~1.8 KB | Pinned trust anchor for Task 3 `verifyChainToRoot` |

## Reproducing this

The Gradle `connectedDebugAndroidTest` task uninstalls the app afterwards, which wipes
`/data/data/com.nopeek.app/files` and destroys the fixtures. Capture by installing both
APKs and running the instrumentation directly, then pulling before uninstalling:

```
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.nopeek.app.data.attest.KeyStoreAttestationTest \
  com.nopeek.app.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as com.nopeek.app cat files/device-chain.pem > .../fixtures/device-chain.pem
```

MIUI prerequisite: Developer options → **Install via USB** must be enabled, with a Mi
account signed in and a SIM present, or installs fail with
`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`.
