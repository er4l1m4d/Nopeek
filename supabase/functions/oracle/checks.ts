/**
 * The ten-check attestation verifier (spec 4.3), in the order the spec fixes.
 *
 * Order is load-bearing, not cosmetic: the cheapest and most fundamental gate runs
 * first, so a forged chain never reaches the point where we are reasoning about
 * usage numbers. `checks.test.ts` asserts that with a tripwire - if anything after
 * the anchor is consulted once the chain fails, that test breaks.
 *
 * Two properties this module exists to hold:
 *
 * 1. **It never throws.** Every failure comes back as `{ ok: false, reason }`. The
 *    oracle's report endpoint is unauthenticated by design (the signature is the
 *    authentication), so an exception here would surface as a retryable HTTP 500 and
 *    turn a definite "this evidence is forged" into a transient server error.
 * 2. **Every failure is in the safe direction.** Anything it cannot positively
 *    establish is a rejection. Ambiguity never settles money in the reporter's favour.
 *
 * The chain trust is injected as `verifyChain`, bound in production to x509's
 * `verifyChainToPinnedRoot`. There is deliberately no root certificate anywhere in
 * this module's inputs: if the anchor could be passed in by a caller, one wiring
 * slip would reduce the entire design to "the chain vouches for itself" - the
 * failure mode the plan rejected as ledger Row 9.
 */

import type { EvidenceFields } from "./evidence.ts";
import { decodeEvidence, evidenceHash } from "./evidence.ts";
import {
  isSerialRevoked,
  readAttestationExtension,
  verifyChainToPinnedRoot,
  verifyEcdsaSignature,
} from "./x509.ts";

/**
 * Reasons a report can be refused. Three beyond the plan's original twelve, each
 * because the plan predated code that could actually detect them:
 *   CHAIN_REVOKED            - Google published this batch attestation key as leaked.
 *   ATTESTATION_NOT_HARDWARE - securityLevel is Software, so no hardware root of trust.
 *   ATTESTATION_UNREADABLE   - extension missing or malformed; strictness from x509.
 *   PAYLOAD_UNPARSEABLE      - bytes are not a canonical v1 payload at all.
 */
export type RejectReason =
  | "CHAIN_NOT_ROOTED"
  | "CHAIN_REVOKED"
  | "ATTESTATION_UNREADABLE"
  | "ATTESTATION_NOT_HARDWARE"
  | "PAYLOAD_UNPARSEABLE"
  | "KEY_NOT_ENROLLED"
  | "BAD_SIGNATURE"
  | "NONCE_MISMATCH"
  | "NONCE_CONSUMED"
  | "NONCE_EXPIRED"
  | "BOOT_UNLOCKED"
  | "BOOT_UNVERIFIED"
  | "PACKAGE_MISMATCH"
  | "USAGE_EXCEEDS_ACTIVE"
  | "DIGEST_MISMATCH"
  | "REPORT_LATE";

export type VerifyResult =
  | { ok: true; evidenceHash: string; checks: string[]; fields: EvidenceFields }
  | { ok: false; reason: RejectReason; detail?: string };

export interface AttestationFacts {
  securityLevel: string;
  deviceLocked: boolean;
  verifiedBootState: string;
  packageName: string;
  signingCertDigestB64: string;
  challengeB64: string;
  leafSpkiB64: string;
  issuerDn: string;
}

export type NonceStatus = "ok" | "mismatch" | "consumed" | "expired";

export interface VerifyDeps {
  /** The package the app was built as; the attested identity must equal it. */
  expectedPackageName: string;
  /** Bound to verifyChainToPinnedRoot in production. No root argument by design. */
  verifyChain(chainPem: string): Promise<boolean>;
  isChainRevoked(chainPem: string): Promise<boolean>;
  readAttestation(chainPem: string): Promise<AttestationFacts>;
  verifySignature(spkiB64: string, bytes: Uint8Array, signatureB64: string): Promise<boolean>;
  lookupEnrolledKey(walletPubkey: string): Promise<{ spkiB64: string } | null>;
  consumeNonce(nonceB64: string, challengePubkey: string, walletPubkey: string): Promise<NonceStatus>;
}

