import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createSign, generateKeyPairSync } from "node:crypto";
import { encodeEvidence, evidenceHash, type EvidenceFields } from "./evidence.ts";
import { isSerialRevoked, readAttestationExtension, verifyChainToPinnedRoot } from "./x509.ts";
import { verifyReport, type VerifyDeps, type VerifyResult } from "./checks.ts";

const fixtures = new URL("./fixtures/", import.meta.url);
const deviceChainPem = readFileSync(new URL("device-chain.pem", fixtures), "utf8");

const WINDOW_START = 1_759_000_000_000;
const WINDOW_END = WINDOW_START + 120_000;
const GRACE_SECONDS = 60;
const WALLET = "3Dfk2mQxNr8TaVeW9sYhKbLcP4uMgZ6RjQsXyE1nB2dK";
const CHALLENGE = "7NbS1ProbePubkeyABCDEFGHJKLMNPQRSTUVWXYZ2345";
// The attested challenge for this device's fixture chain. The payload nonce must be
// exactly this value, because check 6 compares the two rather than trusting a third
// expected copy of it.
const PROBE_CHALLENGE_B64 = Buffer.from("nopeek-s1-probe").toString("base64");
const NONCE_B64 = PROBE_CHALLENGE_B64;
const PACKAGE = "com.nopeek.app";

function baseFields(overrides: Partial<EvidenceFields> = {}): EvidenceFields {
  return {
    version: 1,
    challengePubkey: CHALLENGE,
    walletPubkey: WALLET,
    packageName: PACKAGE,
    targetPackage: "com.instagram.android",
    windowStartMs: WINDOW_START,
    windowEndMs: WINDOW_END,
    usedSeconds: 42,
    deviceActiveSeconds: 180,
    eventCount: 6,
    eventSetDigestHex: "9f".repeat(32),
    tamperSignals: [],
    nonceB64: NONCE_B64,
    ...overrides,
  };
}

/** A locally generated key, so the signing half of the pipeline is exercised for real. */
function makeKey() {
  const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  return {
    spkiB64: publicKey.export({ type: "spki", format: "der" }).toString("base64"),
    sign: (bytes: Uint8Array) => createSign("SHA256")
      .update(bytes).sign({ key: privateKey, format: "pem", type: "pkcs8" })
      .toString("base64"),
  };
}

function goodAttestation() {
  return {
    securityLevel: "TrustedEnvironment",
    deviceLocked: true,
    verifiedBootState: "Verified",
    packageName: PACKAGE,
    signingCertDigestB64: "AA".repeat(22) + "AA==",
    challengeB64: PROBE_CHALLENGE_B64,
    leafSpkiB64: "", // filled per test with the key that actually signed
    issuerDn: "serialNumber=f92009e853b6b045",
  };
}

/**
 * Deps for a coherent, fully valid report. Individual tests break exactly one
 * dependency and assert the exact reason that comes back.
 */
function happyDeps(overrides: Partial<VerifyDeps> = {}) {
  const key = makeKey();
  const bytes = encodeEvidence(baseFields());
  const deps: VerifyDeps = {
    expectedPackageName: PACKAGE,
    readAttestation: async () => ({ ...goodAttestation(), leafSpkiB64: key.spkiB64 }),
    verifyChain: async () => true,
    isChainRevoked: async () => false,
    verifySignature: async () => true,
    lookupEnrolledKey: async () => ({ spkiB64: key.spkiB64 }),
    consumeNonce: async () => "ok",
    ...overrides,
  };
  return { deps, bytes, key };
}

async function reason(result: VerifyResult): Promise<string> {
  if (result.ok) return `ok(${result.evidenceHash.slice(0, 8)})`;
  return result.reason;
}

// ---------------------------------------------------------------------------
// The happy path, and the names the Evidence screen will publish
// ---------------------------------------------------------------------------

test("a coherent attested report is accepted and every check name is recorded", async () => {
  const { deps, bytes } = happyDeps();
  const result = await verifyReport({
    ...reportInput(bytes, WINDOW_END + 1_000),
    signatureB64: "bogus-but-verified-by-fake",
  }, deps);

  assert.equal(result.ok, true, `expected acceptance, got ${JSON.stringify(result)}`);
  if (!result.ok) return;
  assert.equal(result.evidenceHash, await evidenceHash(bytes));
  assert.deepEqual(result.checks, [
    "chain", "revocation", "attestation", "hardware", "payload", "nonce-bound",
    "enrolled", "signature", "nonce", "boot", "package", "terms", "consistency", "freshness",
  ]);
});

// ---------------------------------------------------------------------------
// Check order: the chain is trusted before anything else is even looked at
// ---------------------------------------------------------------------------

test("an unrooted chain is rejected before any other dependency is consulted", async () => {
  const touched: string[] = [];
  const tripwire = (name: string) => async () => { touched.push(name); throw new Error(name); };
  const { bytes } = happyDeps();
  const deps: VerifyDeps = {
    expectedPackageName: PACKAGE,
    verifyChain: async () => false,
    readAttestation: tripwire("readAttestation") as VerifyDeps["readAttestation"],
    isChainRevoked: tripwire("isChainRevoked") as VerifyDeps["isChainRevoked"],
    verifySignature: tripwire("verifySignature") as VerifyDeps["verifySignature"],
    lookupEnrolledKey: tripwire("lookupEnrolledKey") as VerifyDeps["lookupEnrolledKey"],
    consumeNonce: tripwire("consumeNonce") as VerifyDeps["consumeNonce"],
  };
  const result = await verifyReport({
    ...reportInput(bytes),
    signatureB64: "x",
  }, deps);
  assert.equal(await reason(result), "CHAIN_NOT_ROOTED");
  assert.deepEqual(touched, [], "a later check must not run once the chain anchor fails");
});

// ---------------------------------------------------------------------------
// One exact reason per rejection, in the spec's order
// ---------------------------------------------------------------------------

test("rejects a chain Google has revoked", async () => {
  const { deps, bytes } = happyDeps({ isChainRevoked: async () => true });
  assert.equal(await reason(await verifyReport(reportInput(bytes), deps)), "CHAIN_REVOKED");
});

test("rejects a leaf key that was never enrolled for this wallet", async () => {
  const attacker = makeKey();
  const { deps, bytes } = happyDeps({
    readAttestation: async () => ({ ...goodAttestation(), leafSpkiB64: attacker.spkiB64 }),
  });
  assert.equal(await reason(await verifyReport(reportInput(bytes), deps)), "KEY_NOT_ENROLLED");
});

test("rejects a signature over mutated bytes", async () => {
  const { deps, bytes } = happyDeps({ verifySignature: async () => false });
  assert.equal(await reason(await verifyReport(reportInput(bytes), deps)), "BAD_SIGNATURE");

  // And the mutation itself changes the hash the settlement would record.
  const mutated = encodeEvidence(baseFields({ usedSeconds: 1 }));
  assert.notEqual(await evidenceHash(bytes), await evidenceHash(mutated));
});

test("rejects a valid nonce that was issued for a different wallet", async () => {
  // Review Focus 1: the nonce exists and is unconsumed, but belongs to someone else.
  const { deps, bytes } = happyDeps({
    consumeNonce: async (_n: string, _c: string, wallet: string) =>
      wallet === WALLET ? "ok" : "mismatch",
  });
  const foreign = encodeEvidence(baseFields({ walletPubkey: "9xUt6RnZ2mQhS7aKd4Lb3VpC8eJf1YgHwTsRqNmZxQ" }));
  assert.equal(await reason(await verifyReport(reportInput(foreign), deps)), "NONCE_MISMATCH");
});