export interface ReportInput {
  /** The exact bytes the device signed. Never re-encoded on either side. */
  canonicalBytes: Uint8Array;
  signatureB64: string;
  chainPem: string;
  submittedAtMs: number;
  windowEndMs: number;
  graceSeconds: number;
}

/**
 * The names published at /evidence/<hash> and rendered by the Evidence screen.
 * Stable strings: a rename here is a change to a public artifact.
 */
const CHECK_NAMES = [
  "chain", "revocation", "attestation", "hardware", "payload",
  "nonce-bound", "enrolled", "signature", "nonce", "boot",
  "package", "consistency", "freshness",
] as const;

const SHA256_HEX = /^[0-9a-f]{64}$/;
const SOFTWARE_LEVELS = ["Software", "NotSet"];

export async function verifyReport(input: ReportInput, deps: VerifyDeps): Promise<VerifyResult> {
  const passed: string[] = [];
  const { canonicalBytes, signatureB64, chainPem } = input;

  // 1. Trust anchor. Byte-identity to the pinned Google root, plus per-link
  // signatures. Without this, nothing below means anything.
  let rooted = false;
  try {
    rooted = await deps.verifyChain(chainPem);
  } catch {
    rooted = false;
  }
  if (!rooted) return reject("CHAIN_NOT_ROOTED");
  passed.push(CHECK_NAMES[0]);

  // 2. A validly-signed chain is worthless if Google published its batch key as
  // leaked. Unavailable revocation data is treated as revoked, never as clean.
  let revoked = true;
  try {
    revoked = await deps.isChainRevoked(chainPem);
  } catch {
    revoked = true;
  }
  if (revoked) return reject("CHAIN_REVOKED");
  passed.push(CHECK_NAMES[1]);

  // 3. The attestation extension itself. x509 raises on anything malformed rather
  // than defaulting a field, and a raise here means "cannot be trusted".
  let facts: AttestationFacts;
  try {
    facts = await deps.readAttestation(chainPem);
  } catch (error) {
    return reject("ATTESTATION_UNREADABLE", message(error));
  }
  passed.push(CHECK_NAMES[2]);

  // 4. Software attestation proves a string was signed, not that a secure element
  // held the key - which is the entire difference between evidence and assertion.
  if (SOFTWARE_LEVELS.includes(facts.securityLevel)) {
    return reject("ATTESTATION_NOT_HARDWARE", facts.securityLevel);
  }
  passed.push(CHECK_NAMES[3]);

  // 5. The payload must be a canonical v1 document. Everything after this point reads
  // its values, so a permissive decode here would be the whole attack.
  let fields: EvidenceFields;
  try {
    fields = decodeEvidence(canonicalBytes);
  } catch (error) {
    return reject("PAYLOAD_UNPARSEABLE", message(error));
  }
  if (fields.version !== 1) return reject("PAYLOAD_UNPARSEABLE", `version ${fields.version}`);
  passed.push(CHECK_NAMES[4]);

  // 6. The attested challenge must be the nonce carried inside the signed payload.
  // Comparing the two rather than each against a third value means a substituted
  // nonce and a replayed attestation are the same rejection, correctly attributed.
  if (facts.challengeB64 !== fields.nonceB64) {
    return reject("NONCE_MISMATCH", "attested challenge is not the payload nonce");
  }
  passed.push(CHECK_NAMES[5]);

  // 7. The key that signed this report must be the one enrolled for this wallet, and
  // must be the leaf the chain attests. Three-way, or an enrolled key from another
  // device validates anything its owner signs.
  const enrolled = await deps.lookupEnrolledKey(fields.walletPubkey);
  if (!enrolled || enrolled.spkiB64 !== facts.leafSpkiB64) {
    return reject("KEY_NOT_ENROLLED");
  }
  passed.push(CHECK_NAMES[6]);

  // 8. Signature over the exact received bytes.
  let signed = false;
  try {
    signed = await deps.verifySignature(enrolled.spkiB64, canonicalBytes, signatureB64);
  } catch {
    signed = false;
  }
  if (!signed) return reject("BAD_SIGNATURE");
  passed.push(CHECK_NAMES[7]);

  // 9. Single-use, and bound to this (challenge, wallet) pair.
  const nonceStatus = await deps.consumeNonce(
    fields.nonceB64, fields.challengePubkey, fields.walletPubkey,
  );
  if (nonceStatus === "consumed") return reject("NONCE_CONSUMED");
  if (nonceStatus === "expired") return reject("NONCE_EXPIRED");
  if (nonceStatus !== "ok") return reject("NONCE_MISMATCH");
  passed.push(CHECK_NAMES[8]);

  // 10. Boot state. Both fields are hardware-enforced, which is why they are read
  // from RootOfTrust and never from a client-reported signal.
  if (!facts.deviceLocked) return reject("BOOT_UNLOCKED");
  if (facts.verifiedBootState !== "Verified") return reject("BOOT_UNVERIFIED", facts.verifiedBootState);
  passed.push(CHECK_NAMES[9]);

  // 11. Attested package must be ours, and must match the package the payload claims.
  // Corroboration rather than proof - it is software-enforced in the extension - but
  // a mismatch is disqualifying regardless of which tier the claim sits in.
  if (facts.packageName !== deps.expectedPackageName || fields.packageName !== deps.expectedPackageName) {
    return reject("PACKAGE_MISMATCH", facts.packageName);
  }
  passed.push(CHECK_NAMES[10]);

  // 12. Internal consistency. Attestation proves provenance, not truthfulness, so
  // the only lie that is cheap to catch is a self-contradicting one: usage cannot
  // exceed the device's own total active time, and the event-set digest must at least
  // be a sha-256 or it corresponds to no event set at all.
  if (fields.usedSeconds > fields.deviceActiveSeconds) return reject("USAGE_EXCEEDS_ACTIVE");
  if (!SHA256_HEX.test(fields.eventSetDigestHex)) return reject("DIGEST_MISMATCH");
  if (fields.usedSeconds > 0 && fields.eventCount === 0) {
    return reject("DIGEST_MISMATCH", "usage reported with an empty event set");
  }
  passed.push(CHECK_NAMES[11]);

  // 13. Freshness. Absent or late evidence is already a loss in the settlement rule;
  // here it is refused so a late report cannot be quietly filed as a pass.
  const deadline = input.windowEndMs + input.graceSeconds * 1000;
  if (input.submittedAtMs > deadline) return reject("REPORT_LATE");
  passed.push(CHECK_NAMES[12]);

  return {
    ok: true,
    evidenceHash: await evidenceHash(canonicalBytes),
    checks: passed,
    fields,
  };
}