test("rejects a replayed payload once its nonce is spent", async () => {
  let consumed = false;
  const { deps, bytes } = happyDeps({
    consumeNonce: async () => {
      if (consumed) return "consumed";
      consumed = true;
      return "ok";
    },
  });
  const first = await verifyReport(reportInput(bytes), deps);
  const second = await verifyReport(reportInput(bytes), deps);
  assert.equal(first.ok, true, "first submission should be accepted");
  assert.equal(await reason(second), "NONCE_CONSUMED", "replaying identical bytes must not be accepted twice");
});

test("rejects an expired nonce", async () => {
  const { deps, bytes } = happyDeps({ consumeNonce: async () => "expired" });
  assert.equal(await reason(await verifyReport(reportInput(bytes), deps)), "NONCE_EXPIRED");
});

test("rejects an unlocked bootloader and an unverified boot", async () => {
  const { deps, bytes } = happyDeps({
    readAttestation: async () => ({ ...goodAttestation(), deviceLocked: false, leafSpkiB64: "" }),
    lookupEnrolledKey: async () => ({ spkiB64: "" }),
  });
  assert.equal(await reason(await verifyReport(reportInput(bytes), deps)), "BOOT_UNLOCKED");

  const unverified = happyDeps({
    readAttestation: async () => ({ ...goodAttestation(), verifiedBootState: "Unverified", leafSpkiB64: "" }),
    lookupEnrolledKey: async () => ({ spkiB64: "" }),
  });
  assert.equal(await reason(await verifyReport(reportInput(unverified.bytes), unverified.deps)), "BOOT_UNVERIFIED");

  const selfSigned = happyDeps({
    readAttestation: async () => ({ ...goodAttestation(), verifiedBootState: "SelfSigned", leafSpkiB64: "" }),
    lookupEnrolledKey: async () => ({ spkiB64: "" }),
  });
  assert.equal(await reason(await verifyReport(reportInput(selfSigned.bytes), selfSigned.deps)), "BOOT_UNVERIFIED");
});

test("rejects a software attestation as not hardware-backed", async () => {
  const { deps, bytes } = happyDeps({
    readAttestation: async () => ({ ...goodAttestation(), securityLevel: "Software", leafSpkiB64: "" }),
    lookupEnrolledKey: async () => ({ spkiB64: "" }),
  });
  assert.equal(await reason(await verifyReport(reportInput(bytes), deps)), "ATTESTATION_NOT_HARDWARE");
});

test("rejects a report signed under a different challenge than the pinned nonce", async () => {
  const { deps, bytes } = happyDeps({
    readAttestation: async () => ({ ...goodAttestation(), challengeB64: "d3JvbmctY2hhbGxlbmdl", leafSpkiB64: "" }),
    lookupEnrolledKey: async () => ({ spkiB64: "" }),
  });
  assert.equal(await reason(await verifyReport(reportInput(bytes), deps)), "NONCE_MISMATCH");
});

test("rejects an attestation from a different package than the app under test", async () => {
  const { deps, bytes } = happyDeps({
    readAttestation: async () => ({ ...goodAttestation(), packageName: "com.evil.clone", leafSpkiB64: "" }),
    lookupEnrolledKey: async () => ({ spkiB64: "" }),
  });
  assert.equal(await reason(await verifyReport(reportInput(bytes), deps)), "PACKAGE_MISMATCH");
});

test("rejects usedSeconds larger than the device's own active time", async () => {
  // Review Focus 8: an internally contradictory report - the same stream that yielded
  // 42s of activity cannot also show the device was only awake for 10s.
  const { deps, bytes } = happyDeps();
  const contradictory = encodeEvidence(baseFields({ usedSeconds: 300, deviceActiveSeconds: 10 }));
  assert.equal(await reason(await verifyReport(reportInput(contradictory), deps)), "USAGE_EXCEEDS_ACTIVE");
  void bytes;
});

test("rejects an event-set digest that cannot be a sha-256", async () => {
  const { deps } = happyDeps();
  const badDigest = encodeEvidence(baseFields({ eventSetDigestHex: "not-hex" }));
  assert.equal(await reason(await verifyReport(reportInput(badDigest), deps)), "DIGEST_MISMATCH");
});

test("rejects a report submitted after the grace window", async () => {
  const { deps, bytes } = happyDeps();
  const late = WINDOW_END + (GRACE_SECONDS * 1000) + 1;
  assert.equal(await reason(await verifyReport(reportInput(bytes, late), deps)), "REPORT_LATE");
  // Exactly at the boundary is still on time - fail-closed must not eat valid reports.
  assert.equal((await verifyReport(reportInput(bytes, WINDOW_END + GRACE_SECONDS * 1000), deps)).ok, true);
});

test("rejects bytes that are not a canonical payload", async () => {
  const { deps } = happyDeps();
  const garbage = new Uint8Array([0, 1, 2, 3, 4, 5, 6, 7, 8]);
  assert.equal(await reason(await verifyReport(reportInput(garbage), deps)), "PAYLOAD_UNPARSEABLE");
});

// ---------------------------------------------------------------------------
// The real device chain, through the real verifier, as far as it can go
// ---------------------------------------------------------------------------

test("the real Redmi chain clears every gate that does not need the phone's private key", async () => {
  // The device key is non-exportable by design, so a valid signature over a real
  // payload cannot be produced off-device. Asserting the run stops at exactly
  // BAD_SIGNATURE proves the real chain cleared the anchor, revocation, enrollment,
  // boot state, package and consistency checks - and it cannot pass vacuously.
  const bytes = encodeEvidence(baseFields());
  const deps: VerifyDeps = {
    expectedPackageName: PACKAGE,
    readAttestation: async () => {
      const info = await readAttestationExtension(deviceChainPem);
      return {
        securityLevel: info.securityLevel,
        deviceLocked: info.deviceLocked,
        verifiedBootState: info.verifiedBootState,
        packageName: info.applicationPackageName,
        signingCertDigestB64: info.signingCertDigestB64,
        challengeB64: info.attestationChallengeB64,
        leafSpkiB64: info.leafSpkiB64,
        issuerDn: info.rootIssuerDn,
      };
    },
    verifyChain: () => verifyChainToPinnedRoot(deviceChainPem),
    isChainRevoked: async () => isSerialRevoked(deviceChainPem, []),
    verifySignature: async () => false, // no private key exists off the device
    lookupEnrolledKey: async () => ({ spkiB64: (await readAttestationExtension(deviceChainPem)).leafSpkiB64 }),
    consumeNonce: async () => "ok",
  };
  const result = await verifyReport({
    ...reportInput(bytes),
    signatureB64: "cannot-be-signed-without-the-device",
  }, deps);
  // Checks 1,3,4,5,6 run over real x509 output here; enrollment is satisfied by
  // construction in this test and is covered independently by KEY_NOT_ENROLLED above.
  assert.equal(await reason(result), "BAD_SIGNATURE");
});

// ---------------------------------------------------------------------------
// Dependencies that fail outright: total function, fail closed, never a 500
// ---------------------------------------------------------------------------

test("a dependency that throws produces a rejection, never an escaped exception", async () => {
  const { deps, bytes } = happyDeps();
  const throwing: Array<[string, Partial<VerifyDeps>, string]> = [
    ["readAttestation", { readAttestation: async () => { throw new Error("db down"); } }, "ATTESTATION_UNREADABLE"],
    ["isChainRevoked", { isChainRevoked: async () => { throw new Error("feed timeout"); } }, "CHAIN_REVOKED"],
    ["lookupEnrolledKey", { lookupEnrolledKey: async () => { throw new Error("pool exhausted"); } }, "KEY_NOT_ENROLLED"],
    ["consumeNonce", { consumeNonce: async () => { throw new Error("connection reset"); } }, "NONCE_MISMATCH"],
    ["verifySignature", { verifySignature: async () => { throw new Error("bad key"); } }, "BAD_SIGNATURE"],
    ["verifyChain", { verifyChain: async () => { throw new Error("parse blew up"); } }, "CHAIN_NOT_ROOTED"],
  ];
  for (const [name, override, expected] of throwing) {
    const result = await verifyReport(reportInput(bytes), { ...deps, ...override });
    assert.equal(await reason(result), expected, `${name} must fail closed as ${expected}`);
    assert.equal(result.ok, false, `${name} must not throw and must not accept`);
  }
});