function reject(reason: RejectReason, detail?: string): VerifyResult {
  return { ok: false, reason, ...(detail ? { detail } : {}) };
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/**
 * Production dependency bundle. Keeping the wiring in one named place is what makes
 * the "no caller-supplied anchor" property auditable: this is the only function that
 * touches x509 directly, and it is the only place verifyChain may come from.
 */
export function productionDeps(expectedPackageName: string, hooks: {
  lookupEnrolledKey: VerifyDeps["lookupEnrolledKey"];
  consumeNonce: VerifyDeps["consumeNonce"];
  /** Cached copy of Google's revocation feed; empty list is refused, never trusted. */
  revokedEntries: () => Promise<{ issuer: string; serial: string }[]>;
}): VerifyDeps {
  return {
    expectedPackageName,
    verifyChain: (chainPem) => verifyChainToPinnedRoot(chainPem),
    isChainRevoked: async (chainPem) => {
      const entries = await hooks.revokedEntries();
      return isSerialRevoked(chainPem, entries);
    },
    readAttestation: async (chainPem) => {
      const info = await readAttestationExtension(chainPem);
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
    verifySignature: (spkiB64, bytes, signatureB64) =>
      verifyEcdsaSignature(spkiB64, bytes, signatureB64),
    lookupEnrolledKey: hooks.lookupEnrolledKey,
    consumeNonce: hooks.consumeNonce,
  };
}