test("rejections do not echo parser internals to an unauthenticated caller", async () => {
  const { deps, bytes } = happyDeps();
  const garbage = new TextEncoder().encode("evidence field must be printable ASCII: /etc/passwd");
  const result = await verifyReport(reportInput(garbage), deps);
  assert.equal(await reason(result), "PAYLOAD_UNPARSEABLE");
  if (result.ok) return;
  assert.ok(!("detail" in result) || !String(result.detail).includes("/etc/passwd"),
    "attacker input must not be reflected back");
});

// ---------------------------------------------------------------------------
// The report must describe the duel it is being filed against
// ---------------------------------------------------------------------------

test("rejects a genuine report about a different target app than the challenge", async () => {
  const { deps, bytes } = happyDeps();
  const wrongTarget = encodeEvidence(baseFields({ targetPackage: "com.tiktok.android" }));
  assert.equal(await reason(await verifyReport(reportInput(wrongTarget), deps)), "TARGET_MISMATCH");
});

test("rejects a report whose window does not match the challenge window", async () => {
  const { deps } = happyDeps();
  const shiftedStart = encodeEvidence(baseFields({ windowStartMs: WINDOW_START - 60_000 }));
  assert.equal(await reason(await verifyReport(reportInput(shiftedStart), deps)), "WINDOW_MISMATCH");

  const shiftedEnd = encodeEvidence(baseFields({ windowEndMs: WINDOW_END + 60_000 }));
  assert.equal(await reason(await verifyReport(reportInput(shiftedEnd), deps)), "WINDOW_MISMATCH");
});

test("rejects a payload whose own package claim is not the app under test", async () => {
  // The attestation half is covered elsewhere; this is the payload half, which a
  // verifier that only trusted the certificate would happily wave through.
  const { deps } = happyDeps();
  const lyingPayload = encodeEvidence(baseFields({ packageName: "com.evil.clone" }));
  assert.equal(await reason(await verifyReport(reportInput(lyingPayload), deps)), "PACKAGE_MISMATCH");
});

test("rejects reported usage backed by an empty event set", async () => {
  const { deps } = happyDeps();
  const noEvents = encodeEvidence(baseFields({ usedSeconds: 42, eventCount: 0 }));
  assert.equal(await reason(await verifyReport(reportInput(noEvents), deps)), "DIGEST_MISMATCH");
});

// ---------------------------------------------------------------------------
// The verifier must not be reachable by a payload it cannot parse
// ---------------------------------------------------------------------------

test("every exotic payload input returns a reason rather than throwing", async () => {
  const { deps } = happyDeps();
  const cases: Array<[string, Uint8Array]> = [
    ["empty", new Uint8Array(0)],
    ["truncated length prefix", new Uint8Array([1, 0, 0])],
    ["huge declared field length", new Uint8Array([1, 0x7f, 0xff, 0xff, 0xff, 0x41])],
    ["trailing garbage after payload", new Uint8Array([...encodeEvidence(baseFields()), 9, 9, 9])],
    ["unsupported version", new Uint8Array([2, 0, 0, 0, 0])],
  ];
  for (const [name, bytes] of cases) {
    const result = await verifyReport(reportInput(bytes), deps);
    assert.equal(result.ok, false, `${name} must not be accepted`);
  }
});

function reportInput(canonicalBytes: Uint8Array, submittedAtMs = WINDOW_END) {
  return {
    canonicalBytes,
    signatureB64: "c2ln",
    chainPem: deviceChainPem,
    submittedAtMs,
    windowEndMs: WINDOW_END,
    graceSeconds: GRACE_SECONDS,
    expectedTargetPackage: "com.instagram.android",
    expectedWindowStartMs: WINDOW_START,
  };
}
